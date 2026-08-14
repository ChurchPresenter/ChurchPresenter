package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.apply
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.keyboard_shortcuts_title
import churchpresenter.composeapp.generated.resources.no_results_found
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.shortcut_category_mouse
import churchpresenter.composeapp.generated.resources.shortcut_description_context_menu
import churchpresenter.composeapp.generated.resources.shortcut_description_go_live
import churchpresenter.composeapp.generated.resources.shortcut_description_reorder_item
import churchpresenter.composeapp.generated.resources.shortcut_key_double_click
import churchpresenter.composeapp.generated.resources.shortcut_key_right_click
import churchpresenter.composeapp.generated.resources.shortcut_key_shift_drag
import churchpresenter.composeapp.generated.resources.shortcut_search_by_key
import churchpresenter.composeapp.generated.resources.shortcut_search_placeholder
import churchpresenter.composeapp.generated.resources.shortcut_search_press_prompt
import churchpresenter.composeapp.generated.resources.shortcut_settings_change
import churchpresenter.composeapp.generated.resources.shortcut_settings_clear
import churchpresenter.composeapp.generated.resources.shortcut_settings_reset
import churchpresenter.composeapp.generated.resources.shortcut_settings_reset_all
import churchpresenter.composeapp.generated.resources.symbol_cancel
import churchpresenter.composeapp.generated.resources.symbol_ok
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.SearchField
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.utils.labelOrUnbound
import org.churchpresenter.app.churchpresenter.utils.searchText
import org.jetbrains.compose.resources.stringResource

/** Fits `⌃⇧N` / `Ctrl+Shift+N` and the "Not set" placeholder without the chip resizing per row. */
private val CHIP_WIDTH = 92.dp

/** Keeps "Reset" and "Clear" the same width, so the buttons line up down the column. */
private val REVERT_WIDTH = 56.dp

/** Tighter than the Material default, which is sized for a full-width dialog button. */
private val BUTTON_PADDING = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/** Test tag for the reset-everything button, which several tests need to locate. */
internal const val SHORTCUT_RESET_ALL_TAG = "shortcut_reset_all"

/** The "no results" line, which has no other stable handle once the list is empty. */
internal const val SHORTCUT_NO_RESULTS_TAG = "shortcut_no_results"

/** The "Press key" toggle. */
internal const val SHORTCUT_PRESS_MODE_TAG = "shortcut_press_mode"

/** The panel that listens for a key while "Press key" is on. */
internal const val SHORTCUT_PRESS_PANEL_TAG = "shortcut_press_panel"

/** The per-action row's key chip, tagged by action so a test can read one row's binding. */
internal fun shortcutChipTag(action: ShortcutAction) = "shortcut_chip_${action.name}"

/** The per-action rebind button. */
internal fun shortcutChangeTag(action: ShortcutAction) = "shortcut_change_${action.name}"

/**
 * The per-action Reset/Clear button.
 *
 * Tagged per action because every row carries one, so "the Clear button" matches ~40 nodes.
 */
internal fun shortcutRevertTag(action: ShortcutAction) = "shortcut_revert_${action.name}"

