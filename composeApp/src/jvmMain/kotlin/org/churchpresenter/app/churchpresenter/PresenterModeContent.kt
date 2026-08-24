package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.announcements.AnnouncementsPresenter
import org.churchpresenter.dictionary.tab.DictionaryPresenter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAQRCodePresenter
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager

/**
 * Draws whatever [mode] means for one output: the dispatch from [Presenting] to the matching
 * presenter, with [screenAssignment] deciding visibility, layout and language for that output.
 *
 * Shared by every output — the per-screen windows, the DeckLink fill and key surfaces and the
 * browser-source overlays — which each supply their own Window/Crossfade wrapper and differ only
 * in [outputRole] and whether backgrounds are drawn. [showBackgroundOverride] exists because the
 * key and DeckLink paths historically omitted `showBackground` and so took the presenters' `true`
 * default; passing `true` there keeps that exact behaviour rather than quietly changing what those
 * outputs render.
 */
@Composable
internal fun PresenterModeContent(
    mode: Presenting,
    screenAssignment: ScreenAssignment,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    mediaViewModel: MediaViewModel,
    sttManager: STTManager,
    serverUrl: String,
    qaDisplayUrl: String,
    lottieComposition: LottieComposition?,
    clearAnnouncementOnFinish: () -> Unit,
    outputRole: String,
    showBg: Boolean,
    showBackgroundOverride: Boolean? = null,
) {
    val selectedVerses by presenterManager.selectedVerses
    val displayedVerses by presenterManager.displayedVerses
    val bibleTransitionAlpha by presenterManager.bibleTransitionAlpha
    val lyricSection by presenterManager.lyricSection
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songTransitionAlpha by presenterManager.songTransitionAlpha
    val songDisplayLineIndex by presenterManager.songDisplayLineIndex
    val allLyricSections by presenterManager.allLyricSections
    val songDisplaySectionIndex by presenterManager.songDisplaySectionIndex
    val displayedImagePath by presenterManager.displayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val previousDisplayedImagePath by presenterManager.previousDisplayedImagePath
    val pictureSlideOffset by presenterManager.pictureSlideOffset
    val displayedSlide by presenterManager.displayedSlide
    val slideFrozen by presenterManager.slideFrozen
    val presentationFrame by presenterManager.presentationFrame
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val previousDisplayedSlide by presenterManager.previousDisplayedSlide
    val slideSlideOffset by presenterManager.slideSlideOffset
    val animationType by presenterManager.animationType
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val lottieFrame by presenterManager.lottieFrame
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val websiteUrl by presenterManager.websiteUrl
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val qaTransitionAlpha by presenterManager.qaTransitionAlpha
    val showQRCodeOnDisplay by presenterManager.showQRCodeOnDisplay
    val displayedDictionaryEntry by presenterManager.displayedDictionaryEntry

    when (mode) {
        Presenting.BIBLE ->
            if (screenAssignment.showBible) {
                BiblePresenter(
                    selectedVerses = displayedVerses,
                    appSettings = appSettings,
                    isLowerThird = screenAssignment.isLowerThird,
                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                    outputRole = outputRole,
                    transitionAlpha = bibleTransitionAlpha,
                    showBackground = showBackgroundOverride ?: (showBg && screenAssignment.showBibleBackground),
                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                    bibleTranslations = screenAssignment.bibleTranslations
                )
            }

        Presenting.LYRICS ->
            if (screenAssignment.showSongs) {
                SongPresenter(
                    lyricSection = displayedLyricSection,
                    appSettings = appSettings,
                    isLowerThird = screenAssignment.isLowerThird,
                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                    outputRole = outputRole,
                    transitionAlpha = songTransitionAlpha,
                    displayLineIndex = songDisplayLineIndex,
                    lookAheadEnabled = screenAssignment.songLookAhead,
                    allLyricSections = allLyricSections,
                    displaySectionIndex = songDisplaySectionIndex,
                    showBackground = showBackgroundOverride ?: (showBg && screenAssignment.showSongsBackground),
                    crossfadeEnabled = appSettings.songSettings.crossfade,
                    languageOverride = screenAssignment.songMode,
                    showChords = screenAssignment.showChords,
                )
            }

        Presenting.PICTURES ->
            if (screenAssignment.showPictures)
                PicturePresenter(
                    imagePath = displayedImagePath,
                    previousImagePath = previousDisplayedImagePath,
                    transitionAlpha = pictureTransitionAlpha,
                    slideOffset = pictureSlideOffset,
                    animationType = animationType
                )

        Presenting.PRESENTATION ->
            if (screenAssignment.showPictures)
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
            if (screenAssignment.showMedia) {
                if (mediaViewModel.isAudioFile) {
                    // Audio: playback handled by hidden VideoPlayer in MainDesktop
                    // Projection shows background only
                } else {
                    MediaPresenter(
                        modifier = Modifier.fillMaxSize(),
                        transitionAlpha = mediaTransitionAlpha
                    )
                }
            }

        Presenting.LOWER_THIRD ->
            if (screenAssignment.showStreaming)
                LowerThirdPresenter(
                    composition = lottieComposition,
                    progress = { presenterManager.lottieProgress.value },
                    appSettings = appSettings,
                    frame = lottieFrame
                )

        Presenting.ANNOUNCEMENTS ->
            if (screenAssignment.showAnnouncements)
                AnnouncementsPresenter(
                    text = displayedAnnouncementText,
                    appSettings = appSettings,
                    outputRole = outputRole,
                    transitionAlpha = announcementTransitionAlpha,
                    onFinished = clearAnnouncementOnFinish,
                    showBackground = showBackgroundOverride ?: showBg
                )

        Presenting.WEBSITE ->
            if (screenAssignment.showWebsite) WebsitePresenter(
                url = websiteUrl,
                modifier = Modifier.fillMaxSize(),
                onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
                onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
            )

        Presenting.CANVAS -> { if (screenAssignment.showCanvas) ScenePresenter(scene = activeScene) }

        Presenting.QA ->
            if (screenAssignment.showQA) {
                if (showQRCodeOnDisplay) {
                    QAQRCodePresenter(
                        url = qaQrCodeUrl(qaDisplayUrl, serverUrl),
                        qaSettings = appSettings.qaSettings,
                        transitionAlpha = qaTransitionAlpha,
                    )
                } else {
                    QAPresenter(
                        question = displayedQuestion,
                        qaSettings = appSettings.qaSettings,
                        transitionAlpha = qaTransitionAlpha,
                    )
                }
            }

        Presenting.STT ->
            if (screenAssignment.showSTT) {
                STTPresenter(
                    segments = sttManager.segments,
                    inProgressText = sttManager.inProgressText.value,
                    translationSegments = sttManager.translationSegments,
                    inProgressTranslation = sttManager.inProgressTranslation.value,
                    highlightedWords = sttManager.highlightedWords,
                    sttSettings = appSettings.sttSettings,
                )
            }
        Presenting.DICTIONARY ->
            if (screenAssignment.showDictionary)
                DictionaryPresenter(
                    dictionarySettings = appSettings.dictionarySettings,
                    entry = displayedDictionaryEntry,
                    outputRole = outputRole,
                    transitionAlpha = 1f
                )
        Presenting.NONE -> { /* nothing */
        }
    }
}
