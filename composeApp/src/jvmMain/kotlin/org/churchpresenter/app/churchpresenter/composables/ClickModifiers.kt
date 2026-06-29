package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Drop-in replacement for [Modifier.clickable] that processes click events at
 * [PointerEventPass.Initial] — before LazyColumn's scroll gesture can consume them.
 *
 * On ARM Mac, the default [PointerEventPass.Main] used by `.clickable` lets
 * the scroll handler eat pointer-up events before the item click handler fires.
 */
fun Modifier.initialPassClickable(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Release &&
                    event.changes.any { !it.isConsumed }
                ) {
                    event.changes.forEach { it.consume() }
                    onClick()
                }
            }
        }
    }

/**
 * Background-click handler that fires only when no child composable consumed the event.
 *
 * Uses [PointerEventPass.Final] which runs after Main, so child [Modifier.clickable] handlers
 * (IconButton, etc.) that consume in Main pass will prevent this from firing. Use this on a
 * container Row/Box when you want "click anywhere empty in the row" behavior without blocking
 * child interactions.
 */
fun Modifier.finalPassClickable(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.type == PointerEventType.Release &&
                    event.changes.any { !it.isConsumed }
                ) {
                    event.changes.forEach { it.consume() }
                    onClick()
                }
            }
        }
    }

/**
 * Drop-in replacement for [Modifier.combinedClickable] that processes click events at
 * [PointerEventPass.Initial] with double-click detection (300 ms threshold).
 */
fun Modifier.initialPassCombinedClickable(
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
): Modifier =
    this.pointerInput(onClick, onDoubleClick) {
        var lastClickTime = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Release &&
                    event.changes.any { !it.isConsumed }
                ) {
                    event.changes.forEach { it.consume() }
                    val now = System.currentTimeMillis()
                    val isDouble = now - lastClickTime < 300L
                    lastClickTime = now
                    if (isDouble && onDoubleClick != null) {
                        onDoubleClick()
                    } else {
                        onClick()
                    }
                }
            }
        }
    }
