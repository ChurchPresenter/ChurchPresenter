package org.churchpresenter.bibleeditor

import org.churchpresenter.bibleedits.VerseAddress
import org.churchpresenter.bibleedits.VerseChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditHistoryTest {

    private var time = 1_000L
    private val history = EditHistory { time++ }
    private val verse = VerseAddress("rst.spb", 40, 17, 2)
    private val fixed = VerseChange(text = "shine")

    @Test
    fun `a change is recorded once, with the verse's previous state`() {
        history.commit(verse, fixed, original = "shnie")
        history.commit(verse, fixed, original = "shnie")
        val entry = history.entries.single()
        assertEquals(1, entry.id)
        assertNull(entry.before)
        assertEquals("shnie", entry.original)
        assertEquals(fixed, history.changes[verse])
        assertEquals(entry, history.lastFor(verse))
    }

    @Test
    fun `reverting puts the verse back and notes which change it undid`() {
        history.commit(verse, fixed, "shnie")
        val first = history.entries.single()
        assertFalse(history.isUndone(first))
        history.revert(first)
        assertTrue(history.isUndone(first))
        history.revert(first)
        assertEquals(2, history.entries.size)
        assertEquals(first.id, history.entries.last().revertOf)
        assertNull(history.changes[verse])
    }

    @Test
    fun `discarding cuts the history back to the last save`() {
        history.reset(emptyList())
        history.commit(verse, fixed, "shnie")
        history.markSaved()
        history.commit(verse, VerseChange(removed = true), "shnie")
        assertEquals(1, history.unsavedCount)
        assertTrue(history.isUnsaved(history.entries.last()))
        assertFalse(history.isUnsaved(history.entries.first()))
        history.discardUnsaved()
        assertEquals(0, history.unsavedCount)
        assertEquals(fixed, history.changes[verse])
    }

    @Test
    fun `a loaded history counts as saved`() {
        history.reset(EditorFixture.savedFixes())
        assertEquals(2, history.savedCount)
        assertEquals(0, history.unsavedCount)
        assertNull(history.lastFor(verse.copy(verse = 3)))
    }
}
