package converter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three patch tables applied to a real `.spb`: a corrupted verse ID, a truncated verse text,
 * and a verse the file leaves out entirely.
 *
 * Each one is guarded, because the same module is shipped by several sources with different
 * wording: a text patch that overwrote a *different* translation of the same verse would be worse
 * than the truncation it fixes, so a patch only lands when the text it replaces is the text it was
 * written against.
 */
class SpbVersePatchApplicationTest {

    private val temp: File = Files.createTempDirectory("converter-patch-apply").toFile()

    private val truncated = "Сына [одной] женщины из дочерей Дановых, — а отец его Тирянин, — умеющего делать"
    private val psalmWrong = "Смиренных возвышает Господь, а нечестивых унижает до землю."
    private val psalmRight = "Смиренных возвышает Господь, а нечестивых унижает до земли."

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun spb(name: String, vararg verseLines: String): File =
        File(temp, name).apply {
            writeText(
                buildString {
                    appendLine("##spDataVersion:\t1")
                    appendLine("##Title:\tСинодальный")
                    appendLine("-----")
                    verseLines.forEach { appendLine(it) }
                },
                Charsets.UTF_8,
            )
        }

    private fun verseLines(file: File) =
        file.readLines().dropWhile { it != "-----" }.drop(1).filter { it.isNotBlank() }

    // ── Wrong verse IDs ───────────────────────────────────────────────────────

    @Test
    fun `a corrupted verse id is rewritten without touching the rest of the row`() {
        val file = spb("ids.spb", "B019C147V096\t19\t146\t6\t$psalmRight")

        assertEquals(1, SpbVersePatcher.applyPatches(file))
        assertEquals("B019C147V006\t19\t146\t6\t$psalmRight", verseLines(file).single())
    }

    // ── Truncated and mis-worded texts ────────────────────────────────────────

    @Test
    fun `a truncated verse is completed`() {
        val file = spb("truncated.spb", "B014C002V014\t14\t2\t14\t$truncated")

        assertEquals(1, SpbVersePatcher.applyPatches(file))
        assertTrue(verseLines(file).single().endsWith("отца твоего."), "got '${verseLines(file).single()}'")
    }

    @Test
    fun `a verse already complete is left as it is`() {
        val complete = VersePatches.PATCHES[Triple(14, 2, 14)]!!.correctedText
        val file = spb("complete.spb", "B014C002V014\t14\t2\t14\t$complete")

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    @Test
    fun `a different translation of the same verse is never overwritten`() {
        // The patch only extends the text it was written against: anything else is another
        // translation, and completing it from the Synodal text would be a mistranslation.
        val other = "Сын женщины из колена Данова, отец его Тирянин, искусный в работе по золоту"
        val file = spb("other.spb", "B014C002V014\t14\t2\t14\t$other")

        assertEquals(0, SpbVersePatcher.applyPatches(file))
        assertEquals("B014C002V014\t14\t2\t14\t$other", verseLines(file).single())
    }

    @Test
    fun `a stub too short to identify is left for a human`() {
        val file = spb("stub.spb", "B014C002V014\t14\t2\t14\t...госпо")

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    @Test
    fun `an exact-match patch corrects the wording it names and nothing else`() {
        val file = spb("wording.spb", "B019C146V006\t19\t146\t6\t$psalmWrong")

        assertEquals(1, SpbVersePatcher.applyPatches(file))
        assertTrue(verseLines(file).single().endsWith(psalmRight))
    }

    @Test
    fun `an exact-match patch leaves a verse worded differently alone`() {
        val file = spb("wording-other.spb", "B019C146V006\t19\t146\t6\tГосподь возвышает смиренных.")

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    @Test
    fun `a row whose numbers are not numbers is passed through`() {
        val file = spb(
            "nonnumeric.spb",
            "B014C002V012\tx\t2\t14\t$truncated",
            "B014C002V013\t14\ty\t14\t$truncated",
            "B014C002V014\t14\t2\tz\t$truncated",
        )

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    // ── Verses missing from the file ──────────────────────────────────────────

    @Test
    fun `a missing verse is inserted after the verse it follows`() {
        val file = spb(
            "missing.spb",
            "B019C146V005\t19\t145\t5\tБлажен, кому помощник Бог Иаковлев",
            "B019C146V007\t19\t145\t7\tГосподь разрешает узников",
        )

        assertEquals(1, SpbVersePatcher.applyPatches(file))
        val ids = verseLines(file).map { it.substringBefore('\t') }
        assertEquals(listOf("B019C146V005", "B019C146V006", "B019C146V007"), ids)
    }

    @Test
    fun `a verse already present is not inserted a second time`() {
        val file = spb(
            "present.spb",
            "B019C146V005\t19\t145\t5\tБлажен, кому помощник Бог Иаковлев",
            "B019C146V006\t19\t145\t6\tСотворившего небо и землю",
        )

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    @Test
    fun `a file that never reaches that psalm is left alone`() {
        val file = spb("elsewhere.spb", "B001C001V001\t1\t1\t1\tВ начале сотворил Бог")

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    @Test
    fun `a blank row between verses does not stop the insertion`() {
        val file = spb(
            "blanks.spb",
            "B019C146V005\t19\t145\t5\tБлажен, кому помощник Бог Иаковлев",
            "",
            "B019C146V007\t19\t145\t7\tГосподь разрешает узников",
        )

        assertEquals(1, SpbVersePatcher.applyPatches(file))
        assertTrue(verseLines(file).any { it.startsWith("B019C146V006\t") })
    }

    @Test
    fun `a row too short to carry display numbers is not an insertion point`() {
        val file = spb("short.spb", "B019C146V005\t19\t145")

        assertEquals(0, SpbVersePatcher.applyPatches(file))
    }

    @Test
    fun `a row whose id is not a verse id is left where it is`() {
        val file = spb(
            "notaverse.spb",
            "#comment\t19\t145\t5\tsomething",
            "B019C146V005\t19\t145\t5\tБлажен, кому помощник Бог Иаковлев",
        )

        assertEquals(1, SpbVersePatcher.applyPatches(file))
        assertTrue(verseLines(file).first().startsWith("#comment"))
    }
}
