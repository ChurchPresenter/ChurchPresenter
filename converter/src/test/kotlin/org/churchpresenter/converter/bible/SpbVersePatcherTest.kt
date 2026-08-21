package org.churchpresenter.converter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repairing known defects in source Bible XML after conversion.
 *
 * Some upstream files split one verse across two rows sharing an ID (a superscription broken off
 * from its verse), carry a wrong verse ID, truncate a verse mid-word, or omit a verse entirely.
 * All four are corrected in place, so the invariant these tests defend is that the header block
 * and every unaffected verse come through byte-identical — a patcher that rewrites more than it
 * was asked to would corrupt whole translations quietly.
 */
class SpbVersePatcherTest {

    private val temp: File = Files.createTempDirectory("converter-patch-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun spb(vararg verseLines: String): File {
        val file = File(temp, "bible.spb")
        file.writeText(
            buildString {
                appendLine("##spDataVersion:\t1")
                appendLine("##Title:\tTest Version")
                appendLine("-----")
                verseLines.forEach { appendLine(it) }
            },
            Charsets.UTF_8,
        )
        return file
    }

    private fun verseLines(file: File) =
        file.readLines().dropWhile { it != "-----" }.drop(1).filter { it.isNotBlank() }

    @Test
    fun `a file with nothing to fix is left alone`() {
        val file = spb(
            "B001C001V001\t1\t1\t1\tIn the beginning",
            "B001C001V002\t1\t1\t2\tAnd the earth",
        )
        val before = file.readText()
        assertEquals(0, SpbVersePatcher.applyPatches(file), "nothing was changed")
        assertEquals(before, file.readText(), "and the file is byte-identical")
    }

    @Test
    fun `two rows sharing a verse id are merged into one`() {
        // A superscription split off from its verse arrives as two rows with the same ID; the app
        // shows one verse per ID, so the second row would otherwise be invisible.
        val file = spb(
            "B019C003V001\t19\t3\t1\tA Psalm of David.",
            "B019C003V001\t19\t3\t1\tLord, how many are my foes!",
            "B019C003V002\t19\t3\t2\tMany are saying",
        )
        val changes = SpbVersePatcher.applyPatches(file)

        assertTrue(changes >= 1)
        val lines = verseLines(file)
        assertEquals(2, lines.size, "the duplicate pair became one row")
        assertEquals(
            "B019C003V001\t19\t3\t1\tA Psalm of David. Lord, how many are my foes!",
            lines.first(),
            "the two texts are joined with a single space",
        )
        assertTrue(lines.last().startsWith("B019C003V002"), "the following verse is untouched")
    }

    @Test
    fun `only consecutive duplicates are merged`() {
        val file = spb(
            "B001C001V001\t1\t1\t1\tFirst",
            "B001C001V002\t1\t1\t2\tSecond",
            "B001C001V001\t1\t1\t1\tA later row with the same id",
        )
        SpbVersePatcher.applyPatches(file)
        assertEquals(3, verseLines(file).size, "a non-adjacent repeat is not merged")
    }

    @Test
    fun `the header block survives patching untouched`() {
        val file = spb(
            "B019C003V001\t19\t3\t1\tA Psalm of David.",
            "B019C003V001\t19\t3\t1\tLord, how many are my foes!",
        )
        SpbVersePatcher.applyPatches(file)
        val header = file.readLines().takeWhile { it != "-----" }
        assertEquals(listOf("##spDataVersion:\t1", "##Title:\tTest Version"), header)
    }

    @Test
    fun `a malformed row is passed through rather than dropped`() {
        // Losing a row silently would be worse than leaving one that looks odd.
        val file = spb(
            "B001C001V001\t1\t1\t1\tGood row",
            "not a verse row at all",
            "B001C001V002\t1\t1\t2\tAnother good row",
        )
        SpbVersePatcher.applyPatches(file)
        assertTrue(verseLines(file).any { it == "not a verse row at all" })
        assertEquals(3, verseLines(file).size)
    }

    @Test
    fun `a file with no separator is not mangled`() {
        val file = File(temp, "headerless.spb")
        file.writeText("B001C001V001\t1\t1\t1\tOnly a verse\n", Charsets.UTF_8)
        val before = file.readText()
        SpbVersePatcher.applyPatches(file)
        assertEquals(before, file.readText())
    }

    @Test
    fun `patch tables are internally consistent`() {
        // These are hand-maintained data; a patch whose corrected text is blank, or a missing-verse
        // entry with a malformed id, would corrupt the verse it claims to repair.
        for ((key, patch) in VersePatches.PATCHES) {
            assertTrue(patch.correctedText.isNotBlank(), "patch $key has no replacement text")
            assertTrue(patch.minimumPrefixLength >= 0, "patch $key has a negative safety length")
        }
        for (missing in VersePatches.MISSING_VERSES) {
            assertTrue(
                Regex("""^B\d{3}C\d{3}V\d{3}$""").matches(missing.verseId),
                "missing-verse id '${missing.verseId}' is not a canonical code",
            )
            assertTrue(missing.verseText.isNotBlank(), "missing verse ${missing.verseId} has no text")
            assertTrue(missing.bookNum in 1..66, "missing verse ${missing.verseId} is outside the canon")
        }
        for ((wrong, corrected) in VersePatches.ID_CORRECTIONS) {
            assertTrue(wrong != corrected, "id correction $wrong changes nothing")
            assertTrue(
                Regex("""^B\d{3}C\d{3}V\d{3}$""").matches(corrected),
                "id correction target '$corrected' is not a canonical code",
            )
        }
    }
}
