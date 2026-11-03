package org.churchpresenter.liveoutput.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.output_profile_blank
import org.churchpresenter.strings.generated.resources.output_profile_picker_tooltip
import org.churchpresenter.strings.generated.resources.output_profile_swap_menu_tooltip
import org.churchpresenter.strings.generated.resources.collapse_preview
import org.churchpresenter.strings.generated.resources.expand_preview
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

// The line above each preview: the output's name and mode, the fold caret and the profile picker.

/**
 * The row above one preview: the output's display mode, and a caret that folds the picture away.
 *
 * The mode chip used to be drawn for Stage Monitor and Lower Third only, so the ordinary full-screen
 * outputs -- the majority of them -- were the ones with nothing written above them. Every mode names
 * itself now, which is also what gives every preview a row to click.
 *
 * The profile the output follows is a pill against the far edge, open or folded, so which look an
 * output is wearing can be read at a glance and changed from where it is being watched.
 *
 * [outputLabel] is drawn **only while collapsed**, beside the pill. Open, the output already names
 * itself in the corner of its own picture and repeating it here would say the same thing twice in
 * one glance; collapsed, that corner is gone and a stack of rows reading "Full Screen" three times
 * over could not be told apart.
 */
@Composable
internal fun PreviewHeader(
    modeLabel: String,
    outputLabel: String,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    profiles: List<OutputProfile> = emptyList(),
    activeProfileId: String? = null,
    onPickProfile: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(3.dp))
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (collapsible) Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(Res.string.collapse_preview)
                                 else stringResource(Res.string.expand_preview),
            modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else CARET_CLOSED_DEGREES),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = modeLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!expanded) {
                Text(
                    text = outputLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Only once a profile exists to swap to -- an empty menu would just be clutter on every tile.
            if (profiles.isNotEmpty()) {
                OutputProfilePill(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    modeLabel = modeLabel,
                    onPick = onPickProfile,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/**
 * The header's profile picker: a pill naming the profile this output follows, with a dot that is
 * lit while a real profile is in use, that opens a menu of every saved [OutputProfile].
 */
@Composable
private fun OutputProfilePill(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    modeLabel: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val blankName = stringResource(Res.string.output_profile_blank)
    val active = profiles.firstOrNull { it.id == activeProfileId }
    val activeName = when {
        active != null -> active.name.ifBlank { active.id }
        activeProfileId == BLANK_OUTPUT_PROFILE_ID -> blankName
        else -> null
    }
    val inUse = MaterialTheme.semantic.success
    val unused = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Box(modifier = modifier) {
        KeyButton(
            onClick = { expanded = true },
            shape = AppShape(5.dp),
            contentPadding = PaddingValues(start = 7.dp, end = 4.dp),
            modifier = Modifier.height(PILL_HEIGHT),
        ) {
            ProfileDot(if (active != null) inUse else unused)
            if (activeName != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = activeName,
                    color = if (active != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.output_profile_swap_menu_tooltip),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH),
            offset = DpOffset(0.dp, 6.dp),
            shape = AppShape(9.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                val heading = stringResource(Res.string.output_profile_picker_tooltip)
                Text(
                    text = (if (modeLabel.isBlank()) heading else "$heading · $modeLabel").uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
                )
                ProfileMenuItem(
                    name = blankName,
                    detail = "",
                    dot = unused,
                    selected = activeProfileId == BLANK_OUTPUT_PROFILE_ID,
                ) { expanded = false; onPick(BLANK_OUTPUT_PROFILE_ID) }
                profiles.forEach { profile ->
                    ProfileMenuItem(
                        name = profile.name.ifBlank { profile.id },
                        detail = displayModeLabel(profile.displayMode),
                        dot = inUse,
                        selected = profile.id == activeProfileId,
                    ) { expanded = false; onPick(profile.id) }
                }
            }
        }
    }
}

/** One profile in the pill's menu: its dot, its name, what kind of output it is, and a tick on the one in use. */
@Composable
private fun ProfileMenuItem(
    name: String,
    detail: String,
    dot: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = AppShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .keyboardFocusRing(shape, inset = true)
            .clip(shape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        ProfileDot(dot)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (detail.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(14.dp)) {
            if (selected) Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ProfileDot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

/** How far the caret turns when the preview is folded away; the same quarter turn the tray uses. */
private const val CARET_CLOSED_DEGREES = -90f

private val PILL_HEIGHT = 22.dp
private val MENU_MIN_WIDTH = 236.dp
private val MENU_MAX_WIDTH = 420.dp
private val MENU_ITEM_HEIGHT = 30.dp
