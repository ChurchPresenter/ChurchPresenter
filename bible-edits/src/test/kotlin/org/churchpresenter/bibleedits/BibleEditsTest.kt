package org.churchpresenter.bibleedits

import kotlin.test.Test
import kotlin.test.assertEquals

class BibleEditsTest {

    private fun entry(before: VerseChange?, after: VerseChange?, revertOf: Long? = null, verse: Int = 1) =
        EditEntry(id = 1, time = 0, bible = "kjv.spb", book = 1, chapter = 1, verse = verse,
            before = before, after = after, revertOf = revertOf)

    @Test
    fun `each entry is named for what it did`() {
        val text = VerseChange(text = "new")
        val added = VerseChange(text = "new", added = true)
        val removed = VerseChange(removed = true)
        assertEquals(EditKind.EDITED, entry(null, text).kind())
        assertEquals(EditKind.ADDED, entry(null, added).kind())
        assertEquals(EditKind.EDITED, entry(added, added.copy(text = "newer")).kind())
        assertEquals(EditKind.REMOVED, entry(null, removed).kind())
        assertEquals(EditKind.REMOVED, entry(added, null).kind())
        assertEquals(EditKind.RESTORED, entry(removed, null).kind())
        assertEquals(EditKind.REVERTED, entry(text, null, revertOf = 4).kind())
        assertEquals(EditKind.EDITED, entry(text, null).kind())
    }

    @Test
    fun `the last change to a verse is what it reads, and null hands it back to the file`() {
        val one = VerseChange(text = "one")
        val two = VerseChange(text = "two")
        val changes = currentChanges(
            listOf(entry(null, one), entry(one, two), entry(null, one, verse = 2), entry(one, null, verse = 2)),
        )
        assertEquals(mapOf(VerseAddress("kjv.spb", 1, 1, 1) to two), changes)
    }
}
