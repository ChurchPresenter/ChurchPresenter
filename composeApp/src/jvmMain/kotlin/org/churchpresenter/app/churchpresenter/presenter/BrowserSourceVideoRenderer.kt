package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * Lower Third (Lottie graphics), Canvas (scene compositor), Q&A, STT captions, Dictionary —
 * plus Stage Monitor as a whole separate display-mode layout. Not yet supported: Media
 * (video/audio — would need `MediaViewModel`/`LocalMediaViewModel`, and unlike the others,
 * real video never settles into an "unchanged" frame, so it would force continuous encoding
 * at the full tick rate rather than only-on-change) and Website (JCEF-rendered, would need
 * its own off-screen browser instance rather than a Compose composable).
 *
 * Only encodes and emits a frame when the rendered pixels actually changed since the last
 * tick, so a static slide costs one encode, not continuous encoding — this is what keeps a
 * per-output stream far cheaper than NDI (which encodes continuously regardless of change
 * or of whether anyone is even watching).
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
    private val qaDisplayUrlState: State<String>? = null,
    private val serverUrlState: State<String>? = null,
    private val width: Int = 1920,
    private val height: Int = 1080,
) {
    private companion object {
        const val FRAME_NANOS = 16_666_667L
        const val TICK_DELAY_MS = 100L // ~10fps sampling; only changed frames are actually encoded/emitted
    }

    /**
     * Latest frame delta, replayed to any newly-subscribed HTTP client. Uses [BufferOverflow.DROP_OLDEST]
     * (the established pattern elsewhere in this codebase) rather than the default SUSPEND — a
     * slow network consumer must never stall the render loop itself, only skip stale frames.
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
                val appSettings by appSettingsState
                val screenAssignment by screenAssignmentState
                val effectiveMode by effectiveModeState
                val isIdentifying = presenterManager.browserSourceIdentifying.value.contains(outputIndex)
                val isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD
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
                        val showsContent = when (effectiveMode) {
                            Presenting.BIBLE -> screenAssignment.showBible
                            Presenting.LYRICS -> screenAssignment.showSongs
                            Presenting.PICTURES, Presenting.PRESENTATION -> screenAssignment.showPictures
                            Presenting.ANNOUNCEMENTS -> screenAssignment.showAnnouncements
                            Presenting.LOWER_THIRD -> screenAssignment.showStreaming
                            Presenting.CANVAS -> true
                            Presenting.QA -> screenAssignment.showQA
                            Presenting.STT -> screenAssignment.showSTT
                            Presenting.DICTIONARY -> screenAssignment.showDictionary
                            else -> false
                        }
                        if (effectiveMode != Presenting.NONE && showsContent) {
                            when (effectiveMode) {
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
                                    val frozen = presenterManager.slideFrozen.value
                                    SlidePresenter(
                                        slide = if (frozen) null else presenterManager.displayedSlide.value,
                                        previousSlide = if (frozen) null else presenterManager.previousDisplayedSlide.value,
                                        transitionAlpha = presenterManager.slideTransitionAlpha.value,
                                        slideOffset = presenterManager.slideSlideOffset.value,
                                        animationType = presenterManager.animationType.value,
                                        outputRole = outputRole
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
                                        outputRole = outputRole
                                    )
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
                                else -> {}
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
                while (true) {
                    timeNanos += FRAME_NANOS
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

                    if (contentChanged || newSubscriberJoined) {
                        val frame = if (previous == null || newSubscriberJoined) {
                            BrowserSourceFrame(0, 0, width, height, width, height, encodePng(intBuf, width, height))
                        } else {
                            val rect = computeDirtyRect(intBuf, previous, width, height)
                            val cropped = cropPixels(intBuf, width, rect.x, rect.y, rect.w, rect.h)
                            BrowserSourceFrame(
                                rect.x, rect.y, rect.w, rect.h, width, height,
                                encodePng(cropped, rect.w, rect.h)
                            )
                        }
                        frames.emit(frame)
                        if (contentChanged) lastBuf = intBuf.copyOf()
                    }
                    delay(TICK_DELAY_MS)
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

    private fun encodePng(argb: IntArray, w: Int, h: Int): ByteArray {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, w, h, argb, 0, w)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
