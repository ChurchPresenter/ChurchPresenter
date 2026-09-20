package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.preset_name
import churchpresenter.composeapp.generated.resources.preset_name_hint
import churchpresenter.composeapp.generated.resources.preset_replaces
import churchpresenter.composeapp.generated.resources.save_preset
import org.churchpresenter.calendar.model.cleanPresetName
import org.churchpresenter.calendar.model.suggestedPresetName
import org.churchpresenter.calendar.ui.CompactTextField
import org.churchpresenter.calendar.ui.FieldLabel
import org.churchpresenter.calendar.ui.PrimaryButton
import org.churchpresenter.calendar.ui.QuietButton
import org.churchpresenter.calendar.ui.SheetScaffold
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 400.dp
private val FIELD_HEIGHT = 38.dp

/**
 * Asks what to call the preset **Save preset** is about to write — the design's small sheet: an
 * icon badge, `Save preset` over what is being saved, one field, Cancel and OK.
 *
 * Built from the Calendar Manager's own sheet parts rather than an M3 `AlertDialog`, because the
 * preset is that window's feature and this is the one piece of it drawn in the main window; the
 * two should look like one thing. [item] is what will be saved and null keeps the dialog closed.
 * The name starts as the item's own title — never the row text with its icon and count — so
 * accepting it is Enter or one click.
 */
@Composable
fun SavePresetDialog(
    item: ScheduleItem?,
    existingNames: List<String>,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (item == null) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SavePresetDialogContent(item, existingNames, onConfirm, onDismiss)
    }
}

/** The sheet itself, apart from the dialog so it can be composed without one. */
@Composable
internal fun SavePresetDialogContent(
    item: ScheduleItem,
    existingNames: List<String>,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Letters, digits and ordinary punctuation only, on the way in as well as at the start — see
    // cleanPresetName. Typing an emoji simply does nothing.
    var name by remember(item) { mutableStateOf(suggestedPresetName(item)) }
    val trimmed = name.trim()
    val canSave = trimmed.isNotEmpty()
    val replaces = existingNames.firstOrNull { it.equals(trimmed, ignoreCase = true) }
    val confirm = { if (canSave) { onConfirm(trimmed); onDismiss() } }

    SheetScaffold(
        title = stringResource(Res.string.save_preset),
        subtitle = item.displayText,
        icon = Icons.Filled.BookmarkAdd,
        width = DIALOG_WIDTH,
        onDismiss = onDismiss,
        footer = {
            Spacer(Modifier.weight(1f))
            QuietButton(label = stringResource(Res.string.cancel), onClick = onDismiss)
            PrimaryButton(label = stringResource(Res.string.ok), onClick = confirm, enabled = canSave)
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            FieldLabel(stringResource(Res.string.preset_name))
            Spacer(Modifier.height(6.dp))
            CompactTextField(
                value = name,
                onValueChange = { typed ->
                    // A trailing space survives the clean, so a name can still be typed one
                    // word at a time.
                    val clean = cleanPresetName(typed)
                    name = if (typed.endsWith(" ")) "$clean " else clean
                },
                placeholder = stringResource(Res.string.preset_name_hint),
                height = FIELD_HEIGHT,
                fontSize = 13f,
                focused = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (event.type == KeyEventType.KeyDown && enter) { confirm(); true } else false
                    },
            )
            // Not in the design, but a silent overwrite is worse than one extra line.
            if (replaces != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.preset_replaces, replaces),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
