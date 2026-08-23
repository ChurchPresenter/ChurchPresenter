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
import org.churchpresenter.ui.SettingsTextField
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
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.add_label
import org.churchpresenter.resources.generated.resources.background_color_label
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.edit_label
import org.churchpresenter.resources.generated.resources.enter_label_text
import org.churchpresenter.resources.generated.resources.label_text
import org.churchpresenter.resources.generated.resources.ok
import org.churchpresenter.resources.generated.resources.text_color
import org.churchpresenter.ui.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.LabelColorColumns
import org.churchpresenter.app.churchpresenter.composables.LabelColors
import org.churchpresenter.app.churchpresenter.composables.RecentLabelColors
import org.churchpresenter.app.churchpresenter.composables.matches
import org.churchpresenter.app.churchpresenter.composables.themeLabelPresets
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddLabelDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (text: String, textColor: String, backgroundColor: String) -> Unit,
    existingText: String = "",
    /** Blank means "no colour chosen yet" — the content resolves it from the theme. */
    existingTextColor: String = "",
    existingBackgroundColor: String = "",
    isEdit: Boolean = false
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    // Height is 640dp, not 400dp: the nested ColorPickerDialog opened from either color field is
    // a Compose overlay bound to THIS window's own bounds (not an independent OS window), and its
    // full content — SV panel, hue bar, swatch/hex row, AND a fully populated (12-color, 2-row)
    // "Recent colors" list — needs ~590dp of height to render without scrolling. 400dp cut off
    // everything past the SV panel with no visible scrollbar to hint more was there; a first pass
    // at 560dp still cropped the recent-color swatches down to an unrecognizable sliver. 640dp
    // leaves ~50dp of slack over the measured worst case.
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, ADD_LABEL_DIALOG_WIDTH, ADD_LABEL_DIALOG_HEIGHT),
        width = ADD_LABEL_DIALOG_WIDTH,
        height = ADD_LABEL_DIALOG_HEIGHT
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(if (isEdit) Res.string.edit_label else Res.string.add_label),
        resizable = false
    ) {
        AddLabelDialogContent(
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            existingText = existingText,
            existingTextColor = existingTextColor,
            existingBackgroundColor = existingBackgroundColor,
            isEdit = isEdit
        )
    }
}

/**
 * The label editor itself: the text field, the two colour pickers, and the Cancel/OK row.
 *
 * Held apart from [AddLabelDialog] because that function's only other statement is the
 * `DialogWindow` it opens, which cannot be composed on a headless machine. Keeping the window down
 * to that one call leaves the field defaults, the blank-text guard on OK, and what OK hands back
 * reachable from a test — [AddLabelDialog] itself stays reachable too, with `isVisible = false`,
 * since the `DialogWindow` call sits after that guard.
 */
@Composable
internal fun AddLabelDialogContent(
    onDismiss: () -> Unit,
    onConfirm: (text: String, textColor: String, backgroundColor: String) -> Unit,
    existingText: String = "",
    existingTextColor: String = "",
    existingBackgroundColor: String = "",
    isEdit: Boolean = false
) {
    // A new label starts on the theme's own first preset, which is the tone an ordinary schedule
    // card is drawn in -- a label is a heading *in* the list, not a slab across it. It still reads
    // as one: the row draws its text bold and letter-spaced with the accent bar beside it.
    val presets = themeLabelPresets()
    val themeBackground = presets.first().background
    val themeText = presets.first().text
    var labelText by remember { mutableStateOf(existingText) }
    var textColor by remember { mutableStateOf(existingTextColor.ifBlank { themeText }) }
    var backgroundColor by remember { mutableStateOf(existingBackgroundColor.ifBlank { themeBackground }) }

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
                    SettingsTextField(
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
                    )
                }

                // Theme presets and this user's own history, side by side: pick a pair in one
                // click, or fall through to the two pickers below for something new.
                LabelColorColumns(
                    presets = presets,
                    recents = RecentLabelColors.combos.toList(),
                    onPick = { picked ->
                        backgroundColor = picked.background
                        textColor = picked.text
                    },
                )

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
                TextButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                    Text(
                        stringResource(Res.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    shape = RoundedCornerShape(6.dp),
                    onClick = {
                        if (labelText.isNotBlank()) {
                            val chosen = LabelColors(background = backgroundColor, text = textColor)
                            // A preset is not history: it has its own column already, and
                            // recording it would push out the custom pairs this list is for.
                            if (presets.none { it.matches(chosen) }) RecentLabelColors.add(chosen)
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

