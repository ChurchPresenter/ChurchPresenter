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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.strings.generated.resources.signage_rotation_minutes
import org.churchpresenter.strings.generated.resources.signage_rotation_round
import org.churchpresenter.strings.generated.resources.signage_rotation_seconds_short
import org.churchpresenter.strings.generated.resources.signage_rotation_steps
import org.churchpresenter.strings.generated.resources.signage_rotation_subtitle
import org.churchpresenter.theme.isDarkScheme
import org.churchpresenter.theme.keyboardFocusRing
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
            SignageDialogTitle(
                stringResource(title),
                stringResource(Res.string.signage_rotation_subtitle),
                info = stringResource(Res.string.signage_rotation_hint),
                kind = TileKind.ROTATION,
            )
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FieldCaption(stringResource(Res.string.signage_rotation_steps), Modifier.weight(1f))
                        roundText(steps, byId)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                    RoundBar(steps, byId)
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
                    AddStepButton(enabled = entries.isNotEmpty()) {
                        entries.firstOrNull()?.let { steps += defaultStep(it) }
                    }
                }
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
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ROW_WASH), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(18.dp),
        )
        PickerField(
            value = entry?.name ?: stringResource(Res.string.signage_rotation_missing),
            name = stringResource(Res.string.signage_rotation_step, number),
            options = entries.map { PickerOption(it.name) { KindTile(tileKindOf(it.kind), size = 22.dp) } },
            onPick = { onChange(defaultStep(entries[it])) },
            leading = entry?.let { { KindTile(tileKindOf(it.kind), size = 22.dp) } },
            height = 42.dp,
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

/** The dashed button under the steps that adds one. */
@Composable
private fun AddStepButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    val colour = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .keyboardFocusRing(shape)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    colour.copy(alpha = DASH_ALPHA),
                    cornerRadius = CornerRadius(11.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = colour, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(Res.string.signage_rotation_add_step),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colour,
        )
    }
}

/**
 * One round as a bar: each step a share as long as its time, in its kind's colour. A step without a
 * time of its own (a folder's round, a video to its end) takes [UNTIMED_SHARE] seconds' room.
 */
@Composable
private fun RoundBar(steps: List<RotationStep>, byId: Map<String, PlaylistSummary>) {
    if (steps.isEmpty()) return
    val dark = isDarkScheme(MaterialTheme.colorScheme)
    Row(
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).padding(bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        steps.forEach { step ->
            val hue = byId[step.sourceId]?.let { tileKindOf(it.kind).hue } ?: TileKind.SCREEN.hue
            val seconds = step.seconds ?: UNTIMED_SHARE
            Spacer(
                Modifier.weight(seconds.toFloat().coerceAtLeast(1f)).fillMaxHeight()
                    .background(Color.hsl(hue, BAR_S, if (dark) BAR_L_DARK else BAR_L_LIGHT)),
            )
        }
    }
}

/** How long one round takes, the timed steps added up; null with none timed. */
@Composable
private fun roundText(steps: List<RotationStep>, byId: Map<String, PlaylistSummary>): String? {
    val seconds = steps.filter { byId[it.sourceId] != null }.sumOf { it.seconds ?: 0 }
    if (seconds == 0) return null
    val length = if (seconds >= SECONDS_PER_MINUTE) {
        stringResource(Res.string.signage_rotation_minutes, seconds / SECONDS_PER_MINUTE)
    } else {
        stringResource(Res.string.signage_rotation_seconds_short, seconds)
    }
    return stringResource(Res.string.signage_rotation_round, length)
}

/** A new step for [entry]: a minute for a link, ten seconds for a picture, a video to its end, a folder one round. */
internal fun defaultStep(entry: PlaylistSummary): RotationStep = RotationStep(
    entry.id,
    when (entry.kind) {
        SourceKind.WEB, SourceKind.STREAM -> LINK_SECONDS
        SourceKind.PICTURE -> PICTURE_SECONDS
        SourceKind.VIDEO, SourceKind.FOLDER, SourceKind.SMUGMUG, SourceKind.ROTATION -> null
    },
)

private const val LINK_SECONDS = 60
private const val PICTURE_SECONDS = 10
private val SECONDS = 0..86_400

/** The name field, whose caption is drawn apart from the box a test types into. */
internal const val ROTATION_NAME_TAG = "signage-rotation-name"
private val DIALOG_WIDTH = 560.dp
private val DIALOG_MAX_HEIGHT = 640.dp
private val SECONDS_WIDTH = 130.dp
private const val ROW_WASH = 0.03f
private const val DASH_ALPHA = 0.5f
private const val DASH = 8f
private const val UNTIMED_SHARE = 30
private const val SECONDS_PER_MINUTE = 60
private const val BAR_S = 0.55f
private const val BAR_L_DARK = 0.62f
private const val BAR_L_LIGHT = 0.6f
