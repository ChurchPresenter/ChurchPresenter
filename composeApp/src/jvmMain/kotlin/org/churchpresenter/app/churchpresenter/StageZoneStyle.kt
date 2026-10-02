package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.composables.backdropRoom
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.slides.utils.PictureDecoder
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun CenteredText(text: String, style: StageMonitorZoneStyle) {
    val painter = rememberTextBackdropPainter(style.backdrop)
    OutlinedText(
        text = text,
        outline = style.outline,
        scaleFactor = 1f,
        fillWidth = false,
        color = Color.Unspecified,
        fontSize = TextUnit.Unspecified,
        style = buildTextStyle(
            fontType = style.fontType,
            fontSize = style.fontSize,
            color = parseHexColor(style.color),
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            shadowColor = parseHexColor(style.shadowColor),
            shadowSize = style.shadowSize,
            shadowOpacity = style.shadowOpacity
        ),
        modifier = Modifier.backdropRoom(style.backdrop).then(painter.modifier),
        onTextLayout = painter::onTextLayout,
        textAlign = TextAlign.Center
    )
}

internal fun formatClock(now: LocalTime, use24Hour: Boolean): String {
    val pattern = if (use24Hour) "HH:mm:ss" else "hh:mm:ss a"
    return now.format(DateTimeFormatter.ofPattern(pattern))
}

internal fun buildTextStyle(
    fontType: String,
    fontSize: Int,
    color: Color,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    shadow: Boolean = false,
    shadowColor: Color = Color.Black,
    shadowSize: Int = 100,
    shadowOpacity: Int = 80
): TextStyle {
    val fontFamily = systemFontFamilyOrDefault(fontType)
    return TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (shadow) Shadow(
            color = shadowColor.copy(alpha = shadowOpacity / 100f),
            offset = Offset(shadowSize / SHADOW_OFFSET_DIVISOR, shadowSize / SHADOW_OFFSET_DIVISOR),
            blurRadius = shadowSize / 5f
        ) else null
    )
}

internal suspend fun loadImageBitmapFromPath(path: String?): ImageBitmap? {
    if (path == null) return null
    return withContext(Dispatchers.IO) {
        // PictureDecoder, not Skia directly — the stage monitor draws the same operator-chosen
        // files the output does, and must not blank on a format only the fallbacks read.
        PictureDecoder.decodeOrNull(File(path))?.toComposeImageBitmap()
    }
}

internal fun resolveTextAlign(horizontal: String): TextAlign {
    return when (horizontal) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
}

internal fun resolveColumnVerticalArrangement(vertical: String): Arrangement.Vertical {
    return when (vertical) {
        Constants.BOTTOM -> Arrangement.Bottom
        Constants.MIDDLE -> Arrangement.Center
        else -> Arrangement.Top
    }
}

internal fun resolveColumnHorizontalAlignment(horizontal: String): Alignment.Horizontal {
    return when (horizontal) {
        Constants.RIGHT -> Alignment.End
        Constants.CENTER -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
}

private const val SHADOW_OFFSET_DIVISOR = 10f
