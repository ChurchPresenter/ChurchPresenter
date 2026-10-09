package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
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
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.apply
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.no_results_found
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.shortcut_category_mouse
import org.churchpresenter.strings.generated.resources.shortcut_description_context_menu
import org.churchpresenter.strings.generated.resources.shortcut_description_go_live
import org.churchpresenter.strings.generated.resources.shortcut_description_reorder_item
import org.churchpresenter.strings.generated.resources.shortcut_key_double_click
import org.churchpresenter.strings.generated.resources.shortcut_key_right_click
import org.churchpresenter.strings.generated.resources.shortcut_key_shift_drag
import org.churchpresenter.strings.generated.resources.shortcut_search_by_key
import org.churchpresenter.strings.generated.resources.shortcut_search_placeholder
import org.churchpresenter.strings.generated.resources.shortcut_search_press_prompt
import org.churchpresenter.strings.generated.resources.shortcut_settings_reset_all
import org.churchpresenter.strings.generated.resources.shortcut_unsaved_many
import org.churchpresenter.strings.generated.resources.shortcut_unsaved_one
import org.churchpresenter.strings.generated.resources.symbol_cancel
import org.churchpresenter.strings.generated.resources.symbol_ok
import org.churchpresenter.app.churchpresenter.composables.SearchField
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.ShortcutScope
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.sharedui.utils.searchText
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import androidx.compose.runtime.Stable

/**
 * What the shortcut list is narrowed by. "Press key" mode filters by pressing a combination rather
 * than describing it; it and the text query are mutually exclusive -- each clears the other --
 * because a text query and a pressed chord narrowing the same list at once has no sensible reading.
 */
@Stable
internal class ShortcutFilter {
    var query by mutableStateOf("")
    var pressMode by mutableStateOf(false)
    var pressed by mutableStateOf<KeyChord?>(null)
    var conflictsOnly by mutableStateOf(false)
    var selectedScope by mutableStateOf<ShortcutScope?>(ShortcutScope.entries.first())
    var recording by mutableStateOf<ShortcutAction?>(null)

    val searching: Boolean get() = query.isNotBlank() || pressed != null
    val filtering: Boolean get() = searching || conflictsOnly

    /** Picks a category from the rail, dropping every other filter. */
    fun selectScope(scope: ShortcutScope?) {
        selectedScope = scope
        query = ""
        pressed = null
        pressMode = false
        conflictsOnly = false
        recording = null
    }
}

/**
 * Lists the shortcut Wick names: when the guide session asks for one, every search and filter is
 * dropped and its category is selected, so its row is on screen for the ring to find.
 */
@Composable
internal fun FollowShortcutFocus(filter: ShortcutFilter) {
    val session = LocalGuideSession.current ?: return
    val focus = session.shortcutFocus
    LaunchedEffect(focus) {
        if (focus == null) return@LaunchedEffect
        val action = ShortcutAction.entries.firstOrNull { it.name == focus }
        if (action != null) filter.selectScope(action.scope)
        session.shortcutFocus = null
    }
}

/** What the list shows under the current filter, and the rail's categories with their counts. */
internal class VisibleShortcuts(
    val actions: List<ShortcutAction>,
    val mouseRows: List<Pair<String, String>>,
    val categories: List<ShortcutCategory>,
)

