package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import org.churchpresenter.app.churchpresenter.presenter.LocalBandOutgoing
import org.churchpresenter.app.churchpresenter.presenter.LocalBandSongLineIndex
import org.churchpresenter.app.churchpresenter.presenter.LocalLottieBandClock
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdLayout
import org.churchpresenter.app.churchpresenter.presenter.OverlayModes
import org.churchpresenter.app.churchpresenter.presenter.showsContentFor
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.fill_badge
import org.churchpresenter.strings.generated.resources.live_preview_nothing
import org.churchpresenter.strings.generated.resources.lock_screen_to_tab
import org.churchpresenter.strings.generated.resources.screen_locked_badge
import org.churchpresenter.strings.generated.resources.unlock_screen
import org.churchpresenter.app.churchpresenter.StageMonitorScreen
import org.churchpresenter.app.churchpresenter.offersTranspose
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.presenter.OutputLayers
import org.churchpresenter.app.churchpresenter.presenter.OutputSurface
import org.churchpresenter.app.churchpresenter.presenter.OutputSurfaceKind
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.drawsOverContent
import org.churchpresenter.app.churchpresenter.viewmodel.unlockedModeFor
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.mode

/** A stage monitor output's preview: its confidence layout, scaled into the tile. */
@Composable
internal fun PreviewStageMonitor(
    presenterManager: PresenterManager,
    profile: OutputProfile,
    outputSettings: AppSettings,
    outputSize: OutputSize,
    effectiveMode: Presenting,
    transposeSteps: Int,
) {
    val presentingMode by presenterManager.presentingMode
    val displayedVerses by presenterManager.displayedVerses
    val nextVerses by presenterManager.nextVerses
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songPosition by presenterManager.displayedSongPosition
    val displayedImagePath by presenterManager.displayedImagePath
    val displayedSlide by presenterManager.displayedSlide
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val displayedDictionaryEntry by presenterManager.displayedDictionaryEntry
    val presenterNotes by presenterManager.presenterNotes
    ScaledPresenterContent(output = outputSize) {
        StageMonitorScreen(
            sm = outputSettings.stageMonitorSettings,
            presentingMode = presentingMode,
            showChords = profile.showChords,
            transposeSteps = transposeSteps,
            announcementActive = effectiveMode == Presenting.ANNOUNCEMENTS ||
                presenterManager.isLive(Presenting.ANNOUNCEMENTS),
            currentLyricSection = displayedLyricSection,
            allLyricSections = songPosition.allSections,
            songDisplaySectionIndex = songPosition.sectionIndex,
            displayedVerses = displayedVerses,
            nextVerses = nextVerses,
            announcementText = displayedAnnouncementText,
            displayedImagePath = displayedImagePath,
            displayedSlide = displayedSlide,
            presenterNotes = presenterNotes,
            activeScene = activeScene,
            displayedQuestion = displayedQuestion,
            qaSettings = outputSettings.qaSettings,
            displayedDictionaryEntry = displayedDictionaryEntry,
            dictionarySettings = outputSettings.dictionarySettings,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** What the tile draws for [mode], inside the output's crossfade and lower-third layout. */
@Composable
internal fun PreviewMode(
    mode: Presenting,
    presenterManager: PresenterManager,
    profile: OutputProfile,
    outputSettings: AppSettings,
    showsBackground: Boolean,
    primaryRole: String,
    qaUrl: String,
    sttManager: STTManager?,
) {
    OutputLayers(
        mode = mode,
        surface = OutputSurface(
            kind = OutputSurfaceKind.PREVIEW,
            profile = profile,
            appSettings = outputSettings,
            presenterManager = presenterManager,
            outputRole = primaryRole,
            showBg = showsBackground,
            mediaViewModel = LocalMediaViewModel.current,
            sttManager = sttManager,
            qrCodeUrl = qaUrl,
        ),
    )
}

/**
 * A website on the tile: the live snapshot WebTab pushes every 200ms through the presenter manager.
 * A second JFXPanel instance can't be scaled or clipped by Compose, so this mirrors one instead.
 */
@Composable
internal fun PreviewWebsiteMirror(presenterManager: PresenterManager) {
    val snapshot = presenterManager.webSnapshot.value
    if (snapshot != null) {
        Image(
            bitmap = snapshot,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(PREVIEW_BACKGROUND)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (presenterManager.websiteUrl.value.isBlank()) stringResource(Res.string.live_preview_nothing)
                       else presenterManager.websiteUrl.value,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 2
            )
        }
    }

}

/**
 * The badges over a preview: FILL with a key output, LOCKED and the lock toggle, the transpose,
 * the output's [label] when shown, and the equalizer while media is [mediaAudible].
 */
@Composable
internal fun BoxScope.PreviewBadges(
    screenIndex: Int,
    rawAssignment: ScreenAssignment,
    profile: OutputProfile,
    effectiveMode: Presenting,
    locks: Map<Int, Presenting>,
    onToggleLock: (Presenting?) -> Unit,
    transposeSteps: Int,
    onTranspose: ((Int?) -> Unit)?,
    label: String?,
    mediaAudible: Boolean,
) {
    // FILL badge when key output is configured
    if (rawAssignment.hasKeyOutput) {
        Text(
            text = stringResource(Res.string.fill_badge),
            color = Color.White,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(Color(LIVE_BADGE_COLOR), AppShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }

    // LOCKED badge + lock toggle — not applicable to Stage Monitor screens, which route
    // their own content dynamically and are never locked to a single tab.
    val lockedMode = locks[screenIndex]
    if (profile.displayMode != Constants.DISPLAY_MODE_STAGE_MONITOR) {
        if (lockedMode != null) {
            Text(
                text = stringResource(Res.string.screen_locked_badge),
                color = Color.White,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = if (rawAssignment.hasKeyOutput) 24.dp else 4.dp)
                    .background(Color(LOCK_BADGE_COLOR), AppShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Lock toggle button — bottom-right corner
        KeyIconButton(
            onClick = {
                if (lockedMode != null) {
                    onToggleLock(null)
                } else {
                    onToggleLock(effectiveMode)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(24.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (lockedMode != null) Color(LOCK_BADGE_COLOR) else Color.White.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                imageVector = if (lockedMode != null) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (lockedMode != null) {
                    stringResource(Res.string.unlock_screen)
                } else {
                    stringResource(Res.string.lock_screen_to_tab)
                },
                modifier = Modifier.size(13.dp)
            )
        }
    }

    // The musicians' transpose, on an output whose profile offers it. A Stage Monitor has no
    // lock toggle, so it takes that corner. The same offset the output's page buttons move.
    if (profile.offersTranspose() && onTranspose != null) {
        TransposeOverlay(
            steps = transposeSteps,
            onStep = onTranspose,
            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
        )
    }

    // Screen/output label
    if (label != null) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), AppShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }

    // Animated audio indicator — only when presenting and media is playing
    if (effectiveMode != Presenting.NONE && mediaAudible) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .background(Color.Black.copy(alpha = 0.6f), AppShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            AnimatedEqualizer()
        }
    }
}

/** Whether this output shows anything: its own mode's content, or an overlay up over the slide. */
internal fun previewShowsSomething(
    presenterManager: PresenterManager,
    effectiveMode: Presenting,
    profile: OutputProfile,
): Boolean =
    (effectiveMode != Presenting.NONE && showsContentFor(effectiveMode, profile)) ||
        (effectiveMode == presenterManager.unlockedModeFor(profile) &&
            presenterManager.overlays.value.any { profile.drawsOverContent(it) && showsContentFor(it, profile) })

/**
 * The tile's slide crossfading between modes, and the overlays up over it, each inside the output's
 * lower-third layout.
 */
@Composable
internal fun PreviewModeLayers(
    presenterManager: PresenterManager,
    effectiveMode: Presenting,
    showsContent: Boolean,
    profile: OutputProfile,
    outputSettings: AppSettings,
    showsBackground: Boolean,
    primaryRole: String,
    qaUrl: String,
    sttManager: STTManager?,
) {
    // The clock stays wrapped: unwrapping it here would recompose the tile on every band frame.
    val bandSongLineIndex by presenterManager.bandSongLineIndex
    val bandOutgoing by presenterManager.bandOutgoing
    val modeContent: @Composable (Presenting) -> Unit = { mode ->
        CompositionLocalProvider(
            LocalLottieBandClock provides presenterManager.lottieBandClock,
            LocalBandSongLineIndex provides bandSongLineIndex,
            LocalBandOutgoing provides bandOutgoing,
        ) {
            LowerThirdLayout(mode, profile, outputSettings, showsBackground) {
                PreviewMode(
                    mode, presenterManager, profile, outputSettings, showsBackground, primaryRole, qaUrl, sttManager,
                )
            }
        }
    }
    if (effectiveMode != Presenting.NONE && showsContent) {
        Crossfade(
            targetState = effectiveMode,
            animationSpec = tween(previewCrossfadeMs(outputSettings)),
        ) { mode -> modeContent(mode) }
    }
    OverlayModes(presenterManager, profile, effectiveMode) { mode ->
        if (showsContentFor(mode, profile)) modeContent(mode)
    }
}

private const val PREVIEW_BACKGROUND = 0xFF121212

private const val LIVE_BADGE_COLOR = 0xFF2196F3

private const val LOCK_BADGE_COLOR = 0xFFFFC107
