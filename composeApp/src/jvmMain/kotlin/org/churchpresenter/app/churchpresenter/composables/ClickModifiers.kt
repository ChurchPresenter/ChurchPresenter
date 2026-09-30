package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement

/** Two releases closer together than this are a double-click. */
private const val DOUBLE_CLICK_MS = 300L

/**
 * Drop-in replacement for [Modifier.clickable] that processes click events at
 * [PointerEventPass.Initial] — before LazyColumn's scroll gesture can consume them.
 *
 * On ARM Mac, the default [PointerEventPass.Main] used by `.clickable` lets
 * the scroll handler eat pointer-up events before the item click handler fires.
 */
fun Modifier.initialPassClickable(onClick: () -> Unit): Modifier =
    this then PassClickElement(PointerEventPass.Initial, combined = false, onClick, onDoubleClick = null)

/**
 * Background-click handler that fires only when no child composable consumed the event.
 *
 * Uses [PointerEventPass.Final] which runs after Main, so child [Modifier.clickable] handlers
 * (IconButton, etc.) that consume in Main pass will prevent this from firing. Use this on a
 * container Row/Box when you want "click anywhere empty in the row" behavior without blocking
 * child interactions.
 */
fun Modifier.finalPassClickable(onClick: () -> Unit): Modifier =
    this then PassClickElement(PointerEventPass.Final, combined = false, onClick, onDoubleClick = null)

/**
 * Drop-in replacement for [Modifier.combinedClickable] that processes click events at
 * [PointerEventPass.Initial] with double-click detection (300 ms threshold).
 */
fun Modifier.initialPassCombinedClickable(
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
): Modifier =
    this then PassClickElement(PointerEventPass.Initial, combined = true, onClick, onDoubleClick)

/**
 * Combined-click counterpart to [finalPassClickable] — fires only when no child composable
 * consumed the event, with the same double-click detection (300 ms threshold) as
 * [initialPassCombinedClickable]. Use this on a container Row/Column that needs both click and
 * double-click handling (e.g. select / go-live) while still letting nested clickable children
 * (an [initialPassClickable] line, an IconButton, etc.) win the hit-test for their own clicks.
 */
fun Modifier.finalPassCombinedClickable(
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
): Modifier =
    this then PassClickElement(PointerEventPass.Final, combined = true, onClick, onDoubleClick)

/**
 * The four modifiers above, as one node.
 *
 * A node rather than `pointerInput(onClick, onDoubleClick)`, because keying the gesture on the
 * callbacks restarted it whenever a caller passed a new lambda -- which a recomposition between the
 * two clicks of a double-click does -- and the restart forgot the first click. Here a new lambda
 * only replaces the one the running gesture calls, the way [Modifier.clickable] behaves.
 */
private data class PassClickElement(
    val pass: PointerEventPass,
    val combined: Boolean,
    val onClick: () -> Unit,
    val onDoubleClick: (() -> Unit)?,
) : ModifierNodeElement<PassClickNode>() {
    override fun create() = PassClickNode(pass, combined, onClick, onDoubleClick)

    override fun update(node: PassClickNode) = node.update(pass, combined, onClick, onDoubleClick)
}

private class PassClickNode(
    private var pass: PointerEventPass,
    private var combined: Boolean,
    private var onClick: () -> Unit,
    private var onDoubleClick: (() -> Unit)?,
) : DelegatingNode() {
    private val input = delegate(SuspendingPointerInputModifierNode { detectClicks() })

    fun update(pass: PointerEventPass, combined: Boolean, onClick: () -> Unit, onDoubleClick: (() -> Unit)?) {
        this.onClick = onClick
        this.onDoubleClick = onDoubleClick
        if (pass != this.pass || combined != this.combined) {
            this.pass = pass
            this.combined = combined
            input.resetPointerInputHandler()
        }
    }

    private suspend fun PointerInputScope.detectClicks() {
        var lastClickTime = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(pass)
                if (event.type == PointerEventType.Release &&
                    event.changes.any { !it.isConsumed }
                ) {
                    event.changes.forEach { it.consume() }
                    if (!combined) {
                        onClick()
                        continue
                    }
                    val now = System.currentTimeMillis()
                    val isDouble = now - lastClickTime < DOUBLE_CLICK_MS
                    lastClickTime = now
                    val doubleClick = onDoubleClick
                    if (isDouble && doubleClick != null) {
                        doubleClick()
                    } else {
                        onClick()
                    }
                }
            }
        }
    }
}
