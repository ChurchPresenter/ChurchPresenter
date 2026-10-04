package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.shownModeFor
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.StageMonitorScreen
import org.churchpresenter.app.churchpresenter.qaQrCodeUrl
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.sharedui.models.Presenting


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
        val mediaViewModel = context.mediaViewModel
        // Transparent blanking (real alpha for OBS keying) + the media view model
        // for MEDIA playback — the same CompositionLocal the real windows provide.
        CompositionLocalProvider(
            LocalTransparentBlanking provides transparentBlanking,
            LocalMediaViewModel provides mediaViewModel
        ) {
            val globalSettings by appSettingsState
            val rawScreenAssignment by screenAssignmentState
            // The profile this output is assigned to -- everything about how it looks and what it
            // shows. Everything below reads this, never the raw assignment.
            val profile = remember(globalSettings.projectionSettings, rawScreenAssignment) {
                globalSettings.projectionSettings.profileFor(rawScreenAssignment) ?: OutputProfile()
            }
            // What THIS output renders with: the same profile resolution the presenter windows do
            // in PresenterOutputContent.
            // Remembered for the reason the presenter windows remember theirs: the merge decodes.
            val appSettings = remember(globalSettings, profile) {
                globalSettings.resolvedFor(profile)
            }
            val effectiveMode = presenterManager.shownModeFor(profile, effectiveModeState.value)
            val isIdentifying = when (context.kind) {
                OffscreenOutputKind.BROWSER_SOURCE ->
                    presenterManager.browserSourceIdentifying.value.contains(outputIndex)
                OffscreenOutputKind.NDI -> presenterManager.ndiIdentifying.value.contains(outputIndex)
                OffscreenOutputKind.OMT -> presenterManager.omtIdentifying.value.contains(outputIndex)
            }
            // A musician's transpose is a Browser Source feature: its musician page is where it is
            // set. NDI outputs draw the song in the key it is written in.
            val transposes by presenterManager.browserSourceTranspose.collectAsState()
            val transposeSteps = if (context.kind == OffscreenOutputKind.BROWSER_SOURCE) {
                transposes[outputIndex] ?: 0
            } else {
                0
            }
            val isLowerThird = profile.isLowerThird
            val isStageMonitor = profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
            val outputRole = Constants.OUTPUT_ROLE_NORMAL
            // General per-output background toggle — same field/logic as native output
            // (main.kt). showBibleBackground/showSongsBackground below are an additional
            // layer on top of this, not a replacement for it.
            val showBg = if (isLowerThird) profile.showLowerThirdBackground else profile.showFullscreenBackground
            // One tile of a merged picture shows only its own part of it -- see MergeTile.kt. The
            // identify card below is left out of that, so it names this output, not the picture.
            val mergeOutput = Constants.previewOutputKey(context.kind.previewKind, outputIndex)
            val merge = remember(globalSettings.projectionSettings) {
                globalSettings.projectionSettings.liveMerges()[mergeOutput]
            }

            if (isIdentifying) {
                IdentifyCard(identifyLabel(context.kind, rawScreenAssignment, outputIndex))
            } else MergedTile(merge, mergeOutput) { if (isStageMonitor) {
                OffscreenStageMonitor(appSettings, profile, effectiveMode, transposeSteps, presenterManager)
            } else {
                PresenterScreen(
                    appSettings = appSettings,
                    outputRole = outputRole,
                    isLowerThird = isLowerThird,
                    showBackground = showBg
                ) {
                    // Mode-to-mode crossfade, as the real output windows do it.
                    val modeCrossfadeDuration = BrowserSourceVideoRenderer.crossfadeDurationMs(
                        appSettings.bibleSettings.crossfade, appSettings.bibleSettings.transitionDuration.toInt(),
                        appSettings.songSettings.crossfade, appSettings.songSettings.transitionDuration.toInt()
                    )
                    val screenCrossfadeActive = rememberScreenCrossfadeActive(appSettings, effectiveMode)
                    val modeContent: @Composable (Presenting) -> Unit = { mode ->
                        val showsContent = showsContentFor(mode, profile)
                        if (mode != Presenting.NONE && showsContent) {
                            CompositionLocalProvider(
                                LocalLottieBandClock provides presenterManager.lottieBandClock,
                                LocalBandSongLineIndex provides presenterManager.bandSongLineIndex.value,
                                LocalBandOutgoing provides presenterManager.bandOutgoing.value,
                            ) {
                            LowerThirdLayout(mode, profile, appSettings, showBg) {
                            OutputLayers(
                                mode = mode,
                                surface = OutputSurface(
                                    kind = OutputSurfaceKind.OFFSCREEN,
                                    profile = profile,
                                    appSettings = appSettings,
                                    presenterManager = presenterManager,
                                    outputRole = outputRole,
                                    showBg = showBg,
                                    mediaViewModel = mediaViewModel,
                                    sttManager = context.sttManager,
                                    qrCodeUrl = qaQrCodeUrl(
                                        context.qaDisplayUrlState?.value.orEmpty(),
                                        context.serverUrlState?.value.orEmpty(),
                                    ),
                                ),
                            )
                            }
                            }
                        }
                    }
                    Crossfade(
                        targetState = effectiveMode,
                        animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()
                    ) { mode -> modeContent(mode) }
                    OverlayModes(presenterManager, profile, effectiveMode, modeContent)
                }
            }
            }
        }
}

