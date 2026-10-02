package org.churchpresenter.media.presenter

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.media.composables.SoftwareVlc
import org.churchpresenter.media.composables.openSoftwareVlc
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer

private const val FRAME_INTERVAL_MS = 16L
private const val FULL_VOLUME = 100

// The same libvlc options VideoPlayer uses, and passed the same way -- one argument at a time, so
// play()'s Java vararg does not make the compiler copy an array on every open.
private const val VLC_OPT_SOFTWARE_CODEC = ":codec=avcodec"
private const val VLC_OPT_FAST_DECODE = ":avcodec-fast"
private const val VLC_OPT_TIGHT_CLOCK = ":clock-jitter=0"

/**
 * The decoded-frame side of [EmbeddedVideoDecoder]: the buffer VLC renders into, sized when VLC
 * reports the video's dimensions, and a count of the frames delivered so far.
 */
internal class DecodedFrames {
    @Volatile var frame: BufferedImage? = null
    @Volatile var version = 0L
        private set

    /** Source dimensions VLC reports can be 0 (not yet known); a zero-sized BufferedImage throws. */
    fun allocateDecodedFrame(sourceWidth: Int, sourceHeight: Int): BufferedImage {
        val w = sourceWidth.coerceAtLeast(1)
        val h = sourceHeight.coerceAtLeast(1)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    }

    /** Copies as many whole pixels as [buf] actually holds — a short native buffer must not overrun [pixelData]. */
    fun copyFrameBytes(buf: ByteBuffer, pixelData: IntArray): Int {
        buf.rewind()
        val count = pixelData.size.coerceAtMost(buf.remaining() / 4)
        buf.asIntBuffer().get(pixelData, 0, count)
        return count
    }

    val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val allocated = allocateDecodedFrame(sourceWidth, sourceHeight)
            frame = allocated
            return RV32BufferFormat(allocated.width, allocated.height)
        }
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
    }

    val renderCallback = RenderCallback { _, nativeBuffers, _ ->
        val img = frame ?: return@RenderCallback
        val buf = nativeBuffers?.firstOrNull() ?: return@RenderCallback
        val pixelData = (img.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
        try {
            copyFrameBytes(buf, pixelData)
            version++
        } catch (_: Throwable) {
        }
    }
}

/**
 * Decodes one embedded presentation video (Keynote or PowerPoint) live and republishes it as an
 * [ImageBitmap] sized/offset identically to the poster frame it replaces, so
 * `PresentationPresenter`'s layer draw needs no changes. A new instance per active video layer —
 * NOT the [org.churchpresenter.media.composables.SharedVideoOutput] singleton, which
 * stays scoped to the Media tab's one master video.
 *
 * Starts muted and paused; [resume] unmutes and plays, [pause] silences without releasing the
 * decoder (cheap to call every frame — libvlc play/pause on an already-playing/paused player is
 * a documented no-op, same rationale as [org.churchpresenter.media.composables.SoftwareVideoPlayer]).
 */
