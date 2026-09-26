package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox

/** An on/off setting, drawn as the [LabeledCheckbox] every settings tab uses for a boolean. */
@Composable
internal fun ToggleControl(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = label,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

