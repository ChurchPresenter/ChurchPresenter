package org.churchpresenter.cross.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.cross.data.ClueEntry
import org.churchpresenter.cross.data.CrosswordEngine
import org.churchpresenter.cross.data.RenderedPuzzle
import org.churchpresenter.cross.data.decode
import org.churchpresenter.cross.data.encode
import org.churchpresenter.cross.data.fromPlaintext
import org.churchpresenter.cross.data.fromPlaintextSimple
import org.churchpresenter.cross.data.toPlaintext

// What the admin window is showing, and everything it can do to the puzzle folder.

/** How long typing has to pause before the editor writes the plaintext file back. */
private const val AUTO_SAVE_DEBOUNCE_MS = 800L

/** A clue line: "12. Some clue | ANSWER". */
private val CLUE_LINE = Regex("""^(\d+)\.\s*.+\|.+$""")

private val PLACEHOLDER_ANSWERS = setOf("ANSWER", "WORD")

internal fun templateFor(level: Int) = """
# Level $level
ACROSS:
1. Clue text here | ANSWER
DOWN:
2. Another clue | WORD
""".trimIndent()

internal fun isUnedited(text: String): Boolean {
    val parsed = fromPlaintext(text) ?: return false
    return parsed.third.any { it.answer in PLACEHOLDER_ANSWERS }
}

/**
 * Everything the admin window is showing and everything it can do to the puzzle folder.
 *
 * Held apart from the composable so `AdminApp` is the layout and this is the behaviour: the ten
 * operations behind the buttons used to be local functions inside the composable, which made it a
 * 215-line function whose UI and file handling were interleaved.
 */
@Suppress("TooManyFunctions") // One per action the toolbar offers; splitting them splits the state.
internal class AdminState(private val scope: CoroutineScope) {

    var levels by mutableStateOf(emptyList<Int>())
        private set
    var currentLevel by mutableStateOf(0)
    var jumpText by mutableStateOf("")
    var rawText by mutableStateOf(templateFor(0))
        private set

    /** Levels whose `.txt` is newer than its `.xwp`, or which have no `.xwp` at all. */
    var unexportedLevels by mutableStateOf(emptySet<Int>())
        private set

    /** The subset of [unexportedLevels] still holding an unedited template. */
    var templateLevels by mutableStateOf(emptySet<Int>())
        private set

    var status by mutableStateOf<String?>(null)
    var statusIsError by mutableStateOf(false)
        private set

    private var autoSaveJob: Job? = null

    val parsed by derivedStateOf { fromPlaintext(rawText) }

    private val placedNumbers by derivedStateOf {
        parsed?.third?.let { CrosswordEngine.build(it)?.placedNumbers } ?: emptySet()
    }

    /** Per editor line: null for anything that is not a clue, else whether it made it onto the grid. */
    val lineStatuses by derivedStateOf {
        rawText.lines().map { line ->
            val number = CLUE_LINE.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull()
            number?.let { it in placedNumbers }
        }
    }

    fun say(message: String, error: Boolean = false) {
        status = message
        statusIsError = error
    }

    fun refreshLevels() {
        levels = detectLevels()
        unexportedLevels = scanUnexported(levels)
        templateLevels = scanTemplateLevels(levels)
    }

    fun loadLevel(level: Int) {
        autoSaveJob?.cancel()
        currentLevel = level
        val file = File(PUZZLES_DIR, "level$level.txt")
        rawText = if (file.exists()) file.readText(Charsets.UTF_8) else templateFor(level)
        // Recomputed from disk; editing puts this level back in the set.
        unexportedLevels = scanUnexported(levels)
        templateLevels = scanTemplateLevels(levels)
    }

    fun jumpToLevel() {
        val target = jumpText.toIntOrNull() ?: return
        jumpText = ""
        if (target in levels) {
            loadLevel(target)
        } else {
            currentLevel = target
            rawText = templateFor(target)
            unexportedLevels = unexportedLevels - target
            say("Level $target not found — edit to create it")
        }
    }

    fun newLevel() {
        val level = nextNewLevel(levels)
        currentLevel = level
        rawText = templateFor(level)
        PUZZLES_DIR.mkdirs()
        File(PUZZLES_DIR, "level$level.txt").writeText(rawText, Charsets.UTF_8)
        refreshLevels()
        say("New level $level — edit to begin")
    }

    /** What the editor calls on every keystroke: mark the level unexported and re-arm the autosave. */
    fun edit(text: String) {
        rawText = text
        unexportedLevels = unexportedLevels + currentLevel
        scheduleAutoSave()
    }

