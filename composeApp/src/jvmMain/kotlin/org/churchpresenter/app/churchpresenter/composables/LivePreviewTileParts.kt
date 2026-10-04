package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.BoxScope
import org.churchpresenter.sharedui.utils.contentScale
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
import org.churchpresenter.announcements.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.contentRegion
import org.churchpresenter.dictionary.presenter.DictionaryPresenter
import org.churchpresenter.lowerthird.presenter.LowerThirdPresenter
import org.churchpresenter.media.presenter.MediaPresenter
import org.churchpresenter.slides.presenter.PicturePresenter
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.qa.presenter.QAPresenter
import org.churchpresenter.stt.presenter.STTPresenter
import org.churchpresenter.qa.presenter.QAQRCodePresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.slides.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.OutputSize
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
            announcementActive = effectiveMode == Presenting.ANNOUNCEMENTS,
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
    val mediaViewModel = LocalMediaViewModel.current
    val displayedImagePath by presenterManager.displayedImagePath
    val previousDisplayedImagePath by presenterManager.previousDisplayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val pictureSlideOffset by presenterManager.pictureSlideOffset
    val animationType by presenterManager.animationType
    val displayedSlide by presenterManager.displayedSlide
    val previousDisplayedSlide by presenterManager.previousDisplayedSlide
    val slideFrozen by presenterManager.slideFrozen
    val presentationFrame by presenterManager.presentationFrame
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val slideSlideOffset by presenterManager.slideSlideOffset
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val displayedDictionaryEntry by presenterManager.displayedDictionaryEntry
        when (mode) {
            Presenting.BIBLE -> PreviewBible(presenterManager, profile, outputSettings, showsBackground, primaryRole)
        Presenting.LYRICS -> PreviewSong(presenterManager, profile, outputSettings, showsBackground, primaryRole)
        Presenting.PICTURES ->
                PicturePresenter(
                    imagePath = displayedImagePath,
                    previousImagePath = previousDisplayedImagePath,
                    transitionAlpha = pictureTransitionAlpha,
                    slideOffset = pictureSlideOffset,
                    animationType = animationType,
                    contentScale = outputSettings.pictureSettings.scaleMode.contentScale,
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
                    // The preview is this output, so it takes the same three
                    // subtitle decisions the real one does. It used to pass none
                    // of them, and so always drew every track in default styling
                    // however the profile was configured.
                    MediaPresenter(
                        modifier = Modifier.fillMaxSize(),
                        transitionAlpha = mediaTransitionAlpha,
                        showSubtitles = profile.showSubtitles,
                        profileId = profile.id,
                        mediaSettings = outputSettings.mediaSettings,
                        contentScale = outputSettings.mediaScaleMode.contentScale,
                    )
                }
            Presenting.LOWER_THIRD -> PreviewLowerThird(presenterManager)
        Presenting.ANNOUNCEMENTS ->
                AnnouncementsPresenter(
                    text = displayedAnnouncementText,
                    appSettings = outputSettings,
                    outputRole = primaryRole,
                    transitionAlpha = announcementTransitionAlpha
                )
            Presenting.CANVAS ->
                ScenePresenter(scene = activeScene)
            Presenting.QA -> {
                val showQRCode by presenterManager.showQRCodeOnDisplay
                if (showQRCode) {
                    QAQRCodePresenter(
                        url = qaUrl,
                        qaSettings = outputSettings.qaSettings,
                    )
                } else {
                    QAPresenter(question = displayedQuestion, qaSettings = outputSettings.qaSettings)
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
                        sttSettings = outputSettings.sttSettings,
                        outputRole = primaryRole,
                    )
                }
            }
            Presenting.DICTIONARY ->
                DictionaryPresenter(
                    entry = displayedDictionaryEntry,
                    dictionarySettings = outputSettings.dictionarySettings,
                    outputRole = primaryRole,
                    transitionAlpha = 1f,
                )
            else -> {}
        }
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

/** Scripture on the tile, as this output's profile lays it out. */
@Composable
internal fun PreviewBible(
    presenterManager: PresenterManager,
    profile: OutputProfile,
    outputSettings: AppSettings,
    showsBackground: Boolean,
    primaryRole: String,
) {
    val displayedVerses by presenterManager.displayedVerses
    val bibleTransitionAlpha by presenterManager.bibleTransitionAlpha
    val isLowerThird = profile.isLowerThird
    val isLowerThirdVertical = profile.isLowerThirdVertical
        BiblePresenter(
            modifier = if (isLowerThird) {
                Modifier
            } else {
                Modifier.contentRegion(outputSettings.bibleSettings.contentRegion)
            },
            selectedVerses = displayedVerses,
            appSettings = outputSettings,
            isLowerThird = isLowerThird,
            isLowerThirdVertical = isLowerThirdVertical,
            outputRole = primaryRole,
            transitionAlpha = bibleTransitionAlpha,
            showBackground = showsBackground && profile.showBibleBackground,
            crossfadeEnabled = outputSettings.bibleSettings.crossfade,
            bibleTranslations = profile.bibleTranslations,
        )
    
}

/** The song on the tile, as this output's profile lays it out. */
@Composable
internal fun PreviewSong(
    presenterManager: PresenterManager,
    profile: OutputProfile,
    outputSettings: AppSettings,
    showsBackground: Boolean,
    primaryRole: String,
) {
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songTransitionAlpha by presenterManager.songTransitionAlpha
    val songPosition by presenterManager.displayedSongPosition
    val isLowerThird = profile.isLowerThird
    val isLowerThirdVertical = profile.isLowerThirdVertical
        SongPresenter(
            modifier = if (isLowerThird) {
                Modifier
            } else {
                Modifier.contentRegion(outputSettings.songSettings.layoutExtras.contentRegion)
            },
            lyricSection = displayedLyricSection,
            appSettings = outputSettings,
            isLowerThird = isLowerThird,
            isLowerThirdVertical = isLowerThirdVertical,
            outputRole = primaryRole,
            transitionAlpha = songTransitionAlpha,
            displayLineIndex = songPosition.lineIndex,
            lookAheadEnabled = profile.songLookAhead,
            allLyricSections = songPosition.allSections,
            displaySectionIndex = songPosition.sectionIndex,
            showBackground = showsBackground && profile.showSongsBackground,
            crossfadeEnabled = outputSettings.songSettings.crossfade,
            languageOverride = profile.songMode,
            languageSelection = profile.songTranslations,
        )
    
}

/** The Lottie lower third on the tile. */
@Composable
internal fun PreviewLowerThird(presenterManager: PresenterManager) {
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottieComposition by rememberLottieComposition(lottieJsonContent) {
        LottieCompositionSpec.JsonString(lottieJsonContent)
    }
        LowerThirdPresenter(
            composition = lottieComposition,
            progress = { presenterManager.lottieProgress.value },
            frame = presenterManager.lottieFrame.value?.imageBitmap,
            groupsText = presenterManager.lottieGroupsText.value,
        )
    
}

private const val PREVIEW_BACKGROUND = 0xFF121212

private const val LIVE_BADGE_COLOR = 0xFF2196F3

private const val LOCK_BADGE_COLOR = 0xFFFFC107
