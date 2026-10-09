package org.churchpresenter.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputHistoryTest {

    @Test
    fun `nothing typed yet recalls nothing`() {
        val history = InputHistory()
        assertNull(history.older("draft"))
        assertNull(history.newer())
    }

    @Test
    fun `up walks back and stops at the oldest, down returns to the draft`() {
        val history = InputHistory()
        history.record("one")
        history.record("two")
        assertEquals("two", history.older("draft"))
        assertEquals("one", history.older("two"))
        assertEquals("one", history.older("one"))
        assertEquals("two", history.newer())
        assertEquals("draft", history.newer())
        assertNull(history.newer())
    }

    @Test
    fun `a repeat of the newest is filed once, and recording stops the walk`() {
        val history = InputHistory()
        history.record("one")
        history.record("one")
        assertEquals("one", history.older(""))
        history.record("two")
        assertNull(history.newer())
        assertEquals("two", history.older(""))
        assertEquals("one", history.older("two"))
        assertEquals("one", history.older("one"))
    }

    @Test
    fun `only the newest fifty are kept`() {
        val history = InputHistory()
        repeat(60) { history.record("r$it") }
        var oldest = history.older("")
        repeat(60) { oldest = history.older(oldest.orEmpty()) }
        assertEquals("r10", oldest)
    }
}
