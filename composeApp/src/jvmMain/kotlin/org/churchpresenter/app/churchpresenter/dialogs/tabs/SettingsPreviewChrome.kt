package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings

/**
 * Chrome shared by the Song and Bible settings previews.
 *
 * Both tabs draw the same picture -- the real presenter, scaled down, over a dark plate, with a
 * dashed margin guide and a corner badge -- and both used to carry their own copy of every part of
 * it, including two separate height caps and seven duplicated constants. One copy here; the two
 * preview files supply only what differs, which is the settings the guide reads and the content the
 * presenter draws.
 */

/** The band's height is stored as a whole percentage of the output. */
private const val PERCENT = 100f

internal const val PREVIEW_BACKGROUND = 0xFF08090B
private const val MARGIN_GUIDE_ALPHA = 0.5f
private const val GUIDE_STROKE_PX = 1f
private const val GUIDE_DASH_PX = 4f
private const val BADGE_ALPHA = 0.55f
private const val BADGE_TEXT_ALPHA = 0.75f

/** Fallback geometry for an output that has not reported its own bounds. */
private const val DEFAULT_OUTPUT_WIDTH = 1920
private const val DEFAULT_OUTPUT_HEIGHT = 1080

/**
 * How tall either preview is allowed to get.
 *
 * The dialog is at most 1400x900 and rather less on a 1366x768 laptop, and the controls under the
 * preview need roughly 250dp of it. Left to fill the pane's width the preview would take more than
 * the whole remainder, so it is capped here and centred in the space instead.
 */
internal val SETTINGS_PREVIEW_MAX_HEIGHT = 260.dp

/**
 * Marks the scaled presenter subtree, which is measured at the output's size and drawn at a
 * fraction of it.
 *
 * Its nodes therefore *report* an output-sized layout while occupying a few hundred dp of the
 * dialog, and nothing in it is drawn outside the preview box -- see [ScaledPresenterBox]. The
 * viewport assertions skip it for that reason, exactly as they skip a horizontally scrollable
 * subtree: neither is content the operator can find off the edge of the dialog.
 */
const val SETTINGS_PREVIEW_SCALED_TAG = "settingsPreviewScaledContent"

/** The resolution the styling is being designed against, in the output's own pixels. */
internal data class PreviewOutputSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/**
 * The screen this styling actually lands on.
 *
 * The first assigned output that has reported its bounds, so a 4:3 projector or a 2560x1080
 * ultrawide is previewed at its own shape rather than assumed to be 16:9. With nothing assigned
 * yet, or nothing that has reported, 1920x1080 stands in.
 */
internal fun previewOutputSize(settings: AppSettings): PreviewOutputSize {
    val assigned = settings.projectionSettings.screenAssignments
        .firstOrNull { it.targetBoundsW > 0 && it.targetBoundsH > 0 }
    return if (assigned != null) {
        PreviewOutputSize(assigned.targetBoundsW, assigned.targetBoundsH)
    } else {
        PreviewOutputSize(DEFAULT_OUTPUT_WIDTH, DEFAULT_OUTPUT_HEIGHT)
    }
}

/**
 * Measures [content] at [output]'s own pixel size and scales the drawn result into the box.
 *
 * **Why not simply hand the presenter the small box.** Both presenters derive their scale as
 * `min(maxWidth.toPx() / 1920, maxHeight.toPx() / 1080).coerceIn(0.5f, 3.0f)`, and a preview capped
 * at [SETTINGS_PREVIEW_MAX_HEIGHT] is never wider than about 460dp -- a true scale of 0.24 to 0.48,
 * *always* under that 0.5 floor. So the earlier version of this, which passed the small box through
 * at `Density(1f)`, got the clamped 0.5 every time: type, margins and the bottom inset all came out
 * between 4% and 108% too large for the box, lyrics overflowed the bottom, and the dashed guide --
 * drawn from the true, unclamped fractions -- marked a boundary the text no longer observed.
 *
 * Measuring at the output's own size instead makes the presenter's scale exactly 1.0, so the clamp
 * never engages and the margins, the auto-fit (which computes in the reference 1920x1080 space) and
 * the guide agree with the real screen by construction.
 *
 * The [layout] block reports the **scaled** size, so no output-sized node is left in the dialog's
 * layout tree -- which is what an earlier note here worried about, and is exactly how
 * `LivePreviewPanel.ScaledPresenterContent` has always done it. [LocalDensity] is pinned to 1 so a
 * measured pixel is a unit, which is what makes "the output's own pixel size" the size it is given.
 */
