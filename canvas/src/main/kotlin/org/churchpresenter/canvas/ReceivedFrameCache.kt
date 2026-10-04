package org.churchpresenter.canvas

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import org.churchpresenter.diagnostics.Log

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
 * One network video receiver as the cache drives it: connect, read a picture, disconnect.
 *
 * The three things NDI's and OMT's receivers have in common, which is all the cache needs — each
 * protocol's layer supplies an implementation over its own receiver type.
 */
interface NetworkPictureReceiver {
    /** Connects. False means the library refused, and the cache reports the layer as not connected. */
    fun open(): Boolean

    /** The next picture, copied out of the receiver's reused buffer, or null when none arrived. */
    fun receive(): BufferedImage?

    fun close()
}

/** Packed ARGB [pixels] as a picture of its own — copied, since the receiver reuses [pixels]. */
internal fun argbImage(pixels: IntArray, width: Int, height: Int): BufferedImage =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, width, height, pixels, 0, width)
    }

/**
 * The receiving side of the Canvas for a network video source: one connection per distinct source,
 * however many layers are drawing it.
 *
 * The same shape and the same reason as [SharedCameraFrameCache] — a source shown on the canvas
 * preview *and* on the presenter output is one composable each, and each would otherwise open its
 * own receiver, so the sender would pay to encode the stream twice and the network would carry it
 * twice. Reference counted: the first layer to want a source connects, the last one to let go
 * disconnects.
 *
 * Shared by [NdiFrameCache] and [OmtFrameCache], which supply what a source is keyed by and how one
 * is connected to. Everything that took a bug report to get right — the open and close that survive
 * cancellation, the idle clear — lives here once rather than twice.
 *
 * [openReceiver] is the seam: a test builds a cache over a fake library with nothing installed.
 */
abstract class ReceivedFrameCache<S>(
    private val openReceiver: (S) -> NetworkPictureReceiver?,
    /** The tag on a failed capture's log line — `NDI Input`, `OMT Input`. */
    private val logTag: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<String, CacheEntry>()

    /** Which census count this cache's connections are recorded under. */
    internal abstract val resource: SharedResource

    /**
     * The key two layers must share to share one connection. Anything that changes the stream the
     * sender produces — a proxy or preview flag — belongs in it, or two layers would silently share
     * the wrong picture.
     */
    internal abstract fun keyFor(source: S): String

    /** How [source] is named in a log line. */
    protected abstract fun labelOf(source: S): String

    /**
     * What a layer draws: the latest frame, and whether the receiver is connected at all.
     *
     * The two are separate because they answer different questions. A null frame with [connected]
     * true is a source that has not sent anything yet, which is normal and says "waiting"; a null
     * frame with it false is a library that is not there or a source that is gone, which is worth
     * telling the operator about.
     */
    class Flows(val frame: StateFlow<ImageBitmap?>, val connected: StateFlow<Boolean>)

    private class CacheEntry {
        val frame = MutableStateFlow<ImageBitmap?>(null)
        val connected = MutableStateFlow(false)
        var refCount = 0
        var captureJob: Job? = null
    }

    /**
     * Frames for [source], connecting on the first caller and sharing the connection afterwards.
     *
     * Every acquire must be matched by a [release] — the composables that call this do it from a
     * `DisposableEffect` keyed on the same fields the key is built from.
     */
    @Synchronized
    fun acquire(source: S): Flows {
        val entry = entries.getOrPut(keyFor(source)) { CacheEntry() }
        ResourceCensus.record(resource, entries.size)
        entry.refCount++
        if (entry.refCount == 1) {
            entry.captureJob = scope.launch { capture(source, entry) }
        }
        return Flows(entry.frame, entry.connected)
    }

    /** Lets go of [source]. The connection is dropped when the last layer does. */
    @Synchronized
    fun release(source: S) {
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
    internal fun isConnected(source: S): Boolean = entries.containsKey(keyFor(source))

    // TooGenericExceptionCaught, deliberately and narrowly: everything inside this call ends in a
    // native library, and what a bad frame or a library that has gone away throws on the way back
    // is not a type this code can enumerate. A layer that stops receiving is a black rectangle; the
    // same throw uncaught takes down the coroutine that would have closed the receiver, mid-service.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun capture(source: S, entry: CacheEntry) {
        // NonCancellable around the open for the same reason the close below has it: the last layer
        // can let go while the receiver is still being opened, and a cancellable open throws out of
        // here with the native receiver already alive and nothing holding it — the sender goes on
        // encoding for a subscriber that no longer exists.
        val receiver = withContext(NonCancellable + Dispatchers.IO) {
            openReceiver(source)?.takeIf { it.open() }
        }
        if (receiver == null) {
            // Not an error worth reporting: the ordinary cause is a library that is not installed,
            // which the layer already says on screen and the settings card explains.
            entry.connected.value = false
            return
        }
        entry.connected.value = true
        try {
            pump(receiver, entry)
        } catch (_: CancellationException) {
            // Ordinary teardown — the last layer let go.
        } catch (e: Exception) {
            Log.warn(logTag, "${labelOf(source)}: ${e.message}")
        } finally {
            entry.connected.value = false
            // NonCancellable, and it matters: the ordinary way out of here is the last layer being
            // released, which cancels this coroutine — and a plain `withContext` in a cancelled
            // coroutine throws instead of running, so the receiver would never be closed. The
            // sender would go on encoding for a subscriber that no longer exists, once per layer
            // the operator removes during a service.
            withContext(NonCancellable + Dispatchers.IO) { receiver.close() }
        }
    }

    /** Reads frames into [entry] until the coroutine is cancelled. */
    private suspend fun pump(receiver: NetworkPictureReceiver, entry: CacheEntry) {
        var lastFrameAt = System.nanoTime()
        while (currentCoroutineContext().isActive) {
            // The receive itself blocks in the native library for up to its timeout, so it belongs
            // off the shared Default dispatcher whether or not a frame turns up.
            val image = withContext(Dispatchers.IO) { receiver.receive() }
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
