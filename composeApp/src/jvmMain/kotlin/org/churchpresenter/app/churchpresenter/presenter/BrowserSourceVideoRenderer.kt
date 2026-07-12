package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.StageMonitorScreen
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Renders a Browser Source output's live content off-screen (no window, no JCEF — same
 * [ImageComposeScene] technique as [LowerThirdOffscreenRenderer]) and encodes changed frames
 * as PNG, so a remote OBS/vMix Browser Source gets a pixel-identical stream of the same
 * presenter composables used everywhere else in the app — no reimplementation drift.
 *
 * Supported content types: Bible, Songs/Lyrics, Announcements, Pictures, Presentation slides,
 * Lower Third (Lottie graphics), Canvas (scene compositor), Q&A, STT captions, Dictionary,
 * Media (muted — video frames come from the single master player via SharedVideoOutput;
 * audio stays on the main output; note real video never settles into an "unchanged" frame,
 * so MEDIA encodes continuously at the tick rate rather than only-on-change) and Website
 * (mirrors [PresenterManager.webSnapshot], which is only pushed while the Web tab or a real
 * output window hosts the live JCEF browser — a Browser Source alone cannot drive a site) —
 * plus Stage Monitor as a whole separate display-mode layout.
 *
 * The scene provides [LocalTransparentBlanking] = true, so "no background"/Transparent
 * renders genuinely transparent pixels (real alpha survives PNG encoding) for OBS keying,
 * instead of the black a projector window paints.
 *
 * Only encodes and emits a frame when the rendered pixels actually changed since the last
 * tick, so a static slide costs one encode, not continuous encoding — this is what keeps a
 * per-output stream far cheaper than NDI (which encodes continuously regardless of change
 * or of whether anyone is even watching). The one exception is a periodic full-frame
 * recapture (see [FULL_FRAME_RESEED_MS]) that fires on its own schedule regardless of
 * whether content changed, to bound how long a client can stay desynced from a dropped
 * dirty-rect delta (see [frames]'s KDoc for why that can happen).
 */
/**
 * One emitted delta: a PNG-encoded sub-rectangle of the full [fullWidth]x[fullHeight] frame,
 * positioned at ([x],[y]). A full-frame delta has x=0, y=0, rectWidth=fullWidth,
 * rectHeight=fullHeight — sent for the very first tick and whenever a new HTTP subscriber
 * attaches, since a brand-new client's compositing canvas has nothing to apply a partial rect
 * onto yet. Note: default `equals()`/`hashCode()` on [png] is reference-based, not content-based
 * — harmless since nothing ever compares instances, only passes them through.
 */
data class BrowserSourceFrame(
    val x: Int,
    val y: Int,
    val rectWidth: Int,
    val rectHeight: Int,
    val fullWidth: Int,
    val fullHeight: Int,
    val png: ByteArray,
)

@OptIn(ExperimentalComposeUiApi::class)
class BrowserSourceVideoRenderer(
    private val presenterManager: PresenterManager,
    private val appSettingsState: State<AppSettings>,
    private val screenAssignmentState: State<ScreenAssignment>,
    private val effectiveModeState: State<Presenting>,
    private val outputIndex: Int = 0,
    private val sttManager: STTManager? = null,
    private val mediaViewModel: MediaViewModel? = null,
    private val qaDisplayUrlState: State<String>? = null,
    private val serverUrlState: State<String>? = null,
    private val width: Int = 1920,
    private val height: Int = 1080,
    fps: Int = 30,
) {
    // Sampling cadence from the per-output fps setting; only changed frames are actually
    // encoded/emitted, so this is a ceiling, not a constant cost.
    private val tickDelayMs = 1000L / fps.coerceIn(1, 60)
    // Keep the virtual animation clock in step with real sampling time
    private val frameNanos = tickDelayMs * 1_000_000L

    private companion object {
        // DROP_OLDEST (below) is only safe for dirty-rect deltas because of this: each delta is
        // only valid applied on top of the exact state the previous delta produced, so a silently
        // dropped intermediate delta leaves a connected client permanently wrong in that region
        // with no way to detect it. Forcing a fresh full-frame recapture on this schedule (not
        // just on new-subscriber) bounds how long that drift can persist.
        const val FULL_FRAME_RESEED_MS = 5_000L
    }

    /**
     * Latest frame delta, replayed to any newly-subscribed HTTP client. Uses [BufferOverflow.DROP_OLDEST]
     * (the established pattern elsewhere in this codebase) rather than the default SUSPEND — a
     * slow network consumer must never stall the render loop itself, only skip stale frames.
     *
     * Dropping a *dirty-rect* frame this way is only safe because of [FULL_FRAME_RESEED_MS] —
     * each delta is only valid applied on top of the state the previous one produced, so on its
     * own a dropped delta would leave a client permanently wrong in that region.
     */
    val frames = MutableSharedFlow<BrowserSourceFrame>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.Default) {
            val scene = ImageComposeScene(width, height, Density(1f)) {
                // Transparent blanking (real alpha for OBS keying) + the media view model
                // for MEDIA playback — the same CompositionLocal the real windows provide.
                CompositionLocalProvider(
                    LocalTransparentBlanking provides true,
                    LocalMediaViewModel provides mediaViewModel
                ) {
                    val appSettings by appSettingsState
                    val screenAssignment by screenAssignmentState
                    val effectiveMode by effectiveModeState
                    val isIdentifying = presenterManager.browserSourceIdentifying.value.contains(outputIndex)
                    val isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
                    val isStageMonitor = screenAssignment.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
                    val outputRole = Constants.OUTPUT_ROLE_NORMAL
                    // General per-output background toggle — same field/logic as native output
                    // (main.kt). showBibleBackground/showSongsBackground below are an additional
                    // layer on top of this, not a replacement for it.
                    val showBg = if (isLowerThird) screenAssignment.showLowerThirdBackground else screenAssignment.showFullscreenBackground

                    if (isIdentifying) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "Browser Source ${outputIndex + 1}",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 96.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    } else if (isStageMonitor) {
                        StageMonitorScreen(
                            sm = appSettings.stageMonitorSettings,
                            presentingMode = effectiveMode,
                            currentLyricSection = presenterManager.displayedLyricSection.value,
                            allLyricSections = presenterManager.allLyricSections.value,
                            songDisplaySectionIndex = presenterManager.songDisplaySectionIndex.value,
                            displayedVerses = presenterManager.displayedVerses.value,
                            nextVerses = presenterManager.nextVerses.value,
                            announcementText = presenterManager.displayedAnnouncementText.value,
                            displayedImagePath = presenterManager.displayedImagePath.value,
                            displayedSlide = presenterManager.displayedSlide.value,
                            presenterNotes = presenterManager.presenterNotes.value,
                            activeScene = presenterManager.activeScene.value,
                            displayedQuestion = presenterManager.displayedQuestion.value,
                            qaSettings = appSettings.qaSettings,
                            displayedDictionaryEntry = presenterManager.displayedDictionaryEntry.value,
                            dictionarySettings = appSettings.dictionarySettings
                        )
                    } else {
                        PresenterScreen(
                            appSettings = appSettings,
                            outputRole = outputRole,
                            isLowerThird = isLowerThird,
                            showBackground = showBg
                        ) {
                            // Mode-to-mode crossfade — same behavior and duration formula as the
                            // real output windows (main.kt): fades only when bible/song crossfade
                            // is enabled and neither the outgoing nor incoming mode is NONE.
                            val modeCrossfadeDuration = maxOf(
                                if (appSettings.bibleSettings.crossfade) appSettings.bibleSettings.transitionDuration.toInt() else 0,
                                if (appSettings.songSettings.crossfade) appSettings.songSettings.transitionDuration.toInt() else 0
                            ).coerceAtLeast(100)
                            var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                            val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) &&
                                effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                            if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                            Crossfade(
                                targetState = effectiveMode,
                                animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()
                            ) { mode ->
                                val showsContent = when (mode) {
                                    Presenting.BIBLE -> screenAssignment.showBible
                                    Presenting.LYRICS -> screenAssignment.showSongs
                                    Presenting.PICTURES, Presenting.PRESENTATION -> screenAssignment.showPictures
                                    Presenting.ANNOUNCEMENTS -> screenAssignment.showAnnouncements
                                    Presenting.LOWER_THIRD -> screenAssignment.showStreaming
                                    Presenting.MEDIA -> screenAssignment.showMedia
                                    Presenting.WEBSITE -> screenAssignment.showWebsite
                                    Presenting.CANVAS -> true
                                    Presenting.QA -> screenAssignment.showQA
                                    Presenting.STT -> screenAssignment.showSTT
                                    Presenting.DICTIONARY -> screenAssignment.showDictionary
                                    else -> false
                                }
                                if (mode != Presenting.NONE && showsContent) {
                                    when (mode) {
                                        Presenting.BIBLE -> BiblePresenter(
                                            selectedVerses = presenterManager.displayedVerses.value,
                                            appSettings = appSettings,
                                            isLowerThird = isLowerThird,
                                            outputRole = outputRole,
                                            transitionAlpha = presenterManager.bibleTransitionAlpha.value,
                                            showBackground = showBg && screenAssignment.showBibleBackground,
                                            crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                            languageMode = screenAssignment.bibleMode
                                        )
                                        Presenting.LYRICS -> SongPresenter(
                                            lyricSection = presenterManager.displayedLyricSection.value,
                                            appSettings = appSettings,
                                            isLowerThird = isLowerThird,
                                            outputRole = outputRole,
                                            transitionAlpha = presenterManager.songTransitionAlpha.value,
                                            displayLineIndex = presenterManager.songDisplayLineIndex.value,
                                            lookAheadEnabled = screenAssignment.songLookAhead,
                                            allLyricSections = presenterManager.allLyricSections.value,
                                            displaySectionIndex = presenterManager.songDisplaySectionIndex.value,
                                            showBackground = showBg && screenAssignment.showSongsBackground,
                                            crossfadeEnabled = appSettings.songSettings.crossfade,
                                            languageOverride = screenAssignment.songMode
                                        )
                                        Presenting.PICTURES -> PicturePresenter(
                                            imagePath = presenterManager.displayedImagePath.value,
                                            previousImagePath = presenterManager.previousDisplayedImagePath.value,
                                            transitionAlpha = presenterManager.pictureTransitionAlpha.value,
                                            slideOffset = presenterManager.pictureSlideOffset.value,
                                            animationType = presenterManager.animationType.value
                                        )
                                        Presenting.ANNOUNCEMENTS -> AnnouncementsPresenter(
                                            text = presenterManager.displayedAnnouncementText.value,
                                            appSettings = appSettings,
                                            outputRole = outputRole,
                                            transitionAlpha = presenterManager.announcementTransitionAlpha.value,
                                            showBackground = showBg
                                        )
                                        Presenting.PRESENTATION -> {
                                            PresentationPresenter(
                                                frame = presenterManager.presentationFrame.value,
                                                slide = presenterManager.displayedSlide.value,
                                                previousSlide = presenterManager.previousDisplayedSlide.value,
                                                transitionAlpha = presenterManager.slideTransitionAlpha.value,
                                                slideOffset = presenterManager.slideSlideOffset.value,
                                                animationType = presenterManager.animationType.value,
                                                outputRole = outputRole,
                                                frozen = presenterManager.slideFrozen.value
                                            )
                                        }
                                        Presenting.LOWER_THIRD -> {
                                            val lottieJsonContent = presenterManager.lottieJsonContent.value
                                            val lottieComposition by rememberLottieComposition(key = lottieJsonContent) {
                                                LottieCompositionSpec.JsonString(lottieJsonContent.ifBlank { "{}" })
                                            }
                                            LowerThirdPresenter(
                                                composition = lottieComposition,
                                                progress = { presenterManager.lottieProgress.value },
                                                appSettings = appSettings,
                                                outputRole = outputRole,
                                                frame = presenterManager.lottieFrame.value
                                            )
                                        }
                                        Presenting.MEDIA -> {
                                            // Same rule as the real output (main.kt): audio-only files
                                            // show background only; video draws muted — frames come from
                                            // the master player via SharedVideoOutput, audio stays on the
                                            // main output's audio device.
                                            if (mediaViewModel != null && !mediaViewModel.isAudioFile) {
                                                MediaPresenter(
                                                    modifier = Modifier.fillMaxSize(),
                                                    audioEnabled = false,
                                                    transitionAlpha = presenterManager.mediaTransitionAlpha.value
                                                )
                                            }
                                        }
                                        Presenting.WEBSITE -> {
                                            // Mirror of the live JCEF browser's periodic snapshot — only
                                            // updates while the Web tab or a real output window shows the
                                            // site (a Browser Source alone cannot drive a website). No
                                            // snapshot yet -> nothing (transparent).
                                            presenterManager.webSnapshot.value?.let { snapshot ->
                                                Image(
                                                    bitmap = snapshot,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.FillBounds,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Presenting.CANVAS -> ScenePresenter(scene = presenterManager.activeScene.value)
                                        Presenting.QA -> {
                                            val showQRCode = presenterManager.showQRCodeOnDisplay.value
                                            val qaTransitionAlpha = presenterManager.qaTransitionAlpha.value
                                            if (showQRCode) {
                                                val base = qaDisplayUrlState?.value?.ifEmpty { serverUrlState?.value ?: "" } ?: (serverUrlState?.value ?: "")
                                                QAQRCodePresenter(url = "$base/qa", qaSettings = appSettings.qaSettings, outputRole = outputRole, transitionAlpha = qaTransitionAlpha)
                                            } else {
                                                QAPresenter(question = presenterManager.displayedQuestion.value, qaSettings = appSettings.qaSettings, outputRole = outputRole, transitionAlpha = qaTransitionAlpha)
                                            }
                                        }
                                        Presenting.STT -> {
                                            sttManager?.let { stt ->
                                                STTPresenter(
                                                    segments = stt.segments,
                                                    inProgressText = stt.inProgressText.value,
                                                    translationSegments = stt.translationSegments,
                                                    inProgressTranslation = stt.inProgressTranslation.value,
                                                    highlightedWords = stt.highlightedWords,
                                                    sttSettings = appSettings.sttSettings,
                                                    outputRole = outputRole
                                                )
                                            }
                                        }
                                        Presenting.DICTIONARY -> DictionaryPresenter(
                                            entry = presenterManager.displayedDictionaryEntry.value,
                                            dictionarySettings = appSettings.dictionarySettings,
                                            outputRole = outputRole,
                                            transitionAlpha = 1f
                                        )
                                        Presenting.NONE -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            try {
                var timeNanos = 0L
                val intBuf = IntArray(width * height)
                var lastBuf: IntArray? = null
                var lastSeenSubscriberCount = 0
                var lastFullFrameAtMs = 0L
                while (true) {
                    timeNanos += frameNanos
                    Snapshot.sendApplyNotifications()
                    val img = scene.render(timeNanos)
                    try {
                        img.toComposeImageBitmap().readPixels(intBuf)
                    } finally {
                        img.close()
                    }

                    // A newly-attached HTTP client (OBS/vMix reconnect, or a debug tab opened
                    // mid-service) must be seeded with a full frame before any dirty-rect delta
                    // means anything to it, so force one whenever the subscriber count rises —
                    // even on a tick where content didn't otherwise change.
                    val subscriberCount = frames.subscriptionCount.value
                    val newSubscriberJoined = subscriberCount > lastSeenSubscriberCount
                    lastSeenSubscriberCount = subscriberCount

                    val previous = lastBuf
                    val contentChanged = previous == null || !intBuf.contentEquals(previous)

                    // Independent of contentChanged: this must fire on its own schedule, not only
                    // when content happens to be changing, since drift from a dropped delta can
                    // persist forever otherwise once content settles into a static state (nothing
                    // left to trigger a corrective resend).
                    val elapsedMs = timeNanos / 1_000_000
                    val periodicReseedDue = elapsedMs - lastFullFrameAtMs >= FULL_FRAME_RESEED_MS

                    if (contentChanged || newSubscriberJoined || periodicReseedDue) {
                        val forceFullFrame = previous == null || newSubscriberJoined || periodicReseedDue
                        val frame = if (forceFullFrame) {
                            BrowserSourceFrame(0, 0, width, height, width, height, encodeFrame(intBuf, width, height))
                        } else {
                            val rect = computeDirtyRect(intBuf, previous, width, height)
                            val cropped = cropPixels(intBuf, width, rect.x, rect.y, rect.w, rect.h)
                            BrowserSourceFrame(
                                rect.x, rect.y, rect.w, rect.h, width, height,
                                encodeFrame(cropped, rect.w, rect.h)
                            )
                        }
                        frames.emit(frame)
                        if (forceFullFrame) lastFullFrameAtMs = elapsedMs
                        if (contentChanged) lastBuf = intBuf.copyOf()
                    }
                    delay(tickDelayMs)
                }
            } finally {
                scene.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private data class DirtyRect(val x: Int, val y: Int, val w: Int, val h: Int)

    /**
     * Tight bounding box of pixels that differ between [current] and [previous] (both row-major
     * width*height IntArrays). Only called when the buffers are already known to differ, so a
     * diff always exists. Two-phase for cheapness: first shrink top/bottom via whole-row
     * comparisons to skip unchanged rows cheaply (the common case — a small moving region, e.g.
     * a blinking cursor or a Lottie lower-third, against an otherwise static frame), then scan
     * columns only within the surviving row band. A full-screen crossfade (nearly every pixel
     * changes) degrades to close to the full frame — expected, not a regression.
     */
    private fun computeDirtyRect(current: IntArray, previous: IntArray, width: Int, height: Int): DirtyRect {
        var minRow = 0
        while (minRow < height && rowsEqual(current, previous, minRow, width)) minRow++
        var maxRow = height - 1
        while (maxRow > minRow && rowsEqual(current, previous, maxRow, width)) maxRow--

        var minCol = width
        var maxCol = -1
        for (row in minRow..maxRow) {
            val rowStart = row * width
            for (col in 0 until width) {
                val idx = rowStart + col
                if (current[idx] != previous[idx]) {
                    if (col < minCol) minCol = col
                    if (col > maxCol) maxCol = col
                }
            }
        }
        // Defensive fallback — the caller guarantees a diff exists, so this shouldn't trigger,
        // but never emit an inverted rect.
        if (minCol > maxCol) {
            minCol = 0
            maxCol = width - 1
        }

        return DirtyRect(x = minCol, y = minRow, w = maxCol - minCol + 1, h = maxRow - minRow + 1)
    }

    private fun rowsEqual(a: IntArray, b: IntArray, row: Int, width: Int): Boolean {
        val start = row * width
        return java.util.Arrays.equals(a, start, start + width, b, start, start + width)
    }

    private fun cropPixels(src: IntArray, srcWidth: Int, x: Int, y: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (row in 0 until h) {
            System.arraycopy(src, (y + row) * srcWidth + x, out, row * w, w)
        }
        return out
    }

    /**
     * Encodes a frame rectangle: JPEG when every pixel is fully opaque (several times faster
     * to encode and far smaller — what keeps continuously-changing MEDIA video sustainable at
     * the tick rate), PNG whenever any transparency is present (JPEG has no alpha channel and
     * transparency is the whole point of an overlay). The client sniffs the payload's first
     * byte (0x89 = PNG, 0xFF = JPEG) — see browserSourceOverlayPageHtml in CompanionServer.
     */
    private fun encodeFrame(argb: IntArray, w: Int, h: Int): ByteArray {
        // Early exit on the first non-opaque pixel — overlay-style frames bail almost
        // immediately; a fully opaque frame (pictures, slides, video) costs one linear scan.
        var fullyOpaque = true
        for (px in argb) {
            if (px ushr 24 != 0xFF) {
                fullyOpaque = false
                break
            }
        }
        val out = ByteArrayOutputStream()
        if (fullyOpaque) {
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, argb, 0, w)
            ImageIO.write(image, "jpg", out)
        } else {
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, w, h, argb, 0, w)
            ImageIO.write(image, "png", out)
        }
        return out.toByteArray()
    }
}
