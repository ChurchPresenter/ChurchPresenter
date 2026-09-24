package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.text.TextOutline

/**
 * Text with a stroke around its glyphs, drawn as a second copy underneath.
 *
 * Compose has no "fill and stroke" in one style -- `TextStyle.drawStyle` is one or the other -- so
 * an outline is two passes: the same text stroked in [TextOutline.color] first, the ordinary filled
 * text over it. The inner half of the stroke ends up behind the fill, which is what makes the
 * outline read as an even edge rather than as a thickening of the letters.
 *
 * The stroke copy is laid out with `matchParentSize`, so the *fill* is what sizes the box and the
 * outline can never change where a line sits or how tall it is: switching an outline on moves
 * nothing on the slide. It carries the shadow too, the fill having it removed, so a shadow falls
 * behind the outline instead of being drawn twice at different widths.
 *
 * With no outline configured this is a plain `Text` and composes no box at all, so every caller can
 * route through it unconditionally.
 *
 * [onTextLayout] belongs to the fill alone: the backdrop painters that use it frame what the reader
 * sees, and firing it twice per line would have each measurement overwrite the other's.
 */
@Composable
@Suppress("LongParameterList")
internal fun OutlinedText(
    text: AnnotatedString,
    outline: TextOutline,
    /** The output's reference-to-pixel scale, which the stroke width is stored against. */
    scaleFactor: Float,
    color: Color,
    fontSize: TextUnit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
    /**
     * Whether the text takes the whole width it is offered, which is what makes [textAlign] place it
     * across the slide. False for text pinned somewhere of its own -- a cornered song number -- where
     * filling the width would drag it back out of its corner.
     */
    fillWidth: Boolean = true,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val widthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    if (!outline.isVisible) {
        Text(
            modifier = modifier.then(widthModifier),
            text = text,
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign,
            softWrap = softWrap,
            lineHeight = lineHeight,
            overflow = overflow,
            maxLines = maxLines,
            style = style,
            onTextLayout = onTextLayout,
        )
        return
    }
    Box(modifier = modifier) {
        val strokeColor = parseHexColor(outline.color)
        Text(
            modifier = Modifier.matchParentSize(),
            // Recoloured run by run: a span's own colour beats `color` below, so text built from
            // coloured runs -- captions, a highlighted word -- stroked itself in its fill colour and
            // the outline colour did nothing at all.
            text = text.inColor(strokeColor),
            color = strokeColor,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign,
            softWrap = softWrap,
            lineHeight = lineHeight,
            overflow = overflow,
            maxLines = maxLines,
            style = style.copy(drawStyle = Stroke(width = outline.width * scaleFactor)),
        )
        Text(
            modifier = widthModifier,
            text = text,
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign,
            softWrap = softWrap,
            lineHeight = lineHeight,
            overflow = overflow,
            maxLines = maxLines,
            style = style.copy(shadow = null),
            onTextLayout = onTextLayout,
        )
    }
}

/**
 * [OutlinedText] for a plain string, which is what most of the surfaces outside the song presenter
 * draw. Same two passes, same fallback to a single `Text` when no outline is configured.
 */
@Composable
@Suppress("LongParameterList")
internal fun OutlinedText(
    text: String,
    outline: TextOutline,
    scaleFactor: Float,
    color: Color,
    fontSize: TextUnit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
    fillWidth: Boolean = true,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) = OutlinedText(
    text = AnnotatedString(text),
    outline = outline,
    scaleFactor = scaleFactor,
    color = color,
    fontSize = fontSize,
    style = style,
    modifier = modifier,
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontStyle = fontStyle,
    textAlign = textAlign,
    softWrap = softWrap,
    fillWidth = fillWidth,
    lineHeight = lineHeight,
    overflow = overflow,
    maxLines = maxLines,
    onTextLayout = onTextLayout,
)

/**
 * This text with every span drawn in [color], keeping everything else each span sets -- weight,
 * style, decoration -- so the stroke copy lines up with the fill glyph for glyph.
 */
internal fun AnnotatedString.inColor(color: Color): AnnotatedString {
    if (spanStyles.none { it.item.color.isSpecified }) return this
    return AnnotatedString(
        text = text,
        spanStyles = spanStyles.map { range -> range.copy(item = range.item.copy(color = color)) },
        paragraphStyles = paragraphStyles,
    )
}
