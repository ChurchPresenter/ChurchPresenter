package org.churchpresenter.liveoutput

import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Each screen's hold: one screen or every one, frozen or black, and the three quick presses of Clear
 * that let them all go. The clock is the test's, so "quick" is exact.
 */
class ScreenHoldsTest {

    private var now = 0L
    private val context = PresenterContext()
    private val holds = ScreenHoldsState(context) { now }

    @Test
    fun `a screen is held, blanked and released on its own`() {
        holds.setScreenHold(1, ScreenHold.HOLD)
        assertEquals(mapOf(1 to ScreenHold.HOLD), holds.screenHolds.value)
        holds.setScreenHold(0, ScreenHold.BLANK)
        holds.setScreenHold(1, null)
        assertEquals(mapOf(0 to ScreenHold.BLANK), holds.screenHolds.value)
    }

    @Test
    fun `hold all holds every screen the windows opened, and releases them all`() {
        holds.setScreenCount(3)
        assertEquals(3, holds.screenCount)
        holds.setScreenHold(1, ScreenHold.BLANK)
        holds.holdAllScreens(ScreenHold.HOLD)
        assertEquals(mapOf(0 to ScreenHold.HOLD, 1 to ScreenHold.HOLD, 2 to ScreenHold.HOLD), holds.screenHolds.value)
        holds.holdAllScreens(null)
        assertTrue(holds.screenHolds.value.isEmpty())
    }

    @Test
    fun `with no screens open, hold all holds nothing`() {
        holds.holdAllScreens(ScreenHold.HOLD)
        assertTrue(holds.screenHolds.value.isEmpty())
    }

    @Test
    fun `one or two presses of Clear leave a hold, the third quick one releases every hold`() {
        holds.setScreenHold(0, ScreenHold.HOLD)
        holds.setScreenHold(2, ScreenHold.BLANK)
        holds.noteClearPress()
        now += 500
        holds.noteClearPress()
        assertEquals(2, holds.screenHolds.value.size)
        now += 1_000
        holds.noteClearPress()
        assertTrue(holds.screenHolds.value.isEmpty())
    }

    @Test
    fun `three presses spread out never release, but the last three quick ones do`() {
        holds.setScreenHold(0, ScreenHold.HOLD)
        repeat(3) {
            holds.noteClearPress()
            now += 1_000
        }
        assertEquals(mapOf(0 to ScreenHold.HOLD), holds.screenHolds.value)
        // The two above at 1 s and 2 s are too old; three more, close together, count from the newest.
        now += 5_000
        repeat(2) { holds.noteClearPress() }
        assertEquals(mapOf(0 to ScreenHold.HOLD), holds.screenHolds.value)
        holds.noteClearPress()
        assertTrue(holds.screenHolds.value.isEmpty())
    }

    @Test
    fun `presses made while nothing is held do not count toward a later hold`() {
        holds.noteClearPress()
        holds.noteClearPress()
        holds.setScreenHold(0, ScreenHold.HOLD)
        holds.noteClearPress()
        assertEquals(mapOf(0 to ScreenHold.HOLD), holds.screenHolds.value)
    }

    @Test
    fun `the manager's Clear counts the presses, and still clears what is live`() {
        val manager = PresenterManager(showPresenterWindowInitially = false)
        manager.setScreenHold(0, ScreenHold.HOLD)
        manager.setPresentingMode(Presenting.LYRICS)
        manager.requestClearDisplay()
        assertTrue(manager.clearDisplayRequested.value, "one Clear still clears what is live")
        assertEquals(mapOf(0 to ScreenHold.HOLD), manager.screenHolds.value)
        manager.requestClearDisplay()
        manager.requestClearDisplay()
        assertTrue(manager.screenHolds.value.isEmpty(), "three quick presses release the hold")
    }
}
