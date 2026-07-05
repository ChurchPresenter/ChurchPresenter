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

    /** Latest PNG-encoded frame, replayed to any newly-subscribed HTTP client. */
    val frames = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 1)

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
                        showBackground = !screenAssignment.browserSourceTransparentBackground
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
                                    transitionAlpha = presenterManager.announcementTransitionAlpha.value
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
                while (true) {
                    timeNanos += FRAME_NANOS
                    Snapshot.sendApplyNotifications()
                    val img = scene.render(timeNanos)
                    try {
                        img.toComposeImageBitmap().readPixels(intBuf)
                    } finally {
                        img.close()
                    }
                    if (lastBuf == null || !intBuf.contentEquals(lastBuf)) {
                        val png = encodePng(intBuf, width, height)
                        frames.emit(png)
                        lastBuf = intBuf.copyOf()
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

    private fun encodePng(argb: IntArray, w: Int, h: Int): ByteArray {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, w, h, argb, 0, w)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
