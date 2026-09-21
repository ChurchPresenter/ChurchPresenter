package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The gap between neighbouring previews in a group. */
private val GROUP_GAP = 4.dp

/**
 * One group of the preview panel: [cells] laid out [columns] to a row, filling row by row.
 *
 * Every column gets an equal share of the panel width and each preview scales to its cell by its own
 * aspect ratio, so a group always fits the panel however many outputs it holds. A short last row
 * leaves its unused cells empty rather than stretching the previews that are there.
 */
@Composable
fun PreviewGroupGrid(
    columns: Int,
    cells: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
        cells.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
                row.forEach { cell -> Box(Modifier.weight(1f)) { cell(Modifier.fillMaxWidth()) } }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
