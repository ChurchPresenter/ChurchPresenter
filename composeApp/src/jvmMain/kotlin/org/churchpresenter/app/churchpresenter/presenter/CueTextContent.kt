package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.settings.utils.Constants

// What the text cues draw on one output -- see [CueContent]. Where the three kinds of output have
// drawn a cue differently, the difference is written against [OutputSurfaceKind] here.

@Composable
internal fun BibleCue(surface: OutputSurface) {
    val profile = surface.profile
    val appSettings = surface.appSettings
    val region = appSettings.bibleSettings.contentRegion
    val presenterManager = surface.presenterManager
    BiblePresenter(
        modifier = when {
            profile.isLowerThird -> Modifier
            surface.kind == OutputSurfaceKind.PREVIEW -> Modifier.contentRegion(region)
            else -> Modifier.wholeOutputRegion(region)
        },
        textRegion = if (surface.kind == OutputSurfaceKind.PREVIEW) null else region.textOnly(profile.isLowerThird),
        selectedVerses = presenterManager.displayedVerses.value,
        appSettings = appSettings,
        isLowerThird = profile.isLowerThird,
        isLowerThirdVertical = profile.isLowerThirdVertical,
        outputRole = surface.outputRole,
        transitionAlpha = presenterManager.bibleTransitionAlpha.value,
        showBackground = surface.showBackgroundOverride ?: (surface.showBg && profile.showBibleBackground),
        crossfadeEnabled = appSettings.bibleSettings.crossfade,
        bibleTranslations = profile.bibleTranslations,
    )
}

@Composable
internal fun SongCue(surface: OutputSurface) {
    val profile = surface.profile
    val appSettings = surface.appSettings
    val region = appSettings.songSettings.layoutExtras.contentRegion
    val presenterManager = surface.presenterManager
    val songPosition = presenterManager.displayedSongPosition.value
    SongPresenter(
        modifier = when {
            profile.isLowerThird -> Modifier
            surface.kind == OutputSurfaceKind.PREVIEW -> Modifier.contentRegion(region)
            else -> Modifier.wholeOutputRegion(region)
        },
        textRegion = if (surface.kind == OutputSurfaceKind.PREVIEW) null else region.textOnly(profile.isLowerThird),
        lyricSection = presenterManager.displayedLyricSection.value,
        appSettings = appSettings,
        isLowerThird = profile.isLowerThird,
        isLowerThirdVertical = profile.isLowerThirdVertical,
        outputRole = surface.outputRole,
        transitionAlpha = presenterManager.songTransitionAlpha.value,
        displayLineIndex = songPosition.lineIndex,
        lookAheadEnabled = profile.songLookAhead,
        allLyricSections = songPosition.allSections,
        displaySectionIndex = songPosition.sectionIndex,
        showBackground = surface.showBackgroundOverride ?: (surface.showBg && profile.showSongsBackground),
        crossfadeEnabled = appSettings.songSettings.crossfade,
        languageOverride = profile.songMode,
        languageSelection = profile.songTranslations,
    )
}

@Composable
internal fun AnnouncementCue(surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    AnnouncementsPresenter(
        text = presenterManager.displayedAnnouncementText.value,
        appSettings = surface.appSettings,
        outputRole = surface.outputRole,
        transitionAlpha = presenterManager.announcementTransitionAlpha.value,
        onFinished = surface.onAnnouncementFinished,
        // The preview tile has always drawn the announcement's own background.
        showBackground = if (surface.kind == OutputSurfaceKind.PREVIEW) {
            true
        } else {
            surface.showBackgroundOverride ?: surface.showBg
        },
    )
}

@Composable
internal fun QuestionCue(surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    val qaSettings = surface.appSettings.qaSettings
    // The preview tile has always shown the question at full opacity.
    val alpha = if (surface.kind == OutputSurfaceKind.PREVIEW) 1f else presenterManager.qaTransitionAlpha.value
    if (presenterManager.showQRCodeOnDisplay.value) {
        QAQRCodePresenter(url = surface.qrCodeUrl, qaSettings = qaSettings, transitionAlpha = alpha)
    } else {
        QAPresenter(
            question = presenterManager.displayedQuestion.value,
            qaSettings = qaSettings,
            transitionAlpha = alpha,
        )
    }
}

@Composable
internal fun CaptionsCue(surface: OutputSurface) {
    val stt = surface.sttManager ?: return
    STTPresenter(
        segments = stt.segments,
        inProgressText = stt.inProgressText.value,
        translationSegments = stt.translationSegments,
        inProgressTranslation = stt.inProgressTranslation.value,
        highlightedWords = stt.highlightedWords,
        sttSettings = surface.appSettings.sttSettings,
        // Only the preview tile has drawn captions in its output's role.
        outputRole = if (surface.kind == OutputSurfaceKind.PREVIEW) {
            surface.outputRole
        } else {
            Constants.OUTPUT_ROLE_NORMAL
        },
    )
}

@Composable
internal fun DictionaryCue(surface: OutputSurface) {
    DictionaryPresenter(
        entry = surface.presenterManager.displayedDictionaryEntry.value,
        dictionarySettings = surface.appSettings.dictionarySettings,
        outputRole = surface.outputRole,
        transitionAlpha = 1f,
    )
}
