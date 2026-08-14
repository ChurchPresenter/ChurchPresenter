package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.screen_number
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.jetbrains.compose.resources.stringResource

/**
 * What a single audience output actually draws for the current [effectiveMode] — the dispatch from
 * [Presenting] to the matching presenter, driven by [screenAssignment] so crossfade, lower-third
 * layout, per-type visibility, language mode and backgrounds behave identically for every output.
 *
 * Hoisted out of PresenterWindows.kt, where it was a local lambda inside a composable that builds
 * real AWT windows -- which made it unreachable from a headless test. As a top-level composable it
 * renders with no Window involved, so the mode-to-presenter mapping can finally be asserted.
 *
 * Takes [presenterManager] directly: this is the rendering-bridge exception AGENT.md allows (the
 * same one MediaPresenter/PresentationPlayer rely on), and the alternative is threading 34
 * individually-hoisted values through the signature.
 */
@Composable
internal fun PresenterOutputContent(
    screenAssignment: ScreenAssignment,
    effectiveMode: Presenting,
    screenNumber: Int?,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    mediaViewModel: MediaViewModel,
    sttManager: STTManager,
    serverUrl: String,
    qaDisplayUrl: String,
    identifyingScreen: Boolean,
    lottieComposition: LottieComposition?,
    clearAnnouncementOnFinish: () -> Unit,
) {
    val presentingMode by presenterManager.presentingMode
    val modeCrossfadeDuration = modeCrossfadeDuration(appSettings.bibleSettings, appSettings.songSettings)
    val displayedVerses by presenterManager.displayedVerses
    val nextVerses by presenterManager.nextVerses
    val displayedLyricSection by presenterManager.displayedLyricSection
    val allLyricSections by presenterManager.allLyricSections
    val songDisplaySectionIndex by presenterManager.songDisplaySectionIndex
    val displayedImagePath by presenterManager.displayedImagePath
    val displayedSlide by presenterManager.displayedSlide
    val announcementText by presenterManager.announcementText
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val displayedDictionaryEntry by presenterManager.displayedDictionaryEntry
    val presenterNotes by presenterManager.presenterNotes

        val primaryRole = screenAssignment.primaryOutputRole
        val showBg = showsOutputBackground(screenAssignment)
        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
            if (screenAssignment.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
                // Stage monitor: dedicated presenter-confidence layout
                StageMonitorScreen(
                    sm = appSettings.stageMonitorSettings,
                    presentingMode = presentingMode,
                    announcementActive = effectiveMode == Presenting.ANNOUNCEMENTS,
                    currentLyricSection = displayedLyricSection,
                    allLyricSections = allLyricSections,
                    songDisplaySectionIndex = songDisplaySectionIndex,
                    displayedVerses = displayedVerses,
                    nextVerses = nextVerses,
                    announcementText = displayedAnnouncementText,
                    displayedImagePath = displayedImagePath,
                    displayedSlide = displayedSlide,
                    presenterNotes = presenterNotes,
                    activeScene = activeScene,
                    displayedQuestion = displayedQuestion,
                    qaSettings = appSettings.qaSettings,
                    displayedDictionaryEntry = displayedDictionaryEntry,
                    dictionarySettings = appSettings.dictionarySettings,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PresenterScreen(
                    modifier = Modifier.fillMaxSize(),
                    appSettings = appSettings,
                    outputRole = primaryRole,
                    isLowerThird = screenAssignment.isLowerThird,
                    showBackground = showBg
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                    mediaViewModel.pause()
                                    presenterManager.requestClearDisplay()
                                    true
                                } else false
                            }
                    ) {
                        var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                        val screenCrossfadeActive = isScreenCrossfadeActive(
                        appSettings.bibleSettings, appSettings.songSettings, effectiveMode, prevEffectiveMode,
                    )
                        if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                        Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                            PresenterModeContent(
                                mode = mode,
                                screenAssignment = screenAssignment,
                                presenterManager = presenterManager,
                                appSettings = appSettings,
                                mediaViewModel = mediaViewModel,
                                sttManager = sttManager,
                                serverUrl = serverUrl,
                                qaDisplayUrl = qaDisplayUrl,
                                lottieComposition = lottieComposition,
                                clearAnnouncementOnFinish = clearAnnouncementOnFinish,
                                outputRole = primaryRole,
                                showBg = showBg,
                            )
                        }

                        // Clear live browser ref when leaving WEBSITE mode
                        LaunchedEffect(presentingMode) {
                            if (presentingMode != Presenting.WEBSITE) {
                                presenterManager.setLiveBrowser(null)
                            }
                        }

                        if (screenNumber != null && identifyingScreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.screen_number, screenNumber),
                                    color = Color.White,
                                    fontSize = 96.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
}
