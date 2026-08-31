package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiReceiver
import org.churchpresenter.ndi.NdiSourceInfo
import java.awt.image.BufferedImage

/**
 * How long without a frame it takes to decide a source has stopped sending rather than paused.
 *
 * Long enough to ride out a sender's own hiccup, short enough that a camera someone unplugged does
 * not stay frozen on screen for the rest of the service.
 *
 * Measured against the clock rather than counted in polls, because how long a poll takes is the
 * receive timeout on a live source and nearly nothing on one that answers immediately — a count
 * would mean two seconds in one case and two milliseconds in the other.
 */
private const val IDLE_CLEAR_MS = 2_000L

/**
 * A breath between empty polls.
 *
 * The receive itself blocks in the native library for up to its own timeout, so in production this
 * costs nothing measurable. It is here for the case where it does not block — a stand-in library in
 * a test — so that an idle capture loop does not spin a core.
 */
private const val IDLE_POLL_MS = 5L

private const val NANOS_PER_MS = 1_000_000

/**
 * The receiving side of the Canvas: one NDI connection per distinct source, however many layers
 * are drawing it.
 *
 * The same shape and the same reason as [SharedCameraFrameCache] — a source shown on the canvas
 * preview *and* on the presenter output is one composable each, and each would otherwise open its
 * own receiver, so the sender would pay to encode the stream twice and the network would carry it
 * twice. Reference counted: the first layer to want a source connects, the last one to let go
 * disconnects.
 *
 * A class rather than the object it is reached through, so the whole of it is testable:
 * [openReceiver] is the seam, and a test builds one over a `FakeNdiLibrary` with no runtime installed. That is a
 * constructor parameter, not the ad-hoc mutable `internal var` on a singleton that AGENT.md rules
 * out — nothing has to be restored afterwards because nothing is global.
 */
