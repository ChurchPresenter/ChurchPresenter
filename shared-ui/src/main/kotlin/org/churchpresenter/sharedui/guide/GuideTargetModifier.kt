package org.churchpresenter.sharedui.guide

import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.launch

/**
 * Tags this control as [target], so the helper can ring it.
 *
 * Reports the control's bounds to the window's [LocalGuideTargetRegistry]; the window's spotlight
 * host reads them to ring the control and to tell the [LocalGuideSession] when it is pressed. When
 * the session points at it, it scrolls itself into view, so a ring on a control below the fold is
 * never drawn off-screen. Does nothing at all outside a spotlight host.
 */
fun Modifier.guideTarget(target: GuideTarget): Modifier = this then GuideTargetElement(target)

private data class GuideTargetElement(val target: GuideTarget) : ModifierNodeElement<GuideTargetNode>() {
    override fun create() = GuideTargetNode(target)

    override fun update(node: GuideTargetNode) {
        if (node.target != target) {
            node.forget()
            node.target = target
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "guideTarget"
        properties["target"] = target.id
    }
}

private class GuideTargetNode(var target: GuideTarget) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    ObserverModifierNode,
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {

    // Kept from the last report: composition locals cannot be read once the node is detaching.
    private var registry: GuideTargetRegistry? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val current = currentValueOf(LocalGuideTargetRegistry) ?: return
        registry = current
        current.report(target, coordinates.boundsInRoot())
    }

    /**
     * While the session points at this control, a tint and an outline inside its own edges — what
     * still shows where the window's ring around it is clipped by a scrolling list or covered by an
     * open menu. Read in draw, so it comes and goes with the step without a recomposition.
     */
    override fun ContentDrawScope.draw() {
        drawContent()
        val color = currentValueOf(LocalGuideRingColor)
        if (color.isUnspecified || currentValueOf(LocalGuideSession)?.activeTarget != target) return
        val corner = CornerRadius(HIGHLIGHT_CORNER.toPx())
        drawRoundRect(color.copy(alpha = HIGHLIGHT_FILL_ALPHA), cornerRadius = corner)
        drawRoundRect(color, cornerRadius = corner, style = Stroke(HIGHLIGHT_STROKE.toPx()))
    }

    override fun onAttach() = scrollIntoViewIfActive()

    override fun onObservedReadsChanged() = scrollIntoViewIfActive()

    /** Brings this control into view whenever the session starts pointing at it. */
    private fun scrollIntoViewIfActive() {
        var active = false
        observeReads { active = currentValueOf(LocalGuideSession)?.activeTarget == target }
        if (active) coroutineScope.launch { bringIntoView() }
    }

    override fun onDetach() = forget()

    fun forget() {
        registry?.remove(target)
        registry = null
    }
}

private val HIGHLIGHT_CORNER = 6.dp
private val HIGHLIGHT_STROKE = 2.dp
private const val HIGHLIGHT_FILL_ALPHA = 0.16f
