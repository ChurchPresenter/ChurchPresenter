package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.crossword_across
import churchpresenter.composeapp.generated.resources.crossword_all_done
import churchpresenter.composeapp.generated.resources.crossword_check
import churchpresenter.composeapp.generated.resources.crossword_correct
import churchpresenter.composeapp.generated.resources.crossword_down
import churchpresenter.composeapp.generated.resources.crossword_wrong
import churchpresenter.composeapp.generated.resources.crossword_level_label
import churchpresenter.composeapp.generated.resources.crossword_loading
import churchpresenter.composeapp.generated.resources.crossword_no_puzzles
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.CrosswordCell
import org.churchpresenter.app.churchpresenter.data.CrosswordDecoder
import org.churchpresenter.app.churchpresenter.data.CrosswordLayoutEngine
import org.churchpresenter.app.churchpresenter.data.RenderedCrossword
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

private val CELL_SIZE = 36.dp
private const val MAX_LEVELS = 100

@OptIn(ExperimentalResourceApi::class)
@Composable
fun CrosswordTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val msgCorrect = stringResource(Res.string.crossword_correct)
    val msgWrong = stringResource(Res.string.crossword_wrong)
    val msgAllDone = stringResource(Res.string.crossword_all_done)

    var isLoading by remember { mutableStateOf(true) }
    var puzzles by remember { mutableStateOf<List<RenderedCrossword>>(emptyList()) }
    var currentLevelIdx by remember { mutableStateOf(0) }
    var userInput by remember { mutableStateOf(mapOf<Pair<Int, Int>, Char>()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsCorrect by remember { mutableStateOf(false) }

    // Load all available level files from resources
    LaunchedEffect(Unit) {
        val loaded = mutableListOf<RenderedCrossword>()
        for (n in 1..MAX_LEVELS) {
            val puzzle = loadLevelFile(n) ?: break
            loaded.add(puzzle)
        }
        puzzles = loaded
        isLoading = false
        // Start at the first unsolved level (or the last if all done)
        currentLevelIdx = appSettings.crosswordUnlockedLevel.coerceIn(0, (loaded.size - 1).coerceAtLeast(0))
    }

    // Reset input and feedback when navigating to a different level
    LaunchedEffect(currentLevelIdx) {
        userInput = emptyMap()
        feedback = null
        feedbackIsCorrect = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.crossword_loading))
                    }
                }
            }

            puzzles.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.crossword_no_puzzles),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val puzzle = puzzles[currentLevelIdx]
                val unlockedLevel = appSettings.crosswordUnlockedLevel
                // User may view any level from 1 up to unlockedLevel + 1 (next to solve)
                val maxAccessibleIdx = unlockedLevel.coerceAtMost(puzzles.size - 1)
                val allCompleted = unlockedLevel >= puzzles.size

                // Header: level label + prev/next navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { currentLevelIdx-- },
                        enabled = currentLevelIdx > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }

                    Text(
                        text = stringResource(Res.string.crossword_level_label, puzzle.level, puzzles.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { currentLevelIdx++ },
                        enabled = currentLevelIdx < maxAccessibleIdx
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Main content row: grid + clues panel
                Row(modifier = Modifier.weight(1f)) {
                    // Crossword grid
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        puzzle.grid.forEachIndexed { rowIdx, row ->
                            Row {
                                row.forEachIndexed { colIdx, cell ->
                                    CrosswordCellBox(
                                        cell = cell,
                                        inputChar = userInput[rowIdx to colIdx],
                                        onCharInput = { ch ->
                                            userInput = userInput + ((rowIdx to colIdx) to ch)
                                            feedback = null
                                        },
                                        onClear = {
                                            userInput = userInput - (rowIdx to colIdx)
                                            feedback = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(24.dp))

                    // Clues panel
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (puzzle.acrossClues.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.crossword_across),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.height(4.dp))
                            puzzle.acrossClues.forEach { (num, clue) ->
                                Text(
                                    text = "$num. $clue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (puzzle.downClues.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.crossword_down),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.height(4.dp))
                            puzzle.downClues.forEach { (num, clue) ->
                                Text(
                                    text = "$num. $clue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Bottom bar: feedback text + check button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = feedback ?: "",
                        color = if (feedbackIsCorrect)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )

                    if (!allCompleted || currentLevelIdx < puzzles.size - 1) {
                        Button(
                            onClick = {
                                if (checkAnswers(puzzle, userInput)) {
                                    feedbackIsCorrect = true
                                    feedback = if (allCompleted || currentLevelIdx == puzzles.size - 1)
                                        msgAllDone
                                    else
                                        msgCorrect

                                    // Unlock next level if this is the furthest solved
                                    if (puzzle.level > unlockedLevel) {
                                        onSettingsChange { s ->
                                            s.copy(crosswordUnlockedLevel = maxOf(s.crosswordUnlockedLevel, puzzle.level))
                                        }
                                    }
                                    // Advance to the next level automatically
                                    if (currentLevelIdx < puzzles.size - 1) {
                                        currentLevelIdx++
                                    }
                                } else {
                                    feedbackIsCorrect = false
                                    feedback = msgWrong
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.crossword_check))
                        }
                    } else {
                        Text(
                            text = msgAllDone,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrosswordCellBox(
    cell: CrosswordCell,
    inputChar: Char?,
    onCharInput: (Char) -> Unit,
    onClear: () -> Unit
) {
    if (cell.answer == null) {
        Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        )
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface
            )
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
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(1.dp)
            )
        }
        Text(
            text = inputChar?.toString() ?: "",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
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

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadLevelFile(level: Int): RenderedCrossword? = try {
    val bytes = Res.readBytes("files/crossword/level$level.xwp")
    val base64Content = String(bytes, Charsets.UTF_8)
    val (title, clues) = CrosswordDecoder.decodeFile(base64Content) ?: return null
    CrosswordLayoutEngine.build(level, title, clues)
} catch (_: Exception) {
    null
}
