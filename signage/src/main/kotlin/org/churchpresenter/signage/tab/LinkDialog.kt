package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.LabeledRadioButton
import org.churchpresenter.sharedui.composables.LabeledSwitch
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_add
import org.churchpresenter.strings.generated.resources.signage_link_address
import org.churchpresenter.strings.generated.resources.signage_link_address_hint
import org.churchpresenter.strings.generated.resources.signage_link_allowed_sites
import org.churchpresenter.strings.generated.resources.signage_link_allowed_sites_hint
import org.churchpresenter.strings.generated.resources.signage_link_any_site
import org.churchpresenter.strings.generated.resources.signage_link_any_site_hint
import org.churchpresenter.strings.generated.resources.signage_link_idle_reset
import org.churchpresenter.strings.generated.resources.signage_link_interactive
import org.churchpresenter.strings.generated.resources.signage_link_interactive_hint
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
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource
import java.net.URI
import java.net.URISyntaxException

/**
 * Adds a link playlist, or edits [initial]: its address, a name, and whether it is a web page or a
 * stream, guessed from the address until it is chosen by hand. A web page can reload every few minutes,
 * or be one people use on the touchscreen, which starts over after a time without a touch.
 */
@Composable
internal fun LinkDialog(
    onAdd: (NewLink) -> Unit,
    onDismiss: () -> Unit,
    initial: LinkPlaylist? = null,
) {
    // The cursor starts after the address, so typing carries on from the prefilled start.
    var field by remember {
        val start = initial?.url ?: ADDRESS_PREFIX
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    val url = field.text
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var chosenKind by remember { mutableStateOf(initial?.kind) }
    val page = remember { WebOptions(initial) }
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
                SettingsTextField(
                    value = field,
                    onValueChange = { typed ->
                        val text = withoutPrefixTwice(typed.text)
                        field = if (text == typed.text) typed else TextFieldValue(text, TextRange(text.length))
                    },
                    label = stringResource(Res.string.signage_link_address),
                    supportingText = { FieldHint(stringResource(Res.string.signage_link_address_hint)) },
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().testTag(LINK_ADDRESS_TAG),
                )
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(Res.string.signage_link_name),
                    placeholder = { Text(hostOf(url).orEmpty(), style = MaterialTheme.typography.bodySmall) },
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().testTag(LINK_NAME_TAG),
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledRadioButton(
                        selected = kind == LinkKind.WEB,
                        onClick = { chosenKind = LinkKind.WEB },
                        label = stringResource(Res.string.signage_link_web),
                    )
                    LabeledRadioButton(
                        selected = kind == LinkKind.STREAM,
                        onClick = { chosenKind = LinkKind.STREAM },
                        label = stringResource(Res.string.signage_link_stream),
                    )
                }
                if (kind == LinkKind.WEB) WebOptionFields(page)
                Text(
                    stringResource(Res.string.signage_link_internet) + " " +
                        stringResource(Res.string.signage_link_older_player),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    val web = kind == LinkKind.WEB
                    val interactive = page.interactive().takeIf { web }
                    val drawn = web && interactive == null
                    onAdd(
                        NewLink(
                            name = name.ifBlank { hostOf(url) ?: url },
                            url = url,
                            kind = kind,
                            refreshMinutes = if (drawn) page.refreshMinutes else 0,
                            smooth = drawn && page.smooth,
                            interactive = interactive,
                        ),
                    )
                    onDismiss()
                },
                enabled = valid,
            ) { Text(stringResource(if (initial == null) Res.string.signage_add else Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** A web page's own settings while the dialog is open: drawn on the TV, or used on the touchscreen. */
private class WebOptions(initial: LinkPlaylist?) {
    var refreshMinutes by mutableIntStateOf(initial?.refreshMinutes ?: 0)
    var smooth by mutableStateOf(initial?.smooth == true)
    var touch by mutableStateOf(initial?.interactive != null)
    var idleSeconds by mutableIntStateOf(
        initial?.interactive?.idleResetSeconds ?: InteractiveLink.DEFAULT_IDLE_RESET_SECONDS,
    )

    /** Any site may open from the page: on for a new one, since its sign-up may go anywhere to pay. */
    var anySite by mutableStateOf(initial?.interactive?.let { InteractiveLink.ANY_HOST in it.allowedHosts } ?: true)
    var sites by mutableStateOf(
        initial?.interactive?.allowedHosts.orEmpty().filterNot { it == InteractiveLink.ANY_HOST }.joinToString(", "),
    )

    /** What the page is on a touchscreen; null for one drawn on the TV. */
    fun interactive(): InteractiveLink? {
        val hosts = if (anySite) listOf(InteractiveLink.ANY_HOST) else InteractiveLink.hostsFrom(sites)
        return InteractiveLink(idleSeconds, hosts).takeIf { touch }
    }
}

/**
 * For a page people use on the touchscreen, how it starts over and where it may go; for one drawn on
 * the TV, smoothness and reloads.
 */
@Composable
private fun WebOptionFields(page: WebOptions) {
    SwitchRow(
        title = stringResource(Res.string.signage_link_interactive),
        hint = stringResource(Res.string.signage_link_interactive_hint),
        checked = page.touch,
    ) { page.touch = it }
    if (page.touch) {
        NumberSettingsTextField(
            label = stringResource(Res.string.signage_link_idle_reset),
            initialText = page.idleSeconds,
            range = InteractiveLink.IDLE_RESET_SECONDS,
            onValueChange = { page.idleSeconds = it },
        )
        SwitchRow(
            title = stringResource(Res.string.signage_link_any_site),
            hint = stringResource(Res.string.signage_link_any_site_hint),
            checked = page.anySite,
        ) { page.anySite = it }
        if (!page.anySite) {
            SettingsTextField(
                value = page.sites,
                onValueChange = { page.sites = it },
                label = stringResource(Res.string.signage_link_allowed_sites),
                supportingText = { FieldHint(stringResource(Res.string.signage_link_allowed_sites_hint)) },
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().testTag(LINK_SITES_TAG),
            )
        }
    } else {
        SwitchRow(
            title = stringResource(Res.string.signage_link_smooth),
            hint = stringResource(Res.string.signage_link_smooth_hint),
            checked = page.smooth,
        ) { page.smooth = it }
        NumberSettingsTextField(
            label = stringResource(Res.string.signage_link_refresh),
            initialText = page.refreshMinutes,
            range = REFRESH_MINUTES,
            onValueChange = { page.refreshMinutes = it },
        )
    }
}

/** The line under a field, as small as every other hint in the dialog. */
@Composable
private fun FieldHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** A switch with what it does, and what it costs, said under it: the app's own labelled switch. */
@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabeledSwitch(
        checked = checked,
        onCheckedChange = onChange,
        label = title,
        supporting = hint,
        controlAtEnd = true,
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** An address a screen can open: a scheme and a host, such as `https://example.org` or `rtsp://camera`. */
internal fun isLinkAddress(url: String): Boolean = hostOf(url) != null

private fun hostOf(url: String): String? = try {
    // The prefilled "https://www." alone is no address yet.
    URI(url.trim()).takeIf { it.scheme != null }?.host?.removePrefix("www.")?.trim('.')?.takeIf(String::isNotEmpty)
} catch (_: URISyntaxException) {
    null
}

/**
 * [typed] without the prefilled [ADDRESS_PREFIX] when a whole address, or one starting `www.`, was
 * pasted or typed after it: `https://www.https://example.org` is `https://example.org`.
 */
internal fun withoutPrefixTwice(typed: String): String {
    if (typed == ADDRESS_PREFIX || ADDRESS_PREFIX !in typed) return typed
    val rest = typed.replaceFirst(ADDRESS_PREFIX, "")
    return when {
        "://" in rest -> rest
        rest.startsWith("www.") -> "https://$rest"
        else -> typed
    }
}

/** The dialog's text fields, whose captions are drawn apart from the field a test types into. */
internal const val LINK_ADDRESS_TAG = "signage-link-address"
internal const val LINK_NAME_TAG = "signage-link-name"
internal const val LINK_SITES_TAG = "signage-link-sites"

/** What a new link's address starts as, so only the rest is typed: the same in every language. */
private const val ADDRESS_PREFIX = "https://www."
private val DIALOG_WIDTH = 440.dp
private val REFRESH_MINUTES = 0..1440
