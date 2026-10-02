package org.churchpresenter.crosswordtab

import kotlin.test.Test
import kotlin.test.assertEquals

/** What pressing Check does, apart from the tab: what it unlocks, and whether it moves on. */
class CheckOutcomeTest {

    @Test
    fun `a wrong grid unlocks nothing and stays put`() {
        assertEquals(
            CheckOutcome(solved = false),
            checkOutcome(false, levelIdx = 0, puzzleCount = 3, unlockedLevel = 0),
        )
    }

    @Test
    fun `solving the furthest level opens the next one and moves on to it`() {
        assertEquals(
            CheckOutcome(solved = true, unlock = 1, advance = true),
            checkOutcome(true, levelIdx = 0, puzzleCount = 3, unlockedLevel = 0),
        )
    }

    @Test
    fun `solving an earlier level again opens nothing new`() {
        val outcome = checkOutcome(true, levelIdx = 0, puzzleCount = 3, unlockedLevel = 2)
        assertEquals(null, outcome.unlock, "level 2 is already open; re-solving level 0 must not lower it")
        assertEquals(true, outcome.advance)
    }

    @Test
    fun `solving the last level says so and has nowhere to move on to`() {
        assertEquals(
            CheckOutcome(solved = true, unlock = 3, advance = false, allDone = true),
            checkOutcome(true, levelIdx = 2, puzzleCount = 3, unlockedLevel = 2),
        )
    }
}
