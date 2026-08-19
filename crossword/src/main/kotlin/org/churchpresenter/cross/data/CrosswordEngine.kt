package org.churchpresenter.cross.data

/**
 * Lays a list of clues out as an interlocking grid.
 *
 * The longest answer goes down at the origin and every other answer is hung off a letter it shares
 * with one already placed, trying its own direction first and the opposite second. A pass that
 * places nothing ends the fill: a word that fits only once its neighbour exists is why the passes
 * repeat, and a word that never fits is simply left out — [RenderedPuzzle.placedNumbers] is what
 * says which made it in.
 */
object CrosswordEngine {

    fun build(clues: List<ClueEntry>): RenderedPuzzle? {
        val entries = clues.filter { it.answer.isNotBlank() }
        if (entries.isEmpty()) return null

        val layout = Layout()
        val directions = layout.fill(entries.sortedByDescending { it.answer.length })
        return layout.render(clues, directions)
    }

    private data class PlacedEntry(val entry: ClueEntry, val row: Int, val col: Int)

    /**
     * The grid as it is being filled: which letter sits in which cell, and which answer put it
     * there. Everything that reads or writes a cell lives here, so [build] itself is only the order
     * the work happens in.
     */
    private class Layout {
        private val grid = mutableMapOf<Pair<Int, Int>, Char>()
        private val placed = mutableListOf<PlacedEntry>()

        /** Places what it can of [sorted], and answers with the direction each placed answer took. */
        fun fill(sorted: List<ClueEntry>): Map<Int, Direction> {
            val first = sorted.first()
            place(first, 0, 0)
            val directions = mutableMapOf(first.number to first.direction)

            // Repeated passes, not one: an answer can be unplaceable until a later one gives it a
            // letter to cross. A pass that places nothing means no further pass can either.
            val waiting = sorted.drop(1).toMutableList()
            var before = -1
            while (waiting.isNotEmpty() && waiting.size != before) {
                before = waiting.size
                waiting.removeAll { entry ->
                    tryPlace(entry)?.also { directions[entry.number] = it } != null
                }
            }
            return directions
        }

        fun render(clues: List<ClueEntry>, directions: Map<Int, Direction>): RenderedPuzzle? {
            if (placed.isEmpty()) return null

            // Normalized so the top-left of what was actually laid out is (0,0): the fill grows in
            // every direction from the origin, so half the cells are at negative coordinates.
            val minRow = grid.keys.minOf { it.first }
            val minCol = grid.keys.minOf { it.second }
            val cells = grid.mapKeys { (pos, _) -> (pos.first - minRow) to (pos.second - minCol) }
            val starts = placed.map { it.copy(row = it.row - minRow, col = it.col - minCol) }

            // One number per starting cell in reading order, so two answers that begin in the same
            // cell share a number rather than being numbered twice.
            val numbers = starts.map { it.row to it.col }
                .distinct()
                .sortedWith(compareBy({ it.first }, { it.second }))
                .mapIndexed { index, pos -> pos to index + 1 }
                .toMap()

            return RenderedPuzzle(
                grid = cells.mapValues { (pos, letter) -> GridCell(letter, numbers[pos]) },
                rows = cells.keys.maxOf { it.first } + 1,
                cols = cells.keys.maxOf { it.second } + 1,
                clues = clues,
                placedNumbers = placed.map { it.entry.number }.toSet(),
                placedDirections = directions,
                placedPositions = starts.associate { it.entry.number to (it.row to it.col) },
            )
        }

        /** The direction [entry] ended up in, or null if it fits nowhere yet. */
        private fun tryPlace(entry: ClueEntry): Direction? =
            listOf(entry.direction, entry.direction.opposite())
                .firstOrNull { direction -> placeSomewhere(entry.copy(direction = direction)) }

        private fun placeSomewhere(candidate: ClueEntry): Boolean {
            val spot = crossings(candidate).firstOrNull { fits(candidate, it.first, it.second) }
                ?: return false
            place(candidate, spot.first, spot.second)
            return true
        }

        /** Every origin at which [candidate] would cross an already-placed answer on a shared letter. */
        private fun crossings(candidate: ClueEntry): List<Pair<Int, Int>> =
            placed.toList()
                .filter { it.entry.direction != candidate.direction }
                .flatMap { other ->
                    sharedLetters(candidate.answer, other.entry.answer).map { (own, theirs) ->
                        if (candidate.direction == Direction.ACROSS) {
                            (other.row + theirs) to (other.col - own)
                        } else {
                            (other.row - own) to (other.col + theirs)
                        }
                    }
                }

        /**
         * Whether [entry] can be laid down at ([row], [col]).
         *
         * Three separate questions, and all three have to hold: every cell it wants is either free
         * with nothing running alongside it or already holds the same letter; it touches what is
         * there rather than floating free; and neither end runs straight into another word.
         */
        private fun fits(entry: ClueEntry, row: Int, col: Int): Boolean {
            val cells = cellsOf(entry, row, col)
            return cells.all { cellIsFree(it, entry.direction) } &&
                (placed.isEmpty() || cells.any { it.first in grid }) &&
                endsAreClear(entry, row, col)
        }

        private fun cellIsFree(cell: Pair<Pair<Int, Int>, Char>, direction: Direction): Boolean {
            val (pos, letter) = cell
            grid[pos]?.let { return it == letter }
            // Nothing may run alongside: two words side by side would read as words of their own
            // across the join.
            val (before, after) = if (direction == Direction.ACROSS) {
                (pos.first - 1 to pos.second) to (pos.first + 1 to pos.second)
            } else {
                (pos.first to pos.second - 1) to (pos.first to pos.second + 1)
            }
            return before !in grid && after !in grid
        }

        private fun endsAreClear(entry: ClueEntry, row: Int, col: Int): Boolean {
            val length = entry.answer.length
            val (before, after) = if (entry.direction == Direction.ACROSS) {
                (row to col - 1) to (row to col + length)
            } else {
                (row - 1 to col) to (row + length to col)
            }
            return before !in grid && after !in grid
        }

        private fun place(entry: ClueEntry, row: Int, col: Int) {
            cellsOf(entry, row, col).forEach { (pos, letter) -> grid[pos] = letter }
            placed += PlacedEntry(entry, row, col)
        }

    }

    /** Which cells [entry] would occupy at ([row], [col]), and the letter each would hold. */
    private fun cellsOf(entry: ClueEntry, row: Int, col: Int): List<Pair<Pair<Int, Int>, Char>> =
        entry.answer.mapIndexed { i, letter ->
            val pos = if (entry.direction == Direction.ACROSS) row to col + i else row + i to col
            pos to letter
        }

    /** Every (index in [own], index in [other]) pair whose letters match. */
    private fun sharedLetters(own: String, other: String): List<Pair<Int, Int>> =
        own.indices.flatMap { i -> other.indices.filter { own[i] == other[it] }.map { i to it } }

    private fun Direction.opposite(): Direction =
        if (this == Direction.ACROSS) Direction.DOWN else Direction.ACROSS
}
