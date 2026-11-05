package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.sharedui.composables.tabMovesFocus
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_add
import org.churchpresenter.strings.generated.resources.signage_link_address
import org.churchpresenter.strings.generated.resources.signage_link_allowed_sites
import org.churchpresenter.strings.generated.resources.signage_link_allowed_sites_hint
import org.churchpresenter.strings.generated.resources.signage_link_any_site
import org.churchpresenter.strings.generated.resources.signage_link_any_site_hint
import org.churchpresenter.strings.generated.resources.signage_link_idle_hint
import org.churchpresenter.strings.generated.resources.signage_link_idle_no_playlists
import org.churchpresenter.strings.generated.resources.signage_link_idle_playlist
import org.churchpresenter.strings.generated.resources.signage_link_idle_playlist_pick
import org.churchpresenter.strings.generated.resources.signage_link_idle_prompt
import org.churchpresenter.strings.generated.resources.signage_link_idle_prompt_default
import org.churchpresenter.strings.generated.resources.signage_link_idle_prompt_hint
import org.churchpresenter.strings.generated.resources.signage_link_idle_reset
import org.churchpresenter.strings.generated.resources.signage_link_idle_screen
import org.churchpresenter.strings.generated.resources.signage_link_idle_show_page
import org.churchpresenter.strings.generated.resources.signage_link_idle_url
import org.churchpresenter.strings.generated.resources.signage_link_idle_web
import org.churchpresenter.strings.generated.resources.signage_link_interactive
import org.churchpresenter.strings.generated.resources.signage_link_interactive_hint
import org.churchpresenter.strings.generated.resources.signage_link_internet
import org.churchpresenter.strings.generated.resources.signage_link_idle_caption
import org.churchpresenter.strings.generated.resources.signage_link_name
import org.churchpresenter.strings.generated.resources.signage_link_older_player
import org.churchpresenter.strings.generated.resources.signage_link_page_code
import org.churchpresenter.strings.generated.resources.signage_link_page_code_hint
import org.churchpresenter.strings.generated.resources.signage_link_page_code_idle
import org.churchpresenter.strings.generated.resources.signage_link_page_css
import org.churchpresenter.strings.generated.resources.signage_link_page_script
import org.churchpresenter.strings.generated.resources.signage_link_page_script_hint
import org.churchpresenter.strings.generated.resources.signage_link_refresh
import org.churchpresenter.strings.generated.resources.signage_link_smooth
import org.churchpresenter.strings.generated.resources.signage_link_kind
import org.churchpresenter.strings.generated.resources.signage_link_motion
import org.churchpresenter.strings.generated.resources.signage_link_motion_hint
import org.churchpresenter.strings.generated.resources.signage_link_normal
import org.churchpresenter.strings.generated.resources.signage_link_normal_desc
import org.churchpresenter.strings.generated.resources.signage_link_smooth_desc
import org.churchpresenter.strings.generated.resources.signage_link_stream_desc
import org.churchpresenter.strings.generated.resources.signage_link_subtitle
import org.churchpresenter.strings.generated.resources.signage_link_web_desc
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import org.churchpresenter.strings.generated.resources.signage_link_stream
import org.churchpresenter.strings.generated.resources.signage_link_title
import org.churchpresenter.strings.generated.resources.signage_link_title_edit
import org.churchpresenter.strings.generated.resources.signage_link_touch_reload_hint
import org.churchpresenter.strings.generated.resources.signage_link_web
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/**
 * Adds a link playlist, or edits [initial]: its address, a name, and whether it is a web page or a
 * stream, guessed from the address until it is chosen by hand. A web page can reload every few minutes,
 * or be one people use on the touchscreen, which starts over after a time without a touch, and can
 * have one of [idlePlaylists] or another page on screen until someone taps. Any web page can carry the
 * church's own CSS and JavaScript.
 */
