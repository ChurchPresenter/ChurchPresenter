package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.apply
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ok
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.stringResource

/**
 * Cancel / Apply / OK along the foot of a small style dialog -- the outline and the backdrop.
 *
 * The same three the Settings dialog ends with, in the same colours, so an edit made in a dialog
 * opened from Settings is committed the same way as the Settings page it came from: Apply writes the
 * draft and stays open to keep adjusting against the preview behind it, OK writes it and closes,
 * Cancel closes without writing anything not already applied.
 */
@Composable
internal fun StyleDialogButtons(
    /** Whether the draft differs from what was last written -- Apply does nothing otherwise. */
    changed: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onOk: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(
            shape = RoundedCornerShape(6.dp),
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(stringResource(Res.string.cancel))
        }
        RaisedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = onApply,
            enabled = changed,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(stringResource(Res.string.apply))
        }
        RaisedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = onOk,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(Res.string.ok))
        }
    }
}