    fun exportCurrent() {
        val (_, title, clues) = parsed ?: return say("Invalid puzzle format.", error = true)
        if (isUnedited(rawText)) return say("Replace the placeholder clues before exporting.", error = true)
        val text = layoutFor(title, clues)
            ?: return say("Cannot export: clues don't form a valid crossword — run Fix/Reorder first.", true)
        ENCODED_DIR.mkdirs()
        File(ENCODED_DIR, "level$currentLevel.xwp").writeText(encode(text), Charsets.UTF_8)
        unexportedLevels = unexportedLevels - currentLevel
        say("Exported level$currentLevel.xwp")
    }

    fun loadEncoded() {
        val file = File(ENCODED_DIR, "level$currentLevel.xwp")
        if (!file.exists()) return say("level$currentLevel.xwp not found in encoded/", error = true)
        runCatching { decode(file.readText(Charsets.UTF_8)) }
            .onSuccess {
                rawText = it
                unexportedLevels = unexportedLevels - currentLevel
                say("Loaded encoded level $currentLevel")
            }
            .onFailure { say("Failed to decode level$currentLevel.xwp", error = true) }
    }

    /**
     * Builds the grid and renumbers every clue into reading order, top to bottom then left to
     * right, so the numbers on screen are the numbers the puzzle will be played with.
     */
    fun fixReorder() {
        val (title, clues) = clueSource() ?: return say("Cannot reorder: invalid puzzle format.", true)
        val unique = withUniqueNumbers(clues)
        val puzzle = CrosswordEngine.build(unique)
            ?: return say("Cannot reorder: no words could be placed.", error = true)

        val renumbered = renumber(unique, puzzle)
        val positions = unique.indices.mapNotNull { i ->
            puzzle.placedPositions[unique[i].number]?.let { renumbered[i].number to it }
        }.toMap()

        rawText = toPlaintext(title, renumbered, positions)
        unexportedLevels = unexportedLevels + currentLevel
        scheduleAutoSave()
        val unplaced = unique.count { it.number !in puzzle.placedNumbers }
        say(
            if (unplaced > 0) "Reordered — $unplaced clue(s) not in puzzle"
            else "Reordered — all ${renumbered.size} clues placed"
        )
    }

    /** The clues to reorder, falling back to the simplified format the tool also accepts. */
    private fun clueSource(): Pair<String, List<ClueEntry>>? {
        fromPlaintext(rawText)?.let { return it.second to it.third }
        val simple = fromPlaintextSimple(rawText) ?: return null
        return "Level $currentLevel" to simple
    }

    /** Writes [rawText] back once typing has paused, so the file on disk follows the editor. */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            PUZZLES_DIR.mkdirs()
            File(PUZZLES_DIR, "level$currentLevel.txt").writeText(rawText, Charsets.UTF_8)
            refreshLevels()
        }
    }

    /**
     * The text to encode: what is in the editor when it already carries a LAYOUT section, otherwise
     * the same clues with one injected, so the presenter lays the grid out exactly as previewed.
     */
    private fun layoutFor(title: String, clues: List<ClueEntry>): String? {
        if (rawText.contains(LAYOUT_MARKER, ignoreCase = true)) return rawText
        val puzzle = CrosswordEngine.build(clues) ?: return null
        val enriched = toPlaintext(title, clues, puzzle.placedPositions, puzzle.placedDirections)
        rawText = enriched
        scheduleAutoSave()
        return enriched
    }
}

/** Clue numbers repeat in hand-written files; the engine needs each answer to be its own entry. */
private fun withUniqueNumbers(clues: List<ClueEntry>): List<ClueEntry> {
    val seen = mutableSetOf<Int>()
    var next = (clues.maxOfOrNull { it.number } ?: 0) + 1
    return clues.map { if (seen.add(it.number)) it else it.copy(number = next++) }
}

/**
 * The same clues carrying their reading-order numbers and the direction they were actually placed
 * in. A clue that shares a starting cell with another, or that did not fit at all, still gets a
 * number — it follows the placed ones rather than colliding with them.
 */
private fun renumber(clues: List<ClueEntry>, puzzle: RenderedPuzzle): List<ClueEntry> {
    val byCell = clues.mapNotNull { clue ->
        val cell = puzzle.placedPositions[clue.number] ?: return@mapNotNull null
        puzzle.grid[cell]?.clueNumber?.let { clue.number to it }
    }.toMap()
    var next = (byCell.values.maxOrNull() ?: 0) + 1
    return clues.map { clue ->
        clue.copy(
            number = byCell[clue.number] ?: next++,
            direction = puzzle.placedDirections[clue.number] ?: clue.direction,
        )
    }
}
