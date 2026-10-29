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
import org.churchpresenter.strings.generated.resources.close
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
        title = { Text(stringResource(Res.string.signage_help_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.width(HELP_WIDTH).heightIn(max = HELP_HEIGHT).verticalScroll(rememberScrollState()),
            ) {
                SECTIONS.forEach { (title, body) -> HelpSection(title, body) }
            }
        },
        confirmButton = { RaisedButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

@Composable
private fun HelpSection(title: StringResource, body: StringResource) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Each part of the help, in the order someone setting up screens needs them. */
private val SECTIONS = listOf(
    Res.string.signage_help_linked_title to Res.string.signage_help_linked,
    Res.string.signage_help_copies_title to Res.string.signage_help_copies,
    Res.string.signage_help_files_title to Res.string.signage_help_files,
    Res.string.signage_help_shared_title to Res.string.signage_help_shared,
    Res.string.signage_help_updates_title to Res.string.signage_help_updates,
)

private val HELP_WIDTH = 520.dp
private val HELP_HEIGHT = 600.dp
