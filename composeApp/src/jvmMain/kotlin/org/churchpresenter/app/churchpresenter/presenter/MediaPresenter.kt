package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.churchpresenter.app.churchpresenter.composables.SharedVideoOutputDisplay
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel

@Composable
fun MediaPresenter(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    transitionAlpha: Float = 1f,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    /** Whether this output draws the subtitle overlay at all -- `OutputProfile.showSubtitles`. */
    showSubtitles: Boolean = true,
    /**
     * The profile this output runs, which decides *which* subtitle tracks it draws.
     *
     * Blank means "no particular output" -- the settings preview, a test -- and every loaded track
     * that is on is drawn, which is also what an unrouted track does on a real output.
     */
    profileId: String = "",
    mediaSettings: MediaSettings = MediaSettings(),
    /** How the video meets the output -- `AppSettings.mediaScaleMode`, through `contentScale`. */
    contentScale: ContentScale = ContentScale.Fit,
) {
    // Key mode: solid white frame (mixer sees "fully visible")
    if (outputRole == Constants.OUTPUT_ROLE_KEY) {
        Box(modifier = modifier.fillMaxSize().background(Color.White).alpha(transitionAlpha))
        return
    }
    val viewModel = LocalMediaViewModel.current ?: return

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            viewModel.pause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(transitionAlpha)
    ) {
        if (viewModel.isLoaded && isVisible) {
            // No VLC instance here — the single master SoftwareVideoPlayer (hosted in
            // MainDesktop / MediaTab) decodes once and writes every frame to SharedVideoOutput.
            // All presenter windows (any number of screens) just display that shared bitmap,
            // eliminating the multiple-decoder jitter that occurred with per-window VideoPlayers.
            SharedVideoOutputDisplay(modifier = Modifier.fillMaxSize(), contentScale = contentScale)

            // Drawn per-output, unlike the shared decoded frame: this is what lets one output hide
            // the subtitle overlay ([showSubtitles]) while another keeps showing it, and what lets
            // two outputs draw different tracks of the same video. An embedded track cannot do
            // either -- VLC burns it into the one frame they all share.
            if (showSubtitles) {
                val cues = viewModel.activeSubtitleCues(profileId)
                if (cues.isNotEmpty()) {
                    SubtitleOverlay(cues = cues, mediaSettings = mediaSettings, outputRole = outputRole)
                }
            }
        }
    }
}
