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
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
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
    content: @Composable BoxScope.() -> Unit
) {
    val bgSettings = appSettings.backgroundSettings
    val defaultType = bgSettings.defaultBackgroundType
    val backgroundColor = parseHexColor(bgSettings.defaultBackgroundColor)

    val backgroundImageBitmap = remember(defaultType, bgSettings.defaultBackgroundImage) {
        if (defaultType == Constants.BACKGROUND_IMAGE && bgSettings.defaultBackgroundImage.isNotEmpty()) {
            try {
                val file = File(bgSettings.defaultBackgroundImage)
                if (file.exists()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
                else null
            } catch (_: Exception) { null }
        } else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background layer
        when (defaultType) {
            Constants.BACKGROUND_IMAGE -> {
                if (backgroundImageBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                Modifier.background(Color.Black)
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = BitmapPainter(backgroundImageBitmap),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
            }
            Constants.BACKGROUND_VIDEO -> {
                LoopingVideoBackground(
                    videoPath = bgSettings.defaultBackgroundVideo,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
            }
        }
        // Content layer
        content()
    }
}