@Composable
fun KeyboardShortcutsDialog(
    isVisible: Boolean,
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 760.dp, 720.dp),
            width = 760.dp,
            height = 720.dp
        ),
        title = stringResource(Res.string.keyboard_shortcuts_title),
        resizable = true
    ) {
        KeyboardShortcutsDialogContent(
            initialSettings = settings,
            onSave = onSave,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The shortcut reference, and the one place shortcuts are changed.
 *
 * Every keyboard row is a `ShortcutAction` rendered through the same `ShortcutMap` the handlers
 * consult, so the list cannot describe a key the app does not respond to. It used to be ~70
 * hand-written rows paired with hand-written key strings, and it had drifted: Page Up/Down, `B` and
 * `.` were all handled but appeared nowhere here.
 *
 * Editing was briefly a separate Settings tab, which meant two windows showing the same table and
 * only one of them able to change it. It is merged in here.
 *
 * Edits are **pending** until Apply or OK, like the settings tab this replaced — which is why the
 * capture dialog is handed this composable's own map rather than reading `LocalShortcuts`: the
 * composition local still holds what was last saved, so validating against it would report a
 * binding the user had just cleared as a conflict, and miss one they had just assigned.
 *
 * Mouse gestures are still written out by hand at the bottom — they are not key bindings, are not
 * rebindable, and have no registry entry to render from.
 */
@Composable
internal fun KeyboardShortcutsDialogContent(
    initialSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentSettings by remember { mutableStateOf(initialSettings) }
    var capturing by remember { mutableStateOf<ShortcutAction?>(null) }
    // View state only. It must never reach currentSettings, or what Apply saves would depend on
    // whether the user happened to be searching at the time.
    var query by remember { mutableStateOf("") }
    // "Press key" mode: filter by pressing a combination rather than describing it. The two filters
    // are mutually exclusive — each clears the other — because a text query and a pressed chord
    // narrowing the same list at once has no sensible reading.
    var pressMode by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf<KeyChord?>(null) }
    val pressFocus = remember { FocusRequester() }

    val shortcuts = remember(currentSettings.keyboardShortcutSettings) {
        ShortcutMap.from(currentSettings.keyboardShortcutSettings)
    }
    val actionsByScope = remember { ShortcutAction.entries.groupBy { it.scope } }

    // Resolved in composition because descriptions and key labels both come from string resources;
    // the match itself is plain Kotlin below.
    val haystacks: Map<ShortcutAction, String> = ShortcutAction.entries.associateWith { action ->
        "${stringResource(action.descriptionRes)} ${shortcuts.searchText(action)}".lowercase()
    }
    val visibleByScope = remember(query, haystacks, pressed, shortcuts) {
        val chord = pressed
        val needle = query.trim().lowercase()
        actionsByScope.mapValues { (_, actions) ->
            when {
                // Exact chord match, the same question `conflictFor` asks: what is *this*
                // combination already doing? A looser match would fold Ctrl+← in with ← and stop
                // answering it.
                chord != null -> actions.filter { chord in shortcuts.chordsFor(it) }
                needle.isEmpty() -> actions
                else -> actions.filter { needle in haystacks.getValue(it) }
            }
        }
    }

    // The mouse rows are plain strings rather than registry entries, so they match on their own
    // resolved text.
    val mouseRows = listOf(
        stringResource(Res.string.shortcut_key_double_click) to stringResource(Res.string.shortcut_description_go_live),
        stringResource(
            Res.string.shortcut_key_right_click
        ) to stringResource(Res.string.shortcut_description_context_menu),
        stringResource(
            Res.string.shortcut_key_shift_drag
        ) to stringResource(Res.string.shortcut_description_reorder_item),
    )
    val visibleMouseRows = remember(query, mouseRows, pressed) {
        val needle = query.trim().lowercase()
        when {
            // A pressed key can never be a mouse gesture, so the section drops out entirely.
            pressed != null -> emptyList()
            needle.isEmpty() -> mouseRows
            else -> mouseRows.filter { (keys, description) -> needle in "$keys $description".lowercase() }
        }
    }

    val nothingMatched = visibleByScope.values.all { it.isEmpty() } && visibleMouseRows.isEmpty()

    fun editOverrides(update: (Map<String, List<KeyChord>>) -> Map<String, List<KeyChord>>) {
        currentSettings = currentSettings.copy(
            keyboardShortcutSettings = currentSettings.keyboardShortcutSettings.copy(
                overrides = update(currentSettings.keyboardShortcutSettings.overrides)
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pressMode) {
                    // While listening, the box shows what was pressed rather than accepting text —
                    // the arrow keys have to reach the filter, and they cannot also move a cursor.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .focusRequester(pressFocus)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                capturedChord(event)?.let { pressed = it }
                                event.type == KeyEventType.KeyDown
                            }
                            .testTag(SHORTCUT_PRESS_PANEL_TAG),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = pressed?.label() ?: stringResource(Res.string.shortcut_search_press_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (pressed != null) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LaunchedEffect(Unit) { pressFocus.requestFocus() }
                } else {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(Res.string.shortcut_search_placeholder),
                        modifier = Modifier.weight(1f),
                    )
                }
                // Toggling either way drops whatever the other mode had filtered by, so the list is
                // never narrowed by a filter the header is no longer showing.
                FilterChip(
                    selected = pressMode,
                    onClick = {
                        pressMode = !pressMode
                        pressed = null
                        query = ""
                    },
                    label = { Text(stringResource(Res.string.shortcut_search_by_key), maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag(SHORTCUT_PRESS_MODE_TAG),
                )
                TextButton(
                    onClick = { editOverrides { emptyMap() } },
                    modifier = Modifier.testTag(SHORTCUT_RESET_ALL_TAG)
                ) { Text(stringResource(Res.string.shortcut_settings_reset_all)) }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scope order is the enum's own order, which is menus → global → per tab. A scope
                // with nothing matching is dropped entirely rather than drawn as an empty box.
                ShortcutScope.entries.forEach { scope ->
                    val actions = visibleByScope[scope].orEmpty()
                    if (actions.isNotEmpty()) {
                        ShortcutsCategory(stringResource(scope.titleRes)) {
                            actions.forEach { action ->
                                ShortcutBindingRow(
                                    action = action,
                                    keys = shortcuts.labelOrUnbound(action),
                                    customized = shortcuts.isCustomized(action),
                                    onChange = { capturing = action },
                                    onReset = { editOverrides { it - action.name } },
                                    onClear = { editOverrides { it + (action.name to emptyList()) } },
                                )
                            }
                        }
                    }
                }

                if (visibleMouseRows.isNotEmpty()) {
                    ShortcutsCategory(stringResource(Res.string.shortcut_category_mouse)) {
                        visibleMouseRows.forEach { (keys, description) ->
                            ShortcutRow(keys, description)
                        }
                    }
                }

                if (nothingMatched) {
                    // Names whichever filter is active — the typed text, or the chord that was
                    // pressed. "No results found for \"\"" would be the obvious bug here.
                    val describedFilter = pressed?.label() ?: query
                    Text(
                        text = stringResource(Res.string.no_results_found, describedFilter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag(SHORTCUT_NO_RESULTS_TAG),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                    Text("${stringResource(Res.string.symbol_cancel)} ${stringResource(Res.string.cancel)}")
                }
                OutlinedButton(shape = RoundedCornerShape(6.dp), onClick = { onSave(currentSettings) }) {
                    Text(stringResource(Res.string.apply))
                }
                Button(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { onSave(currentSettings); onDismiss() }
                ) {
                    Text("${stringResource(Res.string.symbol_ok)} ${stringResource(Res.string.ok)}")
                }
            }
        }
    }

    capturing?.let { action ->
        ShortcutCaptureDialog(
            action = action,
            shortcuts = shortcuts,
            onConfirm = { chord ->
                editOverrides { it + (action.name to listOf(chord)) }
                capturing = null
            },
            onDismiss = { capturing = null },
        )
    }
}

@Composable
fun ShortcutsCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * One rebindable action: description, current binding, and the two controls.
 *
 * Deliberately **not** built on `SettingRow`. That gives the label a fixed width and the controls
 * whatever is left, which left the buttons so narrow that "Clear" wrapped to three lines and every
 * row grew to fit it. Here the description takes the slack and the controls keep their width.
 */
@Composable
private fun ShortcutBindingRow(
    action: ShortcutAction,
    keys: String,
    customized: Boolean,
    onChange: () -> Unit,
    onReset: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(action.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(CHIP_WIDTH)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(shortcutChipTag(action))
            )
        }
        OutlinedButton(
            onClick = onChange,
            shape = RoundedCornerShape(6.dp),
            contentPadding = BUTTON_PADDING,
            modifier = Modifier.testTag(shortcutChangeTag(action))
        ) {
            Text(stringResource(Res.string.shortcut_settings_change), maxLines = 1, softWrap = false)
        }
        // One button, two meanings: an untouched row can only be cleared, a customized one can be
        // put back. Offering both at once would widen every row for a control most never need.
        TextButton(
            onClick = if (customized) onReset else onClear,
            contentPadding = BUTTON_PADDING,
            modifier = Modifier.widthIn(min = REVERT_WIDTH).testTag(shortcutRevertTag(action))
        ) {
            Text(
                text = stringResource(
                    if (customized) Res.string.shortcut_settings_reset
                    else Res.string.shortcut_settings_clear
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun ShortcutRow(keys: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = keys,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .widthIn(min = 80.dp)
        )
    }
}
