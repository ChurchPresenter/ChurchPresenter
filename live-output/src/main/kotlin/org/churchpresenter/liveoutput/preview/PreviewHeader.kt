package org.churchpresenter.liveoutput.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
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
 * [outputLabel] is drawn **only while collapsed**, and against the far edge. Open, the output
 * already names itself in the corner of its own picture and repeating it here would say the same
 * thing twice in one glance; collapsed, that corner is gone and a stack of rows reading "Full
 * Screen" three times over could not be told apart. Pushing it to the trailing edge keeps it in the
 * column the in-picture label sits in, so the name does not jump across the row as a preview folds.
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
            fontSize = 9.sp,
        )
        // Quick-swap: which Output Profile this output follows, right where the operator is
        // already watching it live. Only shown once a profile exists to swap to -- an empty menu
        // would just be clutter on every tile -- and only while the tile is open, where there is
        // room for it; the collapsed row is one line reserved for the label below.
        if (expanded && profiles.isNotEmpty()) {
            OutputProfileSwapMenu(
                profiles = profiles,
                activeProfileId = activeProfileId,
                heading = profileMenuHeading(modeLabel, outputLabel),
                onPick = onPickProfile,
            )
        }
        if (!expanded) {
            Spacer(Modifier.weight(1f))
            Text(
                text = outputLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The header's profile picker: an icon button that opens a menu of every saved [OutputProfile]. */
@Composable
private fun OutputProfileSwapMenu(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    heading: String,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        KeyIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(18.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(Res.string.output_profile_swap_menu_tooltip),
                modifier = Modifier.size(14.dp),
                tint = if (activeProfileId != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProfileMenu(expanded, { expanded = false }, profiles, activeProfileId, heading, onPick)
    }
}

/**
 * The profile an open preview follows, written faintly along the picture's bottom-left edge -- the
 * lock has the other corner -- while the pointer is over the picture, so it never competes with
 * what the congregation is seeing. A label only: the header's swap button is where it is changed.
 */
@Composable
internal fun BoxScope.PreviewProfileLabel(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
) {
    val choice = profileChoice(profiles, activeProfileId)
    val name = choice.name ?: return
    val shape = AppShape(5.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = LABEL_INSET, bottom = LABEL_INSET, end = LABEL_LOCK_ROOM)
            .height(LABEL_HEIGHT)
            .clip(shape)
            .background(LABEL_FILL)
            .padding(horizontal = 7.dp),
    ) {
        Text(
            text = name,
            color = LABEL_INK,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The heading over the profile menu: "OUTPUT PROFILE · FULL SCREEN · SCREEN 1". */
@Composable
private fun profileMenuHeading(modeLabel: String, outputLabel: String): String =
    listOf(stringResource(Res.string.output_profile_picker_tooltip), modeLabel, outputLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .uppercase()

/** Every saved profile, Blank first, the one in use ticked. */
@Composable
private fun ProfileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    heading: String,
    onPick: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH),
        offset = DpOffset(0.dp, 6.dp),
        shape = AppShape(9.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = heading,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
            )
            ProfileMenuItem(
                name = stringResource(Res.string.output_profile_blank),
                dot = unusedDot(),
                selected = activeProfileId == BLANK_OUTPUT_PROFILE_ID,
            ) { onDismiss(); onPick(BLANK_OUTPUT_PROFILE_ID) }
            profiles.forEach { profile ->
                ProfileMenuItem(
                    name = profile.name.ifBlank { profile.id },
                    dot = MaterialTheme.semantic.success,
                    selected = profile.id == activeProfileId,
                ) { onDismiss(); onPick(profile.id) }
            }
        }
    }
}

/** One profile in the menu: its dot, its name, and a tick on the one in use. */
@Composable
private fun ProfileMenuItem(
    name: String,
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

/** What the output follows, by name -- null when nothing is chosen -- and whether it is a real profile. */
private class ProfileChoice(val name: String?, val real: Boolean)

@Composable
private fun profileChoice(profiles: List<OutputProfile>, activeProfileId: String?): ProfileChoice {
    val active = profiles.firstOrNull { it.id == activeProfileId }
    return when {
        active != null -> ProfileChoice(active.name.ifBlank { active.id }, real = true)
        activeProfileId == BLANK_OUTPUT_PROFILE_ID ->
            ProfileChoice(stringResource(Res.string.output_profile_blank), real = false)
        else -> ProfileChoice(null, real = false)
    }
}

@Composable
private fun ProfileChoice.dot(): Color = if (real) MaterialTheme.semantic.success else unusedDot()

@Composable
private fun unusedDot(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

@Composable
private fun ProfileDot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

/** How far the caret turns when the preview is folded away; the same quarter turn the tray uses. */
private const val CARET_CLOSED_DEGREES = -90f

/** How much space the label leaves at the picture's bottom edge, which the bottom-left badges stack above. */
internal val PROFILE_LABEL_CLEARANCE = 28.dp

private val LABEL_HEIGHT = 22.dp
private val LABEL_INSET = 5.dp

/** The lock toggle's corner, which the label never runs into. */
private val LABEL_LOCK_ROOM = 34.dp

// The label sits on the output's own picture, which is dark whatever the app's theme, so it is
// drawn light-on-dark, see-through, like the other badges over the picture.
private val LABEL_FILL = Color.Black.copy(alpha = 0.28f)
private val LABEL_INK = Color.White.copy(alpha = 0.6f)

private val MENU_MIN_WIDTH = 236.dp
private val MENU_MAX_WIDTH = 420.dp
private val MENU_ITEM_HEIGHT = 30.dp
