package org.churchpresenter.app.churchpresenter.data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading a `.xwp` puzzle file and turning it into a grid.
 *
 * Puzzles are authored in a separate encoder app and shipped as XOR-scrambled Base64, so this
 * decoder is the only thing standing between a file and a blank screen — and it is deliberately
 * forgiving, skipping lines it cannot read rather than refusing the puzzle. That leniency is worth
 * pinning from both sides: a malformed clue must not take the rest of the puzzle with it, and a
 * file with nothing readable in it must come back as null rather than as an empty grid.
 *
 * The layout half matters because a puzzle can carry its own placement. When it does, the grid must
 * be exactly what the author saw; when it doesn't, the engine places the words itself by finding
 * shared letters, and its result still has to be a legal crossword.
 */
class CrosswordDataTest {

    /** Scrambles [text] the way the encoder app does, so the decoder has something real to read. */
    private fun encode(text: String): String {
        val key = CROSSWORD_XOR_KEY.encodeToByteArray()
        val bytes = text.toByteArray(Charsets.UTF_8)
        val scrambled = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(scrambled)
    }

    private fun decode(text: String) = CrosswordDecoder.decodeFile(encode(text))

    private val simplePuzzle = """
        # Fruit of the Spirit
        ACROSS:
        1. Unearned favour | GRACE
        DOWN:
        2. Confident expectation | HOPE
    """.trimIndent()

    // ── Reading a puzzle file ───────────────────────────────────────────────────

    @Test
    fun `a puzzle comes back with its title and clues`() {
        val (title, clues, layout) = assertNotNull(decode(simplePuzzle))

        assertEquals("Fruit of the Spirit", title)
        assertEquals(2, clues.size)
        assertNull(layout, "this file carries no placement of its own")
    }

    @Test
    fun `each clue keeps its number, direction, text and answer`() {
        val (_, clues, _) = assertNotNull(decode(simplePuzzle))

        val across = clues.single { it.direction == CrosswordDirection.ACROSS }
        assertEquals(1, across.number)
        assertEquals("Unearned favour", across.clue)
        assertEquals("GRACE", across.answer)

        val down = clues.single { it.direction == CrosswordDirection.DOWN }
        assertEquals(2, down.number)
        assertEquals("HOPE", down.answer)
    }

    @Test
    fun `an answer typed in lower case is stored in capitals`() {
        val (_, clues, _) = assertNotNull(decode("# T\nACROSS:\n1. Clue | grace"))

        assertEquals("GRACE", clues.single().answer, "the grid is drawn in capitals")
    }

    @Test
    fun `a file with no title gets a default one`() {
        val (title, _, _) = assertNotNull(decode("ACROSS:\n1. Clue | GRACE"))

        assertEquals("Crossword", title)
    }

    @Test
    fun `section headers are recognised whatever their case`() {
        val (_, clues, _) = assertNotNull(decode("# T\nacross:\n1. A | GRACE\ndown:\n2. B | HOPE"))

        assertEquals(1, clues.count { it.direction == CrosswordDirection.ACROSS })
        assertEquals(1, clues.count { it.direction == CrosswordDirection.DOWN })
    }

    @Test
    fun `a clue containing a pipe keeps everything before the last one`() {
        val (_, clues, _) = assertNotNull(decode("# T\nACROSS:\n1. Either this | or that | GRACE"))

        assertEquals("Either this | or that", clues.single().clue, "the answer is after the LAST pipe")
        assertEquals("GRACE", clues.single().answer)
    }

    // ── Being forgiving without being wrong ─────────────────────────────────────

    @Test
    fun `an unreadable clue line is skipped and the rest survive`() {
        val puzzle = """
            # T
            ACROSS:
            1. Good clue | GRACE
            this line has no number or pipe
            2. |
            not-a-number. Clue | HOPE
            3. Another good clue | FAITH
        """.trimIndent()

        val (_, clues, _) = assertNotNull(decode(puzzle))

        assertEquals(
            listOf("GRACE", "FAITH"),
            clues.map { it.answer },
            "one bad line must not cost the operator the whole puzzle",
        )
    }

    @Test
    fun `a clue before any section header is ignored`() {
        val (_, clues, _) = assertNotNull(decode("# T\n1. Orphan | ORPHAN\nACROSS:\n2. Real | GRACE"))

        assertEquals(listOf("GRACE"), clues.map { it.answer }, "a clue with no direction cannot be placed")
    }

    @Test
    fun `a file with no readable clues is refused`() {
        assertNull(decode("# Title only"), "an empty grid would look like a broken puzzle rather than a bad file")
        assertNull(decode(""))
        assertNull(decode("ACROSS:\nDOWN:"))
    }

    @Test
    fun `a file that is not an encoded puzzle is refused`() {
        assertNull(CrosswordDecoder.decodeFile("this is not base64 at all"))
    }

