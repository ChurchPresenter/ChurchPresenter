package org.churchpresenter.liveoutput.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.icons.generated.resources.ic_save
import org.churchpresenter.settings.ProfileSet
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.isProfileSetApplied
import org.churchpresenter.settings.withProfileSetApplied
import org.churchpresenter.settings.withProfileSetRenamed
import org.churchpresenter.settings.withProfileSetSaved
import org.churchpresenter.settings.withoutProfileSet
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_sets
import org.churchpresenter.strings.generated.resources.profile_sets_current
import org.churchpresenter.strings.generated.resources.profile_sets_default_name
import org.churchpresenter.strings.generated.resources.profile_sets_delete
import org.churchpresenter.strings.generated.resources.profile_sets_none
import org.churchpresenter.strings.generated.resources.profile_sets_outputs
import org.churchpresenter.strings.generated.resources.profile_sets_rename
import org.churchpresenter.strings.generated.resources.profile_sets_save
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Test handles for the profile sets menu. */
internal fun profileSetTag(id: String) = "profile_set_$id"
internal fun profileSetNameTag(id: String) = "profile_set_name_$id"
internal const val TAG_SAVE_PROFILE_SET = "profile_set_save"

private val POPOVER_WIDTH = 300.dp

/**
 * The saved profile sets, opened from their button in the preview header -- shown while the layout
 * menu's switch for it is on (`ProjectionSettings.showsProfileSetsButton`).
 */
@Composable
fun ProfileSetsPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    proj: ProjectionSettings,
    onChange: (ProjectionSettings) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.width(POPOVER_WIDTH).padding(horizontal = 6.dp)) {
            ProfileSetsSection(proj, onChange)
        }
    }
}

/**
 * The profile sets menu's list: the saved sets, each a record of which profile every
 * output uses, put back with a click on its row -- the one the outputs match now ticked -- and
 * renamed in place or deleted. Save current as a set adds one holding the outputs as they are, its
 * name ready to type. Hands each change back through [onChange], as the layout menu does.
 */
@Composable
private fun ProfileSetsSection(proj: ProjectionSettings, onChange: (ProjectionSettings) -> Unit) {
    SectionLabel(stringResource(Res.string.profile_sets))
    var renaming by remember { mutableStateOf<String?>(null) }
    if (proj.profileSets.isEmpty()) {
        Text(
            stringResource(Res.string.profile_sets_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        )
    }
    proj.profileSets.forEachIndexed { position, set ->
        SetRow(
            set = set,
            position = position,
            applied = proj.isProfileSetApplied(set),
            renaming = renaming == set.id,
            onApply = { onChange(proj.withProfileSetApplied(set.id)) },
            onRename = { renaming = set.id },
            onRenameDone = { renaming = null },
            onName = { onChange(proj.withProfileSetRenamed(set.id, it)) },
            onDelete = { onChange(proj.withoutProfileSet(set.id)) },
        )
    }
    KeyButton(
        onClick = {
            val saved = proj.withProfileSetSaved(name = "")
            onChange(saved)
            renaming = saved.profileSets.last().id
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .height(ROW_HEIGHT)
            .testTag(TAG_SAVE_PROFILE_SET),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(painterResource(IconRes.drawable.ic_save), contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(stringResource(Res.string.profile_sets_save), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * One saved set: a click puts it on the outputs. Ticked while the outputs match it; its name typed
 * over in place while [renaming], Enter ending it.
 */
@Composable
private fun SetRow(
    set: ProfileSet,
    position: Int,
    applied: Boolean,
    renaming: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onRenameDone: () -> Unit,
    onName: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val shownName = set.name.ifBlank { stringResource(Res.string.profile_sets_default_name, position + 1) }
    val nameStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    MenuRow(onClick = onApply, modifier = Modifier.testTag(profileSetTag(set.id))) {
        Box(Modifier.size(14.dp)) {
            if (applied) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(Res.string.profile_sets_current),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            if (renaming) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    value = set.name,
                    onValueChange = onName,
                    singleLine = true,
                    textStyle = nameStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRenameDone() }),
                    decorationBox = { field ->
                        Box {
                            if (set.name.isBlank()) Text(shownName, style = nameStyle, maxLines = 1)
                            field()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(profileSetNameTag(set.id)),
                )
            } else {
                Text(shownName, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            stringResource(Res.string.profile_sets_outputs, set.profiles.size),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_edit),
            text = stringResource(Res.string.profile_sets_rename),
            onClick = onRename,
            iconSize = 13.dp,
            buttonSize = 26.dp,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_delete),
            text = stringResource(Res.string.profile_sets_delete),
            onClick = onDelete,
            iconSize = 13.dp,
            buttonSize = 26.dp,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
