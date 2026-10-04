package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.presenter.BandOutgoing
import org.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.LottieFrame
import org.churchpresenter.app.churchpresenter.presenter.LottieFrameStream
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.lottiegen.lottie.LottieTextShaping
import org.churchpresenter.lowerthird.render.LottieRenderCache
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.IOException

/**
 * The Lottie lower third -- its file, its playback and its pre-rendered frames -- and the Lottie
 * band the Bible and song outputs draw their text in. Part of [PresenterManager].
 */
interface LiveLowerThird {
    /**
     * Where the Lottie lower-third band — Bible or song, whichever is live — is in its entrance /
     * hold / text change / exit. Driven by `PresenterTransitionEffects`; every output maps it
     * onto its own template.
     */
    val lottieBandClock: State<BibleBandClock>

    /**
     * The lyric line the Lottie band shows. Follows `songDisplayLineIndex`, but only once the
     * band has played the old line out, so the text does not change under a running animation.
     */
    val bandSongLineIndex: State<Int>

    /**
     * What the Lottie band is crossfading away from while [lottieBandClock] is on a text swap:
     * the verse or the lyric line it showed before the change. Empty at every other phase.
     */
    val bandOutgoing: State<BandOutgoing>

    /** Playback progress of the lower third, driven centrally. */
    val lottieProgress: State<Float>

    /**
     * Pre-rendered playback: once the `LottieRenderCache` entry for the current content is ready, a
     * `LottieFrameStream` serves decoded frames here and [lottieFrameCount] turns non-null — main.kt's
     * playback clock then switches from [lottieProgress] to frame indices.
     */
    val lottieFrame: State<LottieFrame?>
    val lottieFrameCount: State<Int?>

    /** The frame rate the current cache entry was rendered at; main.kt's playback loop paces off it. */
    val lottiePrerenderFps: State<Int>
    val lottieCurrentFrameIndex: State<Int>
    val lottieJsonContent: State<String>

    /** Whether [lottieJsonContent]'s text is drawn as whole lines, as its file asks ([LottieTextShaping]). */
    val lottieGroupsText: State<Boolean>
    val lottiePauseAtFrame: State<Boolean>
    val lottiePauseFrame: State<Float>
    val lottiePauseDurationMs: State<Long>
    val lottieTrigger: State<Int>

    /**
     * The preset file name (no extension) the current content came from, for the live-state
     * broadcast to report. Rendering only ever uses [lottieJsonContent]; empty for content set
     * some other way.
     */
    val currentLowerThirdName: State<String>

    fun setLottieBandClock(clock: BibleBandClock)
    fun setBandSongLineIndex(index: Int)
    fun setBandOutgoing(outgoing: BandOutgoing)

    /** The ATEM's settings, kept current by main.kt, so pre-renders and ATEM uploads share a size. */
    fun setAtemRenderSettings(atem: AtemSettings?)
    fun setLottieCurrentFrameIndex(index: Int)
    fun setLottieProgress(progress: Float)
    fun setLottieContent(
        json: String,
        pauseAtFrame: Boolean,
        pauseFrame: Float,
        pauseDurationMs: Long,
        presetName: String = "",
    )
}

internal class LiveLowerThirdState(private val context: PresenterContext) : LiveLowerThird {

    private val _lottieBandClock = mutableStateOf(BibleBandClock(BibleBandPhase.IDLE, 0f))
    override val lottieBandClock: State<BibleBandClock> = _lottieBandClock

    private val _bandSongLineIndex = mutableStateOf(-1)
    override val bandSongLineIndex: State<Int> = _bandSongLineIndex

    private val _bandOutgoing = mutableStateOf(BandOutgoing())
    override val bandOutgoing: State<BandOutgoing> = _bandOutgoing

    private val _lottieProgress = mutableStateOf(0f)
    override val lottieProgress: State<Float> = _lottieProgress

    private val _lottieFrame = mutableStateOf<LottieFrame?>(null)
    override val lottieFrame: State<LottieFrame?> = _lottieFrame

    private val _lottieFrameCount = mutableStateOf<Int?>(null)
    override val lottieFrameCount: State<Int?> = _lottieFrameCount

    private val _lottiePrerenderFps = mutableStateOf(LottieRenderCache.PLAYBACK_FPS)
    override val lottiePrerenderFps: State<Int> = _lottiePrerenderFps

    private val _lottieCurrentFrameIndex = mutableStateOf(0)
    override val lottieCurrentFrameIndex: State<Int> = _lottieCurrentFrameIndex

    private val _lottieJsonContent = mutableStateOf("")
    override val lottieJsonContent: State<String> = _lottieJsonContent

    private val _lottieGroupsText = mutableStateOf(false)
    override val lottieGroupsText: State<Boolean> = _lottieGroupsText

    private val _lottiePauseAtFrame = mutableStateOf(false)
    override val lottiePauseAtFrame: State<Boolean> = _lottiePauseAtFrame

    private val _lottiePauseFrame = mutableStateOf(-1f)
    override val lottiePauseFrame: State<Float> = _lottiePauseFrame

    private val _lottiePauseDurationMs = mutableStateOf(DEFAULT_PAUSE_MS)
    override val lottiePauseDurationMs: State<Long> = _lottiePauseDurationMs

    private val _lottieTrigger = mutableStateOf(0)
    override val lottieTrigger: State<Int> = _lottieTrigger

