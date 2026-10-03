package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.presenter.WEB_SNAPSHOT_TAG
import org.churchpresenter.app.churchpresenter.presenter.LocalInMergedTile
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.app.churchpresenter.presenter.textOnly
import org.churchpresenter.app.churchpresenter.presenter.wholeOutputRegion
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.announcements.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.dictionary.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdLayout
import org.churchpresenter.lowerthird.presenter.LowerThirdPresenter
import org.churchpresenter.media.presenter.MediaPresenter
import org.churchpresenter.slides.presenter.PicturePresenter
import org.churchpresenter.slides.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.LocalBandOutgoing
import org.churchpresenter.app.churchpresenter.presenter.LocalBandSongLineIndex
import org.churchpresenter.app.churchpresenter.presenter.LocalLottieBandClock
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.qa.presenter.QAPresenter
import org.churchpresenter.qa.presenter.QAQRCodePresenter
import org.churchpresenter.stt.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.web.presenter.WebsitePresenter
import org.churchpresenter.sharedui.utils.contentScale
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.stt.STTManager

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
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val lottieFrame by presenterManager.lottieFrame
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val activeScene by presenterManager.activeScene
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
    LowerThirdLayout(mode, profile, appSettings, showBackgroundOverride ?: showBg) {
    when (mode) {
        Presenting.BIBLE ->
            BibleOutput(
                profile, presenterManager, appSettings, outputRole,
                showBackground = showBackgroundOverride ?: (showBg && profile.showBibleBackground),
            )

        Presenting.LYRICS ->
            SongOutput(
                profile, presenterManager, appSettings, outputRole,
                showBackground = showBackgroundOverride ?: (showBg && profile.showSongsBackground),
            )

        Presenting.PICTURES ->
            if (profile.showPictures) PictureOutput(presenterManager, appSettings)

        Presenting.PRESENTATION ->
            if (profile.showPictures) PresentationOutput(presenterManager)

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
                        profileId = profile.id,
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
                    frame = lottieFrame?.imageBitmap,
                    groupsText = presenterManager.lottieGroupsText.value,
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
            WebsiteOutput(profile, presenterManager, appSettings)

        Presenting.CANVAS -> { if (profile.showCanvas) ScenePresenter(scene = activeScene) }

        Presenting.QA ->
            QaOutput(profile, presenterManager, appSettings, qaQrCodeUrl(qaDisplayUrl, serverUrl))

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
}

/** Scripture on this output, when its profile shows it. */
@Composable
private fun BibleOutput(
    profile: OutputProfile,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    outputRole: String,
    showBackground: Boolean,
) {
    val displayedVerses by presenterManager.displayedVerses
    val bibleTransitionAlpha by presenterManager.bibleTransitionAlpha
    if (profile.showBible) {
        BiblePresenter(
            modifier = if (profile.isLowerThird) {
                Modifier
            } else {
                Modifier.wholeOutputRegion(appSettings.bibleSettings.contentRegion)
            },
            textRegion = appSettings.bibleSettings.contentRegion.textOnly(profile.isLowerThird),
            selectedVerses = displayedVerses,
            appSettings = appSettings,
            isLowerThird = profile.isLowerThird,
            isLowerThirdVertical = profile.isLowerThirdVertical,
            outputRole = outputRole,
            transitionAlpha = bibleTransitionAlpha,
            showBackground = showBackground,
            crossfadeEnabled = appSettings.bibleSettings.crossfade,
            bibleTranslations = profile.bibleTranslations,
        )
    }
}

/** The song on this output, when its profile shows songs. */
@Composable
private fun SongOutput(
    profile: OutputProfile,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    outputRole: String,
    showBackground: Boolean,
) {
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songTransitionAlpha by presenterManager.songTransitionAlpha
    val songPosition by presenterManager.displayedSongPosition
    if (profile.showSongs) {
        SongPresenter(
            modifier = if (profile.isLowerThird) {
                Modifier
            } else {
                Modifier.wholeOutputRegion(appSettings.songSettings.layoutExtras.contentRegion)
            },
            textRegion = appSettings.songSettings.layoutExtras.contentRegion.textOnly(profile.isLowerThird),
            lyricSection = displayedLyricSection,
            appSettings = appSettings,
            isLowerThird = profile.isLowerThird,
            isLowerThirdVertical = profile.isLowerThirdVertical,
            outputRole = outputRole,
            transitionAlpha = songTransitionAlpha,
            displayLineIndex = songPosition.lineIndex,
            lookAheadEnabled = profile.songLookAhead,
            allLyricSections = songPosition.allSections,
            displaySectionIndex = songPosition.sectionIndex,
            showBackground = showBackground,
            crossfadeEnabled = appSettings.songSettings.crossfade,
            languageOverride = profile.songMode,
            languageSelection = profile.songTranslations,
        )
    }
}

/** The live web page on this output; inside a merged tile, the snapshot of it instead. */
@Composable
private fun WebsiteOutput(profile: OutputProfile, presenterManager: PresenterManager, appSettings: AppSettings) {
    val websiteUrl by presenterManager.websiteUrl
    if (profile.showWebsite && LocalInMergedTile.current) {
        presenterManager.webSnapshot.value?.let { snapshot ->
            Image(
                bitmap = snapshot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().testTag(WEB_SNAPSHOT_TAG),
            )
        }
    } else if (profile.showWebsite) WebsitePresenter(
        url = websiteUrl,
        modifier = Modifier.fillMaxSize(),
        onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
        onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
        onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
        onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
        audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
    )
}

/** The question on display, or the QR code that leads to the submission page. */
@Composable
private fun QaOutput(
    profile: OutputProfile,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    qrCodeUrl: String,
) {
    val displayedQuestion by presenterManager.displayedQuestion
    val qaTransitionAlpha by presenterManager.qaTransitionAlpha
    val showQRCodeOnDisplay by presenterManager.showQRCodeOnDisplay
    if (profile.showQA) {
        if (showQRCodeOnDisplay) {
            QAQRCodePresenter(
                url = qrCodeUrl,
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
}

/** The picture on this output, sliding or fading in over the one before. */
@Composable
private fun PictureOutput(presenterManager: PresenterManager, appSettings: AppSettings) {
    val displayedImagePath by presenterManager.displayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val previousDisplayedImagePath by presenterManager.previousDisplayedImagePath
    val pictureSlideOffset by presenterManager.pictureSlideOffset
    val animationType by presenterManager.animationType
    PicturePresenter(
        imagePath = displayedImagePath,
        previousImagePath = previousDisplayedImagePath,
        transitionAlpha = pictureTransitionAlpha,
        slideOffset = pictureSlideOffset,
        animationType = animationType,
        contentScale = appSettings.pictureSettings.scaleMode.contentScale,
    )
}

/** The presentation's current slide on this output, or its frame while it animates. */
@Composable
private fun PresentationOutput(presenterManager: PresenterManager) {
    val displayedSlide by presenterManager.displayedSlide
    val slideFrozen by presenterManager.slideFrozen
    val presentationFrame by presenterManager.presentationFrame
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val previousDisplayedSlide by presenterManager.previousDisplayedSlide
    val slideSlideOffset by presenterManager.slideSlideOffset
    val animationType by presenterManager.animationType
    PresentationPresenter(
        frame = presentationFrame,
        slide = displayedSlide,
        previousSlide = previousDisplayedSlide,
        transitionAlpha = slideTransitionAlpha,
        slideOffset = slideSlideOffset,
        animationType = animationType,
        frozen = slideFrozen
    )
}
