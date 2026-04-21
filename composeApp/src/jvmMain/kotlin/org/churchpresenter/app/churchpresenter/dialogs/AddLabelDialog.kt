package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_label
import churchpresenter.composeapp.generated.resources.background_color_label
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.edit_label
import churchpresenter.composeapp.generated.resources.enter_label_text
import churchpresenter.composeapp.generated.resources.label_text
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.text_color
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddLabelDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String, textColor: String, backgroundColor: String) -> Unit,
    existingText: String = "",
    existingTextColor: String = "#FFFFFF",
    existingBackgroundColor: String = "#2196F3",
    isEdit: Boolean = false
) {
    var labelText by remember { mutableStateOf(existingText) }
    var textColor by remember { mutableStateOf(existingTextColor) }
    var backgroundColor by remember { mutableStateOf(existingBackgroundColor) }

    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, 500.dp, 400.dp),
        width = 500.dp,
        height = 400.dp
    )

    Dialog(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(if (isEdit) Res.string.edit_label else Res.string.add_label),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = stringResource(if (isEdit) Res.string.edit_label else Res.string.add_label),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Label text input
                    Column {
                        Text(
                            text = stringResource(Res.string.label_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            placeholder = {
                                Text(
                                    stringResource(Res.string.enter_label_text),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Text color picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.text_color),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ColorPickerField(
                            color = textColor,
                            onColorChange = { textColor = it }
                        )
                    }

                    // Background color picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.background_color_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ColorPickerField(
                            color = backgroundColor,
                            onColorChange = { backgroundColor = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(Res.string.cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (labelText.isNotBlank()) {
                                onConfirm(labelText.trim(), textColor, backgroundColor)
                                onDismiss()
                            }
                        },
                        enabled = labelText.isNotBlank(),
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
}

