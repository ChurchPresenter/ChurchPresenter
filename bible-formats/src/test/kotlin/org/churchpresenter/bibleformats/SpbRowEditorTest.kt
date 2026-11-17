package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Bible verse fixer's file side: reading rows and book names, and writing edits, removals and additions. */
class SpbRowEditorTest {

    private val temp: File = Files.createTempDirectory("spb-row-editor").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private val rows = arrayOf(
        "B047C010V004\t47\t10\t4\tFour",
        "B047C010V0050\t47\t10\t5\tFive",
        "B047C010V006\t47\t10\t6\tSix",
    )

    private fun spb(): File = File(temp, "bible.spb").also { file ->
        file.writeText(
            "##Title:\tTest\n47\t2 Corinthians\t13\n-----\n" + rows.joinToString("\n") + "\n",
            Charsets.UTF_8,
        )
    }

    @Test
    fun `rows and the book names come from the file`() {
        val file = spb()
        assertEquals(listOf(4, 5, 6), SpbRowEditor.readRows(file).map { it.verse.toInt() })
        assertEquals(6, SpbRowEditor.readRows(file).last().lineNumber)
        assertEquals(mapOf(47 to "2 Corinthians"), SpbRowEditor.readBookNames(file))
    }

    @Test
    fun `an edited id and text are written and the original is kept once`() {
        val file = spb()
        val changed = SpbRowEditor.applyChanges(
            file,
            SpbChanges(edits = mapOf(5 to SpbRowEdit(id = "B047C010V005", text = "Five fixed\twith a tab"))),
        )
        assertEquals(1, changed)
        assertEquals("B047C010V005\t47\t10\t5\tFive fixed with a tab", file.readLines()[4])
        assertTrue(File(file.path + ".bak").readText().contains("V0050"))
    }

    @Test
    fun `an invalid id is not written`() {
        val file = spb()
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(edits = mapOf(5 to SpbRowEdit(id = "B047C010V05")))))
        assertFalse(File(file.path + ".bak").exists())
    }

    @Test
    fun `a removed row is gone and a row is added after its anchor`() {
        val file = spb()
        val added = SpbRowView(0, "B047C010V007", "47", "10", "7", "Seven")
        val changed = SpbRowEditor.applyChanges(
            file,
            SpbChanges(deleted = setOf(5), inserted = mapOf(6 to listOf(added))),
        )
        assertEquals(2, changed)
        assertEquals(
            listOf("B047C010V004", "B047C010V006", "B047C010V007"),
            SpbRowEditor.readRows(file).map { it.id },
        )
        assertEquals("##Title:\tTest", file.readLines().first())
    }

    @Test
    fun `a row added at the start goes before the first verse and not into the header`() {
        val file = spb()
        val added = SpbRowView(0, "B047C010V003", "47", "10", "3", "Three")
        SpbRowEditor.applyChanges(file, SpbChanges(inserted = mapOf(0 to listOf(added))))
        assertEquals("-----", file.readLines()[2])
        assertEquals("B047C010V003", SpbRowEditor.readRows(file).first().id)
    }

    @Test
    fun `nothing changed writes nothing`() {
        val file = spb()
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges()))
        assertFalse(File(file.path + ".bak").exists())
    }

    @Test
    fun `a damaged row has a suggested id`() {
        val damaged = SpbRowEditor.readRows(spb())[1]
        assertEquals("B047C010V005", SpbRowEditor.suggestedId(damaged))
    }

    @Test
    fun `an edit aimed at a header line, or at no line, changes nothing`() {
        val file = spb()
        val before = file.readText()
        val aimedWrong = mapOf(1 to SpbRowEdit(text = "x"), 99 to SpbRowEdit(text = "y"))
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(edits = aimedWrong)))
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(deleted = setOf(1))))
        assertEquals(before, file.readText())
    }

    @Test
    fun `a row added with an unreadable id is skipped`() {
        val file = spb()
        val bad = SpbRowView(0, "B047C010V07", "47", "10", "7", "Seven")
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(inserted = mapOf(6 to listOf(bad)))))
    }

    @Test
    fun `an edit that changes nothing writes nothing`() {
        val file = spb()
        val same = SpbRowEdit(id = "B047C010V004", text = "Four")
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(edits = mapOf(4 to same))))
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(edits = mapOf(4 to SpbRowEdit()))))
    }

    @Test
    fun `a file with windows line ends keeps them on edited and added rows`() {
        val file = File(temp, "crlf.spb")
        file.writeText("##Title:\tT\r\n-----\r\nB001C001V001\t1\t1\t1\tOne\r\n", Charsets.UTF_8)
        val added = SpbRowView(0, "B001C001V002", "1", "1", "2", "Two")
        val changes = SpbChanges(edits = mapOf(3 to SpbRowEdit(text = "One!")), inserted = mapOf(3 to listOf(added)))
        SpbRowEditor.applyChanges(file, changes)
        val lines = file.readText().split("\n")
        assertEquals("B001C001V001\t1\t1\t1\tOne!\r", lines[2])
        assertEquals("B001C001V002\t1\t1\t2\tTwo\r", lines[3])
    }

    @Test
    fun `a file with no verse rows is left alone and the book list is empty`() {
        val file = File(temp, "empty.spb").apply { writeText("##Title:\tT\n-----\n") }
        val orphan = SpbRowView(0, "B001C001V001", "1", "1", "1", "x")
        assertEquals(0, SpbRowEditor.applyChanges(file, SpbChanges(inserted = mapOf(0 to listOf(orphan)))))
        assertTrue(SpbRowEditor.readRows(file).isEmpty())
        assertTrue(SpbRowEditor.readBookNames(file).isEmpty())
    }

    @Test
    fun `the change set knows when it is empty and the row types compare by value`() {
        assertTrue(SpbChanges().isEmpty)
        assertFalse(SpbChanges(deleted = setOf(1)).isEmpty)
        assertFalse(SpbChanges(edits = mapOf(1 to SpbRowEdit(text = "x"))).isEmpty)
        assertFalse(SpbChanges(inserted = mapOf(0 to emptyList())).isEmpty)
        val row = SpbRowView(1, "B001C001V001", "1", "1", "1", "x")
        assertEquals(row, row.copy())
        assertEquals(row.hashCode(), row.copy().hashCode())
        assertEquals(SpbRowEdit("a", "b"), SpbRowEdit("a", "b").copy())
        assertEquals(SpbChanges(), SpbChanges().copy())
    }
}
