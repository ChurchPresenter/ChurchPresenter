package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TooltipModelTest {

    private val window = IntSize(800, 600)
    private val tip = IntSize(100, 24)

    @Test
    fun `controls in the top half get the tooltip below, the bottom half above`() {
        assertTrue(tooltipBelow(IntRect(10, 10, 40, 40), window))
        assertFalse(tooltipBelow(IntRect(10, 500, 40, 540), window))
        assertEquals(IntOffset(375, 46), tooltipOffset(IntRect(410, 10, 440, 40), window, tip, 6, 8))
        assertEquals(IntOffset(375, 470), tooltipOffset(IntRect(410, 500, 440, 540), window, tip, 6, 8))
    }

    @Test
    fun `a tooltip at an edge is nudged inside the window`() {
        assertEquals(8, tooltipOffset(IntRect(0, 10, 20, 40), window, tip, 6, 8).x)
        assertEquals(692, tooltipOffset(IntRect(780, 10, 800, 40), window, tip, 6, 8).x)
        assertEquals(8, tooltipOffset(IntRect(0, 10, 20, 40), IntSize(50, 600), tip, 6, 8).x)
    }

    @Test
    fun `the position provider places by the same rule`() {
        val provider = TooltipPositionProvider(6, 8)
        assertEquals(
            IntOffset(375, 46),
            provider.calculatePosition(IntRect(410, 10, 440, 40), window, LayoutDirection.Ltr, tip),
        )
    }

    @Test
    fun `warmth lasts its window, on the clock that closed it`() {
        val clock = Any()
        TooltipWarmth.closed(clock, 1_000_000_000L)
        assertTrue(TooltipWarmth.isWarm(clock, 1_200_000_000L, 300))
        assertFalse(TooltipWarmth.isWarm(clock, 1_400_000_000L, 300))
        assertFalse(TooltipWarmth.isWarm(clock, 900_000_000L, 300))
        assertFalse(TooltipWarmth.isWarm(Any(), 1_100_000_000L, 300))
    }

    @Test
    fun `an open tooltip keeps the toolbar warm however long it stays`() {
        val clock = Any()
        TooltipWarmth.opened(clock)
        assertTrue(TooltipWarmth.isWarm(clock, 9_000_000_000L, 300))
        TooltipWarmth.closed(clock, 9_000_000_000L)
        assertFalse(TooltipWarmth.isWarm(clock, 9_500_000_000L, 300))
        TooltipWarmth.closed(clock, 9_600_000_000L)
        assertTrue(TooltipWarmth.isWarm(clock, 9_700_000_000L, 300))
    }

    @Test
    fun `a tooltip that leaves the screen while open stops keeping it warm`() {
        val clock = Any()
        TooltipWarmth.opened(clock)
        TooltipWarmth.forget(Any())
        assertTrue(TooltipWarmth.isWarm(clock, Long.MAX_VALUE, 300))
        TooltipWarmth.forget(clock)
        assertFalse(TooltipWarmth.isWarm(clock, Long.MAX_VALUE, 300))
    }
}
