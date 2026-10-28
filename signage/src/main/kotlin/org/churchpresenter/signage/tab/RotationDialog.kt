package org.churchpresenter.signage.tab

import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_rotation_add_step
import org.churchpresenter.strings.generated.resources.signage_rotation_folder_round
import org.churchpresenter.strings.generated.resources.signage_rotation_hint
import org.churchpresenter.strings.generated.resources.signage_rotation_missing
import org.churchpresenter.strings.generated.resources.signage_rotation_move_down
import org.churchpresenter.strings.generated.resources.signage_rotation_move_up
import org.churchpresenter.strings.generated.resources.signage_rotation_name
import org.churchpresenter.strings.generated.resources.signage_rotation_remove_step
import org.churchpresenter.strings.generated.resources.signage_rotation_seconds
import org.churchpresenter.strings.generated.resources.signage_rotation_step
import org.churchpresenter.strings.generated.resources.signage_rotation_title_add
import org.churchpresenter.strings.generated.resources.signage_rotation_title_edit
import org.churchpresenter.strings.generated.resources.signage_rotation_video_seconds
import org.jetbrains.compose.resources.stringResource

/**
 * Makes or edits a rotation: a name, and steps picked from the other entries of the list, each for
 * its own time — none for a folder, which plays one round of its pictures; 0 for a video plays it to
 * its end. [initial] is the rotation being edited, or null for a new one.
 */
@Composable
internal fun RotationDialog(
    entries: List<PlaylistSummary>,
    initial: RotationPlaylist?,
    onSave: (name: String, steps: List<RotationStep>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    val steps = remember { mutableStateListOf<RotationStep>().apply { addAll(initial?.steps.orEmpty()) } }
    val byId = entries.associateBy { it.id }
    val title = if (initial == null) {
        Res.string.signage_rotation_title_add
    } else {
        Res.string.signage_rotation_title_edit
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(DIALOG_WIDTH)
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(Res.string.signage_rotation_name),
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().testTag(ROTATION_NAME_TAG),
                )
                steps.forEachIndexed { index, step ->
                    StepRow(
                        number = index + 1,
                        step = step,
                        entries = entries,
                        entry = byId[step.sourceId],
                        onChange = { steps[index] = it },
                        onMove = { by ->
                            val to = index + by
                            if (to in steps.indices) steps.add(to, steps.removeAt(index))
                        },
                        onRemove = { steps.removeAt(index) },
                        isFirst = index == 0,
                        isLast = index == steps.lastIndex,
                    )
                }
                RingedGhostButton(
                    onClick = { entries.firstOrNull()?.let { steps += defaultStep(it) } },
                    enabled = entries.isNotEmpty(),
                ) {
                    Icon(rememberVectorPainter(Icons.Filled.Add), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.signage_rotation_add_step))
                }
                Text(
                    stringResource(Res.string.signage_rotation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    onSave(name, steps.toList())
                    onDismiss()
                },
                enabled = name.isNotBlank() && steps.isNotEmpty(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** One step: which entry, for how long, and moving or removing it. */
@Composable
private fun StepRow(
    number: Int,
    step: RotationStep,
    entries: List<PlaylistSummary>,
    entry: PlaylistSummary?,
    onChange: (RotationStep) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownSettingsField(
            value = entry?.name ?: stringResource(Res.string.signage_rotation_missing),
            options = entries.map { it.name },
            onValueChange = { picked -> entries.firstOrNull { it.name == picked }?.let { onChange(defaultStep(it)) } },
            label = stringResource(Res.string.signage_rotation_step, number),
            modifier = Modifier.weight(1f),
        )
        when (entry?.kind) {
            SourceKind.FOLDER -> Text(
                stringResource(Res.string.signage_rotation_folder_round),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(SECONDS_WIDTH),
            )
            null -> Spacer(Modifier.width(SECONDS_WIDTH))
            else -> NumberSettingsTextField(
                modifier = Modifier.width(SECONDS_WIDTH),
                label = stringResource(
                    if (entry.kind == SourceKind.VIDEO) {
                        Res.string.signage_rotation_video_seconds
                    } else {
                        Res.string.signage_rotation_seconds
                    },
                ),
                initialText = step.seconds ?: 0,
                range = SECONDS,
                onValueChange = { onChange(step.copy(seconds = it.takeIf { seconds -> seconds > 0 })) },
            )
        }
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.ArrowUpward),
            text = stringResource(Res.string.signage_rotation_move_up),
            onClick = { onMove(-1) },
            enabled = !isFirst,
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.ArrowDownward),
            text = stringResource(Res.string.signage_rotation_move_down),
            onClick = { onMove(1) },
            enabled = !isLast,
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Close),
            text = stringResource(Res.string.signage_rotation_remove_step),
            onClick = onRemove,
        )
    }
}

/** A new step for [entry]: a minute for a link, ten seconds for a picture, a video to its end, a folder one round. */
internal fun defaultStep(entry: PlaylistSummary): RotationStep = RotationStep(
    entry.id,
    when (entry.kind) {
        SourceKind.WEB, SourceKind.STREAM -> LINK_SECONDS
        SourceKind.PICTURE -> PICTURE_SECONDS
        SourceKind.VIDEO, SourceKind.FOLDER, SourceKind.ROTATION -> null
    },
)

private const val LINK_SECONDS = 60
private const val PICTURE_SECONDS = 10
private val SECONDS = 0..86_400

/** The name field, whose caption is drawn apart from the box a test types into. */
internal const val ROTATION_NAME_TAG = "signage-rotation-name"
private val DIALOG_WIDTH = 560.dp
private val DIALOG_MAX_HEIGHT = 520.dp
private val SECONDS_WIDTH = 130.dp
