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
import org.churchpresenter.diagnostics.CrashReporter
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** How often the shared conversion loop looks for a new decoded frame — about 60fps. */
private const val VIDEO_POLL_MS = 16L

/** Let the video surface attach before asking the player to play. */
private const val PLAYER_SETTLE_MS = 100L

private const val VOLUME_PERCENT_SCALE = 100
private const val BYTES_PER_PIXEL = 4

// libvlc media options, written out per branch rather than spread from a list: play() is a Java
// vararg, so a spread copies the array on every call.
private const val VLC_OPT_TIGHT_CLOCK = ":clock-jitter=0"
private const val VLC_OPT_LOOP = ":input-repeat=65535"

/**
 * Which decode two layers must agree on to share one.
 *
 * Volume is deliberately **not** part of it. Two layers of one file at different volumes are still
 * one decode — the picture is identical and only the audio differs — and keying on it would both
 * decode the file twice and restart playback on every drag of a volume slider.
 */
internal data class SceneVideoSpec(val filePath: String, val loop: Boolean)

/**
 * One decoded video, however many layers are drawing it.
 *
 * [frameVersion] is bumped by the decoder each time it writes a new picture into [frame]; the cache
 * polls it rather than being pushed, so conversion happens on the cache's own coroutine and never
 * on the decoder's render thread — blocking that stalls the audio pipeline.
 */
internal interface SceneVideoHandle {
    val frameVersion: Long
    val frame: BufferedImage?
    fun setVolume(percent: Int)
    fun close()
}

/**
 * One decode per video file, however many layers draw it.
 *
 * The third of the canvas's shared caches, and the one that costs the most. A video layer is
 * composed once in the canvas editor, once in each sidebar live preview and once on each presenter
 * output, and every one of those instances used to build **its own** `MediaPlayerFactory`, its own
 * player and its own conversion loop — decoding the same file at full source resolution that many
 * times over, and converting every frame to an `ImageBitmap` per instance at 60fps.
 *
 * It also played the audio that many times. Nothing mixed those streams; they simply overlapped.
 *
 * Reference counted like [SharedCameraFrameCache] and [NdiFrameCache]: the first layer to want a
 * file starts the decode, the last one to let go stops it.
 *
 * [openPlayer] is the seam — a test supplies one that hands back frames without libvlc, which is
 * not present in the test JVM. A constructor parameter, not the ad-hoc mutable `internal var` on a
 * singleton that AGENT.md rules out.
 */
internal open class SceneVideoCache(
    private val openPlayer: (SceneVideoSpec) -> SceneVideoHandle?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val entries = mutableMapOf<SceneVideoSpec, CacheEntry>()

    private class CacheEntry {
        val frame = MutableStateFlow<ImageBitmap?>(null)
        var refCount = 0
        var job: Job? = null
        var handle: SceneVideoHandle? = null

        /**
         * The volume the layers want, held whether or not a player exists yet.
         *
         * Opening is asynchronous, so the first `acquire` almost always names its volume before
         * there is anything to set it on. Without somewhere to keep it that setting was simply
         * dropped and the file played at the decoder's default — the source's own volume never
         * reached it.
         */
        var volumePercent = VOLUME_PERCENT_SCALE
    }

    /**
     * Frames for [spec], starting the decode on the first caller and sharing it afterwards.
     *
     * Every acquire must be matched by a [release], from the same `DisposableEffect` that made it.
     */
    @Synchronized
    fun acquire(spec: SceneVideoSpec, volume: Float): StateFlow<ImageBitmap?> {
        val entry = entries.getOrPut(spec) { CacheEntry() }
        entry.refCount++
        if (entry.refCount == 1) {
            entry.job = scope.launch { play(spec, entry) }
        }
        setVolume(spec, volume)
        return entry.frame
    }

    /**
     * Sets the volume of an already-playing file.
     *
     * Separate from [acquire] because volume is not part of the key: a slider drag changes this
     * without disturbing the decode. Last writer wins, which is what two layers of one file at
     * different volumes get — they share the one audio stream, as they share the one picture.
     */
    @Synchronized
    fun setVolume(spec: SceneVideoSpec, volume: Float) {
        val entry = entries[spec] ?: return
        entry.volumePercent = (volume * VOLUME_PERCENT_SCALE).toInt()
        entry.handle?.setVolume(entry.volumePercent)
    }

    /** Lets go of [spec]. The decode stops when the last layer does. */
    @Synchronized
    fun release(spec: SceneVideoSpec) {
        val entry = entries[spec] ?: return
        entry.refCount--
        if (entry.refCount > 0) return
        entry.job?.cancel()
        entry.job = null
        entry.frame.value = null
        entries.remove(spec)
    }

    /** How many decodes are running — how a test sees the sharing, and the release. */
    @get:Synchronized
    internal val liveDecodeCount: Int get() = entries.size

    private suspend fun play(spec: SceneVideoSpec, entry: CacheEntry) {
        // NonCancellable around the *open*, not just the close. The last layer can let go while the
        // player is still being built — a scene switched quickly, a composition mounted and
        // unmounted — and a cancellable open throws out of here with the player already alive and
        // nothing holding it, leaking the native decoder for the life of the app. Opening
        // uninterruptibly means cancellation lands on the line below instead, where the `finally`
        // can close what was built.
        val handle = withContext(NonCancellable + Dispatchers.IO) { openPlayer(spec) } ?: return
        synchronized(this) {
            entry.handle = handle
            // Whatever the layers asked for while this was opening.
            handle.setVolume(entry.volumePercent)
        }
        try {
            convertFrames(handle, entry)
        } catch (_: CancellationException) {
            // Ordinary teardown — the last layer let go.
        } finally {
            synchronized(this) { entry.handle = null }
            // NonCancellable: the ordinary way out of here is cancellation, and a plain
            // `withContext` in a cancelled coroutine throws instead of running — which would leak
            // the player and its native decoder for the life of the app.
            withContext(NonCancellable + Dispatchers.IO) { handle.close() }
        }
    }

    /** Publishes each newly decoded picture, converting on this coroutine and not the decoder's. */
    private suspend fun convertFrames(handle: SceneVideoHandle, entry: CacheEntry) {
        var lastVersion = -1L
        while (currentCoroutineContext().isActive) {
            val version = handle.frameVersion
            if (version != lastVersion) {
                lastVersion = version
                handle.frame?.let { entry.frame.value = it.toComposeImageBitmap() }
            }
            delay(VIDEO_POLL_MS)
        }
    }
}

