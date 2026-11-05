package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_help_got_it
import org.churchpresenter.strings.generated.resources.signage_help
import org.churchpresenter.strings.generated.resources.signage_help_copies
import org.churchpresenter.strings.generated.resources.signage_help_copies_title
import org.churchpresenter.strings.generated.resources.signage_help_files
import org.churchpresenter.strings.generated.resources.signage_help_files_title
import org.churchpresenter.strings.generated.resources.signage_help_linked
import org.churchpresenter.strings.generated.resources.signage_help_linked_title
import org.churchpresenter.strings.generated.resources.signage_help_shared
import org.churchpresenter.strings.generated.resources.signage_help_shared_title
import org.churchpresenter.strings.generated.resources.signage_help_title
import org.churchpresenter.strings.generated.resources.signage_help_updates
import org.churchpresenter.strings.generated.resources.signage_help_updates_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.components.RaisedButton

/** The Help button beside Add, and the dialog it opens: how playlists, folders and sharing work. */
@Composable
internal fun SignageHelpButton() {
    var open by remember { mutableStateOf(false) }
    RingedGhostButton(onClick = { open = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.signage_help))
    }
    if (open) SignageHelpDialog(onDismiss = { open = false })
}

@Composable
internal fun SignageHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SignageDialogTitle(
                stringResource(Res.string.signage_help_title),
                tint = with(MaterialTheme.colorScheme) { KindTint(primary.copy(alpha = ACCENT_WASH), primary) },
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.width(HELP_WIDTH).heightIn(max = HELP_HEIGHT).verticalScroll(rememberScrollState()),
            ) {
                SECTIONS.forEachIndexed { index, (kind, text) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HelpSection(kind, text.first, text.second)
                }
            }
        },
        confirmButton = { RaisedButton(onClick = onDismiss) { Text(stringResource(Res.string.signage_help_got_it)) } },
    )
}

@Composable
private fun HelpSection(kind: TileKind, title: StringResource, body: StringResource) {
    Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        KindTile(kind, size = 30.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Each part of the help, in the order someone setting up screens needs them, each with a tile. */
private val SECTIONS = listOf(
    TileKind.FOLDER to (Res.string.signage_help_linked_title to Res.string.signage_help_linked),
    TileKind.PICTURE to (Res.string.signage_help_copies_title to Res.string.signage_help_copies),
    TileKind.VIDEO to (Res.string.signage_help_files_title to Res.string.signage_help_files),
    TileKind.SCREEN to (Res.string.signage_help_shared_title to Res.string.signage_help_shared),
    TileKind.ROTATION to (Res.string.signage_help_updates_title to Res.string.signage_help_updates),
)

private const val ACCENT_WASH = 0.14f
private val HELP_WIDTH = 520.dp
private val HELP_HEIGHT = 600.dp
