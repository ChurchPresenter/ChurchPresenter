package org.churchpresenter.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class TypeScale(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val captionStrong: TextStyle,
    val overline: TextStyle,
)

private fun style(size: TextUnit, line: TextUnit, weight: FontWeight, tracking: TextUnit = 0.em) = TextStyle(
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = tracking,
)

private val BaseTypography = TypeScale(
    display = style(32.sp, 40.sp, FontWeight.Light, (-0.01).em),
    headline = style(20.sp, 28.sp, FontWeight.SemiBold, (-0.01).em),
    title = style(16.sp, 22.sp, FontWeight.SemiBold),
    subtitle = style(14.sp, 20.sp, FontWeight.SemiBold),
    body = style(13.5.sp, 20.sp, FontWeight.Normal),
    bodyStrong = style(13.5.sp, 20.sp, FontWeight.SemiBold),
    label = style(13.sp, 18.sp, FontWeight.SemiBold),
    caption = style(12.sp, 16.sp, FontWeight.Normal),
    captionStrong = style(12.sp, 16.sp, FontWeight.SemiBold),
    overline = style(10.5.sp, 14.sp, FontWeight.Bold, 0.06.em),
)

/** The components' type scale, in [family] -- the font the app's theme uses. */
fun typeScale(family: FontFamily): TypeScale = with(BaseTypography) {
    TypeScale(
        display = display.copy(fontFamily = family),
        headline = headline.copy(fontFamily = family),
        title = title.copy(fontFamily = family),
        subtitle = subtitle.copy(fontFamily = family),
        body = body.copy(fontFamily = family),
        bodyStrong = bodyStrong.copy(fontFamily = family),
        label = label.copy(fontFamily = family),
        caption = caption.copy(fontFamily = family),
        captionStrong = captionStrong.copy(fontFamily = family),
        overline = overline.copy(fontFamily = family),
    )
}

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun String.asOverline(): String = uppercase()
