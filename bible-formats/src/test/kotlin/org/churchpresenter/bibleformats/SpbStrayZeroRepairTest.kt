package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A verse id with a stray trailing zero (`B047C010V0050`) is repaired, with the original kept.
 *
 * The rows are the ones in a real Russian Synodal file: 2 Corinthians 10:4 broken into two rows,
 * the second wearing the id verse 5 needs, and verse 5 itself written as `V0050`.
 */
class SpbStrayZeroRepairTest {

    private val temp: File = Files.createTempDirectory("spb-stray-zero").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun spb(vararg verseLines: String): File = File(temp, "bible.spb").also { file ->
        file.writeText(
            buildString {
                appendLine("##Title:\tTest Version")
                appendLine("-----")
                verseLines.forEach { appendLine(it) }
            },
            Charsets.UTF_8,
        )
    }

    private fun verseLines(file: File) =
        file.readLines().dropWhile { it != "-----" }.drop(1).filter { it.isNotBlank() }

    @Test
    fun `a stray zero is dropped from the id`() {
        val file = spb(
            "B001C001V009\t1\t1\t9\tNine",
            "B001C001V0100\t1\t1\t10\tTen",
        )

        assertEquals(1, SpbVersePatcher.applyPatches(file))

        assertEquals(
            listOf("B001C001V009\t1\t1\t9\tNine", "B001C001V010\t1\t1\t10\tTen"),
            verseLines(file),
        )
    }

    @Test
    fun `a verse whose id the zero row needs is a continuation and is joined to the verse before it`() {
        val file = spb(
            "B047C010V003\t47\t10\t3\tThree",
            "B047C010V004\t47\t10\t4\tFour begins",
            "B047C010V005\t47\t10\t4\tand ends",
            "B047C010V0050\t47\t10\t5\tFive",
            "B047C010V006\t47\t10\t6\tSix",
        )

        assertTrue(SpbVersePatcher.applyPatches(file) >= 2)

        assertEquals(
            listOf(
                "B047C010V003\t47\t10\t3\tThree",
                "B047C010V004\t47\t10\t4\tFour begins and ends",
                "B047C010V005\t47\t10\t5\tFive",
                "B047C010V006\t47\t10\t6\tSix",
            ),
            verseLines(file),
        )
    }

    @Test
    fun `the id takes the display verse when dropping the zero would land on the verse before`() {
        // Numbers 21:15 in the Synodal file: the first half of verse 15 was written V0140.
        val file = spb(
            "B004C021V013\t4\t21\t13\tThirteen",
            "B004C021V014\t4\t21\t14\tFourteen",
            "B004C021V0140\t4\t21\t15\tFifteen begins",
            "B004C021V015\t4\t21\t15\tand ends",
        )

        SpbVersePatcher.applyPatches(file)

        assertEquals(
            listOf(
                "B004C021V013\t4\t21\t13\tThirteen",
                "B004C021V014\t4\t21\t14\tFourteen",
                "B004C021V015\t4\t21\t15\tFifteen begins and ends",
            ),
            verseLines(file),
        )
    }

    @Test
    fun `a module whose display numbering is not its code numbering only loses the zero`() {
        val file = spb("B019C051V0020\t19\t50\t1\tA psalm title")

        SpbVersePatcher.applyPatches(file)

        assertEquals(listOf("B019C051V002\t19\t50\t1\tA psalm title"), verseLines(file))
    }

    @Test
    fun `the same verse written twice under one id is left to the merge pass`() {
        val file = spb(
            "B001C001V005\t1\t1\t5\tFirst half",
            "B001C001V0050\t1\t1\t5\tsecond half",
        )

        SpbVersePatcher.applyPatches(file)

        assertEquals(listOf("B001C001V005\t1\t1\t5\tFirst half second half"), verseLines(file))
    }

    @Test
    fun `an id taken by a row that is not a continuation is not touched`() {
        val file = spb(
            "B001C001V004\t1\t1\t4\tFour",
            "B001C001V005\t1\t1\t6\tSomething else",
            "B001C001V0050\t1\t1\t5\tFive",
        )
        val before = file.readText()

        assertEquals(0, SpbVersePatcher.applyPatches(file))

        assertEquals(before, file.readText())
        assertFalse(File(file.path + ".bak").exists(), "nothing changed, so nothing was copied")
    }

    @Test
    fun `what the repair could not settle is counted afterwards`() {
        // Revelation 20:9: V009 is verse 8's and V010 is verse 10's, so verse 9 has no id to take.
        val file = spb(
            "B066C020V008\t66\t20\t7\tSeven",
            "B066C020V009\t66\t20\t8\tEight",
            "B066C020V0090\t66\t20\t9\tNine",
            "B066C020V010\t66\t20\t10\tTen",
        )
        assertEquals(1, malformedSpbIdCount(file))

        assertEquals(0, SpbVersePatcher.applyPatches(file))

        assertEquals(1, malformedSpbIdCount(file), "left as it was, and still reported")
    }

    @Test
    fun `a repaired file has no malformed ids left`() {
        val file = spb("B001C001V0100\t1\t1\t10\tTen")
        SpbVersePatcher.applyPatches(file)
        assertEquals(0, malformedSpbIdCount(file))
    }

    @Test
    fun `the file as it was is copied beside it, once`() {
        val file = spb("B001C001V0100\t1\t1\t10\tTen")
        val original = file.readText()

        SpbVersePatcher.applyPatches(file)
        val backup = File(file.path + ".bak")
        assertEquals(original, backup.readText())

        file.writeText(original, Charsets.UTF_8)
        file.appendText("B001C001V0110\t1\t1\t11\tEleven\n")
        SpbVersePatcher.applyPatches(file)
        assertEquals(original, backup.readText(), "a second run keeps the first copy")
    }
}
