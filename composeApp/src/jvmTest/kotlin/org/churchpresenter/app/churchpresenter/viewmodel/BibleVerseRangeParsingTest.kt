package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.settings.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleVerseRangeParsingTest {

    private val model = BibleViewModel(
        AppSettings(),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @AfterTest
    fun cleanUp() {
        runCatching { model.dispose() }
    }

    @Test
    fun `a single verse number is one verse`() {
        assertEquals(listOf(16), model.parseVerseNumbers("16"))
    }

    @Test
    fun `a hyphenated range is expanded`() {
        assertEquals(listOf(16, 17, 18), model.parseVerseNumbers("16-18"))
    }

    @Test
    fun `a comma-separated list keeps its gaps`() {
        assertEquals(listOf(1, 3, 5), model.parseVerseNumbers("1,3,5"))
    }

    @Test
    fun `ranges and single verses can be mixed`() {
        assertEquals(listOf(1, 2, 3, 7, 9, 10), model.parseVerseNumbers("1-3,7,9-10"))
    }

    @Test
    fun `spaces around the separators are ignored`() {
        assertEquals(listOf(1, 2, 5), model.parseVerseNumbers(" 1 - 2 , 5 "))
    }

    @Test
    fun `a range that runs backwards yields nothing for that part`() {
        assertEquals(listOf(9), model.parseVerseNumbers("5-3,9"))
    }

    @Test
    fun `a range whose bounds are equal is that one verse`() {
        assertEquals(listOf(4), model.parseVerseNumbers("4-4"))
    }

    @Test
    fun `a part that is not a number is skipped rather than crashing`() {
        assertEquals(listOf(2), model.parseVerseNumbers("abc,2"))
    }

    @Test
    fun `a range with a missing bound is skipped`() {
        assertEquals(listOf(3), model.parseVerseNumbers("-,3"))
    }

    @Test
    fun `a range with a non-numeric upper bound is skipped`() {
        assertEquals(listOf(8), model.parseVerseNumbers("1-x,8"))
    }

    @Test
    fun `an empty range yields no verses`() {
        assertEquals(emptyList<Int>(), model.parseVerseNumbers(""))
    }
}
