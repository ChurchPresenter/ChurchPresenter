package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.live_preview_nothing
import churchpresenter.composeapp.generated.resources.live_preview_title
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource

/**
 * A scaled-down preview of whatever is currently live on the presenter windows.
 * Shows one preview per configured display, each respecting its screen assignment.
 */
@Composable
fun LivePreviewPanel(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    modifier: Modifier = Modifier
) {
    val proj = appSettings.projectionSettings
    val displayCount = proj.numberOfWindows.coerceIn(1, 4)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until displayCount) {
            val screenAssignment = when (i) {
                0 -> proj.screen1Assignment
                1 -> proj.screen2Assignment
                2 -> proj.screen3Assignment
                else -> proj.screen4Assignment
            }

            SingleDisplayPreview(
                screenIndex = i,
                screenAssignment = screenAssignment,
                presenterManager = presenterManager,
                appSettings = appSettings,
                modifier = Modifier.fillMaxWidth()
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
    modifier: Modifier = Modifier
) {
    val presentingMode by presenterManager.presentingMode
    val selectedVerses by presenterManager.selectedVerses
    val lyricSection by presenterManager.lyricSection
    val selectedImagePath by presenterManager.selectedImagePath
    val selectedSlide by presenterManager.selectedSlide
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottiePauseAtFrame by presenterManager.lottiePauseAtFrame
    val lottiePauseFrame by presenterManager.lottiePauseFrame
    val lottiePauseDurationMs by presenterManager.lottiePauseDurationMs
    val lottieTrigger by presenterManager.lottieTrigger
    val announcementText by presenterManager.announcementText
    val websiteUrl by presenterManager.websiteUrl

    val isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD

    // Determine if this screen shows the current content
    val showsContent = when (presentingMode) {
        Presenting.BIBLE -> screenAssignment.showBible
        Presenting.LYRICS -> screenAssignment.showSongs
        Presenting.PICTURES, Presenting.PRESENTATION -> screenAssignment.showPictures
        Presenting.MEDIA -> screenAssignment.showMedia
        Presenting.LOWER_THIRD -> screenAssignment.showStreaming
        Presenting.ANNOUNCEMENTS -> screenAssignment.showAnnouncements
        Presenting.WEBSITE -> true
        Presenting.NONE -> false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        ScaledPresenterContent {
            PresenterScreen(appSettings = appSettings) {
                if (presentingMode != Presenting.NONE && showsContent) {
                    when (presentingMode) {
                        Presenting.BIBLE ->
                            BiblePresenter(
                                selectedVerses = selectedVerses,
                                appSettings = appSettings,
                                isLowerThird = isLowerThird
                            )
                        Presenting.LYRICS ->
                            SongPresenter(
                                lyricSection = lyricSection,
                                appSettings = appSettings,
                                isLowerThird = isLowerThird
                            )
                        Presenting.PICTURES ->
                            PicturePresenter(imagePath = selectedImagePath)
                        Presenting.PRESENTATION ->
                            SlidePresenter(slide = selectedSlide)
                        Presenting.MEDIA ->
                            MediaPresenter(modifier = Modifier.fillMaxSize())
                        Presenting.LOWER_THIRD ->
                            LowerThirdPresenter(
                                jsonContent = lottieJsonContent,
                                pauseAtFrame = lottiePauseAtFrame,
                                pauseFrame = lottiePauseFrame,
                                pauseDurationMs = lottiePauseDurationMs,
                                trigger = lottieTrigger,
                                appSettings = appSettings
                            )
                        Presenting.ANNOUNCEMENTS ->
                            AnnouncementsPresenter(
                                text = announcementText,
                                appSettings = appSettings
                            )
                        Presenting.WEBSITE ->
                            WebsitePresenter(url = websiteUrl, modifier = Modifier.fillMaxSize())
                        Presenting.NONE -> {}
                    }
                } else {
                    // Dark blank screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF121212)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.live_preview_nothing),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // "LIVE" badge — only when this screen is showing content
        if (presentingMode != Presenting.NONE && showsContent) {
            Text(
                text = stringResource(Res.string.live_preview_title),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Red, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Screen number label
        Text(
            text = "Screen ${screenIndex + 1}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
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
                val presenterWidth = 1920
                val presenterHeight = 1080

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
