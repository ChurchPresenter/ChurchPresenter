package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.fill_badge
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.display_lower_third_horizontal
import churchpresenter.composeapp.generated.resources.display_lower_third_vertical
import churchpresenter.composeapp.generated.resources.live_preview_nothing
import churchpresenter.composeapp.generated.resources.live_preview_title
import churchpresenter.composeapp.generated.resources.lock_screen_to_tab
import churchpresenter.composeapp.generated.resources.screen_locked_badge
import churchpresenter.composeapp.generated.resources.screen_number
import churchpresenter.composeapp.generated.resources.unlock_screen
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.StageMonitorScreen
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAQRCodePresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.DevFlags
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A scaled-down preview of whatever is currently live on the presenter windows.
 * Shows one preview per configured display, each respecting its screen assignment.
 */
@Composable
fun LivePreviewPanel(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    qaDisplayUrl: String = "",
    sttManager: STTManager? = null,
) {
    val proj = appSettings.projectionSettings
    val deckLinkCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val realWindowCount = ((rememberScreenDevices().size - 1) + deckLinkCount).coerceAtLeast(0)
    // Mirror main.kt's dev-fallback: with no real output, preview the dev fallback window(s) so the
    // right-pane preview matches what's actually opened. devWindowCount lets several be previewed.
    val devWindowedFallback = (!BuildConfig.IS_RELEASE || DevFlags.forceDevWindow) && realWindowCount == 0
    val displayCount = realWindowCount + if (devWindowedFallback) proj.devWindowCount.coerceAtLeast(1) else 0
    val mediaViewModel = LocalMediaViewModel.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until displayCount) {
            val screenAssignment = proj.getAssignment(i)

            // Skip displays the user set to "None" — but never skip dev-fallback slots (i >=
            // realWindowCount): those are auto-resolved to None only because no hardware exists,
            // yet main.kt still opens a window for them, so they must appear in the preview too.
            val isDevFallbackSlot = devWindowedFallback && i >= realWindowCount
            if (!isDevFallbackSlot && screenAssignment.targetDisplay == Constants.KEY_TARGET_NONE) continue

            SingleDisplayPreview(
                screenIndex = i,
                screenAssignment = screenAssignment,
                presenterManager = presenterManager,
                appSettings = appSettings,
                modifier = Modifier.fillMaxWidth(),
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                sttManager = sttManager,
                locks = presenterManager.screenLocks.value,
                onToggleLock = { mode -> presenterManager.setScreenLock(i, mode) },
                label = stringResource(Res.string.screen_number, i + 1),
            )
        }

        // Browser Source outputs — virtual, no physical hardware, so they get their own
        // loop over ProjectionSettings.browserSourceOutputs and their own lock index space.
        for (i in proj.browserSourceOutputs.indices) {
            SingleDisplayPreview(
                screenIndex = i,
                screenAssignment = proj.browserSourceOutputs[i],
                presenterManager = presenterManager,
                appSettings = appSettings,
                modifier = Modifier.fillMaxWidth(),
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                sttManager = sttManager,
                locks = presenterManager.browserSourceLocks.value,
                onToggleLock = { mode -> presenterManager.setBrowserSourceLock(i, mode) },
                label = stringResource(Res.string.browser_source_output_label, i + 1),
            )
        }

        // Media controls — visible when presenting and media is loaded.
        // Controls disappear only via Clear Display / Escape (which reset presentingMode to NONE).
        val presentingMode by presenterManager.presentingMode
        if (presentingMode != Presenting.NONE
            && mediaViewModel != null && mediaViewModel.isLoaded
        ) {
            MediaPreviewControls(
                    isPlaying = mediaViewModel.isPlaying,
                    duration = mediaViewModel.duration,
                    currentPosition = mediaViewModel.currentPosition,
                    formatTime = { mediaViewModel.formatTime(it) },
                    onTogglePlayPause = { mediaViewModel.togglePlayPause() },
                    onSeekTo = { mediaViewModel.seekTo(it) }
                )
        }
    }
}

