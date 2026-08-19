package org.churchpresenter.app.churchpresenter.data

import java.util.Base64

private const val CLUE_LINE_FIELDS = 4

// XOR key shared with the :crossword encoder app
internal const val CROSSWORD_XOR_KEY = "CHURCHPRESENTER"

enum class CrosswordDirection { ACROSS, DOWN }

data class CrosswordClue(
    val number: Int,
    val direction: CrosswordDirection,
    val clue: String,
    val answer: String
)

data class CrosswordCell(
    val answer: Char?,          // null = black/blocked cell
    val clueNumber: Int? = null // smallest clue number starting at this cell
)

data class RenderedCrossword(
    val level: Int,
    val title: String,
    val grid: List<List<CrosswordCell>>,
    val acrossClues: List<Pair<Int, String>>,
    val downClues: List<Pair<Int, String>>
)

internal data class PlacedEntry(
    val number: Int,
    val answer: String,
    val row: Int,
    val col: Int,
    val direction: CrosswordDirection
)

// ---------------------------------------------------------------------------
// Decoder — mirrors the encoder in the :crossword module
// ---------------------------------------------------------------------------

object CrosswordDecoder {

    /**
     * Decodes a Base64 + XOR encoded puzzle file.
     * Returns a Triple of (title, clues, layout) where layout maps clue number →
     * (direction, row, col), or null if the file is malformed or empty.
     * Layout is null when the file predates the LAYOUT section.
     */
    fun decodeFile(base64Content: String): Triple<String, List<CrosswordClue>, Map<Pair<Int, CrosswordDirection>, Pair<Int, Int>>?>? = try {
        val bytes = Base64.getDecoder().decode(base64Content.trim())
        val keyBytes = CROSSWORD_XOR_KEY.encodeToByteArray()
        val decoded = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        parseText(String(decoded, Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    /**
     * Parses plaintext puzzle format:
     *
     *   # Title
     *   ACROSS:
     *   1. Clue text | ANSWER
     *   DOWN:
     *   2. Clue text | ANSWER
     */
    private fun putLayoutEntry(
        line: String,
        layout: MutableMap<Pair<Int, CrosswordDirection>, Pair<Int, Int>>,
    ) {
        val (key, position) = parseLayoutLine(line) ?: return
        layout[key] = position
    }

    private fun addClue(line: String, direction: CrosswordDirection?, clues: MutableList<CrosswordClue>) {
        val dir = direction ?: return
        parseClueLine(line, dir)?.let { clues.add(it) }
    }

    private fun parseText(text: String): Triple<String, List<CrosswordClue>, Map<Pair<Int, CrosswordDirection>, Pair<Int, Int>>?>? {
        var title = "Crossword"
        val clues = mutableListOf<CrosswordClue>()
        // Key is (clueNumber, direction) so two words sharing the same sequential number
        // but running in different directions both survive in the map.
        val layout = mutableMapOf<Pair<Int, CrosswordDirection>, Pair<Int, Int>>()
        var direction: CrosswordDirection? = null
        var inLayout = false

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#") -> { title = line.removePrefix("#").trim(); inLayout = false }
                line.isBlank() -> Unit
                line.equals("ACROSS:", ignoreCase = true) -> { direction = CrosswordDirection.ACROSS; inLayout = false }
                line.equals("DOWN:", ignoreCase = true) -> { direction = CrosswordDirection.DOWN; inLayout = false }
                line.equals("LAYOUT:", ignoreCase = true) -> inLayout = true
                inLayout -> putLayoutEntry(line, layout)
                else -> addClue(line, direction, clues)
            }
        }
        return if (clues.isEmpty()) null
        else Triple(title, clues, if (layout.isEmpty()) null else layout)
    }

    /** `"1 ACROSS 3 5"` → the clue key and its (row, col) origin, or null if the line is malformed. */
    private fun parseLayoutLine(line: String): Pair<Pair<Int, CrosswordDirection>, Pair<Int, Int>>? {
        val parts = line.split(" ").takeIf { it.size == CLUE_LINE_FIELDS } ?: return null
        val num = parts[0].toIntOrNull()
        val dir = when (parts[1].uppercase()) {
            "ACROSS" -> CrosswordDirection.ACROSS
            "DOWN" -> CrosswordDirection.DOWN
            else -> null
        }
        if (num == null || dir == null) return null
        val row = parts[2].toIntOrNull()
        val col = parts[3].toIntOrNull()
        return if (row != null && col != null) (num to dir) to (row to col) else null
    }

    /** `"1. Clue text | ANSWER"` → the clue, or null if the line is malformed or has no answer. */
    private fun parseClueLine(line: String, dir: CrosswordDirection): CrosswordClue? {
        val dotIdx = line.indexOf('.')
        val pipeIdx = line.lastIndexOf('|')
        if (dotIdx < 0 || pipeIdx < 0 || pipeIdx <= dotIdx) return null
        val number = line.substring(0, dotIdx).trim().toIntOrNull() ?: return null
        val clueText = line.substring(dotIdx + 1, pipeIdx).trim()
        val answer = line.substring(pipeIdx + 1).trim().uppercase()
        return if (answer.isBlank()) null else CrosswordClue(number, dir, clueText, answer)
    }
}

// ---------------------------------------------------------------------------
// Layout engine — places words in a grid by finding letter intersections
// ---------------------------------------------------------------------------

object CrosswordLayoutEngine {

