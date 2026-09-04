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
 *
 * **The settings are state on one long-lived painter, not a new painter per setting.** A painter
 * draws from the last [TextLayoutResult] its `Text` handed it, and a `Text` only produces one when
 * something it actually measures changes — turning a backdrop on changes nothing it measures. So a
 * painter rebuilt on every settings change starts with no layout and never gets one: the backdrop
 * silently does not appear until the text itself happens to change. For the same reason the
 * `drawBehind` is always installed and the emptiness check happens inside it, rather than the
 * modifier being `Modifier` while the backdrop is off — a modifier that appears later would have
 * the same problem.
 */
@Stable
class TextBackdropPainter internal constructor() {
    private var layout by mutableStateOf<TextLayoutResult?>(null)

    internal var backdrop by mutableStateOf(TextBackdrop())
    internal var scale by mutableStateOf(1f)

    /** Paints the bands and the box, behind whatever the text draws. */
    val modifier: Modifier = Modifier.drawBehind {
        val current = backdrop
        if (!current.isEmpty) layout?.let { drawTextBackdrop(it, current, scale) }
    }

    /** Pass to `Text(onTextLayout = …)`, chaining any callback the call site already had. */
    fun onTextLayout(result: TextLayoutResult) {
        layout = result
    }
}

/**
 * Remembers a painter for [backdrop].
 *
 * [scale] shrinks every measurement by the same factor the call site shrank its font size by — for
 * the tab previews, which draw the presenter's text inside a thumbnail. Leave it at 1 wherever the
 * text is drawn at the size it was configured for.
 */
@Composable
fun rememberTextBackdropPainter(backdrop: TextBackdrop, scale: Float = 1f): TextBackdropPainter {
    val painter = remember { TextBackdropPainter() }
    painter.backdrop = backdrop
    painter.scale = scale
    return painter
}

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
