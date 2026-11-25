package org.churchpresenter.bibleedits

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bible.VerseEdit
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppliedEditsTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-edits-applied").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun entry(id: Long, bible: String, verse: Int, after: VerseChange?) =
        EditEntry(id, 0, bible, 1, 1, verse, after = after)

    @Test
    fun `only the named Bible's current changes become edits`() {
        val edits = verseEditsFor(
            listOf(
                entry(1, "kjv.spb", 1, VerseChange(text = "Edited.")),
                entry(2, "kjv.spb", 2, VerseChange(removed = true)),
                entry(3, "kjv.spb", 3, VerseChange(added = true)),
                entry(4, "rst.spb", 1, VerseChange(text = "Other Bible.")),
            ),
            "kjv.spb",
        )
        assertEquals(setOf(VerseEdit(1, 1, 1, "Edited."), VerseEdit(1, 1, 2, null)), edits.toSet())
    }

    @Test
    fun `saved corrections are laid over a loaded Bible`() {
        val store = BibleEditsStore(dir)
        store.save(listOf(entry(1, "test.spb", 3, VerseChange(text = "And God said, Let there be light!"))))
        val bible = SpbFixture.loadedBible(dir)
        bible.applySavedEdits(store, "test.spb")
        assertEquals("And God said, Let there be light!", bible.getVerseDetails(1, 1, 3)?.second)
    }
}
