package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * Displays a Lottie animation in the presenter window.
 *
 * Animation progress is driven centrally by PresenterWindows so all
 * presenter displays stay in perfect sync.
 */
@Composable
fun LowerThirdPresenter(
    jsonContent: String,
    progress: Float,
    appSettings: AppSettings
) {
    if (jsonContent.isBlank()) return

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieCompositionSpec.JsonString(jsonContent)
    }

    val s = appSettings.streamingSettings
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            painter = rememberLottiePainter(
                composition = composition,
                progress = { progress }
            ),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(
                    start = s.windowLeft.dp,
                    end = s.windowRight.dp,
                    top = s.windowTop.dp,
                    bottom = s.windowBottom.dp
                )
                .fillMaxSize()
        )
    }
}
