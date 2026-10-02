package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import org.churchpresenter.strings.generated.resources.web_url_hint
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.SettingsTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.theme.AppShape
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.website_disclaimer
import org.churchpresenter.strings.generated.resources.website_dialog_title
import org.churchpresenter.strings.generated.resources.website_title_hint
import org.churchpresenter.strings.generated.resources.website_title_label
import org.churchpresenter.strings.generated.resources.website_url_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddWebsiteDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String) -> Unit
) {
    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, ADD_WEBSITE_DIALOG_WIDTH, ADD_WEBSITE_DIALOG_HEIGHT),
        width = ADD_WEBSITE_DIALOG_WIDTH,
        height = ADD_WEBSITE_DIALOG_HEIGHT
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.website_dialog_title),
        resizable = false
    ) {
        ProvideUiFontScale {
            AddWebsiteDialogContent(onDismiss = onDismiss, onConfirm = onConfirm)
        }
    }
}

@Composable
internal fun AddWebsiteDialogContent(
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String) -> Unit
) {
    var url by remember { mutableStateOf("https://") }
    var displayTitle by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(Res.string.website_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title field (first)
                    WebsiteField(
                        label = stringResource(Res.string.website_title_label),
                        hint = stringResource(Res.string.website_title_hint),
                        value = displayTitle,
                        onValueChange = { displayTitle = it },
                    )

                    // URL field (second)
                    WebsiteField(
                        label = stringResource(Res.string.website_url_label),
                        hint = stringResource(Res.string.web_url_hint),
                        value = url,
                        onValueChange = { url = it },
                    )

                    // Disclaimer
                    Text(
                        text = stringResource(Res.string.website_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GhostButton(shape = AppShape(6.dp), onClick = onDismiss) {
                        Text(
                            stringResource(Res.string.cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RaisedButton(
                        shape = AppShape(6.dp),
                        onClick = {
                            val finalUrl = url.trim()
                            val finalTitle = displayTitle.trim().ifBlank { finalUrl }
                            onConfirm(finalUrl, finalTitle)
                            onDismiss()
                        },
                        enabled = url.isNotBlank() && url != "https://",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            stringResource(Res.string.ok),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

/** One labelled single-line field of the dialog, with its hint shown while it is empty. */
@Composable
private fun WebsiteField(label: String, hint: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        SettingsTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
