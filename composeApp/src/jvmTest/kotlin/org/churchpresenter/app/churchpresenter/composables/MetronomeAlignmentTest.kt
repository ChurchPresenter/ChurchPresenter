package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.Alignment
import org.churchpresenter.settings.MetronomePosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetronomeAlignmentTest {

    @Test
    fun `no position means no metronome`() {
        assertNull(MetronomePosition.NONE.toAlignment())
    }

    @Test
    fun `each of the nine positions sits in its own place on screen`() {
        val placed = MetronomePosition.entries.filterNot { it == MetronomePosition.NONE }.map { it.toAlignment() }
        assertEquals(9, placed.toSet().size)
        assertEquals(Alignment.TopStart, MetronomePosition.TOP_LEFT.toAlignment())
        assertEquals(Alignment.BottomEnd, MetronomePosition.BOTTOM_RIGHT.toAlignment())
    }
}
