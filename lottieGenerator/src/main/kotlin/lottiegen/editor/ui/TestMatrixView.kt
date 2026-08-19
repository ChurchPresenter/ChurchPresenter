package lottiegen.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import lottiegen.editor.EditorState
import lottiegen.editor.MatrixCell
import lottiegen.ui.Strings

private val CellBorderColor = Color(0xFF2A2D35)
private val CellBackgroundColor = Color(0xFF10131A)

/**
 * The hard-constraints check grid: 12 static cells (3 alignments × logo × background),
 * each frozen at the end of the animate-in — no per-frame animation, so a full grid
 * stays cheap to keep on screen.
 */
@Composable
fun TestMatrixView(state: EditorState, modifier: Modifier = Modifier) {
    val cells = state.matrixCells
    val endOfInProgress = state.inFrames.toFloat() / state.totalFrames.toFloat()
    val aspect = state.testConfig.canvasW.toFloat() / state.testConfig.canvasH.toFloat()

    Column(
        modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (cells.isEmpty()) {
            Text(
                Strings.generating,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        for (align in listOf("left", "center", "right")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cell in cells.filter { it.align == align }) {
                    MatrixCellView(cell, endOfInProgress, aspect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MatrixCellView(cell: MatrixCell, progress: Float, aspect: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = buildString {
                append(EditorLabels.align(cell.align))
                if (cell.logo) append(" · ${Strings.editorMatrixTagLogo}")
                if (cell.bg) append(" · ${Strings.editorMatrixTagBg}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, CellBorderColor, RoundedCornerShape(4.dp))
                .background(CellBackgroundColor)
        ) {
            val composition by rememberLottieComposition(key = cell.json) {
                LottieCompositionSpec.JsonString(cell.json)
            }
            composition?.let {
                Image(
                    painter = rememberLottiePainter(composition = it, progress = { progress }),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