/**
 * A factory and a player from it, or null when either could not be built.
 *
 * Its own function so [openVlcSceneVideo] has one failure path rather than three: a factory that
 * cannot be created is reported, and a player that cannot be created releases the factory it came
 * from rather than leaking it.
 */
@Suppress("TooGenericExceptionCaught")
private fun newVlcPlayer(): Pair<MediaPlayerFactory, EmbeddedMediaPlayer>? {
    val factory = try {
        MediaPlayerFactory("--no-video-title-show")
    } catch (t: Throwable) {
        CrashReporter.reportException(t, "SceneVideoCache: VLC MediaPlayerFactory init failed")
        null
    } ?: return null

    val player = try {
        factory.mediaPlayers().newEmbeddedMediaPlayer()
    } catch (_: Throwable) {
        try { factory.release() } catch (_: Throwable) { }
        null
    } ?: return null

    return factory to player
}

/**
 * A libvlc player behind [SceneVideoHandle], or null when one could not be built.
 *
 * The render callback writes straight into the `BufferedImage`'s own int array and bumps the
 * version — no allocation, no conversion, nothing that can block the decoder.
 */
@Suppress("TooGenericExceptionCaught")
internal fun openVlcSceneVideo(spec: SceneVideoSpec): SceneVideoHandle? {
    val file = File(spec.filePath)
    if (!file.exists() || !isVlcAvailable) return null
    val (factory, player) = newVlcPlayer() ?: return null

    val holder = AtomicReference<BufferedImage?>(null)
    val version = AtomicLong(0)

    val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            holder.set(BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB))
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
    }
    val renderCallback = RenderCallback { _, nativeBuffers, _ ->
        val img = holder.get() ?: return@RenderCallback
        if (nativeBuffers.isNullOrEmpty()) return@RenderCallback
        val pixels = (img.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
        try {
            val buf = nativeBuffers[0] ?: return@RenderCallback
            buf.rewind()
            buf.asIntBuffer().get(pixels, 0, pixels.size.coerceAtMost(buf.remaining() / BYTES_PER_PIXEL))
            version.incrementAndGet()
        } catch (_: Throwable) { }
    }
    player.videoSurface().set(factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true))

    Thread.sleep(PLAYER_SETTLE_MS)
    try {
        if (spec.loop) player.media().play(file.absolutePath, VLC_OPT_TIGHT_CLOCK, VLC_OPT_LOOP)
        else player.media().play(file.absolutePath, VLC_OPT_TIGHT_CLOCK)
    } catch (_: Throwable) { }

    return object : SceneVideoHandle {
        override val frameVersion: Long get() = version.get()
        override val frame: BufferedImage? get() = holder.get()
        override fun setVolume(percent: Int) {
            try { player.audio().setVolume(percent) } catch (_: Throwable) { }
        }
        override fun close() {
            try {
                player.controls().stop()
                player.release()
                factory.release()
            } catch (_: Throwable) { }
        }
    }
}

/**
 * The one cache the app decodes canvas video layers and looping backgrounds from.
 *
 * Holds no logic of its own — everything it does is [SceneVideoCache]'s, which is an ordinary class
 * a test builds over a stand-in player.
 */
internal object SharedSceneVideoCache : SceneVideoCache(openPlayer = ::openVlcSceneVideo)
