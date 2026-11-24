package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Two releases closer together than this are a double-click. */
private const val DOUBLE_CLICK_MILLIS = 300L

/**
 * The controls inside a clickable container, so the container can leave their clicks to them.
 *
 * A container and its controls both take clicks at the initial pass, where the container sees a
 * release first: without this it would take the click meant for a button inside it.
 */
internal class ClickTargets {
    var self: LayoutCoordinates? = null
    val children = mutableListOf<ClickTarget>()

    fun hitsChild(position: Offset): Boolean {
        val container = self?.takeIf { it.isAttached } ?: return false
        return children.any { child ->
            val coordinates = child.coordinates?.takeIf { it.isAttached } ?: return@any false
            container.localBoundingBoxOf(coordinates, clipBounds = false).contains(position)
        }
    }
}

/** One control's place, kept up to date while it is laid out. */
internal class ClickTarget {
    var coordinates: LayoutCoordinates? = null
}

internal val LocalClickTargets = staticCompositionLocalOf<ClickTargets?> { null }

/** Gives [content] to a container whose clicks yield to the controls inside it. */
@Composable
internal fun ProvideClickTargets(targets: ClickTargets, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalClickTargets provides targets, content = content)
}

/**
 * Fires [onClick] on a release taken at the initial pass, before a scrolling list's gesture can use
 * it up — on ARM Macs a plain `clickable` inside a list can lose its release to the scroll. Put it
 * just before the `clickable`, `toggleable` or `selectable` that keeps the keyboard, focus,
 * screen-reader action and pressed look: that one then sees the release already used and does not
 * fire again.
 *
 * A double-click fires [onClick] on the first release and [onDoubleClick] on the second.
 *
 * @param container set on a row that holds controls: its clicks then skip a release over any of
 * them, and its content must be wrapped in [ProvideClickTargets] with the same value.
 */
@Composable
internal fun Modifier.firstPassClick(
    enabled: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    container: ClickTargets? = null,
): Modifier {
    val parent = LocalClickTargets.current.takeIf { it !== container }
    val click by rememberUpdatedState(onClick)
    val double by rememberUpdatedState(onDoubleClick)
    val active by rememberUpdatedState(enabled)
    val target = remember { ClickTarget() }
    if (parent != null) {
        DisposableEffect(parent, target) {
            parent.children += target
            onDispose { parent.children -= target }
        }
    }
    return this
        .onGloballyPositioned {
            target.coordinates = it
            container?.self = it
        }
        .pointerInput(Unit) {
            var lastClick = 0L
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null && takesRelease(event.type, change, active, container)) {
                        change.consume()
                        val now = change.uptimeMillis
                        val second = double
                        if (second != null && lastClick != 0L && now - lastClick < DOUBLE_CLICK_MILLIS) {
                            lastClick = 0L
                            second()
                        } else {
                            lastClick = now
                            click()
                        }
                    }
                }
            }
        }
}

/** Whether this release is ours: unused, inside the control, and not over a control the container holds. */
private fun PointerInputScope.takesRelease(
    type: PointerEventType,
    change: PointerInputChange,
    active: Boolean,
    container: ClickTargets?,
): Boolean {
    val inside = change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()
    return type == PointerEventType.Release && !change.isConsumed && inside && active &&
        container?.hitsChild(change.position) != true
}
