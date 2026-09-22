package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
    /** Whether this output draws the subtitle overlay at all -- `ScreenAssignment.showSubtitles`. */
    showSubtitles: Boolean = true,
    mediaSettings: MediaSettings = MediaSettings(),
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
            SharedVideoOutputDisplay(modifier = Modifier.fillMaxSize())

            // Drawn per-output, unlike the shared decoded frame: this is what lets one output
            // hide the subtitle overlay ([showSubtitles]) while another keeps showing it.
            if (showSubtitles) {
                viewModel.activeSubtitleCue?.let { cue ->
                    SubtitleOverlay(cue = cue, mediaSettings = mediaSettings, outputRole = outputRole)
                }
            }
        }
    }
}
