package org.churchpresenter.announcements.presenter

import org.churchpresenter.settings.AnnouncementsSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.sharedui.presenter.ReferenceScaledBox
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.composables.SlideAcross
import org.churchpresenter.sharedui.utils.announcementSecondsMs
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.utils.calculateAutoFitFontSize
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault

/** The inset the announcement text is drawn inside, and so the inset auto-fit measures against. */
val ANNOUNCEMENT_TEXT_INSET = 32.dp
private val TEXT_PADDING_VERTICAL = 16.dp

@Composable
fun AnnouncementsPresenter(
    modifier: Modifier = Modifier,
    text: String,
    appSettings: AppSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
    onFinished: () -> Unit = {},
    showBackground: Boolean = true,
) = ReferenceScaledBox(modifier) {
    AnnouncementsPresenterContent(
        text = text,
        appSettings = appSettings,
        outputRole = outputRole,
        transitionAlpha = transitionAlpha,
        onFinished = onFinished,
        showBackground = showBackground,
    )
}

/** [AnnouncementsPresenter] as a 1920x1080-family output draws it; [ReferenceScaledBox] fits it to the real one. */
@Composable
private fun AnnouncementsPresenterContent(
    text: String,
    appSettings: AppSettings,
    outputRole: String,
    transitionAlpha: Float,
    onFinished: () -> Unit,
    showBackground: Boolean,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val settings   = appSettings.announcementsSettings
    val textColor  = if (isKey) Color.White else parseHexColor(settings.textColor)
    val bgColor    = if (!showBackground) Color.Transparent
                     else if (settings.backgroundColor == Constants.COLOR_VALUE_TRANSPARENT) Color.Transparent
                     else parseHexColor(settings.backgroundColor)
    val textStyle = announcementTextStyle(settings, textColor)

    val isDirectional = settings.animationType in listOf(
        Constants.ANIMATION_SLIDE_FROM_LEFT,
        Constants.ANIMATION_SLIDE_FROM_RIGHT,
        Constants.ANIMATION_SLIDE_FROM_TOP,
        Constants.ANIMATION_SLIDE_FROM_BOTTOM
    )

    val isHorizontal = settings.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                       settings.animationType == Constants.ANIMATION_SLIDE_FROM_RIGHT

    val movesPositive = settings.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                        settings.animationType == Constants.ANIMATION_SLIDE_FROM_TOP

    val slideAlignment = slideAlignmentFor(settings.position, isHorizontal)

    val crossingMs = announcementSecondsMs(settings.animationDuration)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .graphicsLayer { alpha = transitionAlpha }
    ) {
        // Measured in dp, not in pixels. `calculateAutoFitFontSize` measures at `Density(1f)` and
        // returns a size that is then drawn as `.sp`, which the platform scales by the output's
        // density -- so fitting against a pixel width let a HiDPI output take a size `density`
        // times too large for the space it was fitted to, and the text overran the slide.
        val availableWidth = (maxWidth - ANNOUNCEMENT_TEXT_INSET * 2).value.toInt()
        val availableHeight = (maxHeight - TEXT_PADDING_VERTICAL * 2).value.toInt()

        val effectiveFontSize = rememberAnnouncementFontSize(
            text, textStyle, settings.fontSize, availableWidth, availableHeight, isDirectional, isHorizontal,
        )

        val painter = rememberTextBackdropPainter(settings.backdrop)
        val textBlock: @Composable (Boolean) -> Unit = { wrap ->
            Box(
                modifier = Modifier
                    .then(if (!wrap) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                    .wrapContentHeight()
                    .background(bgColor)
                    .padding(horizontal = ANNOUNCEMENT_TEXT_INSET, vertical = TEXT_PADDING_VERTICAL),
                contentAlignment = Alignment.Center
            ) {
                OutlinedText(
                    modifier = painter.modifier,
                    onTextLayout = painter::onTextLayout,
                    text = text,
                    outline = settings.outline,
                    // The stroke is stored against the *configured* size, and auto-fit changes the
                    // size actually drawn -- so it tracks that ratio and keeps its weight relative
                    // to the letters instead of thinning out as the text grows.
                    scaleFactor = effectiveFontSize.toFloat() / settings.fontSize.coerceAtLeast(1),
                    color = Color.Unspecified,
                    style = textStyle,
                    fontSize = effectiveFontSize.sp,
                    softWrap = wrap,
                    fillWidth = false,
                )
            }
        }

        if (isDirectional) {
            SlideAcross(
                crossingMs = crossingMs,
                horizontal = isHorizontal,
                movesPositive = movesPositive,
                alignment = slideAlignment,
                loopCount = settings.loopCount,
                onFinished = onFinished,
            ) { textBlock(!isHorizontal) }
        } else {
            // Static or fade — animation driven centrally via transitionAlpha
            val boxAlignment = positionToAlignment(settings.position)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = boxAlignment
            ) { textBlock(true) }
        }
    }
}

