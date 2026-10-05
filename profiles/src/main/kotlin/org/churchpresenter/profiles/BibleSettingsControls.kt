package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A control under its own small caption, which is how every cell of the typography grid is built.
 *
 * The caption drops a trailing colon, because the shared string resources are written for the
 * `Label: [control]` rows used elsewhere and this grid sets them above the control instead.
 */
@Composable
internal fun ControlColumn(
    label: String,
    modifier: Modifier = Modifier,
    /**
     * The control draws [label] inside itself, as a dropdown does, so no caption line is drawn above
     * it. Only for a row whose cells all do the same -- the colour, font and size row -- since a
     * cell without the line sits a caption's height above a captioned neighbour.
     */
    labelInsideControl: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!labelInsideControl) {
            Text(
                text = label.removeSuffix(":"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}