class EmbeddedVideoDecoder internal constructor(
    private val videoFile: File,
    /** Full padded-canvas poster bitmap already rasterized by the engine — the compositing base. */
    private val posterCanvas: BufferedImage,
    /** Where within [posterCanvas], in its own pixel space, decoded frames should be blitted. */
    private val contentRectPx: Rectangle,
    /** Opens VLC for callback rendering, or null when it cannot be. */
    private val openVlc: () -> SoftwareVlc?,
) : AutoCloseable {

    constructor(videoFile: File, posterCanvas: BufferedImage, contentRectPx: Rectangle) :
        this(videoFile, posterCanvas, contentRectPx, ::openSoftwareVlc)

    @Volatile var latestFrame: ImageBitmap? = null
        private set

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    // internal (not private): a MediaPlayer can't be constructed without a real libvlc native
    // handle, so tests inject a mock here to exercise resume()/pause()'s guard logic.
    internal var mp: MediaPlayer? = null
    private var release: (() -> Unit)? = null
    internal val frames = DecodedFrames()
    // internal: lets tests drive composite() directly with a decoded frame instead of needing a
    // real VLC render callback to populate it.
    internal var decodedFrame: BufferedImage?
        get() = frames.frame
        set(value) { frames.frame = value }
    @Volatile private var resumed = false
    @Volatile private var closed = false
    // Confirmed via vlcj's own playing()/paused() events — resume()/pause() only reissue the
    // native command while unconfirmed (closes the start() race below without hammering libvlc
    // every frame for the rest of the clip once the transition actually lands). internal so tests
    // can simulate the event without a real MediaPlayer to fire it.
    @Volatile internal var confirmedPlaying = false
    @Volatile internal var confirmedPaused = false

    fun start() {
        if (closed) return
        val vlc = openVlc() ?: return
        mp = vlc.mp
        release = vlc.release
        vlc.attachSurface(frames.bufferFormatCallback, frames.renderCallback)
        begin(vlc.mp)
    }

    /** The player's events, as this decoder needs them. */
    internal val events = object : MediaPlayerEventAdapter() {
        override fun error(mediaPlayer: MediaPlayer) {
            onErrorEvent()
        }
        override fun playing(mediaPlayer: MediaPlayer) {
            onPlayingConfirmed()
        }
        override fun paused(mediaPlayer: MediaPlayer) {
            onPausedConfirmed()
        }
    }

    /** Listens to [player], opens the video silently, and composites each new frame as it lands. */
    internal fun begin(player: MediaPlayer) {
        player.events().addMediaPlayerEventListener(events)
        player.audio().setVolume(0)
        // media().play() only queues the open/play command — libvlc transitions to actually
        // playing asynchronously, so a pause() issued synchronously right here can (and, observed
        // hands-on, does) race ahead of it and land on a player still "opening," making it a
        // no-op. The real gate is [pause]/[resume], called every frame by PresentationPlayer —
        // no need to duplicate the call here.
        player.media().play(
            videoFile.absolutePath, VLC_OPT_SOFTWARE_CODEC, VLC_OPT_FAST_DECODE, VLC_OPT_TIGHT_CLOCK,
        )

        pollJob = scope.launch {
            var lastVersion = 0L
            while (isActive) {
                val v = frames.version
                if (v != lastVersion) {
                    lastVersion = v
                    composite()
                }
                delay(FRAME_INTERVAL_MS) // ~60fps cap, off the VLC render thread
            }
        }
    }

    internal fun onErrorEvent() {
        Log.warn("VLCJ (embedded video)", "playback error for ${videoFile.name}")
    }

    internal fun onPlayingConfirmed() {
        confirmedPlaying = true
        confirmedPaused = false
    }

    internal fun onPausedConfirmed() {
        confirmedPaused = true
        confirmedPlaying = false
    }

    internal fun composite() {
        val src = decodedFrame ?: return
        val working = BufferedImage(posterCanvas.width, posterCanvas.height, BufferedImage.TYPE_INT_ARGB)
        val g = working.createGraphics()
        try {
            g.drawImage(posterCanvas, 0, 0, null)
            g.drawImage(src, contentRectPx.x, contentRectPx.y, contentRectPx.width, contentRectPx.height, null)
        } finally {
            g.dispose()
        }
        latestFrame = working.toComposeImageBitmap()
    }

    /**
     * Reissues `play()` every call while not yet [confirmedPlaying] — a single play()/pause()
     * pair right after [start] can race libvlc's async open and land as a no-op (see [start]),
     * silently letting the video autoplay through with nothing to correct it; retrying every
     * frame (~16ms) until the `playing()` event actually confirms it closes that race. Once
     * confirmed, steady-state playback issues no further native calls for the rest of the clip —
     * calling play()/pause() unconditionally every frame for the whole clip made playback choppy
     * (regression caught hands-on), so this must NOT go back to a blind per-frame reissue.
     * [resumed] only gates the one-time volume change so unmuting doesn't repeat every frame.
     */
    fun resume() {
        if (closed) return
        if (!resumed) {
            resumed = true
            mp?.audio()?.setVolume(FULL_VOLUME)
        }
        if (!confirmedPlaying) mp?.controls()?.play()
    }

    fun pause() {
        if (closed) return
        resumed = false
        if (!confirmedPaused) mp?.controls()?.pause()
    }

    override fun close() {
        closed = true
        scope.cancel()
        try {
            mp?.controls()?.stop()
            release?.invoke()
        } catch (_: Throwable) {
        }
    }
}