    private val _currentLowerThirdName = mutableStateOf("")
    override val currentLowerThirdName: State<String> = _currentLowerThirdName

    private var preRenderJob: Job? = null

    // Read from the decode worker thread (frame-publish identity guard) — volatile so a
    // Main-thread clear is seen there promptly.
    @Volatile
    private var lottieFrameStream: LottieFrameStream? = null

    private var atemRenderSettings: AtemSettings? = null

    override fun setLottieBandClock(clock: BibleBandClock) {
        _lottieBandClock.value = clock
    }

    override fun setBandSongLineIndex(index: Int) {
        _bandSongLineIndex.value = index
    }

    override fun setBandOutgoing(outgoing: BandOutgoing) {
        _bandOutgoing.value = outgoing
    }

    override fun setAtemRenderSettings(atem: AtemSettings?) {
        atemRenderSettings = atem
    }

    override fun setLottieCurrentFrameIndex(index: Int) {
        _lottieCurrentFrameIndex.value = index
        lottieFrameStream?.requestFrame(index)
    }

    override fun setLottieProgress(progress: Float) {
        _lottieProgress.value = progress
    }

    override fun setLottieContent(
        json: String,
        pauseAtFrame: Boolean,
        pauseFrame: Float,
        pauseDurationMs: Long,
        presetName: String,
    ) {
        _lottieJsonContent.value = json
        _lottieGroupsText.value = LottieTextShaping.groupsText(json)
        _lottiePauseAtFrame.value = pauseAtFrame
        _lottiePauseFrame.value = pauseFrame
        _lottiePauseDurationMs.value = pauseDurationMs
        _lottieTrigger.value++
        _currentLowerThirdName.value = presetName
        context.notify(Presenting.LOWER_THIRD)

        // Clear stale frames immediately so the presenter falls back to compottie
        preRenderJob?.cancel()
        _lottieFrameCount.value = null
        _lottieFrame.value = null
        _lottiePrerenderFps.value = LottieRenderCache.PLAYBACK_FPS
        _lottieCurrentFrameIndex.value = 0
        lottieFrameStream?.close()
        lottieFrameStream = null

        if (json.isNotBlank()) preRenderJob = context.scope.launch { preRender(json) }
    }

    /** Renders [json] off screen, or reads it from the cache, and switches playback to its frames. */
    private suspend fun preRender(json: String) {
        var stream: LottieFrameStream? = null
        var published = false
        try {
            val variant = LottieRenderCache.desktopVariant(json, atemRenderSettings)
                ?: return // JSON has no timing — stay on the live renderer
            // Instant on a cache hit; renders in the background otherwise
            val cached = LottieRenderCache.prepare(json, variant).await()
            stream = LottieFrameStream(file = cached, scope = context.scope) { frame ->
                // Identity guard: a decode already in flight when newer content replaced
                // this stream must not publish into the new content's cleared state (the
                // stale bitmap would be closed by this stream's teardown while displayed).
                if (lottieFrameStream === stream) _lottieFrame.value = frame
            }
            if (!stream.open()) {
                // A silently-blank off-screen render: discard the cache file (so a later
                // play re-renders instead of re-reading the blank entry) and keep using
                // the live renderer — better than switching to bitmaps of nothing.
                stream.close()
                cached.delete()
                // The message stays constant so this groups as one issue; everything that
                // varies goes in tags and extras. Carrying only the subsystem, as it did,
                // made the report unanswerable — a blank render is a property of the
                // variant and the file it produced, and neither was in it.
                CrashReporter.reportWarning(
                    "Lottie pre-render produced blank frames, discarded",
                    tags = mapOf(
                        "subsystem" to "lower_third",
                        "lottie.clip" to variant.clip.toString(),
                        "lottie.empty_cache_file" to (cached.length() == 0L).toString()
                    ),
                    extras = mapOf(
                        "variant" to "${variant.width}x${variant.height} " +
                            "@${variant.fps}fps, ${variant.frameCount} frames",
                        "cacheFileBytes" to cached.length().toString()
                    )
                )
                return
            }
            withContext(Dispatchers.Main) {
                // Do NOT reset _lottieCurrentFrameIndex here — main.kt's playback loop
                // may already be partway through this play (it switches to raw frames
                // the instant they're ready, not just at play start) and derives the
                // correct index from real elapsed time on its very next tick. Resetting
                // to 0 here raced that loop and caused a visible flash back to frame 0
                // right at the switchover moment.
                lottieFrameStream = stream
                _lottiePrerenderFps.value = variant.fps.toInt()
                _lottieFrameCount.value = stream.frameCount
                published = true
            }
            // Serve the current playhead position immediately so the switchover from the
            // live renderer has a frame to draw on its very first tick.
            stream.requestFrame(_lottieCurrentFrameIndex.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            lottiePreRenderFailed(e)
        } catch (e: IllegalArgumentException) {
            // A lower third whose JSON will not parse.
            lottiePreRenderFailed(e)
        } catch (e: IllegalStateException) {
            lottiePreRenderFailed(e)
        } finally {
            // Newer content cancelled this job after the stream was opened but before
            // it was adopted — release it, it will never be drawn.
            if (!published) stream?.close()
        }
    }

    private fun lottiePreRenderFailed(e: Exception) {
        Log.error("PresenterManager", "Lottie pre-render failed: ${e.message}")
        CrashReporter.reportException(e, "Lottie pre-render")
    }
}

private const val DEFAULT_PAUSE_MS = 2000L
