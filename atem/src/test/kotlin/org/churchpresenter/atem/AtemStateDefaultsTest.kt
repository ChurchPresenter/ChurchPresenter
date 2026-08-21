package org.churchpresenter.atem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [AtemState]'s defaults, which `parseAtemState` never exercises because it always fills all nine
 * fields. They are what a caller gets from a switcher whose firmware predates the commands behind
 * them — `MPSp` (clip capacity) is 8.0+, and `_top`/`_MeC` are absent on the oldest devices — so
 * the documented "0 = unknown" and "empty" answers are a real contract, not padding.
 */
class AtemStateDefaultsTest {

    private fun minimal() = AtemState(
        fps = 25.0,
        videoMode = "1080p25",
        stillSlots = listOf(AtemMediaSlot(0, "logo", isUsed = true)),
        clipSlots = emptyList(),
    )

    @Test
    fun `a state built from the required fields alone reports every optional field as unknown`() {
        val state = minimal()
        assertEquals(emptyList(), state.clipMaxFrames, "no MPSp seen means no known clip capacity")
        assertEquals(0, state.unassignedFrames)
        assertEquals(0, state.mixEffectCount, "0 is the documented 'topology unknown'")
        assertEquals(emptyList(), state.keyersPerMe)
        assertEquals(0, state.downstreamKeyers)
    }

    @Test
    fun `the required fields are kept as given`() {
        val state = minimal()
        assertEquals(25.0, state.fps)
        assertEquals("1080p25", state.videoMode)
        assertEquals(1, state.stillSlots.size)
        assertTrue(state.stillSlots.single().isUsed)
        assertTrue(state.clipSlots.isEmpty())
    }

    @Test
    fun `two states differing only in an optional field are not equal`() {
        // The data-class equality the upload dialog relies on to notice a re-read changed something.
        assertNotEquals(minimal(), minimal().copy(downstreamKeyers = 2))
        assertNotEquals(minimal(), minimal().copy(clipMaxFrames = listOf(90)))
    }
}
