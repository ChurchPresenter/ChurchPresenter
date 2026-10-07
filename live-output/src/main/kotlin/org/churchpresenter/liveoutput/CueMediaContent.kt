package org.churchpresenter.liveoutput

import org.churchpresenter.lowerthird.presenter.LowerThirdPresenter
import org.churchpresenter.presenter.LocalInMergedTile
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import org.churchpresenter.media.presenter.MediaPresenter
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.contentScale
import org.churchpresenter.slides.presenter.PicturePresenter
import org.churchpresenter.slides.presenter.PresentationPresenter
import org.churchpresenter.web.presenter.WebsitePresenter

// What the media, graphics and web cues draw on one output -- see [CueContent]. Where the three
// kinds of output have drawn a cue differently, the difference is written against
// [OutputSurfaceKind] here.

@Composable
internal fun PictureCue(surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    PicturePresenter(
        imagePath = presenterManager.displayedImagePath.value,
        previousImagePath = presenterManager.previousDisplayedImagePath.value,
        transitionAlpha = presenterManager.pictureTransitionAlpha.value,
        slideOffset = presenterManager.pictureSlideOffset.value,
        animationType = presenterManager.animationType.value,
        contentScale = surface.appSettings.pictureSettings.scaleMode.contentScale,
    )
}

@Composable
internal fun PresentationCue(surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    PresentationPresenter(
        frame = presenterManager.presentationFrame.value,
        slide = presenterManager.displayedSlide.value,
        previousSlide = presenterManager.previousDisplayedSlide.value,
        transitionAlpha = presenterManager.slideTransitionAlpha.value,
        slideOffset = presenterManager.slideSlideOffset.value,
        animationType = presenterManager.animationType.value,
        frozen = presenterManager.slideFrozen.value,
    )
}

@Composable
internal fun MediaCue(surface: OutputSurface) {
    val mediaViewModel = surface.mediaViewModel ?: return
    // Audio-only media shows the background alone; playback is the hidden player in MainDesktop.
    if (mediaViewModel.isAudioFile) return
    val appSettings = surface.appSettings
    MediaPresenter(
        modifier = Modifier.fillMaxSize(),
        transitionAlpha = surface.presenterManager.mediaTransitionAlpha.value,
        // The preview tile has always drawn media in the normal role.
        outputRole = if (surface.kind == OutputSurfaceKind.PREVIEW) {
            Constants.OUTPUT_ROLE_NORMAL
        } else {
            surface.outputRole
        },
        showSubtitles = surface.profile.look.media.subtitles,
        profileId = surface.profile.id,
        mediaSettings = appSettings.mediaSettings,
        contentScale = appSettings.mediaScaleMode.contentScale,
    )
}

@Composable
internal fun LowerThirdCue(surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    // An output holds its pixel size, so one larger than the desktop frames gets frames of its own
    // and draws them one to one. The preview tiles are small: they draw the desktop frames.
    val outputFrames = presenterManager.lowerThird.outputFrames
    val hold = if (surface.kind == OutputSurfaceKind.PREVIEW) null else remember(outputFrames) { outputFrames.hold() }
    if (hold != null) DisposableEffect(hold) { onDispose { hold.close() } }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frame = hold?.let { outputFrames.frameFor(size.width, size.height) } ?: presenterManager.lottieFrame.value
    val composition = if (surface.kind == OutputSurfaceKind.WINDOW) {
        surface.lottieComposition
    } else {
        val json = presenterManager.lottieJsonContent.value
        val parsed by rememberLottieComposition(json) { LottieCompositionSpec.JsonString(json.ifBlank { "{}" }) }
        parsed
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged {
            size = it
            hold?.resize(it.width, it.height)
        },
    ) {
        LowerThirdPresenter(
            composition = composition,
            progress = { presenterManager.lottieProgress.value },
            frame = frame?.imageBitmap,
            groupsText = presenterManager.lottieGroupsText.value,
        )
    }
}

@Composable
internal fun WebCue(surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    when {
        surface.kind == OutputSurfaceKind.PREVIEW -> Unit
        surface.kind == OutputSurfaceKind.OFFSCREEN || LocalInMergedTile.current ->
            presenterManager.webSnapshot.value?.let { snapshot ->
                Image(
                    bitmap = snapshot,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().testTag(WEB_SNAPSHOT_TAG),
                )
            }
        else -> WebsitePresenter(
            url = presenterManager.websiteUrl.value,
            modifier = Modifier.fillMaxSize(),
            onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
            onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
            onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
            onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
            audioDeviceId = surface.appSettings.projectionSettings.audioOutputDeviceId,
        )
    }
}
