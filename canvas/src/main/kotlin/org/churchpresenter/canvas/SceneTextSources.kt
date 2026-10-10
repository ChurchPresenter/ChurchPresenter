package org.churchpresenter.canvas

import androidx.compose.foundation.background
import org.churchpresenter.strings.generated.resources.canvas_bible_select_verse
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.abs
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.sharedui.utils.Utils.parseHexColor

import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import androidx.compose.foundation.Canvas

import androidx.compose.ui.graphics.drawscope.Stroke
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.composables.backdropRoom
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter

/**
 * Tracking as Compose wants it: [TextUnit.Unspecified] for none at all, rather than a spacing of
 * zero. The two are not the same to the text shaper — asking for zero re-shapes the line and moves
 * it by a pixel, which would change every scene that has never touched the setting.
 */
internal fun trackingOf(percent: Float): TextUnit =
    if (percent == 0f) TextUnit.Unspecified else (percent / PERCENT_SCALE).em

/** Underline, strike-through, both, or neither — as Compose wants it. */
internal fun textDecorationOf(underline: Boolean, strikethrough: Boolean): TextDecoration? = when {
    underline && strikethrough ->
        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strikethrough -> TextDecoration.LineThrough
    else -> null
}

@Composable
internal fun TextSourceContent(source: SceneSource.TextSource, modifier: Modifier, fontScale: Float = 1f) {
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
    val lineHeightMultiplier = source.lineSpacing / 100f
    val verticalAlign = when (source.verticalAlignment) {
        "top" -> Alignment.TopCenter
        "bottom" -> Alignment.BottomCenter
        else -> Alignment.Center
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor).clipToBounds(),
        contentAlignment = verticalAlign
    ) {
        if (source.curve != 0f) {
            CurvedText(
                text = source.text,
                curve = source.curve,
                style = TextStyle(
                    color = textColor,
                    fontSize = drawnFontSize(source.fontSize, fontScale),
                    fontFamily = fontFamily,
                    fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(source.underline, source.strikethrough),
                    letterSpacing = trackingOf(source.letterSpacing),
                ),
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } else {
            val painter = rememberTextBackdropPainter(source.backdrop, fontScale)
            OutlinedText(
                text = source.text,
                outline = source.outline,
                scaleFactor = fontScale,
                color = textColor,
                fontSize = drawnFontSize(source.fontSize, fontScale),
                fontFamily = fontFamily,
                style = TextStyle(
                    fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(source.underline, source.strikethrough),
                    letterSpacing = trackingOf(source.letterSpacing),
                ),
                textAlign = align,
                lineHeight = drawnFontSize(source.fontSize, fontScale * lineHeightMultiplier),
                overflow = TextOverflow.Ellipsis,
                fillWidth = false,
                modifier = Modifier.padding(4.dp)
                    .backdropRoom(source.backdrop, fontScale)
                    .then(painter.modifier),
                onTextLayout = painter::onTextLayout,
            )
        }
    }
}

/**
 * One line of text bent around a circle: [curve] is a percentage, positive arching the line over
 * the circle and negative cupping it under, and 100 spends a half circle on it.
 *
 * Compose has no text-on-a-path, so each glyph is measured and drawn on its own, rotated to the
 * angle its own centre sits at. That is also why the line cannot wrap — newlines become spaces.
 */
@Composable
fun CurvedText(
    text: String,
    curve: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
    /** Stroked underneath, glyph for glyph, exactly as `OutlinedText` does for laid-out text. */
    outline: TextOutline = TextOutline(),
    outlineScale: Float = 1f,
) {
    if (outline.isVisible) {
        // The stroke first and the fill over it, both drawn into the same box: a bent line has no
        // layout of its own to disturb, so the two passes land glyph for glyph.
        CurvedText(
            text = text,
            curve = curve,
            style = style.copy(
                color = parseHexColor(outline.color),
                drawStyle = Stroke(width = outline.width * outlineScale),
            ),
            modifier = modifier,
        )
    }
    val measurer = rememberTextMeasurer()
    val glyphs = remember(text, style) {
        text.replace('\n', ' ').map { measurer.measure(AnnotatedString(it.toString()), style) }
    }
    val widths = remember(glyphs) { glyphs.map { it.size.width.toFloat() } }
    val lineWidth = widths.sum()
    val lineHeight = remember(glyphs) { glyphs.maxOfOrNull { it.size.height.toFloat() } ?: 0f }

    Canvas(modifier) {
        if (lineWidth <= 0f) return@Canvas
        val sweep = (abs(curve) / PERCENT_SCALE).coerceAtMost(MAX_CURVE_TURNS) * PI.toFloat()
        val radius = lineWidth / sweep
        // How far the ends fall away from the middle of the arc, which is what it costs in height.
        val sagitta = radius * (1f - cos(sweep / 2f))
        val arch = curve > 0f
        val extentTop = (size.height - (sagitta + lineHeight)) / 2f
        val apexTop = if (arch) extentTop else extentTop + sagitta
        val pivot = Offset(size.width / 2f, if (arch) apexTop + radius else apexTop - radius)

        var travelled = 0f
        glyphs.forEachIndexed { index, glyph ->
            val centre = travelled + widths[index] / 2f
            val angle = Math.toDegrees(((centre - lineWidth / 2f) / radius).toDouble()).toFloat()
            rotate(degrees = if (arch) angle else -angle, pivot = pivot) {
                drawText(glyph, topLeft = Offset(size.width / 2f - widths[index] / 2f, apexTop))
            }
            travelled += widths[index]
        }
    }
}

