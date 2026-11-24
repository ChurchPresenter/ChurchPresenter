package org.churchpresenter.hosanna

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.hosanna.generated.resources.Res
import org.churchpresenter.hosanna.generated.resources.inter_bold
import org.churchpresenter.hosanna.generated.resources.inter_light
import org.churchpresenter.hosanna.generated.resources.inter_regular
import org.churchpresenter.hosanna.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

@Immutable
data class HosannaTypography(
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

@Composable
fun rememberInterFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_light, FontWeight.Light),
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

private fun style(size: TextUnit, line: TextUnit, weight: FontWeight, tracking: TextUnit = 0.em) = TextStyle(
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = tracking,
)

private val BaseTypography = HosannaTypography(
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

fun hosannaTypography(family: FontFamily): HosannaTypography = with(BaseTypography) {
    HosannaTypography(
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

fun HosannaTypography.toMaterial(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = headline,
    headlineMedium = headline,
    headlineSmall = headline,
    titleLarge = title,
    titleMedium = subtitle,
    titleSmall = subtitle,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = captionStrong,
    labelSmall = overline,
)

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun String.asOverline(): String = uppercase()
