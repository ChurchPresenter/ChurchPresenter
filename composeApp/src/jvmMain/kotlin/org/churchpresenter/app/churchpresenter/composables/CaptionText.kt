package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline

/**
 * Shows text clipped to the last N lines. Content is bottom-aligned —
 * when text exceeds maxLines, old lines are clipped off the top.
 * The text is shifted upward so the last line sits at the bottom of the clip area.
 *
 * Shared between `STTPresenter` (live captions) and `SubtitleOverlay` (video subtitles) — one
 * rendering of "styled, backdropped, outlined caption text" rather than two copies drifting apart.
 */
@Composable
fun BottomAlignedText(
    text: AnnotatedString,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    backdrop: TextBackdrop = TextBackdrop(),
    outline: TextOutline = TextOutline(),
    // The outline's stroke follows the same reference-resolution scale as [style]'s own font size,
    // so a caller drawing at other than 1x (see `presenterScale`) keeps the two in proportion.
    // 1f (the default) is right for a caller that never scales, e.g. STTPresenter today.
    scaleFactor: Float = 1f,
) {
    // The painter goes on the content text in both branches, never on the invisible reference
    // below: that one exists to measure a fixed number of lines, and banding it would paint a
    // block of empty lines behind the captions.
    val painter = rememberTextBackdropPainter(backdrop)
    if (maxLines <= 0) {
        OutlinedText(
            text = text,
            outline = outline,
            scaleFactor = scaleFactor,
            color = Color.Unspecified,
            fontSize = TextUnit.Unspecified,
            style = style,
            modifier = modifier.fillMaxWidth().then(painter.modifier),
            onTextLayout = painter::onTextLayout,
        )
        return
    }

    // Reference text with exactly maxLines lines — measured to get precise pixel height
    val referenceText = remember(maxLines) { "\n".repeat(maxLines - 1).ifEmpty { " " } }

    Layout(
        content = {
            // Invisible reference: measures exact height of maxLines lines
            Text(
                text = referenceText,
                style = style,
                modifier = Modifier.fillMaxWidth(),
                maxLines = maxLines
            )
            // Actual content: measured unconstrained. One measurable either way -- an outlined
            // draw is a box holding both passes, and the layout below indexes by position.
            OutlinedText(
                text = text,
                outline = outline,
                scaleFactor = scaleFactor,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                modifier = Modifier.fillMaxWidth().then(painter.modifier),
                onTextLayout = painter::onTextLayout,
            )
        },
        modifier = modifier.clipToBounds()
    ) { measurables, constraints ->
        val unconstrainedConstraints = Constraints(
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity
        )
        // Measure reference to get exact N-line height
        val refPlaceable = measurables[0].measure(unconstrainedConstraints)
        val clipHeightPx = refPlaceable.height

        // Measure actual text at full height
        val textPlaceable = measurables[1].measure(unconstrainedConstraints)

        val reportedHeight = clipHeightPx.coerceAtMost(textPlaceable.height)
        layout(constraints.maxWidth, reportedHeight) {
            // Place text bottom-aligned: shift up so last lines are visible
            val y = reportedHeight - textPlaceable.height
            textPlaceable.place(0, y.coerceAtMost(0))
            // Don't place reference — it's just for measurement
        }
    }
}
