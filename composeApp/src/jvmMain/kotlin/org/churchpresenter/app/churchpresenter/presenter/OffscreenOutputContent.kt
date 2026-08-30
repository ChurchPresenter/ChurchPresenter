package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.StageMonitorScreen
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel


/**
 * Everything a Browser Source output draws, as a composable in its own right.
 *
 * Extracted from [BrowserSourceVideoRenderer.start], where it was the content lambda of an
 * `ImageComposeScene` inside a coroutine — 209 lines that no test could reach, because reaching them
 * meant standing up the whole render loop. It is ordinary Compose: the identify overlay, the stage
 * monitor, and the mode dispatch to each presenter. Only the frame pump around it genuinely needs
 * the scene, and that stays in [BrowserSourceVideoRenderer.start].
 *
 * This mirrors the `…Content` split the dialogs already use (`PlanningCenterImportDialogContent`,
 * `RemoteEventDialogContent`, `CCLIReportContent`): the window or loop keeps the part that cannot be
 * tested, the content becomes a composable a test can render.
 *
 * [presenterManager] is passed in rather than reached for. That is the rendering-bridge exception
 * AGENT.md allows — this composable *is* the panel the renderer draws, and it read the same manager
 * before the extraction; nothing new escapes the renderer.
 */
@Composable
internal fun OffscreenOutputContent(
    context: OffscreenOutputContext,
    /**
     * Whether "no background"/Transparent renders as genuinely transparent pixels rather than as
     * the black a projector window paints.
     *
     * True for a Browser Source (OBS keys on the alpha) and for an NDI output in alpha mode. False
     * for NDI's fill modes, where the frame is flattened to opaque anyway and a transparent
     * blanking would arrive as a black hole instead of as the configured background.
     */
    transparentBlanking: Boolean = true,
) {
        val appSettingsState = context.appSettingsState
        val screenAssignmentState = context.screenAssignmentState
        val effectiveModeState = context.effectiveModeState
        val presenterManager = context.presenterManager
        val outputIndex = context.outputIndex
        val sttManager = context.sttManager
        val qaDisplayUrlState = context.qaDisplayUrlState
        val serverUrlState = context.serverUrlState
        val mediaViewModel = context.mediaViewModel
        // Transparent blanking (real alpha for OBS keying) + the media view model
        // for MEDIA playback — the same CompositionLocal the real windows provide.
        CompositionLocalProvider(
            LocalTransparentBlanking provides transparentBlanking,
            LocalMediaViewModel provides mediaViewModel
        ) {
            val appSettings by appSettingsState
            val screenAssignment by screenAssignmentState
            val effectiveMode by effectiveModeState
            val isIdentifying = when (context.kind) {
                OffscreenOutputKind.BROWSER_SOURCE ->
                    presenterManager.browserSourceIdentifying.value.contains(outputIndex)
                OffscreenOutputKind.NDI -> presenterManager.ndiIdentifying.value.contains(outputIndex)
            }
            val isLowerThirdVertical = screenAssignment.isLowerThirdVertical
            val isLowerThird = screenAssignment.isLowerThird
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
                        // Rendered into the OBS feed rather than into the app's own UI, which has
                        // no compose-resource environment here — hence the literal fallback. A
                        // renamed output shows the operator's own name instead.
                        text = when (context.kind) {
                            OffscreenOutputKind.BROWSER_SOURCE ->
                                screenAssignment.browserSourceLabelOr("Browser Source ${outputIndex + 1}")
                            OffscreenOutputKind.NDI ->
                                screenAssignment.ndiLabelOr("NDI Output ${outputIndex + 1}")
                        },
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
                    showChords = screenAssignment.showChords,
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
                    val modeCrossfadeDuration = BrowserSourceVideoRenderer.crossfadeDurationMs(
                        appSettings.bibleSettings.crossfade, appSettings.bibleSettings.transitionDuration.toInt(),
                        appSettings.songSettings.crossfade, appSettings.songSettings.transitionDuration.toInt()
                    )
                    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                    val screenCrossfadeActive = BrowserSourceVideoRenderer.isScreenCrossfadeActive(
                        appSettings.bibleSettings.crossfade, appSettings.songSettings.crossfade,
                        effectiveMode, prevEffectiveMode
                    )
                    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                    Crossfade(
                        targetState = effectiveMode,
                        animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()
                    ) { mode ->
                        val showsContent = BrowserSourceVideoRenderer.showsContentFor(mode, screenAssignment)
                        if (mode != Presenting.NONE && showsContent) {
                            when (mode) {
                                Presenting.BIBLE -> BiblePresenter(
                                    selectedVerses = presenterManager.displayedVerses.value,
                                    appSettings = appSettings,
                                    isLowerThird = isLowerThird,
                                    isLowerThirdVertical = isLowerThirdVertical,
                                    outputRole = outputRole,
                                    transitionAlpha = presenterManager.bibleTransitionAlpha.value,
                                    showBackground = showBg && screenAssignment.showBibleBackground,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                    bibleTranslations = screenAssignment.bibleTranslations
                                )
                                Presenting.LYRICS -> SongPresenter(
                                    lyricSection = presenterManager.displayedLyricSection.value,
                                    appSettings = appSettings,
                                    isLowerThird = isLowerThird,
                                    isLowerThirdVertical = isLowerThirdVertical,
                                    outputRole = outputRole,
                                    transitionAlpha = presenterManager.songTransitionAlpha.value,
                                    displayLineIndex = presenterManager.songDisplayLineIndex.value,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = presenterManager.allLyricSections.value,
                                    displaySectionIndex = presenterManager.songDisplaySectionIndex.value,
                                    showBackground = showBg && screenAssignment.showSongsBackground,
                                    crossfadeEnabled = appSettings.songSettings.crossfade,
                                    languageOverride = screenAssignment.songMode,
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