    /**
     * Builds a [RenderedCrossword] from a list of clues by auto-generating the grid.
     * Words are placed greedily: longest first, each subsequent word perpendicular to
     * an already-placed word sharing a common letter.
     * Returns null if no words could be placed.
     */
    fun build(
        level: Int,
        title: String,
        clues: List<CrosswordClue>,
        precomputedLayout: Map<Pair<Int, CrosswordDirection>, Pair<Int, Int>>? = null
    ): RenderedCrossword? {
        if (clues.isEmpty()) return null

        // Use pre-computed layout from the .xwp file when available — avoids re-running the
        // placement algorithm which could produce a different (but equally valid) arrangement.
        // Key is (clueNumber, direction) so ACROSS and DOWN words sharing the same cell number
        // are both found correctly.
        if (precomputedLayout != null) {
            val gridMap = mutableMapOf<Pair<Int, Int>, Char>()
            val placed = mutableListOf<PlacedEntry>()
            for (clue in clues) {
                val (row, col) = precomputedLayout[clue.number to clue.direction] ?: continue
                placeWord(gridMap, clue.answer, row, col, clue.direction)
                placed += PlacedEntry(clue.number, clue.answer, row, col, clue.direction)
            }
            if (placed.isNotEmpty()) return renderGrid(level, title, placed, gridMap, clues)
            // Fall through to algorithmic placement if layout data was unusable
        }

        val sorted = clues.sortedByDescending { it.answer.length }
        val gridMap = mutableMapOf<Pair<Int, Int>, Char>()
        val placed = mutableListOf<PlacedEntry>()

        // Place first word at origin in its specified direction
        val first = sorted[0]
        placeWord(gridMap, first.answer, 0, 0, first.direction)
        placed += PlacedEntry(first.number, first.answer, 0, 0, first.direction)

        // Place remaining words; retry until no more progress (later words may unlock earlier ones)
        val deferred = sorted.drop(1).toMutableList()
        var lastSize = -1
        while (deferred.isNotEmpty() && deferred.size != lastSize) {
            lastSize = deferred.size
            val iter = deferred.iterator()
            while (iter.hasNext()) {
                val clue = iter.next()
                val pos = findPlacement(gridMap, placed, clue) ?: continue
                placeWord(gridMap, clue.answer, pos.first, pos.second, pos.third)
                placed += PlacedEntry(clue.number, clue.answer, pos.first, pos.second, pos.third)
                iter.remove()
            }
        }

        if (placed.isEmpty()) return null
        return renderGrid(level, title, placed, gridMap, clues)
    }

    private fun placeWord(
        grid: MutableMap<Pair<Int, Int>, Char>,
        word: String, row: Int, col: Int,
        dir: CrosswordDirection
    ) {
        word.forEachIndexed { i, ch ->
            val pos = if (dir == CrosswordDirection.ACROSS) row to col + i else row + i to col
            grid[pos] = ch
        }
    }

    /** Returns (startRow, startCol, direction) of the best valid placement, or null.
     *  Tries the clue's specified direction first; falls back to the opposite direction
     *  if no valid placement is found, mirroring the admin tool's tryPlace() logic. */
    internal fun findPlacement(
        grid: Map<Pair<Int, Int>, Char>,
        placed: List<PlacedEntry>,
        clue: CrosswordClue
    ): Triple<Int, Int, CrosswordDirection>? {
        val opposite = if (clue.direction == CrosswordDirection.ACROSS) CrosswordDirection.DOWN else CrosswordDirection.ACROSS
        for (dir in listOf(clue.direction, opposite)) {
            for (pw in placed) {
                // Only intersect words running in the opposite direction
                if (pw.direction == dir) continue
                crossingAt(grid, clue, pw, dir)?.let { return it }
            }
        }
        return null
    }

