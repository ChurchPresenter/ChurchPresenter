package org.churchpresenter.bibletab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveVerseStepTest {

    private val chapter = listOf("1. First", "2. Second", "3. Third")

    @Test
    fun `an empty chapter has nowhere to step`() = assertNull(nextLiveVerseNumber(emptyList(), 1, moveUp = false))

    @Test
    fun `down steps to the next verse and stops at the last`() {
        assertEquals(2, nextLiveVerseNumber(chapter, 1, moveUp = false))
        assertEquals(3, nextLiveVerseNumber(chapter, 3, moveUp = false))
    }

    @Test
    fun `up steps to the previous verse and stops at the first`() {
        assertEquals(2, nextLiveVerseNumber(chapter, 3, moveUp = true))
        assertEquals(1, nextLiveVerseNumber(chapter, 1, moveUp = true))
    }

    @Test
    fun `a verse the chapter does not have steps from the top`() =
        assertEquals(2, nextLiveVerseNumber(chapter, 9, moveUp = false))

    @Test
    fun `a line without a number gives no verse`() =
        assertNull(nextLiveVerseNumber(listOf("1. First", "heading"), 1, moveUp = false))
}
