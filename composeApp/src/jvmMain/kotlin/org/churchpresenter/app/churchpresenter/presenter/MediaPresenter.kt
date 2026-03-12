package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.composables.VideoPlayer
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel

@Composable
fun MediaPresenter(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    audioEnabled: Boolean = true,
    audioDeviceId: String = "",
    transitionAlpha: Float = 1f
) {
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
            VideoPlayer(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                audioEnabled = audioEnabled,
                audioDeviceId = audioDeviceId
            )
        }
    }
}
