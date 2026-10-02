package org.churchpresenter.crosswordtab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.crossword_all_done
import org.churchpresenter.strings.generated.resources.crossword_ask_more
import org.churchpresenter.strings.generated.resources.crossword_correct
import org.churchpresenter.strings.generated.resources.crossword_wrong
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.crosswordtab.data.CrosswordCell
import org.churchpresenter.crosswordtab.data.CrosswordDecoder
import org.churchpresenter.crosswordtab.data.CrosswordLayoutEngine
import org.churchpresenter.crosswordtab.data.RenderedCrossword
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val SETTLE_MS = 500L

private val CELL_SIZE = 36.dp
private const val MAX_LEVELS = 100

/** The test tag on the grid square at [row], [col]. */
internal fun crosswordCellTag(row: Int, col: Int) = "crossword_cell_${row}_$col"

// Fixed crossword colours — grid always looks like paper regardless of app theme
private val CellBackground    = Color.White
private val CellText          = Color(0xFF1A1A1A)
private val CellBorder        = Color(0xFF9E9E9E)
private val BlockedCell       = Color(0xFF1A1A1A)
private val FocusedBorder     = Color(0xFF1565C0)
private val FocusedBackground = Color(0xFFBBDEFB)

// Serialise/deserialise user input as "row,col:C|row,col:C|…"
internal fun serializeInput(input: Map<Pair<Int, Int>, Char>): String =
    input.entries.joinToString("|") { (pos, ch) -> "${pos.first},${pos.second}:$ch" }

private fun deserializeInput(s: String): Map<Pair<Int, Int>, Char> {
    if (s.isBlank()) return emptyMap()
    return s.split("|").mapNotNull { entry ->
        val colon = entry.indexOf(':')
        if (colon < 0) return@mapNotNull null
        val parts = entry.substring(0, colon).split(",")
        if (parts.size != 2) return@mapNotNull null
        val row = parts[0].toIntOrNull() ?: return@mapNotNull null
        val col = parts[1].toIntOrNull() ?: return@mapNotNull null
        val ch  = entry.getOrNull(colon + 1) ?: return@mapNotNull null
        (row to col) to ch
    }.toMap()
}