open class NdiFrameCache(
    private val openReceiver: (NdiSourceInfo, NdiBandwidth) -> NdiReceiver?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<String, CacheEntry>()

    /**
     * What a layer draws: the latest frame, and whether the receiver is connected at all.
     *
     * The two are separate because they answer different questions. A null frame with [connected]
     * true is a source that has not sent anything yet, which is normal and says "waiting"; a null
     * frame with it false is a runtime that is not there or a source that is gone, which is worth
     * telling the operator about.
     */
    class NdiFlows(val frame: StateFlow<ImageBitmap?>, val connected: StateFlow<Boolean>)

    private class CacheEntry {
        val frame = MutableStateFlow<ImageBitmap?>(null)
        val connected = MutableStateFlow(false)
        var refCount = 0
        var captureJob: Job? = null
    }

    /**
     * The key two layers must share to share one connection.
     *
     * The bandwidth is part of it: two layers of the same source at different bandwidths are two
     * different streams from the sender, and folding them together would silently give one of them
     * the wrong picture.
     */
    internal fun keyFor(source: SceneSource.NdiSource): String =
        "${source.sourceName}|${source.sourceAddress}|${source.lowBandwidth}"

    /** What [source] resolves to on the network. */
    internal fun infoFor(source: SceneSource.NdiSource): NdiSourceInfo =
        NdiSourceInfo(source.sourceName, source.sourceAddress)

    /**
     * Frames for [source], connecting on the first caller and sharing the connection afterwards.
     *
     * Every acquire must be matched by a [release] — the composables that call this do it from a
     * `DisposableEffect` keyed on the same fields the key is built from.
     */
    @Synchronized
    fun acquire(source: SceneSource.NdiSource): NdiFlows {
        val entry = entries.getOrPut(keyFor(source)) { CacheEntry() }
        entry.refCount++
        if (entry.refCount == 1) {
            entry.captureJob = scope.launch { capture(source, entry) }
        }
        return NdiFlows(entry.frame, entry.connected)
    }

    /** Lets go of [source]. The connection is dropped when the last layer does. */
    @Synchronized
    fun release(source: SceneSource.NdiSource) {
        val key = keyFor(source)
        val entry = entries[key] ?: return
        entry.refCount--
        if (entry.refCount > 0) return
        entry.captureJob?.cancel()
        entry.captureJob = null
        entry.frame.value = null
        entry.connected.value = false
        entries.remove(key)
    }

    /** Whether a connection is open for [source] — how a test sees the sharing, and the release. */
    @Synchronized
    internal fun isConnected(source: SceneSource.NdiSource): Boolean = entries.containsKey(keyFor(source))

    // TooGenericExceptionCaught, deliberately and narrowly: everything inside this call ends in a
    // native library, and what a bad frame or a runtime that has gone away throws on the way back
    // is not a type this code can enumerate. A layer that stops receiving is a black rectangle; the
    // same throw uncaught takes down the coroutine that would have closed the receiver, mid-service.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun capture(source: SceneSource.NdiSource, entry: CacheEntry) {
        val receiver = withContext(Dispatchers.IO) {
            openReceiver(infoFor(source), NdiBandwidth.of(source.lowBandwidth))?.takeIf { it.open() }
        }
        if (receiver == null) {
            // Not an error worth reporting: the ordinary cause is an NDI Runtime that is not
            // installed, which the layer already says on screen and the settings card explains.
            entry.connected.value = false
            return
        }
        entry.connected.value = true
        try {
            pump(receiver, entry)
        } catch (_: CancellationException) {
            // Ordinary teardown — the last layer let go.
        } catch (e: Exception) {
            System.err.println("[NDI Input] ${source.sourceName}: ${e.message}")
        } finally {
            entry.connected.value = false
            // NonCancellable, and it matters: the ordinary way out of here is the last layer being
            // released, which cancels this coroutine — and a plain `withContext` in a cancelled
            // coroutine throws instead of running, so the receiver would never be closed. The
            // sender would go on encoding for a subscriber that no longer exists, once per NDI
            // layer the operator removes during a service.
            withContext(NonCancellable + Dispatchers.IO) { receiver.close() }
        }
    }

    /** Reads frames into [entry] until the coroutine is cancelled. */
    private suspend fun pump(receiver: NdiReceiver, entry: CacheEntry) {
        var lastFrameAt = System.nanoTime()
        while (currentCoroutineContext().isActive) {
            // The receive itself blocks in the native library for up to its timeout, so it belongs
            // off the shared Default dispatcher whether or not a frame turns up.
            val image = withContext(Dispatchers.IO) {
                receiver.receive()?.let { frame ->
                    BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB).apply {
                        // Copied out before the next receive, which overwrites the pixel buffer.
                        setRGB(0, 0, frame.width, frame.height, frame.pixels, 0, frame.width)
                    }
                }
            }
            if (image == null) {
                val idleMs = (System.nanoTime() - lastFrameAt) / NANOS_PER_MS
                if (idleMs > IDLE_CLEAR_MS && entry.frame.value != null) entry.frame.value = null
                delay(IDLE_POLL_MS)
                continue
            }
            lastFrameAt = System.nanoTime()
            entry.frame.value = image.toComposeImageBitmap()
        }
    }
}

/**
 * The one cache the app draws NDI layers from, over the app's one runtime.
 *
 * Holds no logic of its own — everything it does is [NdiFrameCache]'s, which is an ordinary class a
 * test builds over a fake library.
 */
object SharedNdiFrameCache : NdiFrameCache(
    openReceiver = { source, bandwidth -> NdiManager.createReceiver(source, bandwidth, RECEIVER_NAME) },
)

/**
 * What the *sender* sees this app called in its own connection list.
 *
 * Worth naming rather than leaving to the runtime: an operator looking at why their camera has an
 * extra receiver should see which app it is.
 */
private const val RECEIVER_NAME = "ChurchPresenter Canvas"
