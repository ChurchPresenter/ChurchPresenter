package org.churchpresenter.cross.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The crossword puzzle text format and its at-rest obfuscation.
 *
 * `encode`/`decode` are a repeating-key XOR over base64 — **not** encryption, and nothing here
 * pretends otherwise. Their job is only to stop a `.xwp` file's answers being readable by anyone
 * who opens it in a text editor before the puzzle is played, so what these tests pin is that the
 * round trip is lossless (including for non-ASCII) and that the stored form is not plaintext.
 */
class EncoderTest {

    @Test
    fun `encoding then decoding returns the original text`() {
        val original = "# Fruits of the Spirit\nACROSS:\n1. Not war | PEACE"
        assertEquals(original, decode(encode(original)))
    }

    @Test
    fun `the encoded form is not the plaintext`() {
        val text = "PEACE"
        assertNotEquals(text, encode(text), "an answer must not be readable as-is in the file")
        assertTrue(!encode(text).contains("PEACE"))
    }

    @Test
    fun `a text longer than the key round-trips, so the key repeats correctly`() {
        // The key is 15 chars; this is comfortably longer, so every byte index wraps at least once.
        val long = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(4)
        assertEquals(long, decode(encode(long)))
    }

    @Test
    fun `non-ASCII text survives the round trip`() {
        // Puzzles are authored in the app's other languages too, so the XOR must be over UTF-8
        // bytes rather than chars — a char-wise XOR would mangle these.
        val text = "Плоды Духа — любовь, радость"
        assertEquals(text, decode(encode(text)))
    }

    @Test
    fun `an empty string round-trips`() {
        assertEquals("", decode(encode("")))
    }

    // ── toPlaintext ───────────────────────────────────────────────────────────

    private val clues = listOf(
        ClueEntry(1, Direction.ACROSS, "Not war", "PEACE"),
        ClueEntry(2, Direction.DOWN, "Fruit of the Spirit", "JOY"),
    )

    @Test
    fun `toPlaintext writes a title header and both direction sections`() {
        val text = toPlaintext(title = "Galatians 5", clues = clues)
        val lines = text.lines()
        assertEquals("# Galatians 5", lines.first())
        assertTrue(lines.contains("ACROSS:"))
        assertTrue(lines.contains("1. Not war | PEACE"))
        assertTrue(lines.contains("DOWN:"))
        assertTrue(lines.contains("2. Fruit of the Spirit | JOY"))
    }

    @Test
    fun `a section with no clues is omitted entirely`() {
        val acrossOnly = toPlaintext("T", listOf(ClueEntry(1, Direction.ACROSS, "c", "A")))
        assertTrue(!acrossOnly.contains("DOWN:"), "an empty section would parse back as nothing")
    }

    @Test
    fun `a layout section records the placed position and direction of each clue`() {
        val text = toPlaintext(
            "T", clues,
            layout = mapOf(1 to (0 to 0), 2 to (0 to 3)),
            placedDirections = mapOf(1 to Direction.ACROSS, 2 to Direction.DOWN),
        )
        assertTrue(text.contains("LAYOUT:"))
        assertTrue(text.contains("1 ACROSS 0 0"))
        assertTrue(text.contains("2 DOWN 0 3"))
    }

    @Test
    fun `the layout records the direction actually used, not the one authored`() {
        // The engine may flip a clue to its opposite direction to make it fit; the layout has to
        // record where it really ended up or the puzzle cannot be rebuilt from the file.
        val text = toPlaintext(
            "T", listOf(ClueEntry(1, Direction.ACROSS, "c", "PEACE")),
            layout = mapOf(1 to (0 to 0)),
            placedDirections = mapOf(1 to Direction.DOWN),
        )
        assertTrue(text.contains("1 DOWN 0 0"), "the placed direction wins over the authored one")
    }

    @Test
    fun `a clue missing from the layout is skipped rather than written with a wrong position`() {
        val text = toPlaintext("T", clues, layout = mapOf(1 to (0 to 0)))
        assertTrue(text.contains("1 ACROSS 0 0"))
        assertTrue(!text.contains("2 DOWN"), "an unplaced clue has no position to record")
    }

    // ── fromPlaintext ─────────────────────────────────────────────────────────

    @Test
    fun `a full document round-trips through toPlaintext and back`() {
        val text = toPlaintext("Galatians 5", clues)
        val (_, title, parsed) = fromPlaintext(text)!!
        assertEquals("Galatians 5", title)
        assertEquals(clues, parsed, "the format survives a full write-then-read cycle")
    }

    @Test
    fun `clues take the direction of the section they appear under`() {
        val (_, _, parsed) = fromPlaintext(
            """
            # T
            ACROSS:
            1. one | ALPHA
            DOWN:
            2. two | BETA
            3. three | GAMMA
            """.trimIndent()
        )!!
        assertEquals(Direction.ACROSS, parsed.single { it.number == 1 }.direction)
        assertEquals(Direction.DOWN, parsed.single { it.number == 2 }.direction)
        assertEquals(Direction.DOWN, parsed.single { it.number == 3 }.direction, "the section stays in effect")
    }

    @Test
    fun `answers are upper-cased on the way in`() {
        val (_, _, parsed) = fromPlaintext("# T\nACROSS:\n1. lower | peace")!!
        assertEquals("PEACE", parsed.single().answer, "the grid is built from upper-case letters")
    }

    @Test
    fun `a document with no title header is rejected`() {
        assertNull(fromPlaintext("ACROSS:\n1. one | ALPHA"), "the header is what identifies the format")
    }

    @Test
    fun `an empty document is rejected`() {
        assertNull(fromPlaintext(""))
        assertNull(fromPlaintext("   \n  \n"))
    }

    @Test
    fun `a malformed clue line is skipped and the rest still parse`() {
        val (_, _, parsed) = fromPlaintext(
            """
            # T
            ACROSS:
            1. fine | ALPHA
            this line has no number or pipe
            2. also fine | BETA
            """.trimIndent()
        )!!
        assertEquals(listOf("ALPHA", "BETA"), parsed.map { it.answer })
    }

    // ── fromPlaintextSimple ───────────────────────────────────────────────────

    @Test
    fun `the simple format numbers bare clue lines in order`() {
        val parsed = fromPlaintextSimple("Not war | PEACE\nGladness | JOY")!!
        assertEquals(listOf(1, 2), parsed.map { it.number })
        assertEquals(listOf("PEACE", "JOY"), parsed.map { it.answer })
        assertEquals("Not war", parsed.first().clue)
        assertTrue(parsed.all { it.direction == Direction.ACROSS }, "the simple form has no sections")
    }

    @Test
    fun `the simple format ignores headers, section labels and already-numbered lines`() {
        // Pasting a full document into the simple box should not produce doubled entries.
        val parsed = fromPlaintextSimple(
            """
            # A title
            ACROSS:
            1. numbered | ALPHA
            bare clue | BETA
            """.trimIndent()
        )!!
        assertEquals(listOf("BETA"), parsed.map { it.answer }, "only the bare line is taken")
    }

    @Test
    fun `text with no usable clue lines is rejected`() {
        assertNull(fromPlaintextSimple("just some prose with no pipe"))
        assertNull(fromPlaintextSimple(""))
    }

    @Test
    fun `the simple format upper-cases answers too`() {
        assertEquals("PEACE", fromPlaintextSimple("clue | peace")!!.single().answer)
    }
}
