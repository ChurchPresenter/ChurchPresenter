package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.STTSettings

/**
 * Fades the captions out once [shown] has stayed the same for [CaptionReading.clearAfterSeconds],
 * and brings them straight back when it changes. Always whole while clearing is off.
 */
@Composable
internal fun rememberSilenceFade(shown: String, reading: CaptionReading): State<Float> {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(shown, reading.clearAfterSilence, reading.clearAfterSeconds, reading.clearFadeMillis) {
        alpha.snapTo(1f)
        if (!reading.clearAfterSilence || shown.isBlank()) return@LaunchedEffect
        delay(reading.clearAfterSeconds.coerceAtLeast(1) * SILENCE_MS_PER_SECOND)
        alpha.animateTo(0f, tween(reading.clearFadeMillis.coerceAtLeast(0)))
    }
    return alpha.asState()
}

private const val SILENCE_MS_PER_SECOND = 1000L

/** How long a line takes to slide up, or 0 when roll-up is off. */
internal fun CaptionReading.rollUpMillisOrOff(): Int = if (rollUp) rollUpMillis.coerceAtLeast(0) else 0

/** The caption's own margins in from each edge. */
internal fun Modifier.captionMargins(s: STTSettings): Modifier =
    absolutePadding(
        left = s.marginLeft.dp,
        top = s.marginTop.dp,
        right = s.marginRight.dp,
        bottom = s.marginBottom.dp,
    )

/** The translation's look: the transcript's, with its own size, and bold or italic on top when asked. */
internal fun translationTextStyle(base: TextStyle, s: STTSettings): TextStyle {
    val size = s.translationFontSize.takeIf { it > 0 } ?: return base.copy(
        fontWeight = if (s.translationBold) FontWeight.Bold else base.fontWeight,
        fontStyle = if (s.translationItalic) FontStyle.Italic else base.fontStyle,
    )
    return base.copy(
        fontSize = size.sp,
        lineHeight = (size * s.lineSpacing / PERCENT).sp,
        letterSpacing = spacingEm(s.letterSpacing, size).em,
        fontWeight = if (s.translationBold) FontWeight.Bold else base.fontWeight,
        fontStyle = if (s.translationItalic) FontStyle.Italic else base.fontStyle,
    )
}
