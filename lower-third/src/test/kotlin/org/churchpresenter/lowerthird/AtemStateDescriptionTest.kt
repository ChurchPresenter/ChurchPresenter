package org.churchpresenter.lowerthird

import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one line the ATEM settings show once a switcher answers: its mode, and its clip memory. */
class AtemStateDescriptionTest {

    private fun slots(count: Int) = List(count) { AtemMediaSlot(it, "", isUsed = false) }

    @Test
    fun `a switcher without clip memory gives its mode and rate alone`() {
        val state = AtemState(fps = 25.0, videoMode = "1080p25", stillSlots = slots(20), clipSlots = emptyList())
        assertEquals("1080p25 (25 fps)", runBlocking { describeAtemState(state) })
    }

    @Test
    fun `clip memory is told once per distinct size, in seconds, with what is left unassigned`() {
        val state = AtemState(
            fps = 25.0,
            videoMode = "1080p25",
            stillSlots = slots(20),
            clipSlots = slots(2),
            clipMaxFrames = listOf(100, 100),
            unassignedFrames = 40,
        )
        assertEquals(
            "1080p25 (25 fps) — 2 clips × up to 100 frames (≈4.0s), 40 frames unassigned",
            runBlocking { describeAtemState(state) },
        )
        assertEquals(
            "1080p25 (25 fps) — 2 clips × up to 100 frames (≈4.0s)/50 frames (≈2.0s)",
            runBlocking { describeAtemState(state.copy(clipMaxFrames = listOf(100, 50), unassignedFrames = 0)) },
        )
    }
}
