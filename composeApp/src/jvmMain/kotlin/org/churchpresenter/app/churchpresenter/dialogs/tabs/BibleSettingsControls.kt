package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Dim enough to read as "not typed yet", solid enough to read at all. */
private const val PLACEHOLDER_ALPHA = 0.6f

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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.removeSuffix(":"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

/**
 * The module's own value, shown in an empty name field as the thing that is still in force.
 *
 * A blank name field means "keep using what the module calls itself", so the placeholder is the
 * live value rather than a hint about one.
 */
@Composable
internal fun PanelPlaceholder(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
