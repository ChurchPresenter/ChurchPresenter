package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_layer_image
import org.churchpresenter.settings.BackgroundContent
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background_default
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.set_as_background
import org.churchpresenter.strings.generated.resources.set_as_background_all_outputs
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.keyboardFocusRing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * One place a picture or clip can be set as the background: the outputs on profile [profileId],
 * named by [label] ("Screen 1 + NDI 1"). Null [profileId] is every output.
 */
@Immutable
data class BackgroundTarget(val profileId: String?, val label: String)

/** Test handle for one choice: the target's profile (`all` for every output) and the content. */
fun setAsBackgroundTag(profileId: String?, content: BackgroundContent): String =
    "set_as_background_${profileId ?: "all"}_${content.name}"

/**
 * Set as Background, in a context menu: pressed, it opens below itself into every output then each
 * profile in [targets], each with Default, Songs and Bible to press. [onSet] is told the profile
 * (null for every output) and which background; the menu is the caller's to close.
 */
@Composable
fun ColumnScope.SetAsBackgroundItems(
    targets: List<BackgroundTarget>,
    startOpen: Boolean = false,
    /** Whether this picture or clip is that background already; its button is outlined. */
    isCurrent: (profileId: String?, content: BackgroundContent) -> Boolean = { _, _ -> false },
    onSet: (profileId: String?, content: BackgroundContent) -> Unit,
) {
    var open by remember { mutableStateOf(startOpen) }
    ContextMenuItem(
        label = stringResource(Res.string.set_as_background),
        icon = painterResource(IconRes.drawable.ic_layer_image),
        accent = MaterialTheme.colorScheme.tertiary,
        onClick = { open = !open },
    )
    if (!open) return
    val all = BackgroundTarget(null, stringResource(Res.string.set_as_background_all_outputs))
    (listOf(all) + targets).forEach { target -> BackgroundTargetRow(target, isCurrent, onSet) }
}

@Composable
private fun BackgroundTargetRow(
    target: BackgroundTarget,
    isCurrent: (profileId: String?, content: BackgroundContent) -> Boolean,
    onSet: (profileId: String?, content: BackgroundContent) -> Unit,
) {
    // The outputs' names on a line of their own, the three choices under them: side by side, a
    // name like "Screen 1 + NDI Output 1" was cut off in a menu this narrow.
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = TARGET_INDENT, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            target.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (target.profileId == null) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BackgroundContent.entries.forEach { content ->
                ContentChip(
                    label = stringResource(content.label()),
                    current = isCurrent(target.profileId, content),
                    modifier = Modifier.testTag(setAsBackgroundTag(target.profileId, content)),
                    onClick = { onSet(target.profileId, content) },
                )
            }
        }
    }
}

@Composable
private fun ContentChip(label: String, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = AppShape(6.dp)
    Box(
        modifier = modifier
            .keyboardFocusRing(shape)
            // The background this already is: outlined, so the choice made last time can be seen.
            .then(if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .semantics { selected = current }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

private fun BackgroundContent.label() = when (this) {
    BackgroundContent.DEFAULT -> Res.string.background_default
    BackgroundContent.SONG -> Res.string.songs
    BackgroundContent.BIBLE -> Res.string.bible
}

/** Lines the targets up under the item's label, past its icon chip. */
private val TARGET_INDENT = 43.dp
