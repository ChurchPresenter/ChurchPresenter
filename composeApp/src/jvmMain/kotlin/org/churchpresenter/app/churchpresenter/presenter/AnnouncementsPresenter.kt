package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

@Composable
fun AnnouncementsPresenter(
    modifier: Modifier = Modifier,
    text: String,
    appSettings: AppSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
) {
    val isFillOrKey = outputRole == Constants.OUTPUT_ROLE_FILL || outputRole == Constants.OUTPUT_ROLE_KEY
    val settings   = appSettings.announcementsSettings
    val textColor  = parseHexColor(settings.textColor)
    val bgColor    = if (isFillOrKey) Color.Transparent
                     else if (settings.backgroundColor == "transparent") Color.Transparent
                     else parseHexColor(settings.backgroundColor)
    val fontFamily = systemFontFamilyOrDefault(settings.fontType)

    val textStyle = TextStyle(
        fontFamily     = fontFamily,
        fontWeight     = if (settings.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle      = if (settings.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (settings.underline) TextDecoration.Underline else TextDecoration.None,
        shadow         = if (settings.shadow) Shadow(
            color      = Color.Black.copy(alpha = 0.7f),
            offset     = Offset(2f, 2f),
            blurRadius = 4f
        ) else null,
        textAlign = when (settings.horizontalAlignment) {
            Constants.LEFT -> TextAlign.Left
            Constants.RIGHT -> TextAlign.Right
            else -> TextAlign.Center
        },
        color     = textColor
    )

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

    // For horizontal slides: use position's vertical component (top/center/bottom)
    // For vertical slides: use position's horizontal component (left/center/right)
    val slideAlignment: Alignment = if (isHorizontal) {
        when {
            settings.position.startsWith("Top")    -> Alignment.TopCenter
            settings.position.startsWith("Bottom") -> Alignment.BottomCenter
            else                                   -> Alignment.Center
        }
    } else {
        when {
            settings.position.endsWith("Left")  -> Alignment.CenterStart
            settings.position.endsWith("Right") -> Alignment.CenterEnd
            else                                -> Alignment.Center
        }
    }

    val scrollDurationMs = settings.animationDuration.coerceAtLeast(500)

    val textBlock: @Composable (Boolean) -> Unit = { fullWidth ->
        Box(
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .wrapContentHeight()
                .background(bgColor)
                .padding(horizontal = 32.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = text,
                style    = textStyle,
                fontSize = settings.fontSize.sp,
                modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .graphicsLayer { alpha = transitionAlpha }
    ) {
        if (isDirectional) {
            key(scrollDurationMs, movesPositive, settings.animationType) {
                val infiniteTransition = rememberInfiniteTransition(label = "presenterScroll")
                val offsetFraction by infiniteTransition.animateFloat(
                    initialValue = if (movesPositive) -1f else 1f,
                    targetValue  = if (movesPositive) 1f else -1f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(durationMillis = scrollDurationMs, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "presenterOffset"
                )

                if (isHorizontal) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(slideAlignment)
                            .graphicsLayer { translationX = size.width * offsetFraction },
                        contentAlignment = Alignment.Center
                    ) { textBlock(true) }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(slideAlignment)
                            .graphicsLayer { translationY = size.height * offsetFraction },
                        contentAlignment = Alignment.Center
                    ) { textBlock(false) }
                }
            }
        } else {
            // Static or fade — animation driven centrally via transitionAlpha
            val boxAlignment = positionToAlignment(settings.position)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = boxAlignment
            ) { textBlock(false) }
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