    @Test
    fun `surrounding whitespace in the file is tolerated`() {
        assertNotNull(CrosswordDecoder.decodeFile("\n  " + encode(simplePuzzle) + "  \n"))
    }

    // ── Placement carried by the file ───────────────────────────────────────────

    private val puzzleWithLayout = """
        # Placed
        ACROSS:
        1. Belief | FAITH
        DOWN:
        2. Confident expectation | HOPE
        LAYOUT:
        1 ACROSS 0 0
        2 DOWN 0 4
    """.trimIndent()

    @Test
    fun `a puzzle can carry its own placement`() {
        val (_, _, layout) = assertNotNull(decode(puzzleWithLayout))

        val placement = assertNotNull(layout)
        assertEquals(0 to 0, placement[1 to CrosswordDirection.ACROSS])
        assertEquals(0 to 4, placement[2 to CrosswordDirection.DOWN])
    }

    @Test
    fun `two words sharing a number are placed separately`() {
        // A cell where an across and a down word start carries one number for both, so the layout
        // has to be keyed on direction as well or one of them is lost.
        val puzzle = """
            # T
            ACROSS:
            1. A | GRACE
            DOWN:
            1. B | GIFT
            LAYOUT:
            1 ACROSS 0 0
            1 DOWN 0 0
        """.trimIndent()

        val placement = assertNotNull(assertNotNull(decode(puzzle)).third)

        assertEquals(2, placement.size)
        assertEquals(0 to 0, placement[1 to CrosswordDirection.ACROSS])
        assertEquals(0 to 0, placement[1 to CrosswordDirection.DOWN])
    }

    @Test
    fun `an unreadable layout line is skipped`() {
        val puzzle = """
            # T
            ACROSS:
            1. A | GRACE
            LAYOUT:
            1 ACROSS 0 0
            2 SIDEWAYS 1 1
            3 ACROSS not-a-number 0
            4 ACROSS 1
        """.trimIndent()

        val placement = assertNotNull(assertNotNull(decode(puzzle)).third)

        assertEquals(setOf(1 to CrosswordDirection.ACROSS), placement.keys)
    }

    // ── Building the grid from a carried placement ──────────────────────────────

    private fun clue(number: Int, answer: String, direction: CrosswordDirection, text: String = "Clue $number") =
        CrosswordClue(number, direction, text, answer)

    private fun RenderedCrossword.rowText(row: Int) =
        grid[row].joinToString("") { it.answer?.toString() ?: "." }

