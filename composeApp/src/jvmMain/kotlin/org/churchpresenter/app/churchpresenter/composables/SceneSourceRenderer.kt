package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.Image
import java.io.File

@Composable
fun SceneSourceRenderer(
    source: SceneSource,
    modifier: Modifier = Modifier,
    isPresenter: Boolean = false
) {
    when (source) {
        is SceneSource.ImageSource -> ImageSourceContent(source, modifier)
        is SceneSource.TextSource -> TextSourceContent(source, modifier)
        is SceneSource.ColorSource -> ColorSourceContent(source, modifier)
        is SceneSource.VideoSource -> VideoSourceContent(source, modifier, isPresenter)
        is SceneSource.BrowserSource -> BrowserSourceContent(source, modifier, isPresenter)
    }
}

@Composable
private fun ImageSourceContent(source: SceneSource.ImageSource, modifier: Modifier) {
    val bitmap = remember(source.filePath) {
        try {
            val file = File(source.filePath)
            if (file.exists()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            else null
        } catch (_: Exception) { null }
    }

    if (bitmap != null) {
        val scale = when (source.contentScale) {
            "FILL" -> ContentScale.Crop
            "STRETCH" -> ContentScale.FillBounds
            "NONE" -> ContentScale.None
            else -> ContentScale.Fit
        }
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = source.name,
            contentScale = scale,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Image not found", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TextSourceContent(source: SceneSource.TextSource, modifier: Modifier) {
    val bgColor = if (source.backgroundColor.equals("#00000000", ignoreCase = true))
        Color.Transparent
    else
        parseHexColor(source.backgroundColor)
    val textColor = parseHexColor(source.fontColor)
    val fontFamily = remember(source.fontFamily) { systemFontFamilyOrDefault(source.fontFamily) }
    val align = when (source.horizontalAlignment) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = source.text,
            color = textColor,
            fontSize = source.fontSize.sp,
            fontFamily = fontFamily,
            fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = align,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
private fun ColorSourceContent(source: SceneSource.ColorSource, modifier: Modifier) {
    val color1 = parseHexColor(source.color).copy(alpha = source.sourceOpacity)
    if (source.isGradient) {
        val color2 = parseHexColor(source.gradientColor2).copy(alpha = source.sourceOpacity)
        val angleRad = Math.toRadians(source.gradientAngle.toDouble())
        val pos = source.gradientPosition.coerceIn(0.001f, 0.999f)
        val brush = Brush.linearGradient(
            colorStops = arrayOf(0f to color1, pos to color2, 1f to color2),
            start = Offset(0.5f - 0.5f * cos(angleRad).toFloat(), 0.5f - 0.5f * sin(angleRad).toFloat()) * 1000f,
            end = Offset(0.5f + 0.5f * cos(angleRad).toFloat(), 0.5f + 0.5f * sin(angleRad).toFloat()) * 1000f
        )
        Box(modifier = modifier.fillMaxSize().background(brush))
    } else {
        Box(modifier = modifier.fillMaxSize().background(color1))
    }
}

@Composable
private fun VideoSourceContent(
    source: SceneSource.VideoSource,
    modifier: Modifier,
    isPresenter: Boolean
) {
    // In preview mode, show a placeholder. In presenter mode, a real VideoPlayer would be used.
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPresenter) "Video: ${source.name}" else "Video: ${File(source.filePath).name}",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun BrowserSourceContent(
    source: SceneSource.BrowserSource,
    modifier: Modifier,
    isPresenter: Boolean
) {
    // In preview mode, show a placeholder. In presenter mode, a real CefBrowser would be used.
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Browser: ${source.url}",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