    /** Where [clue] can sit crossing [pw] in [dir], or null when no shared letter fits. */
    private fun crossingAt(
        grid: Map<Pair<Int, Int>, Char>,
        clue: CrosswordClue,
        pw: PlacedEntry,
        dir: CrosswordDirection
    ): Triple<Int, Int, CrosswordDirection>? {
        for ((i, ch) in clue.answer.withIndex()) {
            for ((j, pwCh) in pw.answer.withIndex()) {
                if (ch != pwCh) continue

                // The intersection cell in absolute coordinates
                val (intRow, intCol) = if (pw.direction == CrosswordDirection.ACROSS)
                    pw.row to pw.col + j else pw.row + j to pw.col

                // Start of the new word given the intersection is at index i
                val (startRow, startCol) = if (dir == CrosswordDirection.ACROSS)
                    intRow to intCol - i else intRow - i to intCol

                if (isValid(grid, clue.answer, startRow, startCol, dir)) {
                    return Triple(startRow, startCol, dir)
                }
            }
        }
        return null
    }

    internal fun isValid(
        grid: Map<Pair<Int, Int>, Char>,
        word: String, row: Int, col: Int,
        dir: CrosswordDirection
    ): Boolean {
        // Cells immediately before and after the word must be empty
        val before = if (dir == CrosswordDirection.ACROSS) row to col - 1 else row - 1 to col
        val after  = if (dir == CrosswordDirection.ACROSS) row to col + word.length else row + word.length to col
        if (grid.containsKey(before) || grid.containsKey(after)) return false

        var hasIntersection = false
        for (i in word.indices) {
            val (r, c) = if (dir == CrosswordDirection.ACROSS) row to col + i else row + i to col
            if (blocksLetter(grid, r, c, word[i], dir)) return false
            if (grid.containsKey(r to c)) hasIntersection = true
        }
        return hasIntersection  // must share at least one letter with an existing word
    }

    private fun blocksLetter(
        grid: Map<Pair<Int, Int>, Char>,
        r: Int, c: Int,
        letter: Char,
        dir: CrosswordDirection
    ): Boolean {
        val existing = grid[r to c]
        if (existing != null) return existing != letter  // letter conflict
        // No parallel adjacency — perpendicular neighbours must be empty
        val n1 = if (dir == CrosswordDirection.ACROSS) r - 1 to c else r to c - 1
        val n2 = if (dir == CrosswordDirection.ACROSS) r + 1 to c else r to c + 1
        return grid.containsKey(n1) || grid.containsKey(n2)
    }

    internal fun renderGrid(
        level: Int, title: String,
        placed: List<PlacedEntry>,
        grid: Map<Pair<Int, Int>, Char>,
        originalClues: List<CrosswordClue>
    ): RenderedCrossword {
        val minRow = grid.keys.minOf { it.first }
        val maxRow = grid.keys.maxOf { it.first }
        val minCol = grid.keys.minOf { it.second }
        val maxCol = grid.keys.maxOf { it.second }

        // Assign sequential cell numbers in reading order (top→bottom, left→right).
        // Two words sharing the same start cell get one number — fixing the mismatch
        // where the cell showed one number but the clue list referenced another.
        val cellNumber = placed
            .map { (it.row - minRow) to (it.col - minCol) }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
            .mapIndexed { idx, pos -> pos to (idx + 1) }
            .toMap()

        val oldToNew = placed.associate { pe ->
            pe.number to cellNumber[(pe.row - minRow) to (pe.col - minCol)]!!
        }

        val clueMap = originalClues.associateBy { it.number }

        val rows = (minRow..maxRow).map { r ->
            (minCol..maxCol).map { c ->
                CrosswordCell(
                    answer = grid[r to c],
                    clueNumber = cellNumber[(r - minRow) to (c - minCol)]
                )
            }
        }

        val acrossClues = placed
            .filter { it.direction == CrosswordDirection.ACROSS }
            .sortedBy { oldToNew[it.number] }
            .mapNotNull { pe -> clueMap[pe.number]?.let { oldToNew[pe.number]!! to it.clue } }

        val downClues = placed
            .filter { it.direction == CrosswordDirection.DOWN }
            .sortedBy { oldToNew[it.number] }
            .mapNotNull { pe -> clueMap[pe.number]?.let { oldToNew[pe.number]!! to it.clue } }

        return RenderedCrossword(level, title, rows, acrossClues, downClues)
    }
}
