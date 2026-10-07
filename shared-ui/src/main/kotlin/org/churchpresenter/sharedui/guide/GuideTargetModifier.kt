package org.churchpresenter.sharedui.guide

import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * Tags this control as [target], so the helper can ring it.
 *
 * Reports the control's bounds to the window's [LocalGuideTargetRegistry] and tells the
 * [LocalGuideSession] when it is pressed. Observes the press without consuming it — the control
 * still gets its click. When the session points at it, it scrolls itself into view, so a ring on a
 * control below the fold is never drawn off-screen. Does nothing at all outside a spotlight host.
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
    PointerInputModifierNode,
    ObserverModifierNode,
    CompositionLocalConsumerModifierNode {

    // Kept from the last report: composition locals cannot be read once the node is detaching.
    private var registry: GuideTargetRegistry? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val current = currentValueOf(LocalGuideTargetRegistry) ?: return
        registry = current
        current.report(target, coordinates.boundsInRoot())
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press) {
            currentValueOf(LocalGuideSession)?.pressed(target)
        }
    }

    override fun onCancelPointerInput() = Unit

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
