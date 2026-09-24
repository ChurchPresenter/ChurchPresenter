package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The list arithmetic behind a profile's Bible source: which positions it draws, in what order, and
 * how an edit is stored.
 *
 * An empty stored list means "the whole stack, in stack order" -- so showing none has to be stored
 * as scripture switched off, and showing all in stack order is stored as that empty list, so a
 * translation added later still reaches a profile that was showing everything.
 */
class ProfileSourcesTest {

    @Test
    fun `an untouched profile draws the whole stack in stack order`() {
        assertEquals(listOf(0, 1, 2), shownBiblePositions(OutputProfile(), stackSize = 3))
    }

    @Test
    fun `a profile draws its own positions in its own order`() {
        assertEquals(listOf(2, 0), shownBiblePositions(OutputProfile(bibleTranslations = listOf(2, 0)), 3))
    }

    @Test
    fun `a profile with scripture off draws nothing`() {
        assertEquals(emptyList(), shownBiblePositions(OutputProfile(bibleMode = Constants.SONG_LANG_OFF), 3))
    }

    @Test
    fun `nothing left switches scripture off rather than storing an empty list`() {
        val next = withBiblePositions(OutputProfile(), emptyList(), stackSize = 3)
        assertEquals(Constants.SONG_LANG_OFF, next.bibleMode)
        assertEquals(emptyList(), next.bibleTranslations)
    }

    @Test
    fun `the whole stack in stack order is stored as all of them`() {
        val next = withBiblePositions(OutputProfile(bibleTranslations = listOf(1)), listOf(0, 1, 2), stackSize = 3)
        assertEquals(emptyList(), next.bibleTranslations)
    }

    @Test
    fun `the whole stack in another order is stored as that order`() {
        val next = withBiblePositions(OutputProfile(), listOf(2, 1, 0), stackSize = 3)
        assertEquals(listOf(2, 1, 0), next.bibleTranslations)
    }

    @Test
    fun `adding to a profile with scripture off switches it on`() {
        val off = OutputProfile(bibleMode = Constants.SONG_LANG_OFF)
        val next = withBiblePositions(off, listOf(1), stackSize = 3)
        assertEquals(Constants.SONG_LANG_BOTH, next.bibleMode)
        assertEquals(listOf(1), next.bibleTranslations)
    }

    @Test
    fun `an edit to a profile already showing keeps its language mode`() {
        val showing = OutputProfile(bibleMode = Constants.SONG_LANG_PRIMARY)
        assertEquals(Constants.SONG_LANG_PRIMARY, withBiblePositions(showing, listOf(1), 3).bibleMode)
    }

    @Test
    fun `swapping two entries swaps them and nothing else`() {
        assertEquals(listOf("b", "a", "c"), swapped(listOf("a", "b", "c"), 0, 1))
    }

    @Test
    fun `a swap off either end changes nothing`() {
        val list = listOf("a", "b")
        assertEquals(list, swapped(list, 0, -1))
        assertEquals(list, swapped(list, -1, 0))
        assertEquals(list, swapped(list, 1, 2))
    }
}
