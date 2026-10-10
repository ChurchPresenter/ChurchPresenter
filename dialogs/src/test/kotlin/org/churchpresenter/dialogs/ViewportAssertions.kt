@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/**
 * Asserts that what a fixed-size surface draws actually fits in the height it declares.
 *
 * Six dialogs declare a hard `rememberDialogState(width = …, height = …)`, most of them
 * `resizable = false`, and hold no scroll container. Content that needs more room than that gets
 * none: it is cut off with no scrollbar, so nothing on screen indicates anything is missing. The
 * most likely way to outgrow the window is translation — English ships the shortest string of the
 * fourteen locales — but a longer song title or host name does it too.
 *
 * **This measures minimum intrinsic height, and it has to.** Two more obvious approaches both fail
 * silently, each verified against `AddLabelDialog` at the 400dp its own height comment records as
 * broken:
 *
 *  - *Compose into a box of the declared size and look for nodes lying outside it.* `Modifier.size`
 *    hands the content a `maxHeight`, and Compose honours it: children are measured against the
 *    space that is left rather than spilling past the edge, so every node reports a position inside
 *    the box while the text inside them is squeezed and clipped. Nothing is ever outside the box,
 *    which is precisely why this defect is invisible on screen too. Passes at 400dp.
 *  - *Compose at the real width with unbounded height and read where the content settles.* A child
 *    with `Modifier.weight(1f)` — which this editor's content column has — is dividing up leftover
 *    space, and there is no meaningful way to divide an infinite height, so it collapses. Reported
 *    210dp for content that needs 474dp, and passes at 400dp for that reason.
 *
 * `IntrinsicSize.Min` asks each child how much height it needs at this width and resolves weights
 * against the answer, which is the question actually being asked. The cost is that it requires the
 * content to support intrinsic measurement; a composable that does not will throw rather than
 * mislead, which is the right failure.
 */

private const val VIEWPORT_TAG = "viewport-under-test"

/** Carries the composition's density back out, so a px measurement can be read back in dp. */
internal class ViewportProbe {
    var density: Float = 1f
}

/**
 * Composes [content] at exactly [width], sized to the minimum height that content can accept.
 *
 * Width is fixed because it is what wraps the text, and text wrapping is the whole mechanism by
 * which a long string becomes a tall layout. Height is resolved by intrinsic measurement because it
 * is the answer being asked for.
 *
 * [fontScale] scales every `sp`-sized piece of text, which is how a dialog whose text is entirely
 * fixed string resources can still be tested for growth. See [TEXT_GROWTH_SCALE].
 */
@Composable
internal fun Viewport(
    width: Dp,
    probe: ViewportProbe,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    probe.density = base.density
    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
        Box(
            modifier = Modifier
                .width(width)
                .height(IntrinsicSize.Min)
                .testTag(VIEWPORT_TAG),
        ) {
            content()
        }
    }
}

/**
 * The growth factor a fixed-size dialog is expected to absorb.
 *
 * Several of these dialogs draw nothing but fixed string resources, so there is no parameter to pass
 * a longer string through — the only way their text grows is translation, and this suite must not
 * read the non-English locale files (`AGENT.md`: they are managed separately). Gating CI on their
 * contents would also mean a translator lengthening a sentence breaks the build, which is the wrong
 * place to catch it.
 *
 * Scaling the font instead grows the text without inventing any, and it is not merely a stand-in:
 * desktop OS font scaling is a real setting real users have on, so a dialog that cannot absorb 1.3x
 * is already broken for them regardless of language.
 *
 * The figure is the rule of thumb for translation expansion out of English — English is the most
 * compact of the fourteen locales shipped, and German and the Slavic languages commonly run ~30%
 * longer. It is an approximation of length growth by height growth, not a substitute for rendering
 * the real strings, and the dialogs that only get this treatment say so.
 */
internal const val TEXT_GROWTH_SCALE = 1.3f

/**
 * How tall the content laid out inside [Viewport] actually is, in dp.
 *
 * Taken as the furthest bottom edge reached by any descendant rather than the box's own reported
 * size: an intermediate wrapper can still be coerced by the harness root, but a child's
 * [SemanticsNode.positionInRoot] is unclipped and its [SemanticsNode.size] is what it measured to,
 * so the deepest bottom edge is the honest answer. Zero-sized nodes are skipped — a section that is
 * legitimately absent measures `0x0`, the same distinction `StepperArrows` had to make.
 */
internal fun ComposeUiTest.measuredContentHeight(probe: ViewportProbe): Dp {
    val viewport = onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode()
    val top = viewport.positionInRoot.y

    var bottom = viewport.size.height.toFloat()
    fun walk(node: SemanticsNode) {
        if (node.size.width > 0 && node.size.height > 0) {
            bottom = maxOf(bottom, node.positionInRoot.y + node.size.height - top)
        }
        node.children.forEach(::walk)
    }
    viewport.children.forEach(::walk)

    return Dp(bottom / probe.density)
}

/**
 * Asserts the content composed in [Viewport] fits within [declared] — the height its dialog gives it.
 *
 * [tolerance] absorbs the sub-pixel difference between a dp-declared window and a px-measured
 * layout. It is deliberately small: this is forgiving rounding, not slack.
 */
internal fun ComposeUiTest.assertFitsDeclaredHeight(
    declared: Dp,
    probe: ViewportProbe,
    tolerance: Dp = Dp(1f),
) {
    val needed = measuredContentHeight(probe)
    assertTrue(
        needed <= declared + tolerance,
        "content needs ${needed.value.roundToInt()}dp but its window opens only " +
            "${declared.value.roundToInt()}dp tall, so the bottom " +
            "${(needed - declared).value.roundToInt()}dp is off screen the moment it opens",
    )
}
