package org.churchpresenter.crosswordtab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.crossword_across
import org.churchpresenter.strings.generated.resources.crossword_check
import org.churchpresenter.strings.generated.resources.crossword_down
import org.churchpresenter.strings.generated.resources.crossword_next_level
import org.churchpresenter.strings.generated.resources.crossword_prev_level
import org.churchpresenter.strings.generated.resources.crossword_level_label
import org.churchpresenter.strings.generated.resources.crossword_loading
import org.churchpresenter.strings.generated.resources.crossword_no_puzzles
import org.churchpresenter.crosswordtab.data.RenderedCrossword
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag

@Composable
internal fun LoadingNotice() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.crossword_loading))
        }
    }
}

@Composable
internal fun NoPuzzlesNotice() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.crossword_no_puzzles),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The level label between the buttons that step to the previous and the next unlocked level. */
@Composable
internal fun LevelHeader(
    level: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        KeyIconButton(
            onClick = onBack,
            enabled = canGoBack
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.crossword_prev_level),
                tint = if (canGoBack)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Text(
            text = stringResource(Res.string.crossword_level_label, level),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        KeyIconButton(
            onClick = onForward,
            enabled = canGoForward
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(Res.string.crossword_next_level),
                tint = if (canGoForward)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/** The grid; [onInput] gets a typed letter for a square, or null when the square is cleared. */
@Composable
internal fun CrosswordGrid(
    puzzle: RenderedCrossword,
    userInput: Map<Pair<Int, Int>, Char>,
    onInput: (square: Pair<Int, Int>, letter: Char?) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        puzzle.grid.forEachIndexed { rowIdx, row ->
            Row {
                row.forEachIndexed { colIdx, cell ->
                    CrosswordCellBox(
                        modifier = Modifier.testTag(crosswordCellTag(rowIdx, colIdx)),
                        cell = cell,
                        inputChar = userInput[rowIdx to colIdx],
                        onCharInput = { ch -> onInput(rowIdx to colIdx, ch) },
                        onClear = { onInput(rowIdx to colIdx, null) },
                    )
                }
            }
        }
    }
}

/** The Across and Down clues beside the grid, each list only when it has clues. */
@Composable
internal fun ClueList(puzzle: RenderedCrossword) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (puzzle.acrossClues.isNotEmpty()) {
            ClueSection(stringResource(Res.string.crossword_across), puzzle.acrossClues)
            Spacer(Modifier.height(12.dp))
        }
        if (puzzle.downClues.isNotEmpty()) {
            ClueSection(stringResource(Res.string.crossword_down), puzzle.downClues)
        }
    }
}

@Composable
internal fun ClueSection(title: String, clues: List<Pair<Int, String>>) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(4.dp))
    clues.forEach { (num, clue) ->
        Text(
            text = "$num. $clue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * What pressing Check does. [unlock] is the level to open, when the one just solved is the furthest
 * solved; [advance] moves on when there is a next level; [allDone] is true on the last one.
 */
internal data class CheckOutcome(
    val solved: Boolean,
    val unlock: Int? = null,
    val advance: Boolean = false,
    val allDone: Boolean = false,
)

/** Pressing Check on level [levelIdx] of [puzzleCount], with [unlockedLevel] the next one open. */
internal fun checkOutcome(solved: Boolean, levelIdx: Int, puzzleCount: Int, unlockedLevel: Int): CheckOutcome =
    if (!solved) {
        CheckOutcome(solved = false)
    } else {
        CheckOutcome(
            solved = true,
            unlock = (levelIdx + 1).takeIf { levelIdx >= unlockedLevel },
            advance = levelIdx < puzzleCount - 1,
            allDone = levelIdx == puzzleCount - 1,
        )
    }

/** The verdict on the last check, and the Check button -- or, once every level is solved, a note. */
@Composable
internal fun CheckBar(
    feedback: String?,
    feedbackIsCorrect: Boolean,
    canCheck: Boolean,
    allDoneText: String,
    askMoreText: String,
    onCheck: () -> Unit,
) {
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

        if (canCheck) {
            RaisedButton(shape = AppShape(6.dp), onClick = onCheck) {
                Text(stringResource(Res.string.crossword_check))
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = allDoneText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = askMoreText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