/**
 * The verse over its reference, both bent by [SceneSource.BibleSource.curve].
 *
 * A bent line cannot wrap, so the verse is one line however long it is — which is the trade the
 * curve asks for, and why it is off by default.
 */
@Composable
private fun CurvedBibleText(
    source: SceneSource.BibleSource,
    textColor: Color,
    refColor: Color,
    fontFamily: FontFamily,
    fontScale: Float
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        CurvedText(
            text = source.verseText.ifEmpty { stringResource(Res.string.canvas_bible_select_verse) },
            curve = source.curve,
            style = TextStyle(
                color = if (source.verseText.isEmpty()) Color.Gray else textColor,
                fontSize = drawnFontSize(source.fontSize, fontScale),
                fontFamily = fontFamily,
                fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = textDecorationOf(source.underline, source.strikethrough),
                letterSpacing = trackingOf(source.letterSpacing),
            ),
            modifier = Modifier.fillMaxWidth().weight(1f),
            outline = source.outline,
            outlineScale = fontScale,
        )
        if (source.referenceText.isNotEmpty()) {
            CurvedText(
                text = source.referenceText,
                curve = source.curve,
                style = TextStyle(
                    color = refColor,
                    fontSize = drawnFontSize(source.referenceFontSize, fontScale),
                    fontFamily = fontFamily,
                    fontWeight = if (source.referenceBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.referenceItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(
                        source.referenceUnderline,
                        source.referenceStrikethrough
                    ),
                    letterSpacing = trackingOf(source.letterSpacing),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((source.referenceFontSize * fontScale * REFERENCE_ROWS).dp),
                outline = source.referenceOutline,
                outlineScale = fontScale,
            )
        }
    }
}

@Composable
internal fun BibleSourceContent(source: SceneSource.BibleSource, modifier: Modifier, fontScale: Float = 1f) {
    val bgColor = if (source.backgroundColor.equals("#00000000", ignoreCase = true))
        Color.Transparent
    else
        parseHexColor(source.backgroundColor)
    val textColor = parseHexColor(source.fontColor)
    val refColor = parseHexColor(source.referenceFontColor)
    val fontFamily = remember(source.fontFamily) { systemFontFamilyOrDefault(source.fontFamily) }
    val align = when (source.horizontalAlignment) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }
    val lineHeightMultiplier = source.lineSpacing / 100f
    val verticalAlign = when (source.verticalAlignment) {
        "top" -> Alignment.TopStart
        "bottom" -> Alignment.BottomStart
        else -> Alignment.Center
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor).clipToBounds(),
        contentAlignment = verticalAlign
    ) {
        if (source.curve != 0f) {
            CurvedBibleText(source, textColor, refColor, fontFamily, fontScale)
            return@Box
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = when (source.horizontalAlignment) {
                "left" -> Alignment.Start
                "right" -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
        ) {
            val versePainter = rememberTextBackdropPainter(source.backdrop, fontScale)
            val refPainter = rememberTextBackdropPainter(source.referenceBackdrop, fontScale)
            OutlinedText(
                text = source.verseText.ifEmpty { stringResource(Res.string.canvas_bible_select_verse) },
                outline = source.outline,
                scaleFactor = fontScale,
                color = if (source.verseText.isEmpty()) Color.Gray else textColor,
                fontSize = drawnFontSize(source.fontSize, fontScale),
                fontFamily = fontFamily,
                style = TextStyle(
                    fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(source.underline, source.strikethrough),
                    letterSpacing = trackingOf(source.letterSpacing),
                ),
                textAlign = align,
                lineHeight = drawnFontSize(source.fontSize, fontScale * lineHeightMultiplier),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .backdropRoom(source.backdrop, fontScale)
                    .then(versePainter.modifier),
                onTextLayout = versePainter::onTextLayout,
            )
            if (source.referenceText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedText(
                    text = source.referenceText,
                    outline = source.referenceOutline,
                    scaleFactor = fontScale,
                    color = refColor,
                    fontSize = drawnFontSize(source.referenceFontSize, fontScale),
                    fontFamily = fontFamily,
                    style = TextStyle(
                        fontWeight = if (source.referenceBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (source.referenceItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = textDecorationOf(
                            source.referenceUnderline,
                            source.referenceStrikethrough,
                        ),
                        letterSpacing = trackingOf(source.letterSpacing),
                    ),
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth()
                        .backdropRoom(source.referenceBackdrop, fontScale)
                        .then(refPainter.modifier),
                    onTextLayout = refPainter::onTextLayout,
                )
            }
        }
    }
}

private const val PERCENT_SCALE = 100f

/** A curve of 100% spends half a circle on the line; more than a full circle would overlap itself. */
private const val MAX_CURVE_TURNS = 2f

/** How much room a bent reference line gets: its own height, plus the room the bend needs. */
private const val REFERENCE_ROWS = 3f