@Composable
private fun SingleDisplayPreview(
    screenIndex: Int,
    screenAssignment: ScreenAssignment,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    qaDisplayUrl: String = "",
    sttManager: STTManager? = null,
    locks: Map<Int, Presenting> = emptyMap(),
    onToggleLock: (Presenting?) -> Unit = {},
    label: String,
) {
    val presentingMode by presenterManager.presentingMode
    val effectiveMode = locks[screenIndex] ?: presentingMode
    val displayedVerses by presenterManager.displayedVerses
    val nextVerses by presenterManager.nextVerses
    val bibleTransitionAlpha by presenterManager.bibleTransitionAlpha
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songTransitionAlpha by presenterManager.songTransitionAlpha
    val songDisplayLineIndex by presenterManager.songDisplayLineIndex
    val allLyricSections by presenterManager.allLyricSections
    val songDisplaySectionIndex by presenterManager.songDisplaySectionIndex
    val displayedImagePath by presenterManager.displayedImagePath
    val previousDisplayedImagePath by presenterManager.previousDisplayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val pictureSlideOffset by presenterManager.pictureSlideOffset
    val displayedSlide by presenterManager.displayedSlide
    val previousDisplayedSlide by presenterManager.previousDisplayedSlide
    val slideFrozen by presenterManager.slideFrozen
    val presentationFrame by presenterManager.presentationFrame
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val slideSlideOffset by presenterManager.slideSlideOffset
    val animationType by presenterManager.animationType
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottieComposition by rememberLottieComposition(key = lottieJsonContent) {
        LottieCompositionSpec.JsonString(lottieJsonContent)
    }
    val lottieProgress by presenterManager.lottieProgress
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val websiteUrl by presenterManager.websiteUrl
    val webSnapshot by presenterManager.webSnapshot
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val displayedDictionaryEntry by presenterManager.displayedDictionaryEntry
    val presenterNotes by presenterManager.presenterNotes
    val mediaViewModel = LocalMediaViewModel.current

    val isLowerThirdVertical = screenAssignment.isLowerThirdVertical
    val isLowerThird = screenAssignment.isLowerThird

    // Determine if this screen shows the current content
    val showsContent = when (effectiveMode) {
        Presenting.BIBLE -> screenAssignment.showBible
        Presenting.LYRICS -> screenAssignment.showSongs
        Presenting.PICTURES, Presenting.PRESENTATION -> screenAssignment.showPictures
        Presenting.MEDIA -> screenAssignment.showMedia
        Presenting.LOWER_THIRD -> screenAssignment.showStreaming
        Presenting.ANNOUNCEMENTS -> screenAssignment.showAnnouncements
        Presenting.WEBSITE -> screenAssignment.showWebsite
        Presenting.CANVAS -> screenAssignment.showCanvas
        Presenting.QA -> screenAssignment.showQA
        Presenting.STT -> screenAssignment.showSTT
        Presenting.DICTIONARY -> screenAssignment.showDictionary
        Presenting.NONE -> false
    }

    val isLive = effectiveMode != Presenting.NONE && showsContent
    val liveTransition = rememberInfiniteTransition(label = "live")
    val livePulse by liveTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isLive) Color.Red.copy(alpha = 0.85f)
                      else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(300),
        label = "border_color"
    )

    val isStageMonitor = screenAssignment.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    val displayModeChipLabel = when (screenAssignment.displayMode) {
        Constants.DISPLAY_MODE_STAGE_MONITOR -> stringResource(Res.string.display_stage_monitor)
        Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL -> stringResource(Res.string.display_lower_third_horizontal)
        Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL -> stringResource(Res.string.display_lower_third_vertical)
        else -> null
    }

    Column(modifier = modifier) {
        // Display mode chip (e.g. "Stage Monitor", "Lower Third") — sits above the preview so it
        // never covers the content.
        if (displayModeChipLabel != null) {
            Text(
                text = displayModeChipLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(presenterAspectRatio())
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
        ) {
        val primaryRole = screenAssignment.primaryOutputRole

        // ── Stage Monitor: dedicated presenter-confidence layout, not the normal presenter ──
        if (screenAssignment.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
            ScaledPresenterContent {
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
            }
        } else
        // ── Scaled presenter content (all modes except WEBSITE) ───────────────
        // JavaFX/Swing heavyweight components cannot be scaled by Compose layout,
        // so WEBSITE is handled separately below at native size.
        if (effectiveMode != Presenting.WEBSITE) {
            ScaledPresenterContent {
                PresenterScreen(appSettings = appSettings, outputRole = primaryRole, isLowerThird = isLowerThird) {
                    if (effectiveMode != Presenting.NONE && showsContent) {
                        val modeCrossfadeOn = appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade
                        val modeCrossfadeDur = maxOf(
                            if (appSettings.bibleSettings.crossfade) appSettings.bibleSettings.transitionDuration.toInt() else 0,
                            if (appSettings.songSettings.crossfade) appSettings.songSettings.transitionDuration.toInt() else 0
                        ).coerceAtLeast(100)
                        Crossfade(targetState = effectiveMode, animationSpec = tween(if (modeCrossfadeOn) modeCrossfadeDur else 0)) { mode ->
                        when (mode) {
                            Presenting.BIBLE ->
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = isLowerThird,
                                    isLowerThirdVertical = isLowerThirdVertical,
                                    outputRole = primaryRole,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                    languageMode = screenAssignment.bibleMode
                                )
                            Presenting.LYRICS ->
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = isLowerThird,
                                    isLowerThirdVertical = isLowerThirdVertical,
                                    outputRole = primaryRole,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade,
                                    languageOverride = screenAssignment.songMode
                                )
                            Presenting.PICTURES ->
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    previousImagePath = previousDisplayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha,
                                    slideOffset = pictureSlideOffset,
                                    animationType = animationType,
                                )
                            Presenting.PRESENTATION ->
                                PresentationPresenter(
                                    frame = presentationFrame,
                                    slide = displayedSlide,
                                    previousSlide = previousDisplayedSlide,
                                    transitionAlpha = slideTransitionAlpha,
                                    slideOffset = slideSlideOffset,
                                    animationType = animationType,
                                    frozen = slideFrozen
                                )
                            Presenting.MEDIA ->
                                if (mediaViewModel != null && !mediaViewModel.isAudioFile) {
                                    MediaPresenter(modifier = Modifier.fillMaxSize(), audioEnabled = false, transitionAlpha = mediaTransitionAlpha)
                                }
                            Presenting.LOWER_THIRD ->
                                LowerThirdPresenter(
                                    composition = lottieComposition,
                                    progress = { presenterManager.lottieProgress.value },
                                    appSettings = appSettings,
                                    frame = presenterManager.lottieFrame.value
                                )
                            Presenting.ANNOUNCEMENTS ->
                                AnnouncementsPresenter(
                                    text = displayedAnnouncementText,
                                    appSettings = appSettings,
                                    outputRole = primaryRole,
                                    transitionAlpha = announcementTransitionAlpha
                                )
                            Presenting.CANVAS ->
                                ScenePresenter(scene = activeScene)
                            Presenting.QA -> {
                                val showQRCode by presenterManager.showQRCodeOnDisplay
                                if (showQRCode) {
                                    QAQRCodePresenter(url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa", qaSettings = appSettings.qaSettings)
                                } else {
                                    QAPresenter(question = displayedQuestion, qaSettings = appSettings.qaSettings)
                                }
                            }
                            Presenting.STT -> {
                                if (sttManager != null) {
                                    STTPresenter(
                                        segments = sttManager.segments,
                                        inProgressText = sttManager.inProgressText.value,
                                        translationSegments = sttManager.translationSegments,
                                        inProgressTranslation = sttManager.inProgressTranslation.value,
                                        highlightedWords = sttManager.highlightedWords,
                                        sttSettings = appSettings.sttSettings,
                                        outputRole = primaryRole,
                                    )
                                }
                            }
                            Presenting.DICTIONARY ->
                                DictionaryPresenter(
                                    entry = displayedDictionaryEntry,
                                    dictionarySettings = appSettings.dictionarySettings,
                                    outputRole = primaryRole,
                                    transitionAlpha = 1f,
                                )
                            else -> {}
                        }
                        }
                    }
                }
            }
        }

        // ── WEBSITE: display live screenshot captured from WebTab's WebView ─
        // A second JFXPanel instance can't be scaled/clipped by Compose.
        // Instead, WebTab pushes a snapshot bitmap every 200ms via PresenterManager
        // so this panel shows a pixel-accurate mirror including scroll position.
        if (screenAssignment.displayMode != Constants.DISPLAY_MODE_STAGE_MONITOR && effectiveMode == Presenting.WEBSITE) {
            val snapshot = webSnapshot
            if (snapshot != null) {
                Image(
                    bitmap = snapshot,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (websiteUrl.isBlank()) stringResource(Res.string.live_preview_nothing)
                               else websiteUrl,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }
        }

        // "LIVE" badge — only when this screen is showing content
        if (isLive) {
            Text(
                text = stringResource(Res.string.live_preview_title),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Red.copy(alpha = livePulse), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // FILL badge when key output is configured
        if (screenAssignment.hasKeyOutput) {
            Text(
                text = stringResource(Res.string.fill_badge),
                color = Color.White,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color(0xFF2196F3), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // LOCKED badge + lock toggle — not applicable to Stage Monitor screens, which route
        // their own content dynamically and are never locked to a single tab.
        val lockedMode = locks[screenIndex]
        if (!isStageMonitor) {
            if (lockedMode != null) {
                Text(
                    text = stringResource(Res.string.screen_locked_badge),
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = if (screenAssignment.hasKeyOutput) 24.dp else 4.dp)
                        .background(Color(0xFFFFC107), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Lock toggle button — bottom-right corner
            IconButton(
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
                    contentColor = if (lockedMode != null) Color(0xFFFFC107) else Color.White.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = if (lockedMode != null) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (lockedMode != null) stringResource(Res.string.unlock_screen) else stringResource(Res.string.lock_screen_to_tab),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Screen/output label
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )

        // Animated audio indicator — only when presenting and media is playing
        if (effectiveMode != Presenting.NONE
            && mediaViewModel != null && mediaViewModel.isLoaded && mediaViewModel.isPlaying
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                AnimatedEqualizer()
            }
        }
        }
    }
}

@Composable
private fun AnimatedEqualizer() {
    val transition = rememberInfiniteTransition()
    val barHeights = (0..3).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 300 + index * 100,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        barHeights.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightFraction.value)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun MediaPreviewControls(
    isPlaying: Boolean,
    duration: Long,
    currentPosition: Long,
    formatTime: (Long) -> String,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play
                ),
                contentDescription = stringResource(
                    if (isPlaying) Res.string.pause else Res.string.play
                ),
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }

        if (duration > 0) {
            SlimSlider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.weight(1f),
                trailingLabel = formatTime(currentPosition)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Renders [content] at a fixed 1920×1080 logical size and scales it down
 * to fill whatever space its parent allocates — keeping all proportions intact.
 */
@Composable
private fun ScaledPresenterContent(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val screen = presenterScreenBounds()
                val presenterWidth = screen.width
                val presenterHeight = screen.height

                val scaleX = constraints.maxWidth.toFloat() / presenterWidth
                val scaleY = constraints.maxHeight.toFloat() / presenterHeight
                val scale = minOf(scaleX, scaleY)

                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = presenterWidth,
                        maxWidth = presenterWidth,
                        minHeight = presenterHeight,
                        maxHeight = presenterHeight
                    )
                )

                val scaledWidth = (presenterWidth * scale).toInt()
                val scaledHeight = (presenterHeight * scale).toInt()

                layout(scaledWidth, scaledHeight) {
                    placeable.placeWithLayer(0, 0) {
                        this.scaleX = scale
                        this.scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            content()
        }
    }
}
