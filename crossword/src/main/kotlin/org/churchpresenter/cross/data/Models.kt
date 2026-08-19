package org.churchpresenter.cross.data

enum class Direction { ACROSS, DOWN }

data class ClueEntry(
    val number: Int = 1,
    val direction: Direction = Direction.ACROSS,
    val clue: String = "",
    val answer: String = ""
)

data class GridCell(
    val letter: Char,
    val clueNumber: Int? = null
)

data class RenderedPuzzle(
    val grid: Map<Pair<Int, Int>, GridCell>,
    val rows: Int,
    val cols: Int,
    val clues: List<ClueEntry>,
    val placedNumbers: Set<Int>,                        // clue numbers actually placed in the grid
    val placedDirections: Map<Int, Direction> = emptyMap(),  // final direction used per clue number
    val placedPositions: Map<Int, Pair<Int, Int>> = emptyMap()  // final (row, col) per clue number, normalized to (0,0)
)
