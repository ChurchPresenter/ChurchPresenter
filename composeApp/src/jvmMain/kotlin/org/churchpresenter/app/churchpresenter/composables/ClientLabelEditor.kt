package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.client_label_cancel
import churchpresenter.composeapp.generated.resources.client_label_edit_tooltip
import churchpresenter.composeapp.generated.resources.client_label_placeholder
import churchpresenter.composeapp.generated.resources.client_label_save
import org.jetbrains.compose.resources.stringResource

/**
 * Naming a remote device, wherever the operator meets it.
 *
 * Server settings has always had this on each allowed/blocked row. The approval prompt needs the
 * same thing and needs it more — that is the one moment the operator is looking at an unfamiliar
 * device and can still say which phone it is — so the pencil, the field and its two buttons live
 * here rather than being written a second time.
 *
 * @param label the name as stored; the draft resets to it whenever it changes underneath.
 */
internal class ClientLabelEditor(label: String) {
    var editing by mutableStateOf(false)
    var text by mutableStateOf(label)
}

@Composable
internal fun rememberClientLabelEditor(label: String): ClientLabelEditor =
    remember(label) { ClientLabelEditor(label) }

/** The pencil. Callers place it wherever their own row puts actions. */
@Composable
internal fun ClientLabelEditButton(state: ClientLabelEditor, label: String) {
    IconButton(
        onClick = { state.editing = !state.editing; state.text = label },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            Icons.Filled.Edit,
            contentDescription = stringResource(Res.string.client_label_edit_tooltip),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The field and its confirm/abandon buttons. Renders nothing until the pencil has been pressed, so
 * a caller can place it unconditionally.
 */
@Composable
internal fun ClientLabelEditorRow(
    state: ClientLabelEditor,
    label: String,
    onSetLabel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.editing) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsTextField(
            value = state.text,
            onValueChange = { state.text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(
                    stringResource(Res.string.client_label_placeholder),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
        IconButton(
            onClick = {
                onSetLabel(state.text)
                state.editing = false
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(Res.string.client_label_save),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = { state.editing = false; state.text = label },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.client_label_cancel),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
