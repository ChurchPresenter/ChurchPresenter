package org.churchpresenter.crossword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.crossword.data.ClueEntry
import org.churchpresenter.crossword.data.CrosswordEngine
import org.churchpresenter.crossword.data.Direction

@Composable
fun CrosswordPreview(clues: List<ClueEntry>, modifier: Modifier = Modifier) {
    val puzzle = CrosswordEngine.build(clues)

    Column(modifier = modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        if (puzzle == null) {
            Text(Strings.previewEmpty, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            for (r in 0 until puzzle.rows) {
                Row {
                    for (c in 0 until puzzle.cols) {
                        val cell = puzzle.grid[r to c]
                        PreviewCell(cell?.letter, cell?.clueNumber)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val across = clues.filter { it.direction == Direction.ACROSS && it.answer.isNotBlank() }
            val down = clues.filter { it.direction == Direction.DOWN && it.answer.isNotBlank() }

            if (across.isNotEmpty()) {
                Text(Strings.cluesAcross, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                across.forEach { Text("${it.number}. ${it.clue}", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
            }
            if (down.isNotEmpty()) {
                Text(Strings.cluesDown, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                down.forEach { Text("${it.number}. ${it.clue}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun PreviewCell(letter: Char?, clueNumber: Int?) {
    val cellSize = 28.dp
    Box(
        modifier = Modifier
            .size(cellSize)
            .background(if (letter != null) Color.White else Color.Black)
            .border(0.5.dp, Color.Gray)
    ) {
        if (letter != null) {
            if (clueNumber != null) {
                Text(
                    text = clueNumber.toString(),
                    fontSize = 7.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.align(Alignment.TopStart).padding(1.dp)
                )
            }
            Text(
                text = letter.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
