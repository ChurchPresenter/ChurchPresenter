package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.contentRegion
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.LocalBandOutgoing
import org.churchpresenter.app.churchpresenter.presenter.LocalBandSongLineIndex
import org.churchpresenter.app.churchpresenter.presenter.LocalLottieBandClock
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAQRCodePresenter
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.utils.contentScale
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager

/**
 * Draws whatever [mode] means for one output: the dispatch from [Presenting] to the matching
 * presenter, with [profile] deciding visibility, layout and language for that output.
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
    profile: OutputProfile,
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
    val bandSongLineIndex by presenterManager.bandSongLineIndex
    val bandOutgoing by presenterManager.bandOutgoing

    // The Lottie band's clock, line and outgoing text travel as locals: the two presenters that
    // read them are long past their parameter budget, and every output has exactly one of each.
    //
    // The clock is provided as its state holder and deliberately NOT unwrapped here. Reading its
    // value in this body would subscribe the whole dispatch below to a state that moves every
    // animation frame.
    CompositionLocalProvider(
        LocalLottieBandClock provides presenterManager.lottieBandClock,
        LocalBandSongLineIndex provides bandSongLineIndex,
        LocalBandOutgoing provides bandOutgoing,
    ) {
    when (mode) {
        Presenting.BIBLE ->
            if (profile.showBible) {
                BiblePresenter(
                    modifier = if (profile.isLowerThird) {
                        Modifier
                    } else {
                        Modifier.contentRegion(appSettings.bibleSettings.contentRegion)
                    },
                    selectedVerses = displayedVerses,
                    appSettings = appSettings,
                    isLowerThird = profile.isLowerThird,
                    isLowerThirdVertical = profile.isLowerThirdVertical,
                    outputRole = outputRole,
                    transitionAlpha = bibleTransitionAlpha,
                    showBackground = showBackgroundOverride ?: (showBg && profile.showBibleBackground),
                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                    bibleTranslations = profile.bibleTranslations,
                )
            }

        Presenting.LYRICS ->
            if (profile.showSongs) {
                SongPresenter(
                    modifier = if (profile.isLowerThird) {
                        Modifier
                    } else {
                        Modifier.contentRegion(appSettings.songSettings.layoutExtras.contentRegion)
                    },
                    lyricSection = displayedLyricSection,
                    appSettings = appSettings,
                    isLowerThird = profile.isLowerThird,
                    isLowerThirdVertical = profile.isLowerThirdVertical,
                    outputRole = outputRole,
                    transitionAlpha = songTransitionAlpha,
                    displayLineIndex = songDisplayLineIndex,
                    lookAheadEnabled = profile.songLookAhead,
                    allLyricSections = allLyricSections,
                    displaySectionIndex = songDisplaySectionIndex,
                    showBackground = showBackgroundOverride ?: (showBg && profile.showSongsBackground),
                    crossfadeEnabled = appSettings.songSettings.crossfade,
                    languageOverride = profile.songMode,
                    languageSelection = profile.songTranslations,
                )
            }

        Presenting.PICTURES ->
            if (profile.showPictures)
                PicturePresenter(
                    imagePath = displayedImagePath,
                    previousImagePath = previousDisplayedImagePath,
                    transitionAlpha = pictureTransitionAlpha,
                    slideOffset = pictureSlideOffset,
                    animationType = animationType,
                    contentScale = appSettings.pictureSettings.scaleMode.contentScale,
                )

        Presenting.PRESENTATION ->
            if (profile.showPictures)
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
            if (profile.showMedia) {
                if (mediaViewModel.isAudioFile) {
                    // Audio: playback handled by hidden VideoPlayer in MainDesktop
                    // Projection shows background only
                } else {
                    MediaPresenter(
                        modifier = Modifier.fillMaxSize(),
                        transitionAlpha = mediaTransitionAlpha,
                        outputRole = outputRole,
                        showSubtitles = profile.showSubtitles,
                        mediaSettings = appSettings.mediaSettings,
                        contentScale = appSettings.mediaScaleMode.contentScale,
                    )
                }
            }

        Presenting.LOWER_THIRD ->
            if (profile.showStreaming)
                LowerThirdPresenter(
                    composition = lottieComposition,
                    progress = { presenterManager.lottieProgress.value },
                    frame = lottieFrame
                )

        Presenting.ANNOUNCEMENTS ->
            if (profile.showAnnouncements)
                AnnouncementsPresenter(
                    text = displayedAnnouncementText,
                    appSettings = appSettings,
                    outputRole = outputRole,
                    transitionAlpha = announcementTransitionAlpha,
                    onFinished = clearAnnouncementOnFinish,
                    showBackground = showBackgroundOverride ?: showBg
                )

        Presenting.WEBSITE ->
            if (profile.showWebsite) WebsitePresenter(
                url = websiteUrl,
                modifier = Modifier.fillMaxSize(),
                onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
                onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
            )

        Presenting.CANVAS -> { if (profile.showCanvas) ScenePresenter(scene = activeScene) }

        Presenting.QA ->
            if (profile.showQA) {
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
            if (profile.showSTT) {
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
            if (profile.showDictionary)
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
}