@Composable
internal fun LinkDialog(
    onAdd: (NewLink) -> Unit,
    onDismiss: () -> Unit,
    initial: LinkPlaylist? = null,
    /** The playlists the screen may play while nobody uses a touchscreen page. */
    idlePlaylists: List<PlaylistSummary> = emptyList(),
) {
    // The cursor starts after the address, so typing carries on from the prefilled start.
    var field by remember {
        val start = initial?.url ?: ADDRESS_PREFIX
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    val url = field.text
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var chosenKind by remember { mutableStateOf(initial?.kind) }
    val defaultPrompt = stringResource(Res.string.signage_link_idle_prompt_default)
    val page = remember { WebOptions(initial, idlePlaylists, defaultPrompt) }
    val kind = chosenKind ?: LinkKind.guess(url)
    val valid = isLinkAddress(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = if (initial == null) Res.string.signage_link_title else Res.string.signage_link_title_edit
            SignageDialogTitle(
                stringResource(title),
                stringResource(Res.string.signage_link_subtitle),
                info = stringResource(Res.string.signage_link_internet) + " " +
                    stringResource(Res.string.signage_link_older_player),
                kind = if (kind == LinkKind.WEB) TileKind.WEB else TileKind.STREAM,
            )
        },
        text = {
            val scroll = rememberScrollState()
            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.width(DIALOG_WIDTH)
                        .verticalScroll(scroll)
                        .padding(end = SettingsScrollbarGutter),
                ) {
                    AddressRow(field, { field = it }, page.takeIf { kind == LinkKind.WEB })
                    SettingsTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(Res.string.signage_link_name),
                        placeholder = { Text(hostOf(url).orEmpty(), style = MaterialTheme.typography.bodySmall) },
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth().testTag(LINK_NAME_TAG),
                    )
                    Column {
                        FieldCaption(stringResource(Res.string.signage_link_kind))
                        Row(
                            modifier = Modifier.selectableGroup().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChoiceTile(
                                selected = kind == LinkKind.WEB,
                                onClick = { chosenKind = LinkKind.WEB },
                                title = stringResource(Res.string.signage_link_web),
                                description = stringResource(Res.string.signage_link_web_desc),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                leading = { KindTile(TileKind.WEB, size = 30.dp) },
                            )
                            ChoiceTile(
                                selected = kind == LinkKind.STREAM,
                                onClick = { chosenKind = LinkKind.STREAM },
                                title = stringResource(Res.string.signage_link_stream),
                                description = stringResource(Res.string.signage_link_stream_desc),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                leading = { KindTile(TileKind.STREAM, size = 30.dp) },
                            )
                        }
                    }
                    if (kind == LinkKind.WEB) WebOptionFields(page, idlePlaylists)
                }
                Box(Modifier.matchParentSize()) { SettingsScrollbar(scroll) }
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
                            refreshMinutes = if (web) page.refreshMinutes else 0,
                            smooth = drawn && page.smooth,
                            interactive = interactive,
                            pageCode = page.pageCode().takeIf { web },
                        ),
                    )
                    onDismiss()
                },
                enabled = valid && (kind != LinkKind.WEB || page.idleValid()),
            ) { Text(stringResource(if (initial == null) Res.string.signage_add else Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** What a touchscreen page's screen shows while nobody uses it. */
private enum class IdleChoice { PAGE, PLAYLIST, WEB }

/** The link's address, and beside it how often a web page ([page], null for a stream) reloads. */
@Composable
private fun AddressRow(field: TextFieldValue, onField: (TextFieldValue) -> Unit, page: WebOptions?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextField(
            value = field,
            onValueChange = { typed ->
                val text = withoutPrefixTwice(typed.text)
                onField(if (text == typed.text) typed else TextFieldValue(text, TextRange(text.length)))
            },
            label = stringResource(Res.string.signage_link_address),
            fillWidth = true,
            modifier = Modifier.weight(1f).testTag(LINK_ADDRESS_TAG),
        )
        if (page != null) {
            NumberSettingsTextField(
                label = stringResource(Res.string.signage_link_refresh),
                initialText = page.refreshMinutes,
                range = REFRESH_MINUTES,
                onValueChange = { page.refreshMinutes = it },
                modifier = Modifier.width(RELOAD_WIDTH).testTag(LINK_REFRESH_TAG),
            )
        }
    }
}

/** A web page's own settings while the dialog is open: drawn on the TV, or used on the touchscreen. */
private class WebOptions(initial: LinkPlaylist?, idlePlaylists: List<PlaylistSummary>, defaultPrompt: String) {
    var refreshMinutes by mutableIntStateOf(initial?.refreshMinutes ?: 0)
    var idleRefreshMinutes by mutableIntStateOf(initial?.interactive?.idleRefreshMinutes ?: 0)
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

    private val savedIdle = initial?.interactive

    /** The church's own CSS and JavaScript: shown open when the link has some. */
    var codeOn by mutableStateOf(initial?.pageCode != null)
    var css by mutableStateOf(initial?.pageCode?.css.orEmpty())
    var script by mutableStateOf(initial?.pageCode?.script.orEmpty())

    /** What was written, while the switch is on; null with nothing written. */
    fun pageCode(): PageCode? = if (codeOn) PageCode.of(css, script) else null

    /** A playlist that has gone since counts as none: the page itself shows. */
    var idlePlaylistId by mutableStateOf(savedIdle?.idlePlaylistId?.takeIf { id -> idlePlaylists.any { it.id == id } })
    var idleChoice by mutableStateOf(
        when {
            idlePlaylistId != null -> IdleChoice.PLAYLIST
            savedIdle?.idleUrl != null -> IdleChoice.WEB
            else -> IdleChoice.PAGE
        },
    )

    /** What the idle screen says: the saved text, or the default for a link that had no idle screen yet. */
    var idlePrompt by mutableStateOf(
        if (savedIdle?.idlePlaylistId != null || savedIdle?.idleUrl != null) {
            savedIdle.idlePrompt.orEmpty()
        } else {
            defaultPrompt
        },
    )
    var idleField by mutableStateOf(
        (savedIdle?.idleUrl ?: ADDRESS_PREFIX).let { TextFieldValue(it, TextRange(it.length)) },
    )

    /** Whether what was chosen for the idle screen can be saved: a playlist picked, or a whole address. */
    fun idleValid(): Boolean = !touch || when (idleChoice) {
        IdleChoice.PAGE -> true
        IdleChoice.PLAYLIST -> idlePlaylistId != null
        IdleChoice.WEB -> isLinkAddress(idleField.text)
    }

    /** What the page is on a touchscreen; null for one drawn on the TV. */
    fun interactive(): InteractiveLink? {
        val hosts = if (anySite) listOf(InteractiveLink.ANY_HOST) else InteractiveLink.hostsFrom(sites)
        return InteractiveLink(
            idleSeconds,
            hosts,
            idlePlaylistId = idlePlaylistId.takeIf { idleChoice == IdleChoice.PLAYLIST },
            idleUrl = idleField.text.trim().takeIf { idleChoice == IdleChoice.WEB },
            idlePrompt = idlePrompt.trim().takeIf { idleChoice != IdleChoice.PAGE && it.isNotEmpty() },
            idleRefreshMinutes = idleRefreshMinutes.takeIf { idleChoice == IdleChoice.WEB } ?: 0,
        ).takeIf { touch }
    }
}

/**
 * For a page people use on the touchscreen, how it starts over and where it may go; for one drawn on
 * the TV, smoothness and reloads.
 */
@Composable
private fun WebOptionFields(page: WebOptions, idlePlaylists: List<PlaylistSummary>) {
    SwitchRow(
        title = stringResource(Res.string.signage_link_interactive),
        hint = stringResource(Res.string.signage_link_interactive_hint),
        checked = page.touch,
    ) { page.touch = it }
    if (page.touch) {
        IdleScreenFields(page, idlePlaylists)
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
        MotionChoice(page)
    }
    PageCodeFields(page)
}

/** Normal or smooth motion for a page drawn on the TV, side by side, and how to find out which a screen can do. */
@Composable
private fun MotionChoice(page: WebOptions) {
    Column {
        FieldCaption(
            stringResource(Res.string.signage_link_motion),
            info = stringResource(Res.string.signage_link_motion_hint),
        )
        Row(
            modifier = Modifier.selectableGroup().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceTile(
                selected = !page.smooth,
                onClick = { page.smooth = false },
                title = stringResource(Res.string.signage_link_normal),
                description = stringResource(Res.string.signage_link_normal_desc),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ChoiceTile(
                selected = page.smooth,
                onClick = { page.smooth = true },
                title = stringResource(Res.string.signage_link_smooth),
                description = stringResource(Res.string.signage_link_smooth_desc),
                modifier = Modifier.weight(1f).fillMaxHeight().testTag(LINK_SMOOTH_TAG),
            )
        }
    }
}

/** The church's own CSS and JavaScript for the page, behind a switch: for someone who knows web pages. */
@Composable
private fun PageCodeFields(page: WebOptions) {
    SwitchRow(
        title = stringResource(Res.string.signage_link_page_code),
        hint = stringResource(Res.string.signage_link_page_code_hint),
        checked = page.codeOn,
    ) { page.codeOn = it }
    if (!page.codeOn) return
    CodeField(page.css, { page.css = it }, stringResource(Res.string.signage_link_page_css), CSS_EXAMPLE, LINK_CSS_TAG)
    CodeField(
        page.script, { page.script = it }, stringResource(Res.string.signage_link_page_script), SCRIPT_EXAMPLE,
        LINK_SCRIPT_TAG,
    )
    val idlePage = page.touch && page.idleChoice == IdleChoice.WEB
    FieldHint(
        stringResource(Res.string.signage_link_page_script_hint) +
            if (idlePage) " " + stringResource(Res.string.signage_link_page_code_idle) else "",
    )
}

/** A few lines of code, with an example in the empty field; Tab leaves it rather than typing a tab. */
@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, label: String, example: String, tag: String) {
    SettingsTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = false,
        maxLines = CODE_MAX_LINES,
        placeholder = { Text(example, style = MaterialTheme.typography.bodySmall) },
        fillWidth = true,
        modifier = Modifier.fillMaxWidth().tabMovesFocus().testTag(tag),
    )
}

/**
 * How a touchscreen page starts over, and what its screen shows until someone taps: the page, a
 * playlist, or another page. The playlist, the choice and the time share one line, each a share of
 * it, so a wider font or a longer choice shortens them rather than pushing the time off the dialog.
 */
@Composable
private fun IdleScreenFields(page: WebOptions, idlePlaylists: List<PlaylistSummary>) {
    val labels = mapOf(
        IdleChoice.PAGE to stringResource(Res.string.signage_link_idle_show_page),
        IdleChoice.PLAYLIST to stringResource(Res.string.signage_link_idle_playlist),
        IdleChoice.WEB to stringResource(Res.string.signage_link_idle_web),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldCaption(
            stringResource(Res.string.signage_link_idle_caption),
            info = listOfNotNull(
                stringResource(Res.string.signage_link_idle_hint).takeIf { page.idleChoice != IdleChoice.PAGE },
                stringResource(Res.string.signage_link_idle_prompt_hint).takeIf { page.idleChoice != IdleChoice.PAGE },
                stringResource(Res.string.signage_link_touch_reload_hint),
            ).joinToString(" "),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // What to show first, then which playlist when it is one.
            DropdownSettingsField(
                value = labels.getValue(page.idleChoice),
                options = IdleChoice.entries.map(labels::getValue),
                onValueChange = { picked ->
                    page.idleChoice = IdleChoice.entries[labels.values.indexOf(picked)]
                    // A playlist chosen starts on the first one, so the dialog can save straight away.
                    if (page.idleChoice == IdleChoice.PLAYLIST && page.idlePlaylistId == null) {
                        page.idlePlaylistId = idlePlaylists.firstOrNull()?.id
                    }
                },
                label = stringResource(Res.string.signage_link_idle_screen),
                modifier = Modifier.weight(CHOICE_SHARE),
                width = Dp.Unspecified,
            )
            if (page.idleChoice == IdleChoice.PLAYLIST && idlePlaylists.isNotEmpty()) {
                val current = idlePlaylists.firstOrNull { it.id == page.idlePlaylistId }
                DropdownSettingsField(
                    value = current?.name.orEmpty(),
                    options = idlePlaylists.map { it.name },
                    onValueChange = { name ->
                        page.idlePlaylistId = idlePlaylists[idlePlaylists.indexOfFirst { it.name == name }].id
                    },
                    label = stringResource(Res.string.signage_link_idle_playlist_pick),
                    // A long name ends in an ellipsis before the chevron. An unset width makes the field
                    // lay out to the width it is given.
                    modifier = Modifier.weight(PLAYLIST_SHARE),
                    width = Dp.Unspecified,
                )
            }
            NumberSettingsTextField(
                label = stringResource(Res.string.signage_link_idle_reset),
                initialText = page.idleSeconds,
                range = InteractiveLink.IDLE_RESET_SECONDS,
                onValueChange = { page.idleSeconds = it },
                modifier = Modifier.weight(RESET_SHARE),
            )
        }
        when (page.idleChoice) {
            IdleChoice.PAGE -> Unit
            IdleChoice.PLAYLIST -> if (idlePlaylists.isEmpty()) {
                FieldHint(stringResource(Res.string.signage_link_idle_no_playlists))
            }
            IdleChoice.WEB -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsTextField(
                    value = page.idleField,
                    onValueChange = { typed ->
                        val text = withoutPrefixTwice(typed.text)
                        page.idleField = if (text == typed.text) typed else TextFieldValue(text, TextRange(text.length))
                    },
                    label = stringResource(Res.string.signage_link_idle_url),
                    fillWidth = true,
                    modifier = Modifier.weight(1f).testTag(LINK_IDLE_URL_TAG),
                )
                NumberSettingsTextField(
                    label = stringResource(Res.string.signage_link_refresh),
                    initialText = page.idleRefreshMinutes,
                    range = REFRESH_MINUTES,
                    onValueChange = { page.idleRefreshMinutes = it },
                    modifier = Modifier.width(RELOAD_WIDTH).testTag(LINK_IDLE_REFRESH_TAG),
                )
            }
        }
        if (page.idleChoice != IdleChoice.PAGE) {
            SettingsTextField(
                value = page.idlePrompt,
                onValueChange = { page.idlePrompt = it },
                label = stringResource(Res.string.signage_link_idle_prompt),
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().testTag(LINK_IDLE_PROMPT_TAG),
            )
        }
    }
}

