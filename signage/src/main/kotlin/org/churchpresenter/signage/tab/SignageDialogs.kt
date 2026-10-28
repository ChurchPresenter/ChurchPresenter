package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.RowScope
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.AppShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.jetbrains.compose.resources.stringResource

/** Asks before something that cannot be taken back: [confirm] does it, Cancel does not. */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            RaisedButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * The app's [GhostButton] with the keyboard focus ring it does not draw itself (it has no key to hang
 * one on): the quiet action, such as Cancel, or Add beside a list.
 */
@Composable
internal fun RingedGhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = GhostButton(
    onClick = onClick,
    modifier = modifier.keyboardFocusRing(GHOST_SHAPE),
    enabled = enabled,
    shape = GHOST_SHAPE,
    content = content,
)

private val GHOST_SHAPE = AppShape(10.dp)