private fun positionToAlignment(position: String): Alignment = when (position) {
    Constants.TOP_LEFT      -> Alignment.TopStart
    Constants.TOP_CENTER    -> Alignment.TopCenter
    Constants.TOP_RIGHT     -> Alignment.TopEnd
    Constants.CENTER_LEFT   -> Alignment.CenterStart
    Constants.CENTER        -> Alignment.Center
    Constants.CENTER_RIGHT  -> Alignment.CenterEnd
    Constants.BOTTOM_LEFT   -> Alignment.BottomStart
    Constants.BOTTOM_CENTER -> Alignment.BottomCenter
    Constants.BOTTOM_RIGHT  -> Alignment.BottomEnd
    else                    -> Alignment.Center
}

/** The announcement's type: its font, weight, style, shadow and alignment, in [textColor]. */
private fun announcementTextStyle(settings: AnnouncementsSettings, textColor: Color): TextStyle {
val fontFamily = systemFontFamilyOrDefault(settings.fontType)

val shadowColorBase = parseHexColor(settings.shadowColor)
val shadowSizeMul = settings.shadowSize / 100f
val shadowAlpha = (settings.shadowOpacity / 100f).coerceIn(0f, 1f)
val announcementShadow = Shadow(
    color = shadowColorBase.copy(alpha = shadowAlpha),
    offset = Offset(6f * shadowSizeMul, 6f * shadowSizeMul),
    blurRadius = 12f * shadowSizeMul
)

return TextStyle(
    fontFamily     = fontFamily,
    fontWeight     = if (settings.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle      = if (settings.italic) FontStyle.Italic else FontStyle.Normal,
    textDecoration = if (settings.underline) TextDecoration.Underline else TextDecoration.None,
    shadow         = if (settings.shadow) announcementShadow else null,
    textAlign = when (settings.horizontalAlignment) {
        Constants.LEFT -> TextAlign.Left
        Constants.RIGHT -> TextAlign.Right
        else -> TextAlign.Center
    },
    color     = textColor
)
}

/**
 * Where a sliding announcement rides: a horizontal slide keeps [position]'s vertical part
 * (top, centre or bottom), and a vertical one its horizontal part.
 */
private fun slideAlignmentFor(position: String, isHorizontal: Boolean): Alignment {
// For horizontal slides: use position's vertical component (top/center/bottom)
// For vertical slides: use position's horizontal component (left/center/right)
return if (isHorizontal) {
    when {
        position.startsWith("Top")    -> Alignment.TopCenter
        position.startsWith("Bottom") -> Alignment.BottomCenter
        else                                   -> Alignment.Center
    }
} else {
    when {
        position.endsWith("Left")  -> Alignment.CenterStart
        position.endsWith("Right") -> Alignment.CenterEnd
        else                                -> Alignment.Center
    }
}
}

/**
 * The size the announcement is drawn at, never above [fontSize]: fitted to the whole space when it
 * stands still or fades, to the width alone when it slides vertically, and unfitted when it slides
 * horizontally. The space is in dp -- see the caller.
 */
@Composable
private fun rememberAnnouncementFontSize(
    text: String,
    textStyle: TextStyle,
    fontSize: Int,
    availableWidth: Int,
    availableHeight: Int,
    isDirectional: Boolean,
    isHorizontal: Boolean,
): Int {
    val rememberedMeasurer = rememberTextMeasurer()
    return if (!isDirectional) {
        // Static/fade: fit to both width and height
        remember(
            text,
            fontSize,
            availableWidth,
            availableHeight,
            textStyle.fontFamily,
            textStyle.fontWeight
        ) {
            calculateAutoFitFontSize(rememberedMeasurer, text, textStyle, availableWidth, availableHeight)
                .coerceAtMost(fontSize)
        }
    } else if (!isHorizontal) {
        // Vertical slide: fit to width only (scrolls vertically)
        remember(text, fontSize, availableWidth, textStyle.fontFamily, textStyle.fontWeight) {
            calculateAutoFitFontSize(rememberedMeasurer, text, textStyle, availableWidth, Int.MAX_VALUE)
                .coerceAtMost(fontSize)
        }
    } else {
        // Horizontal slide: no auto-fit (scrolls horizontally)
        fontSize
    }
}
