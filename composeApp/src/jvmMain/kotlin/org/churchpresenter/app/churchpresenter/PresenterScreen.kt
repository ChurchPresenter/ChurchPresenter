package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.composables.keySignal
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File

@Composable
fun PresenterScreen(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    isLowerThird: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val isFillOrKey = outputRole == Constants.OUTPUT_ROLE_FILL || outputRole == Constants.OUTPUT_ROLE_KEY
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY

    val bgSettings = appSettings.backgroundSettings
    // Use lower third defaults when screen is in lower third mode
    val bgType = if (isLowerThird) bgSettings.defaultLowerThirdBackgroundType else bgSettings.defaultBackgroundType
    val bgColorHex = if (isLowerThird) bgSettings.defaultLowerThirdBackgroundColor else bgSettings.defaultBackgroundColor
    val bgImagePath = if (isLowerThird) bgSettings.defaultLowerThirdBackgroundImage else bgSettings.defaultBackgroundImage
    val bgVideoPath = if (isLowerThird) bgSettings.defaultLowerThirdBackgroundVideo else bgSettings.defaultBackgroundVideo
    val bgOpacity = if (isLowerThird) bgSettings.defaultLowerThirdBackgroundOpacity else bgSettings.defaultBackgroundOpacity
    val backgroundColor = if (isFillOrKey) Color.Black else parseHexColor(bgColorHex)

    val backgroundImageBitmap = remember(bgType, bgImagePath, isFillOrKey) {
        if (!isFillOrKey && bgType == Constants.BACKGROUND_IMAGE && bgImagePath.isNotEmpty()) {
            try {
                val file = File(bgImagePath)
                if (file.exists()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
                else null
            } catch (_: Exception) { null }
        } else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background layer — always black for fill/key
        if (isFillOrKey) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        } else {
            when (bgType) {
                Constants.BACKGROUND_IMAGE -> {
                    if (backgroundImageBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(Modifier.background(Color.Black))
                        ) {
                            androidx.compose.foundation.Image(
                                painter = BitmapPainter(backgroundImageBitmap),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().alpha(bgOpacity)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                }
                Constants.BACKGROUND_VIDEO -> {
                    LoopingVideoBackground(
                        videoPath = bgVideoPath,
                        modifier = Modifier.fillMaxSize().alpha(bgOpacity)
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(backgroundColor.copy(alpha = bgOpacity)))
                }
            }
        }
        // Content layer — apply key modifier if key mode
        if (isKey) {
            Box(modifier = Modifier.fillMaxSize().keySignal()) {
                content()
            }
        } else {
            content()
        }
    }
}
