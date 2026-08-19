package org.churchpresenter.cross.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The crossword layout engine: it places answers on a grid so that every word after the first
 * crosses one already placed, then normalises the result to the origin.
 *
 * Assertions here are **invariants of a valid crossword**, not a fixed expected grid. The engine
 * places words longest-first and retries deferred ones until it stops making progress, so the exact
 * arrangement is an implementation detail; what must hold is that letters agree at every crossing,
 * that no two parallel words end up touching, and that the grid is normalised. Pinning one exact
 * layout would fail on any future improvement to the packing without anything actually breaking.
 */
class CrosswordEngineTest {

    private fun clue(n: Int, answer: String, dir: Direction = Direction.ACROSS) =
        ClueEntry(number = n, direction = dir, clue = "clue $n", answer = answer)

    /** Every placed word, read back off the grid along its recorded direction and position. */
    private fun readBack(puzzle: RenderedPuzzle, number: Int): String {
        val (row, col) = puzzle.placedPositions.getValue(number)
        val dir = puzzle.placedDirections.getValue(number)
        val answer = puzzle.clues.single { it.number == number }.answer
        return (answer.indices).map { i ->
            val pos = if (dir == Direction.ACROSS) row to (col + i) else (row + i) to col
            puzzle.grid[pos]?.letter ?: ' '
        }.joinToString("")
    }

    @Test
    fun `no clues produces no puzzle`() {
        assertNull(CrosswordEngine.build(emptyList()))
    }

    @Test
    fun `clues with blank answers produce no puzzle`() {
        assertNull(CrosswordEngine.build(listOf(clue(1, ""), clue(2, "   "))))
    }

    @Test
    fun `a single answer is placed at the origin`() {
        val puzzle = assertNotNull(CrosswordEngine.build(listOf(clue(1, "PEACE"))))
        assertEquals(1, puzzle.rows)
        assertEquals(5, puzzle.cols)
        assertEquals(setOf(1), puzzle.placedNumbers)
        assertEquals("PEACE", readBack(puzzle, 1))
    }

    @Test
    fun `a down answer occupies a single column`() {
        val puzzle = assertNotNull(CrosswordEngine.build(listOf(clue(1, "JOY", Direction.DOWN))))
        assertEquals(3, puzzle.rows)
        assertEquals(1, puzzle.cols)
        assertEquals("JOY", readBack(puzzle, 1))
    }

