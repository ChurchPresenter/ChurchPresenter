package org.churchpresenter.cross.data

object CrosswordEngine {
    fun build(clues: List<ClueEntry>): RenderedPuzzle? {
        val entries = clues.filter { it.answer.isNotBlank() }
        if (entries.isEmpty()) return null

        val sorted = entries.sortedByDescending { it.answer.length }
        data class PlacedEntry(val entry: ClueEntry, val row: Int, val col: Int)

        val placed = mutableListOf<PlacedEntry>()
        val grid = mutableMapOf<Pair<Int, Int>, Char>()

        fun isValid(entry: ClueEntry, row: Int, col: Int): Boolean {
            val word = entry.answer
            val isAcross = entry.direction == Direction.ACROSS
            var hasIntersection = placed.isEmpty()

            for (i in word.indices) {
                val r = if (isAcross) row else row + i
                val c = if (isAcross) col + i else col
                val pos = r to c
                val existing = grid[pos]

                if (existing != null) {
                    if (existing != word[i]) return false
                    hasIntersection = true
                } else {
                    // Check no parallel adjacency
                    if (isAcross) {
                        if (grid.containsKey((r - 1) to c) || grid.containsKey((r + 1) to c)) return false
                    } else {
                        if (grid.containsKey(r to (c - 1)) || grid.containsKey(r to (c + 1))) return false
                    }
                }
            }

            if (!hasIntersection) return false

            // Check no letters immediately before/after word
            val beforeR = if (isAcross) row else row - 1
            val beforeC = if (isAcross) col - 1 else col
            val afterR = if (isAcross) row else row + word.length
            val afterC = if (isAcross) col + word.length else col
            if (grid.containsKey(beforeR to beforeC)) return false
            if (grid.containsKey(afterR to afterC)) return false

            return true
        }

        fun placeWord(entry: ClueEntry, row: Int, col: Int) {
            val word = entry.answer
            val isAcross = entry.direction == Direction.ACROSS
            for (i in word.indices) {
                val r = if (isAcross) row else row + i
                val c = if (isAcross) col + i else col
                grid[r to c] = word[i]
            }
            placed.add(PlacedEntry(entry, row, col))
        }

        // Returns the direction actually used, or null if placement failed
        fun tryPlace(entry: ClueEntry): Direction? {
            val opposite = if (entry.direction == Direction.ACROSS) Direction.DOWN else Direction.ACROSS
            for (dir in listOf(entry.direction, opposite)) {
                val candidate = entry.copy(direction = dir)
                for (p in placed.toList()) {
                    if (p.entry.direction == candidate.direction) continue
                    val placedWord = p.entry.answer
                    for (i in candidate.answer.indices) {
                        for (j in placedWord.indices) {
                            if (candidate.answer[i] != placedWord[j]) continue
                            val row: Int
                            val col: Int
                            if (candidate.direction == Direction.ACROSS) {
                                row = p.row + j
                                col = p.col - i
                            } else {
                                row = p.row - i
                                col = p.col + j
                            }
                            if (isValid(candidate, row, col)) {
                                placeWord(candidate, row, col)
                                return dir
                            }
                        }
                    }
                }
            }
            return null
        }

        // Place first word at origin in its specified direction
        val firstEntry = sorted.first()
        placeWord(firstEntry, 0, 0)
        val finalDirections = mutableMapOf<Int, Direction>()
        finalDirections[firstEntry.number] = firstEntry.direction

        // Place remaining words; retry until no more progress (later words may unlock earlier ones)
        // Each word tries its assigned direction first, then the opposite if needed
        val deferred = sorted.drop(1).toMutableList()
        var lastSize = -1
        while (deferred.isNotEmpty() && deferred.size != lastSize) {
            lastSize = deferred.size
            val iter = deferred.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val dir = tryPlace(entry)
                if (dir != null) {
                    finalDirections[entry.number] = dir
                    iter.remove()
                }
            }
        }

        if (placed.isEmpty()) return null

        // Normalize grid to start at (0,0)
        val minRow = grid.keys.minOf { it.first }
        val minCol = grid.keys.minOf { it.second }
        val normalized = grid.mapKeys { (k, _) -> (k.first - minRow) to (k.second - minCol) }
        val rows = normalized.keys.maxOf { it.first } + 1
        val cols = normalized.keys.maxOf { it.second } + 1

        // Assign clue numbers to cells
        val placedNormalized = placed.map { p ->
            p.copy(row = p.row - minRow, col = p.col - minCol)
        }

        // Sequential cell numbers in reading order — two words sharing a start cell
        // get one number, fixing preview mismatches in the admin tool.
        val cellNumber = placedNormalized
            .map { it.row to it.col }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
            .mapIndexed { idx, pos -> pos to (idx + 1) }
            .toMap()

        val cellMap = mutableMapOf<Pair<Int, Int>, GridCell>()
        for ((pos, letter) in normalized) {
            cellMap[pos] = GridCell(letter = letter, clueNumber = cellNumber[pos])
        }

        val placedNumbers = placed.map { it.entry.number }.toSet()
        val placedPositions = placedNormalized.associate { it.entry.number to (it.row to it.col) }
        return RenderedPuzzle(
            grid = cellMap,
            rows = rows,
            cols = cols,
            clues = clues,
            placedNumbers = placedNumbers,
            placedDirections = finalDirections,
            placedPositions = placedPositions,
        )
    }
}