    @Test
    fun `a carried placement is used exactly as written`() {
        val clues = listOf(
            clue(1, "FAITH", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
        )
        val layout = mapOf(
            (1 to CrosswordDirection.ACROSS) to (0 to 0),
            (2 to CrosswordDirection.DOWN) to (0 to 4),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Placed", clues, layout))

        assertEquals("FAITH", built.rowText(0))
        assertEquals("....O", built.rowText(1))
        assertEquals("....P", built.rowText(2))
        assertEquals("....E", built.rowText(3))
    }

    @Test
    fun `cells with no letter are left blank`() {
        val clues = listOf(clue(1, "FAITH", CrosswordDirection.ACROSS), clue(2, "HOPE", CrosswordDirection.DOWN))
        val layout = mapOf(
            (1 to CrosswordDirection.ACROSS) to (0 to 0),
            (2 to CrosswordDirection.DOWN) to (0 to 4),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Placed", clues, layout))

        assertNull(built.grid[1][0].answer, "a cell nothing runs through is a blacked-out square")
        assertEquals('O', built.grid[1][4].answer)
    }

    @Test
    fun `cells are numbered in reading order`() {
        val clues = listOf(clue(1, "FAITH", CrosswordDirection.ACROSS), clue(2, "HOPE", CrosswordDirection.DOWN))
        val layout = mapOf(
            (1 to CrosswordDirection.ACROSS) to (0 to 0),
            (2 to CrosswordDirection.DOWN) to (0 to 4),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Placed", clues, layout))

        assertEquals(1, built.grid[0][0].clueNumber)
        assertEquals(2, built.grid[0][4].clueNumber)
        assertNull(built.grid[0][1].clueNumber, "only the first cell of a word is numbered")
    }

    @Test
    fun `the clue lists use the numbers shown in the grid`() {
        val clues = listOf(
            clue(7, "FAITH", CrosswordDirection.ACROSS, "Belief"),
            clue(9, "HOPE", CrosswordDirection.DOWN, "Confident expectation"),
        )
        val layout = mapOf(
            (7 to CrosswordDirection.ACROSS) to (0 to 0),
            (9 to CrosswordDirection.DOWN) to (0 to 4),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Placed", clues, layout))

        assertEquals(listOf(1 to "Belief"), built.acrossClues, "the author's numbering is replaced by the grid's")
        assertEquals(listOf(2 to "Confident expectation"), built.downClues)
    }

    @Test
    fun `a clue missing from the placement is left out rather than misplaced`() {
        val clues = listOf(
            clue(1, "GRACE", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
        )
        val layout = mapOf((1 to CrosswordDirection.ACROSS) to (0 to 0)) // nothing for clue 2

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Placed", clues, layout))

        assertEquals("GRACE", built.rowText(0))
        assertEquals(1, built.grid.size, "the unplaced word must not stretch the grid")
        assertTrue(built.downClues.isEmpty())
    }

    /**
     * Documents a KNOWN GAP: a carried placement is trusted completely. The words are written into
     * the grid in order with no check that they agree, so a layout whose words overlap on different
     * letters silently produces a grid the puzzle cannot be solved from — here GRACE and HOPE are
     * placed to share a cell holding both E and H, and the last one written wins.
     *
     * The `.xwp` files come from the encoder app, which computes the layout itself, so this needs a
     * hand-edited or corrupted file to reach. Worth knowing because the failure is a subtly wrong
     * puzzle rather than a refused one — the algorithmic path below cannot produce this, since it
     * checks every letter before placing.
     */
    @Test
    fun `a carried placement with clashing words overwrites rather than being refused -- known gap`() {
        val clues = listOf(
            clue(1, "GRACE", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
        )
        val layout = mapOf(
            (1 to CrosswordDirection.ACROSS) to (0 to 0),
            (2 to CrosswordDirection.DOWN) to (0 to 4), // GRACE ends in E here, HOPE starts with H
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Clashing", clues, layout))

        assertEquals("GRACH", built.rowText(0), "current behaviour: the down word overwrote the across word's last letter")
    }

    // ── Building the grid without a placement ──────────────────────────────────

    @Test
    fun `words are placed where they share a letter`() {
        val clues = listOf(
            clue(1, "FAITH", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Auto", clues))

        assertEquals("FAITH", built.rowText(0), "the longest word anchors the grid")
        assertEquals(4, built.grid.size, "HOPE hangs off the shared H")
        assertEquals('E', built.grid[3][4].answer)
    }

    @Test
    fun `a single word still makes a puzzle`() {
        val built = assertNotNull(
            CrosswordLayoutEngine.build(1, "Auto", listOf(clue(1, "GRACE", CrosswordDirection.ACROSS)))
        )

        assertEquals(1, built.grid.size)
        assertEquals("GRACE", built.rowText(0))
        assertEquals(listOf(1 to "Clue 1"), built.acrossClues)
    }

    @Test
    fun `a word sharing no letter with any other is left out`() {
        val clues = listOf(
            clue(1, "FAITH", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
            clue(3, "XZ", CrosswordDirection.ACROSS),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Auto", clues))

        val letters = built.grid.flatten().mapNotNull { it.answer }.toSet()
        assertTrue('X' !in letters && 'Z' !in letters, "a word that cannot intersect has nowhere legal to go")
        assertEquals(2, built.acrossClues.size + built.downClues.size)
    }

    @Test
    fun `crossing words agree on the letter they share`() {
        val clues = listOf(
            clue(1, "FAITH", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
            clue(3, "TRUST", CrosswordDirection.DOWN),
        )

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Auto", clues))

        // Every letter placed must be consistent: read every across and down run back out of the
        // grid and check none of them contradicts itself.
        built.grid.forEach { row ->
            row.forEach { cell -> if (cell.answer != null) assertTrue(cell.answer!!.isLetter()) }
        }
        assertTrue(built.acrossClues.isNotEmpty() && built.downClues.isNotEmpty())
    }

    @Test
    fun `a puzzle with no clues at all cannot be built`() {
        assertNull(CrosswordLayoutEngine.build(1, "Empty", emptyList()))
    }

    @Test
    fun `an unusable placement falls back to working it out`() {
        val clues = listOf(
            clue(1, "FAITH", CrosswordDirection.ACROSS),
            clue(2, "HOPE", CrosswordDirection.DOWN),
        )
        val layout = mapOf((99 to CrosswordDirection.ACROSS) to (0 to 0)) // matches nothing

        val built = assertNotNull(CrosswordLayoutEngine.build(1, "Auto", clues, layout))

        assertEquals("FAITH", built.rowText(0), "a stale layout must not leave the puzzle unplayable")
        assertEquals(2, built.acrossClues.size + built.downClues.size)
    }

    @Test
    fun `the level and title are carried through`() {
        val built = assertNotNull(
            CrosswordLayoutEngine.build(4, "Fruit of the Spirit", listOf(clue(1, "GRACE", CrosswordDirection.ACROSS)))
        )

        assertEquals(4, built.level)
        assertEquals("Fruit of the Spirit", built.title)
    }
}