/** The actions and mouse gestures [filter] lets through, and the rail's categories. */
@Composable
internal fun rememberVisibleShortcuts(
    filter: ShortcutFilter,
    shortcuts: ShortcutMap,
    conflicts: Map<ShortcutAction, List<ShortcutAction>>,
    devMode: Boolean = true,
): VisibleShortcuts {
    // What this run offers: outside dev mode, the actions of dev-mode-only features are left out.
    val offered = ShortcutAction.entries.filter { devMode || !it.devOnly }

    val query = filter.query
    val pressed = filter.pressed
    val conflictsOnly = filter.conflictsOnly
    val selectedScope = filter.selectedScope
    // Resolved in composition because descriptions and key labels both come from string resources;
    // the match itself is plain Kotlin below.
    val haystacks: Map<ShortcutAction, String> = offered.associateWith { action ->
        "${stringResource(action.descriptionRes)} ${shortcuts.searchText(action)}".lowercase()
    }

    // The mouse rows are plain strings rather than registry entries, so they match on their own
    // resolved text.
    val mouseRows = listOf(
        stringResource(Res.string.shortcut_key_double_click) to stringResource(Res.string.shortcut_description_go_live),
        stringResource(Res.string.shortcut_key_right_click) to
            stringResource(Res.string.shortcut_description_context_menu),
        stringResource(Res.string.shortcut_key_shift_drag) to
            stringResource(Res.string.shortcut_description_reorder_item),
    )

    val visibleActions = remember(query, haystacks, pressed, shortcuts, conflicts, conflictsOnly, selectedScope) {
        val chord = pressed
        val needle = query.trim().lowercase()
        when {
            conflictsOnly -> offered.filter { it in conflicts }
            // Exact chord match, the same question `conflictFor` asks: what is *this* combination
            // already doing? A looser match would fold Ctrl+← in with ← and stop answering it.
            chord != null -> offered.filter { chord in shortcuts.chordsFor(it) }
            needle.isNotEmpty() -> offered.filter { needle in haystacks.getValue(it) }
            else -> offered.filter { it.scope == selectedScope }
        }
    }
    val visibleMouseRows = remember(query, mouseRows, pressed, conflictsOnly, selectedScope) {
        val needle = query.trim().lowercase()
        when {
            // A gesture can neither conflict with a key nor be the key that was pressed, so the
            // section drops out of both of those filters entirely.
            conflictsOnly || pressed != null -> emptyList()
            needle.isNotEmpty() -> mouseRows.filter { (keys, description) ->
                needle in "$keys $description".lowercase()
            }
            selectedScope == null -> mouseRows
            else -> emptyList()
        }
    }

    val categories = offered.groupBy { it.scope }.map { (scope, actions) ->
        ShortcutCategory(
            scope = scope,
            title = stringResource(scope.titleRes),
            count = actions.size,
            hasConflict = actions.any { it in conflicts },
        )
    } + ShortcutCategory(
        scope = null,
        title = stringResource(Res.string.shortcut_category_mouse),
        count = mouseRows.size,
        hasConflict = false,
    )
    return VisibleShortcuts(visibleActions, visibleMouseRows, categories)
}