/**
 * Whether the change into [effectiveMode] crossfades: only when Bible or song crossfade is on and
 * neither the mode left nor the mode entered is NONE -- as the real output windows decide it.
 */
@Composable
private fun rememberScreenCrossfadeActive(appSettings: AppSettings, effectiveMode: Presenting): Boolean {
    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
    val active = BrowserSourceVideoRenderer.isScreenCrossfadeActive(
        appSettings.bibleSettings.crossfade, appSettings.songSettings.crossfade,
        effectiveMode, prevEffectiveMode
    )
    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
    return active
}

/** The card an output shows while it is being identified: its name, large, over a dim backdrop. */
@Composable
private fun IdentifyCard(label: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

/**
 * What an identified output is called. Rendered into the OBS feed rather than into the app's own UI,
 * which has no compose-resource environment here -- hence the literal fallback. A renamed output
 * shows the operator's own name instead.
 */
private fun identifyLabel(kind: OffscreenOutputKind, assignment: ScreenAssignment, outputIndex: Int): String =
    when (kind) {
        OffscreenOutputKind.BROWSER_SOURCE -> assignment.browserSourceLabelOr("Browser Source ${outputIndex + 1}")
        OffscreenOutputKind.NDI -> assignment.ndiLabelOr("NDI Output ${outputIndex + 1}")
        OffscreenOutputKind.OMT -> assignment.omtLabelOr("OMT Output ${outputIndex + 1}")
    }

/** The stage monitor layout, fed from what the presenter manager has on screen. */
@Composable
private fun OffscreenStageMonitor(
    appSettings: AppSettings,
    profile: OutputProfile,
    effectiveMode: Presenting,
    transposeSteps: Int,
    presenterManager: PresenterManager,
) {
    StageMonitorScreen(
        sm = appSettings.stageMonitorSettings,
        presentingMode = effectiveMode,
        announcementActive = effectiveMode == Presenting.ANNOUNCEMENTS ||
            presenterManager.isLive(Presenting.ANNOUNCEMENTS),
        showChords = profile.showChords,
        transposeSteps = transposeSteps,
        currentLyricSection = presenterManager.displayedLyricSection.value,
        allLyricSections = presenterManager.displayedSongPosition.value.allSections,
        songDisplaySectionIndex = presenterManager.displayedSongPosition.value.sectionIndex,
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
}

/** The website snapshot an offscreen output draws, for a test to find. */
internal const val WEB_SNAPSHOT_TAG = "offscreen-web-snapshot"
