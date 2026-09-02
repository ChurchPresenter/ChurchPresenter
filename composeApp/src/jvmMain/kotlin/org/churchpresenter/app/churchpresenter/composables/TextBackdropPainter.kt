package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * The two things a `Text` needs to carry a [TextBackdrop]: a modifier that paints it, and the
 * layout callback that tells the modifier where the lines ended up.
 *
 * Kept apart from [BackdropText] because the call sites that most want a backdrop are also the ones
 * that cannot use a wrapper — the presenters measure their own text to fit it, and already pass an
 * `onTextLayout` of their own. Those chain onto this; everything else uses [BackdropText].
 */
@Stable
class TextBackdropPainter internal constructor(private val backdrop: TextBackdrop) {
    private var layout by mutableStateOf<TextLayoutResult?>(null)

    /** Paints the bands and the box, behind whatever the text draws. */
    val modifier: Modifier = if (backdrop.isEmpty) {
        Modifier
    } else {
        Modifier.drawBehind { layout?.let { drawTextBackdrop(it, backdrop) } }
    }

    /** Pass to `Text(onTextLayout = …)`, chaining any callback the call site already had. */
    fun onTextLayout(result: TextLayoutResult) {
        if (!backdrop.isEmpty) layout = result
    }
}

/** Remembers a painter for [backdrop], rebuilding it when the settings behind it change. */
@Composable
fun rememberTextBackdropPainter(backdrop: TextBackdrop): TextBackdropPainter =
    remember(backdrop) { TextBackdropPainter(backdrop) }

/**
 * [Text] with a [TextBackdrop] painted behind it.
 *
 * The parameters are the ones the app's own text call sites use; anything more exotic wants
 * [rememberTextBackdropPainter] and a plain `Text`.
 */
@Suppress("LongParameterList")
@Composable
fun BackdropText(
    text: String,
    backdrop: TextBackdrop,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val painter = rememberTextBackdropPainter(backdrop)
    Text(
        text = text,
        modifier = modifier.then(painter.modifier),
        style = style,
        color = color,
        textAlign = textAlign,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        onTextLayout = { result ->
            painter.onTextLayout(result)
            onTextLayout(result)
        },
    )
}

/** The [AnnotatedString] overload — what the presenters build for letter and word spacing. */
@Suppress("LongParameterList")
@Composable
fun BackdropText(
    text: AnnotatedString,
    backdrop: TextBackdrop,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val painter = rememberTextBackdropPainter(backdrop)
    Text(
        text = text,
        modifier = modifier.then(painter.modifier),
        style = style,
        color = color,
        textAlign = textAlign,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        onTextLayout = { result ->
            painter.onTextLayout(result)
            onTextLayout(result)
        },
    )
}