/** The line under a field, as small as every other hint in the dialog. */
@Composable
private fun FieldHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

/** A switch with what it does, and what it costs, said under it: the app's own labelled switch. */
@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    InfoSwitch(title, hint, checked, onChange)
}

/** The dialog's text fields, whose captions are drawn apart from the field a test types into. */
internal const val LINK_ADDRESS_TAG = "signage-link-address"
internal const val LINK_NAME_TAG = "signage-link-name"
internal const val LINK_SITES_TAG = "signage-link-sites"
internal const val LINK_IDLE_URL_TAG = "signage-link-idle-url"
internal const val LINK_IDLE_REFRESH_TAG = "signage-link-idle-refresh"
internal const val LINK_REFRESH_TAG = "signage-link-refresh"
internal const val LINK_IDLE_PROMPT_TAG = "signage-link-idle-prompt"
internal const val LINK_CSS_TAG = "signage-link-css"
internal const val LINK_SMOOTH_TAG = "signage-link-smooth"
internal const val LINK_SCRIPT_TAG = "signage-link-script"

/** What the empty code fields show: code, the same in every language. */
private const val CSS_EXAMPLE = "header, footer { display: none !important; }"
private const val SCRIPT_EXAMPLE = "document.querySelector('a.home').href = location.origin;"
private const val CODE_MAX_LINES = 8

/** How the touchscreen line is shared: the playlist, what shows while nobody uses it, the reset time. */
private const val PLAYLIST_SHARE = 0.8f
private const val CHOICE_SHARE = 1.1f
private const val RESET_SHARE = 1.1f

private val DIALOG_WIDTH = 480.dp

/** The reload field beside an address: wide enough for its label. */
private val RELOAD_WIDTH = 150.dp
private val REFRESH_MINUTES = 0..1440
