package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_add
import org.churchpresenter.strings.generated.resources.signage_link_address
import org.churchpresenter.strings.generated.resources.signage_link_address_hint
import org.churchpresenter.strings.generated.resources.signage_link_internet
import org.churchpresenter.strings.generated.resources.signage_link_name
import org.churchpresenter.strings.generated.resources.signage_link_older_player
import org.churchpresenter.strings.generated.resources.signage_link_refresh
import org.churchpresenter.strings.generated.resources.signage_link_smooth
import org.churchpresenter.strings.generated.resources.signage_link_smooth_hint
import org.churchpresenter.strings.generated.resources.signage_link_stream
import org.churchpresenter.strings.generated.resources.signage_link_title
import org.churchpresenter.strings.generated.resources.signage_link_title_edit
import org.churchpresenter.strings.generated.resources.signage_link_web
import org.jetbrains.compose.resources.stringResource
import java.net.URI
import java.net.URISyntaxException

/**
 * Adds a link playlist, or edits [initial]: its address, a name, and whether it is a web page or a
 * stream, guessed from the address until it is chosen by hand. A web page can reload every few minutes.
 */
@Composable
internal fun LinkDialog(
    onAdd: (NewLink) -> Unit,
    onDismiss: () -> Unit,
    initial: LinkPlaylist? = null,
) {
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var chosenKind by remember { mutableStateOf(initial?.kind) }
    var refreshMinutes by remember { mutableIntStateOf(initial?.refreshMinutes ?: 0) }
    var smooth by remember { mutableStateOf(initial?.smooth == true) }
    val kind = chosenKind ?: LinkKind.guess(url)
    val valid = isLinkAddress(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = if (initial == null) Res.string.signage_link_title else Res.string.signage_link_title_edit
            Text(stringResource(title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(DIALOG_WIDTH)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.signage_link_address)) },
                    placeholder = { Text(ADDRESS_PREFIX) },
                    supportingText = { Text(stringResource(Res.string.signage_link_address_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.signage_link_name)) },
                    placeholder = { Text(hostOf(url).orEmpty()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.selectableGroup()) {
                    KindChoice(stringResource(Res.string.signage_link_web), kind == LinkKind.WEB) {
                        chosenKind = LinkKind.WEB
                    }
                    KindChoice(stringResource(Res.string.signage_link_stream), kind == LinkKind.STREAM) {
                        chosenKind = LinkKind.STREAM
                    }
                }
                if (kind == LinkKind.WEB) {
                    SmoothToggle(smooth) { smooth = it }
                    NumberSettingsTextField(
                        label = stringResource(Res.string.signage_link_refresh),
                        initialText = refreshMinutes,
                        range = REFRESH_MINUTES,
                        onValueChange = { refreshMinutes = it },
                    )
                }
                Text(
                    stringResource(Res.string.signage_link_internet) + " " +
                        stringResource(Res.string.signage_link_older_player),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val web = kind == LinkKind.WEB
                    onAdd(
                        NewLink(
                            name = name.ifBlank { hostOf(url) ?: url },
                            url = url,
                            kind = kind,
                            refreshMinutes = if (web) refreshMinutes else 0,
                            smooth = web && smooth,
                        ),
                    )
                    onDismiss()
                },
                enabled = valid,
            ) { Text(stringResource(if (initial == null) Res.string.signage_add else Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** Smooth motion, with what it does and what it costs said under it. */
@Composable
private fun SmoothToggle(smooth: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = smooth, onValueChange = onChange, role = Role.Switch),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(Res.string.signage_link_smooth), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.signage_link_smooth_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = smooth, onCheckedChange = null)
    }
}

@Composable
private fun KindChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/** An address a screen can open: a scheme and a host, such as `https://example.org` or `rtsp://camera`. */
internal fun isLinkAddress(url: String): Boolean = hostOf(url) != null

private fun hostOf(url: String): String? = try {
    URI(url.trim()).takeIf { it.scheme != null }?.host?.removePrefix("www.")
} catch (_: URISyntaxException) {
    null
}

/** Shown in the empty address field: a scheme, the same in every language. */
private const val ADDRESS_PREFIX = "https://"
private val DIALOG_WIDTH = 440.dp
private val REFRESH_MINUTES = 0..1440