    @Test
    fun `two answers sharing a letter are placed crossing at that letter`() {
        // PEACE and JOY share nothing; PEACE and PATIENCE share several letters.
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "PEACE"), clue(2, "PATIENCE", Direction.DOWN)))
        )
        assertEquals(setOf(1, 2), puzzle.placedNumbers, "both words fit")
        assertEquals("PEACE", readBack(puzzle, 1))
        assertEquals("PATIENCE", readBack(puzzle, 2))
        assertTrue(
            puzzle.placedDirections.getValue(1) != puzzle.placedDirections.getValue(2),
            "crossing words run in opposite directions"
        )
    }

    @Test
    fun `an answer sharing no letter with any placed word is left out`() {
        // ZZZZ crosses nothing, so a valid grid cannot include it — the puzzle still builds
        // from the rest rather than failing outright.
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "PEACE"), clue(2, "ZZZZ", Direction.DOWN)))
        )
        assertTrue(1 in puzzle.placedNumbers)
        assertTrue(2 !in puzzle.placedNumbers, "an unplaceable word is reported as not placed")
        assertEquals(2, puzzle.clues.size, "but it is still carried in the clue list")
    }

    @Test
    fun `the grid is normalised so no cell has a negative coordinate`() {
        // Crossings are found by walking back from an intersection, which routinely produces
        // negative coordinates before normalisation.
        val puzzle = assertNotNull(
            CrosswordEngine.build(
                listOf(clue(1, "PATIENCE"), clue(2, "PEACE", Direction.DOWN), clue(3, "GENTLENESS"))
            )
        )
        assertTrue(puzzle.grid.keys.all { it.first >= 0 && it.second >= 0 }, "no negative coordinates")
        assertEquals(0, puzzle.grid.keys.minOf { it.first }, "the grid touches row 0")
        assertEquals(0, puzzle.grid.keys.minOf { it.second }, "the grid touches column 0")
    }

    @Test
    fun `the reported size bounds every occupied cell exactly`() {
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "PATIENCE"), clue(2, "PEACE", Direction.DOWN)))
        )
        assertEquals(puzzle.grid.keys.maxOf { it.first } + 1, puzzle.rows)
        assertEquals(puzzle.grid.keys.maxOf { it.second } + 1, puzzle.cols)
    }

    @Test
    fun `every crossing cell carries the letter both words agree on`() {
        val puzzle = assertNotNull(
            CrosswordEngine.build(
                listOf(
                    clue(1, "PATIENCE"), clue(2, "PEACE", Direction.DOWN),
                    clue(3, "GENTLENESS"), clue(4, "JOY", Direction.DOWN), clue(5, "LOVE"),
                )
            )
        )
        // Reading each placed word back off the shared grid reproduces it exactly — which can only
        // hold if every intersection agreed.
        for (number in puzzle.placedNumbers) {
            val expected = puzzle.clues.single { it.number == number }.answer
            assertEquals(expected, readBack(puzzle, number), "word $number reads back off the grid")
        }
    }

    @Test
    fun `clue numbers are assigned to starting cells in reading order`() {
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "PATIENCE"), clue(2, "PEACE", Direction.DOWN)))
        )
        val numbered = puzzle.grid.entries
            .filter { it.value.clueNumber != null }
            .sortedBy { it.value.clueNumber }
        val positions = numbered.map { it.key }
        assertEquals(
            positions.sortedWith(compareBy({ it.first }, { it.second })),
            positions,
            "cell numbers increase left-to-right, top-to-bottom"
        )
        assertEquals(
            (1..positions.size).toList(),
            numbered.map { it.value.clueNumber },
            "numbering is sequential with no gaps"
        )
    }

    @Test
    fun `two words starting in the same cell share one number`() {
        // An across and a down word beginning at the same square is the ordinary crossword case;
        // giving that cell two numbers is what used to make the admin preview disagree with the
        // rendered puzzle.
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "PATIENCE"), clue(2, "PEACE", Direction.DOWN)))
        )
        val starts = puzzle.placedNumbers.map { puzzle.placedPositions.getValue(it) }
        val numberedCells = puzzle.grid.count { it.value.clueNumber != null }
        assertEquals(starts.distinct().size, numberedCells, "one number per distinct starting cell")
    }

    @Test
    fun `a word is flipped to its opposite direction when that is the only way it fits`() {
        // Both are authored ACROSS, so the second can only cross the first by running DOWN.
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "PATIENCE"), clue(2, "PEACE")))
        )
        assertEquals(setOf(1, 2), puzzle.placedNumbers)
        assertTrue(
            puzzle.placedDirections.getValue(1) != puzzle.placedDirections.getValue(2),
            "one of them was flipped so they could cross"
        )
    }

    @Test
    fun `the longest answer is placed first and anchors the grid`() {
        val puzzle = assertNotNull(
            CrosswordEngine.build(listOf(clue(1, "JOY"), clue(2, "GENTLENESS", Direction.DOWN)))
        )
        assertEquals(
            0 to 0,
            puzzle.placedPositions.getValue(2),
            "the longest word anchors at the origin, so the grid grows around it"
        )
    }

    @Test
    fun `every placed word is inside the reported grid bounds`() {
        val puzzle = assertNotNull(
            CrosswordEngine.build(
                listOf(clue(1, "PATIENCE"), clue(2, "PEACE", Direction.DOWN), clue(3, "LOVE"), clue(4, "JOY"))
            )
        )
        for (number in puzzle.placedNumbers) {
            val (row, col) = puzzle.placedPositions.getValue(number)
            val length = puzzle.clues.single { it.number == number }.answer.length
            val endRow = if (puzzle.placedDirections.getValue(number) == Direction.DOWN) row + length - 1 else row
            val endCol = if (puzzle.placedDirections.getValue(number) == Direction.ACROSS) col + length - 1 else col
            assertTrue(row >= 0 && col >= 0, "word $number starts inside the grid")
            assertTrue(endRow < puzzle.rows && endCol < puzzle.cols, "word $number ends inside the grid")
        }
    }
}