@Composable
fun CrosswordTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val msgCorrect = stringResource(Res.string.crossword_correct)
    val msgWrong = stringResource(Res.string.crossword_wrong)
    val msgAllDone = stringResource(Res.string.crossword_all_done)
    val msgAskMore = stringResource(Res.string.crossword_ask_more)

    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var puzzles by remember { mutableStateOf<List<RenderedCrossword>>(emptyList()) }
    var currentLevelIdx by remember { mutableStateOf(0) }
    var userInput by remember { mutableStateOf(mapOf<Pair<Int, Int>, Char>()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsCorrect by remember { mutableStateOf(false) }

    fun saveProgress(levelIdx: Int, input: Map<Pair<Int, Int>, Char>) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SETTLE_MS)
            val serialized = serializeInput(input)
            onSettingsChange { s ->
                s.copy(crosswordProgress = s.crosswordProgress + (levelIdx to serialized))
            }
        }
    }

    // Load all available level files from resources
    LaunchedEffect(Unit) {
        val loaded = mutableListOf<RenderedCrossword>()
        for (n in 0..MAX_LEVELS) {
            val puzzle = loadLevelFile(n) ?: break
            loaded.add(puzzle)
        }
        puzzles = loaded
        isLoading = false
        // Start at the first unsolved level (or the last if all done)
        currentLevelIdx = appSettings.crosswordUnlockedLevel.coerceIn(0, (loaded.size - 1).coerceAtLeast(0))
    }

    // Restore saved input and clear feedback when navigating to a different level
    LaunchedEffect(currentLevelIdx) {
        userInput = deserializeInput(appSettings.crosswordProgress[currentLevelIdx] ?: "")
        feedback = null
        feedbackIsCorrect = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when {
            isLoading -> LoadingNotice()

            puzzles.isEmpty() -> NoPuzzlesNotice()

            else -> {
                val puzzle = puzzles[currentLevelIdx]
                // crosswordUnlockedLevel stores the next accessible index (0 = only first puzzle)
                val unlockedLevel = appSettings.crosswordUnlockedLevel
                val maxAccessibleIdx = unlockedLevel.coerceAtMost(puzzles.size - 1)
                val allCompleted = unlockedLevel >= puzzles.size

                // Header: level label + prev/next navigation
                val canGoBack    = currentLevelIdx > 0
                val canGoForward = currentLevelIdx < maxAccessibleIdx
                LevelHeader(
                    level = puzzle.level,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    onBack = { currentLevelIdx-- },
                    onForward = { currentLevelIdx++ },
                )

                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Main content row: grid + clues panel
                Row(modifier = Modifier.weight(1f)) {
                    CrosswordGrid(
                        puzzle = puzzle,
                        userInput = userInput,
                        onInput = { square, ch ->
                            val newInput = if (ch == null) userInput - square else userInput + (square to ch)
                            userInput = newInput
                            feedback = null
                            saveProgress(currentLevelIdx, newInput)
                        },
                    )

                    Spacer(Modifier.width(24.dp))

                    ClueList(puzzle)
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                CheckBar(
                    feedback = feedback,
                    feedbackIsCorrect = feedbackIsCorrect,
                    canCheck = !allCompleted || currentLevelIdx < puzzles.size - 1,
                    allDoneText = msgAllDone,
                    askMoreText = msgAskMore,
                    onCheck = {
                        val outcome = checkOutcome(
                            solved = checkAnswers(puzzle, userInput),
                            levelIdx = currentLevelIdx,
                            puzzleCount = puzzles.size,
                            unlockedLevel = unlockedLevel,
                        )
                        feedbackIsCorrect = outcome.solved
                        feedback = when {
                            !outcome.solved -> msgWrong
                            outcome.allDone || allCompleted -> msgAllDone
                            else -> msgCorrect
                        }
                        outcome.unlock?.let { next ->
                            onSettingsChange { s ->
                                s.copy(crosswordUnlockedLevel = maxOf(s.crosswordUnlockedLevel, next))
                            }
                        }
                        if (outcome.advance) currentLevelIdx++
                    },
                )
            }
        }
    }
}

@Composable
internal fun CrosswordCellBox(
    cell: CrosswordCell,
    inputChar: Char?,
    onCharInput: (Char) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cell.answer == null) {
        Box(
            modifier = modifier
                .size(CELL_SIZE)
                .background(BlockedCell)
        )
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .size(CELL_SIZE)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusedBorder else CellBorder
            )
            .background(if (isFocused) FocusedBackground else CellBackground)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { focusRequester.requestFocus() }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        onClear(); true
                    }
                    event.utf16CodePoint in 'A'.code..'Z'.code ||
                    event.utf16CodePoint in 'a'.code..'z'.code -> {
                        onCharInput(event.utf16CodePoint.toChar().uppercaseChar()); true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        if (cell.clueNumber != null) {
            Text(
                text = cell.clueNumber.toString(),
                fontSize = 7.sp,
                lineHeight = 7.sp,
                color = CellText,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(1.dp)
            )
        }
        Text(
            text = inputChar?.toString() ?: "",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = CellText
        )
    }
}

private fun checkAnswers(puzzle: RenderedCrossword, userInput: Map<Pair<Int, Int>, Char>): Boolean {
    puzzle.grid.forEachIndexed { rowIdx, row ->
        row.forEachIndexed { colIdx, cell ->
            if (cell.answer == null) return@forEachIndexed
            val typed = userInput[rowIdx to colIdx] ?: return false
            if (typed.uppercaseChar() != cell.answer.uppercaseChar()) return false
        }
    }
    return true
}

internal suspend fun loadLevelFile(level: Int): RenderedCrossword? = try {
    val bytes = withContext(Dispatchers.IO) {
        CrosswordDecoder::class.java.getResourceAsStream("/crossword/level$level.xwp")?.use { it.readBytes() }
    } ?: return null
    val base64Content = String(bytes, Charsets.UTF_8)
    val (title, clues, layout) = CrosswordDecoder.decodeFile(base64Content) ?: return null
    CrosswordLayoutEngine.build(level, title, clues, layout)
} catch (_: Exception) {
    null
}
