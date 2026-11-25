package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import java.lang.ref.WeakReference

internal fun tooltipBelow(anchor: IntRect, window: IntSize): Boolean = anchor.center.y < window.height / 2

internal fun tooltipOffset(anchor: IntRect, window: IntSize, popup: IntSize, gap: Int, margin: Int): IntOffset {
    val centered = anchor.center.x - popup.width / 2
    val x = centered.coerceIn(margin, maxOf(margin, window.width - margin - popup.width))
    val y = if (tooltipBelow(anchor, window)) anchor.bottom + gap else anchor.top - gap - popup.height
    return IntOffset(x, y)
}

internal class TooltipPositionProvider(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = tooltipOffset(anchorBounds, windowSize, popupContentSize, gap, margin)
}

internal object TooltipWarmth {
    private var clock = WeakReference<Any>(null)
    private var open = 0
    private var closedAtNanos = 0L

    private fun on(owner: Any) {
        if (clock.get() !== owner) {
            clock = WeakReference(owner)
            open = 0
        }
    }

    fun opened(owner: Any) {
        on(owner)
        open++
    }

    fun closed(owner: Any, nanos: Long) {
        on(owner)
        open = maxOf(0, open - 1)
        closedAtNanos = nanos
    }

    fun forget(owner: Any) {
        if (clock.get() === owner) open = maxOf(0, open - 1)
    }

    fun isWarm(owner: Any, nanos: Long, windowMillis: Long): Boolean =
        clock.get() === owner && (open > 0 || nanos - closedAtNanos in 0..windowMillis * NANOS_PER_MILLI)

    private const val NANOS_PER_MILLI = 1_000_000L
}
