package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import churchpresenter.composeapp.generated.resources.live_preview_nothing
import churchpresenter.composeapp.generated.resources.live_preview_title
import churchpresenter.composeapp.generated.resources.screen_number
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
    modifier: Modifier = Modifier
) {
    val proj = appSettings.projectionSettings
    val deckLinkCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val displayCount = ((rememberScreenDevices().size - 1) + deckLinkCount).coerceAtLeast(0)
    val mediaViewModel = LocalMediaViewModel.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until displayCount) {
            val screenAssignment = proj.getAssignment(i)

            // Skip displays set to "None"
            if (screenAssignment.targetDisplay == Constants.KEY_TARGET_NONE) continue

            SingleDisplayPreview(
                screenIndex = i,
                screenAssignment = screenAssignment,
                presenterManager = presenterManager,
                appSettings = appSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Media controls — visible when presenting and media is loaded.
        // Controls disappear only via Clear Display / Escape (which reset presentingMode to NONE).
        val presentingMode by presenterManager.presentingMode
        if (presentingMode != Presenting.NONE
            && mediaViewModel != null && mediaViewModel.isLoaded
        ) {
            MediaPreviewControls(mediaViewModel)
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
    val displayedVerses by presenterManager.displayedVerses
    val bibleTransitionAlpha by presenterManager.bibleTransitionAlpha
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songTransitionAlpha by presenterManager.songTransitionAlpha
    val songDisplayLineIndex by presenterManager.songDisplayLineIndex
    val allLyricSections by presenterManager.allLyricSections
    val songDisplaySectionIndex by presenterManager.songDisplaySectionIndex
    val displayedImagePath by presenterManager.displayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val displayedSlide by presenterManager.displayedSlide
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottieProgress by presenterManager.lottieProgress
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val websiteUrl by presenterManager.websiteUrl
    val webSnapshot by presenterManager.webSnapshot
    val activeScene by presenterManager.activeScene
    val mediaViewModel = LocalMediaViewModel.current

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
        Presenting.CANVAS -> true
        Presenting.NONE -> false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(presenterAspectRatio())
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        val primaryRole = screenAssignment.primaryOutputRole

        // ── Scaled presenter content (all modes except WEBSITE) ───────────────
        // JavaFX/Swing heavyweight components cannot be scaled by Compose layout,
        // so WEBSITE is handled separately below at native size.
        if (presentingMode != Presenting.WEBSITE) {
            ScaledPresenterContent {
                PresenterScreen(appSettings = appSettings, outputRole = primaryRole, isLowerThird = isLowerThird) {
                    if (presentingMode != Presenting.NONE && showsContent) {
                        when (presentingMode) {
                            Presenting.BIBLE ->
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = isLowerThird,
                                    outputRole = primaryRole,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade
                                )
                            Presenting.LYRICS ->
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = isLowerThird,
                                    outputRole = primaryRole,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade
                                )
                            Presenting.PICTURES ->
                                PicturePresenter(imagePath = displayedImagePath, transitionAlpha = pictureTransitionAlpha)
                            Presenting.PRESENTATION ->
                                SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha)
                            Presenting.MEDIA ->
                                if (mediaViewModel != null && !mediaViewModel.isAudioFile) {
                                    MediaPresenter(modifier = Modifier.fillMaxSize(), audioEnabled = false, transitionAlpha = mediaTransitionAlpha)
                                }
                            Presenting.LOWER_THIRD ->
                                LowerThirdPresenter(
                                    jsonContent = lottieJsonContent,
                                    progress = lottieProgress,
                                    appSettings = appSettings
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
                            else -> {}
                        }
                    }
                }
            }
        }

        // ── WEBSITE: display live screenshot captured from WebTab's WebView ─
        // A second JFXPanel instance can't be scaled/clipped by Compose.
        // Instead, WebTab pushes a snapshot bitmap every 200ms via PresenterManager
        // so this panel shows a pixel-accurate mirror including scroll position.
        if (presentingMode == Presenting.WEBSITE) {
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

        // Screen number label
        Text(
            text = stringResource(Res.string.screen_number, screenIndex + 1),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )

        // Animated audio indicator — only when presenting and media is playing
        if (presentingMode != Presenting.NONE
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
private fun MediaPreviewControls(viewModel: MediaViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (viewModel.isPlaying) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        ) {
            Icon(
                painter = painterResource(
                    if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play
                ),
                contentDescription = stringResource(
                    if (viewModel.isPlaying) Res.string.pause else Res.string.play
                ),
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }

        if (viewModel.duration > 0) {
            Slider(
                value = viewModel.currentPosition.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..viewModel.duration.toFloat(),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = viewModel.formatTime(viewModel.currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
