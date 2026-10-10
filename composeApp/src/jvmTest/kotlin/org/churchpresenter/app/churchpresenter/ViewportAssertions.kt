@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.churchpresenter.profiles.SETTINGS_PREVIEW_SCALED_TAG
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp

/**
 * Measures what a fixed-size surface draws against the edges it is given, for the settings tabs'
 * sideways and downwards checks. The dialogs' own height check, which measures intrinsic height
 * instead, is `:dialogs`' copy of this file.
 */
private const val VIEWPORT_TAG = "viewport-under-test"

/** Carries the composition's density back out, so a px measurement can be read back in dp. */
internal class ViewportProbe {
    var density: Float = 1f
}

/**
 * Composes [content] into a surface of exactly [width] x [height], for the sideways check below.
 *
 * Deliberately a fixed box rather than the intrinsic measurement the dialogs' height check uses. Two reasons, and
 * the first alone is decisive:
 *
 *  - **Width overflow is visible in positions, where height overflow is not.** A `Column` given a
 *    `maxHeight` squeezes its children into it; a `Row` does no such thing — unweighted children
 *    keep the width they asked for and are simply placed past the edge. So the containment check
 *    that reads as vacuous for height is exactly right for width, and is what lateral clipping
 *    actually looks like.
 *  - `IntrinsicSize.Min` on width over a whole settings tab does not return in any practical time.
 *    Intrinsic measurement re-measures the subtree per query, and these tabs nest deeply enough for
 *    that to become unusable — a probe over twelve of them was killed after 17 minutes with nothing
 *    reported. Nothing in this file may use it for width.
 */
@Composable
internal fun FixedViewport(
    width: Dp,
    height: Dp,
    probe: ViewportProbe,
    content: @Composable () -> Unit,
) {
    probe.density = LocalDensity.current.density
    Box(
        modifier = Modifier
            .size(width, height)
            .testTag(VIEWPORT_TAG),
    ) {
        content()
    }
}

/**
 * How far past its container's right edge the widest *unreachable* node reaches, in dp; `0` when
 * nothing does.
 *
 * **Anything inside a horizontally scrollable container is skipped, subtree and all.** Content that
 * extends past the viewport of something that scrolls is not clipped — it is one gesture away, which
 * is the same criterion that decides which dialogs this file measures at all. Without that
 * exclusion the settings dialog's `PrimaryScrollableTabRow` dominates every reading: its twelve tab
 * labels are wider than the dialog by design, and the measured overhang just tracks how far the row
 * happens to be scrolled (281dp with the first tab selected, falling to 0 by the last) rather than
 * anything about the tab being measured.
 *
 * Zero-sized nodes are skipped too, as elsewhere here: a section that is legitimately absent
 * measures `0x0` and cannot be clipped off an edge.
 */
internal fun ComposeUiTest.horizontalOverflow(probe: ViewportProbe): Dp {
    val viewport = onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode()
    val edge = viewport.positionInRoot.x + viewport.size.width

    var worst = 0f
    fun walk(node: SemanticsNode) {
        if (node.config.contains(SemanticsProperties.HorizontalScrollAxisRange)) return
        // The settings previews render the real presenter at the output's own size and scale the
        // drawn result into a few hundred dp. Their nodes report the size they were measured at, so
        // left in they read as 1600dp of overflow -- while nothing in them is drawn outside the
        // preview box at all. See SETTINGS_PREVIEW_SCALED_TAG.
        if (node.config.getOrNull(SemanticsProperties.TestTag) == SETTINGS_PREVIEW_SCALED_TAG) return
        if (node.size.width > 0 && node.size.height > 0) {
            worst = maxOf(worst, node.positionInRoot.x + node.size.width - edge)
        }
        node.children.forEach(::walk)
    }
    viewport.children.forEach(::walk)

    return Dp(worst / probe.density)
}

/**
 * How far past its container's bottom edge the tallest *unreachable* node reaches, in dp; `0` when
 * nothing does. The vertical counterpart of [horizontalOverflow] -- see its own doc for why the
 * unscrollable, unclipped fixed box this reads (composed via [FixedViewport]) is what makes an
 * overflowing node report a position outside its bounds rather than being silently squeezed to fit.
 */
internal fun ComposeUiTest.verticalOverflow(probe: ViewportProbe): Dp {
    val viewport = onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode()
    val edge = viewport.positionInRoot.y + viewport.size.height

    var worst = 0f
    fun walk(node: SemanticsNode) {
        if (node.config.contains(SemanticsProperties.VerticalScrollAxisRange)) return
        if (node.config.getOrNull(SemanticsProperties.TestTag) == SETTINGS_PREVIEW_SCALED_TAG) return
        if (node.size.width > 0 && node.size.height > 0) {
            worst = maxOf(worst, node.positionInRoot.y + node.size.height - edge)
        }
        node.children.forEach(::walk)
    }
    viewport.children.forEach(::walk)

    return Dp(worst / probe.density)
}