/** The search box -- or, in "Press key" mode, the panel listening for a key -- and the two filters. */
@Composable
internal fun ShortcutsToolbar(filter: ShortcutFilter, conflictCount: Int, pressFocus: FocusRequester) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filter.pressMode) {
            // While listening, the box shows what was filter.pressed rather than accepting text —
            // the arrow keys have to reach the filter, and they cannot also move a cursor.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .sunken(
                        AppShape(8.dp),
                        elevationPalette(),
                        rim = MaterialTheme.colorScheme.primary,
                    )
                    .focusRequester(pressFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        capturedChord(event)?.let { filter.pressed = it }
                        event.type == KeyEventType.KeyDown
                    }
                    .testTag(SHORTCUT_PRESS_PANEL_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.pressed?.label() ?: stringResource(Res.string.shortcut_search_press_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (filter.pressed != null) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LaunchedEffect(Unit) { pressFocus.requestFocus() }
        } else {
            SearchField(
                value = filter.query,
                onValueChange = { filter.query = it; filter.conflictsOnly = false },
                placeholder = stringResource(Res.string.shortcut_search_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
        // Toggling either way drops whatever the other mode had filtered by, so the list is
        // never narrowed by a filter the header is no longer showing.
        RaisedFilterChip(
            selected = filter.pressMode,
            onClick = {
                filter.pressMode = !filter.pressMode
                filter.pressed = null
                filter.query = ""
                filter.conflictsOnly = false
            },
            label = { Text(stringResource(Res.string.shortcut_search_by_key), maxLines = 1, softWrap = false) },
            modifier = Modifier.testTag(SHORTCUT_PRESS_MODE_TAG),
        )
        ConflictsFilterChip(
            count = conflictCount,
            selected = filter.conflictsOnly,
            onClick = {
                filter.conflictsOnly = !filter.conflictsOnly
                filter.query = ""
                filter.pressed = null
                filter.pressMode = false
            },
        )
    }
}

/** The filtered rows: each action with its binding, the mouse gestures, or a line saying none match. */
@Composable
internal fun ShortcutRows(
    filter: ShortcutFilter,
    visible: VisibleShortcuts,
    shortcuts: ShortcutMap,
    conflicts: Map<ShortcutAction, List<ShortcutAction>>,
    onEditOverrides: ((Map<String, List<KeyChord>>) -> Map<String, List<KeyChord>>) -> Unit,
) {
    visible.actions.forEach { action ->
        val clashes = conflicts[action]
        val clashLabel = if (clashes == null) null else {
            // Resolved with a loop rather than joinToString: its transform is
            // not a composable context and these are string resources.
            val names = mutableListOf<String>()
            clashes.forEach { names.add(stringResource(it.descriptionRes)) }
            names.joinToString(", ")
        }
        ShortcutBindingRow(
            action = action,
            chords = shortcuts.chordsFor(action),
            customized = shortcuts.isCustomized(action),
            conflictsWith = clashLabel,
            categoryName = if (filter.filtering) stringResource(action.scope.titleRes) else null,
            recording = filter.recording == action,
            onRecord = { filter.recording = action },
            onStopRecording = { filter.recording = null },
            onCaptured = { chord ->
                onEditOverrides { it + (action.name to listOf(chord)) }
                filter.recording = null
            },
            onRevert = {
                // One control, two meanings: put a customized row back, or
                // unbind an untouched one.
                if (shortcuts.isCustomized(action)) {
                    onEditOverrides { it - action.name }
                } else {
                    onEditOverrides { it + (action.name to emptyList()) }
                }
                filter.recording = null
            },
        )
    }

    visible.mouseRows.forEach { (keys, description) ->
        ShortcutGestureRow(
            gesture = keys,
            description = description,
            categoryName = if (filter.filtering) {
                stringResource(Res.string.shortcut_category_mouse)
            } else {
                null
            },
        )
    }

    if (visible.actions.isEmpty() && visible.mouseRows.isEmpty()) {
        // Names whichever filter is active — the typed text, or the chord that
        // was filter.pressed. "No results found for \"\"" would be the obvious bug.
        val describedFilter = filter.pressed?.label() ?: filter.query
        Text(
            text = stringResource(Res.string.no_results_found, describedFilter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .testTag(SHORTCUT_NO_RESULTS_TAG),
            textAlign = TextAlign.Center,
        )
    }
}

/** Reset all, the unsaved count, and cancel, apply and OK. */
@Composable
internal fun ShortcutsFooter(
    unsavedCount: Int,
    savable: Boolean,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onOk: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(
            shape = AppShape(6.dp),
            onClick = onResetAll,
            modifier = Modifier.testTag(SHORTCUT_RESET_ALL_TAG)
        ) { Text(stringResource(Res.string.shortcut_settings_reset_all), maxLines = 1) }

        if (unsavedCount > 0) {
            Text(
                text = if (unsavedCount == 1) {
                    stringResource(Res.string.shortcut_unsaved_one)
                } else {
                    stringResource(Res.string.shortcut_unsaved_many, unsavedCount)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(SHORTCUT_UNSAVED_TAG),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        GhostButton(shape = AppShape(6.dp), onClick = onDismiss) {
            Text("${stringResource(Res.string.symbol_cancel)} ${stringResource(Res.string.cancel)}")
        }
        KeyButton(
            shape = AppShape(6.dp),
            enabled = savable,
            onClick = onApply,
        ) {
            Text(stringResource(Res.string.apply))
        }
        RaisedButton(
            shape = AppShape(6.dp),
            enabled = savable,
            onClick = onOk
        ) {
            Text("${stringResource(Res.string.symbol_ok)} ${stringResource(Res.string.ok)}")
        }
    }
}