@Composable
internal fun ScaledPresenterBox(output: PreviewOutputSize, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SETTINGS_PREVIEW_SCALED_TAG)
            .layout { measurable, constraints ->
                val scale = minOf(
                    constraints.maxWidth.toFloat() / output.width,
                    constraints.maxHeight.toFloat() / output.height,
                )
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = output.width,
                        maxWidth = output.width,
                        minHeight = output.height,
                        maxHeight = output.height,
                    ),
                )
                layout((output.width * scale).toInt(), (output.height * scale).toInt()) {
                    placeable.placeWithLayer(0, 0) {
                        this.scaleX = scale
                        this.scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            content()
        }
    }
}

/** The four insets a presenter pads by, in the output's own reference pixels. */
internal data class PreviewMargins(val left: Int, val right: Int, val top: Int, val bottom: Int)

/**
 * The dashed rectangle marking where text is actually allowed to go.
 *
 * The presenters respect the projection window insets and their own margins but draw nothing to
 * show them, so the guide is the preview's own -- and it has to agree with the presenter's geometry
 * exactly or it marks a boundary nothing observes. On the lower third that boundary is the band:
 * the presenter takes [bandPercent] of the *inset* height and sits it on the floor of that area, so
 * the guide follows the band rather than the whole screen.
 *
 * [bandPercent] is a parameter rather than something read here, because the two tabs no longer share
 * one: it is `bibleSettings.lowerThirdHeightPercent` for one caller and `songSettings`' for the
 * other, and this composable has no business knowing which of them it is drawing for.
 *
 * Drawn outside [ScaledPresenterBox], not inside it: within the scaled layer a 1dp line is scaled
 * down with everything else and comes out a quarter of a pixel wide. Expressed as fractions of the
 * output instead, which the box matches exactly because it carries the output's own aspect ratio.
 */
@Composable
internal fun MarginGuide(
    output: PreviewOutputSize,
    settings: AppSettings,
    margins: PreviewMargins,
    bandPercent: Int,
    lowerThird: Boolean,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = MARGIN_GUIDE_ALPHA),
) {
    val projection = settings.projectionSettings
    val leftFraction = (projection.windowLeft + margins.left).toFloat() / output.width
    val rightFraction = (projection.windowRight + margins.right).toFloat() / output.width
    val topFraction = (projection.windowTop + margins.top).toFloat() / output.height
    val bottomFraction = (projection.windowBottom + margins.bottom).toFloat() / output.height
    val insetHeightFraction = (1f - topFraction - bottomFraction).coerceAtLeast(0f)
    val guideTopFraction = if (lowerThird) {
        1f - bottomFraction - insetHeightFraction * bandPercent / PERCENT
    } else {
        topFraction
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = size.width * leftFraction
        val top = size.height * guideTopFraction
        val width = size.width * (1f - leftFraction - rightFraction)
        val height = size.height * (1f - guideTopFraction - bottomFraction)
        if (width <= 0f || height <= 0f) return@Canvas
        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(
                width = GUIDE_STROKE_PX,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(GUIDE_DASH_PX, GUIDE_DASH_PX)),
            ),
        )
    }
}

/** Which of the two outputs this picture is of, said in the corner rather than only in the switch. */
@Composable
internal fun PreviewBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = BADGE_ALPHA), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = BADGE_TEXT_ALPHA),
        )
    }
}
