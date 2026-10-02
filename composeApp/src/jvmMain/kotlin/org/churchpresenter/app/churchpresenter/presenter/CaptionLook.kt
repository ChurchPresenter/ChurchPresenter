package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.churchpresenter.app.churchpresenter.composables.BottomAlignedText
import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_ROLL_UP
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
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
import org.churchpresenter.sharedui.utils.spacingEm
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
internal fun CaptionReading.rollUpMillisOrOff(): Int =
    if (rollUp && style == CAPTION_STYLE_ROLL_UP) rollUpMillis.coerceAtLeast(0) else 0

/** True when [s] draws its captions in a full-width band rather than a card. */
private val STTSettings.isBand: Boolean get() = boxShape == CAPTION_BOX_BAND

/**
 * The caption's own margins in from each edge. A band runs edge to edge, so it takes no side
 * margin, and none on the edge it touches when [STTSettings.bandTouchesEdge] -- its side margins
 * become its text's inset instead ([captionCard]).
 */
internal fun Modifier.captionMargins(s: STTSettings): Modifier {
    if (!s.isBand) {
        return absolutePadding(
            left = s.marginLeft.dp,
            top = s.marginTop.dp,
            right = s.marginRight.dp,
            bottom = s.marginBottom.dp,
        )
    }
    val alignment = sttPositionToAlignment(s.position)
    val onTop = alignment == Alignment.TopStart || alignment == Alignment.TopCenter || alignment == Alignment.TopEnd
    val onBottom = alignment == Alignment.BottomStart || alignment == Alignment.BottomCenter ||
        alignment == Alignment.BottomEnd
    return absolutePadding(
        top = if (onTop && s.bandTouchesEdge) 0.dp else s.marginTop.dp,
        bottom = if (onBottom && s.bandTouchesEdge) 0.dp else s.marginBottom.dp,
    )
}

private val CAPTION_CARD_SHAPE = RoundedCornerShape(16.dp)
private val CAPTION_CARD_PADDING = 24.dp
private val CAPTION_BAND_PADDING = 16.dp

/**
 * The box behind the captions in [background]: today's rounded card, or a square band the full
 * width of the room it is in, its text inset by the side margins -- or, [inTextBox], by the card's
 * own padding, since a text box is already placed where the operator put it.
 */
internal fun Modifier.captionCard(s: STTSettings, background: Color, inTextBox: Boolean = false): Modifier =
    if (!s.isBand) {
        fillMaxWidth().clip(CAPTION_CARD_SHAPE).background(background).padding(CAPTION_CARD_PADDING)
    } else {
        val side = if (inTextBox) CAPTION_CARD_PADDING else 0.dp
        fillMaxWidth().background(background).absolutePadding(
            left = if (inTextBox) side else s.marginLeft.dp,
            right = if (inTextBox) side else s.marginRight.dp,
            top = CAPTION_BAND_PADDING,
            bottom = CAPTION_BAND_PADDING,
        )
    }

/**
 * One side's caption text the way the profile presents it: a crawling ticker, an RSVP flash of the
 * newest words, a pop-on block that fills and clears, or the rolling transcript.
 */
@Composable
internal fun CaptionLines(text: AnnotatedString, style: TextStyle, s: STTSettings, modifier: Modifier = Modifier) {
    when (s.reading.style) {
        CAPTION_STYLE_TICKER -> CaptionTicker(text, style, s.outline, s.reading.tickerSpeed, modifier)
        CAPTION_STYLE_RSVP -> BottomAlignedText(
            // Already cut down to the flash on screen -- see withReadingAids
            text = text,
            style = style,
            maxLines = RSVP_MAX_LINES,
            modifier = modifier,
            backdrop = s.backdrop,
            outline = s.outline,
        )
        else -> BottomAlignedText(
            text = text,
            style = style,
            maxLines = s.maxLines,
            modifier = modifier,
            backdrop = s.backdrop,
            outline = s.outline,
            rollUpMillis = s.reading.rollUpMillisOrOff(),
            paged = s.reading.style == CAPTION_STYLE_POP_ON,
        )
    }
}

/** A flash of a word or three fits one line at caption sizes; a long one may take a second. */
private const val RSVP_MAX_LINES = 2

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
