package org.churchpresenter.app.churchpresenter.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a soak window says about the UI thread: answers past the budget counted, the latest kept. */
class UiStallTallyTest {

    @Test
    fun `only answers past the budget are stalls, and the latest answer is kept either way`() {
        val tally = UiStallTally(budgetMs = 250)
        listOf(10L, 250L, 251L, 900L, 30L).forEach(tally::record)
        assertEquals(2 to 900L, tally.take())
    }

    @Test
    fun `taking a window starts the next one empty`() {
        val tally = UiStallTally(budgetMs = 250)
        tally.record(400)
        tally.take()
        assertEquals(0 to 0L, tally.take())
    }
}
