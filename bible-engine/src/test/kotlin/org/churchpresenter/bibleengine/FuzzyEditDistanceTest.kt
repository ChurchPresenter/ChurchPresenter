package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.bible.withinOneEdit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzyEditDistanceTest {

    @Test
    fun `identical stems are within one edit`() {
        assertTrue(withinOneEdit("труждающ", "труждающ"))
    }

    @Test
    fun `one substitution is within one edit`() {
        assertTrue(withinOneEdit("shepherd", "shepnerd"))
    }

    @Test
    fun `an adjacent transposition is within one edit`() {
        assertTrue(withinOneEdit("shepherd", "shephedr"))
        assertTrue(withinOneEdit("shepherd", "hsepherd"))
    }

    @Test
    fun `two separate substitutions are not`() {
        assertFalse(withinOneEdit("shepherd", "xhepherx"))
        assertFalse(withinOneEdit("shepherd", "sxhpherd"))
    }

    @Test
    fun `three differences are not`() {
        assertFalse(withinOneEdit("shepherd", "xxxpherd"))
    }

    @Test
    fun `one insertion or deletion is within one edit, either way round`() {
        assertTrue(withinOneEdit("туждающ", "труждающ"))
        assertTrue(withinOneEdit("труждающ", "туждающ"))
        assertTrue(withinOneEdit("shepherd", "shepherds"))
    }

    @Test
    fun `an insertion that also changes a letter is not`() {
        assertFalse(withinOneEdit("shepherd", "shxpherdz"))
    }

    @Test
    fun `a length gap of two is not`() {
        assertFalse(withinOneEdit("shepherd", "shepherdss"))
    }
}
