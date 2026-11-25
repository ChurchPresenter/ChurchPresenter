package org.churchpresenter.bibleedits

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleEditsStoreTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-edits-store").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.setReadable(true)
        File(dir, "bible-edits.json").setReadable(true)
        dir.deleteRecursively()
    }

    private val entries = listOf(
        EditEntry(1, 100, "kjv.spb", 40, 17, 21, after = VerseChange(text = "Howbeit.", added = true)),
        EditEntry(2, 200, "kjv.spb", 40, 17, 2, after = VerseChange(text = "Shine."), original = "Shnie."),
    )

    @Test
    fun `nothing saved reads as no history`() {
        assertEquals(emptyList(), BibleEditsStore(dir).load())
    }

    @Test
    fun `what is saved reads back, and a second save replaces it`() {
        val store = BibleEditsStore(File(dir, "nested"))
        store.save(entries)
        assertEquals(entries, store.load())
        store.save(entries.take(1))
        assertEquals(entries.take(1), store.load())
        assertEquals(listOf("bible-edits.json"), File(dir, "nested").list()?.toList())
    }

    @Test
    fun `a file that is not a history reads as none`() {
        File(dir, "bible-edits.json").writeText("{ not json")
        assertEquals(emptyList(), BibleEditsStore(dir).load())
    }

    @Test
    fun `a file that cannot be read reads as none`() {
        val file = File(dir, "bible-edits.json")
        BibleEditsStore(dir).save(entries)
        assertTrue(file.setReadable(false))
        if (file.canRead()) return // running as root: nothing is unreadable
        assertEquals(emptyList(), BibleEditsStore(dir).load())
    }
}
