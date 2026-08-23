package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.apply
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.ic_warning
import org.churchpresenter.resources.generated.resources.keyboard_shortcuts_title
import org.churchpresenter.resources.generated.resources.no_results_found
import org.churchpresenter.resources.generated.resources.ok
import org.churchpresenter.resources.generated.resources.shortcut_category_mouse
import org.churchpresenter.resources.generated.resources.shortcut_conflicts_many
import org.churchpresenter.resources.generated.resources.shortcut_conflicts_none
import org.churchpresenter.resources.generated.resources.shortcut_conflicts_one
import org.churchpresenter.resources.generated.resources.shortcut_conflicts_subtitle_many
import org.churchpresenter.resources.generated.resources.shortcut_conflicts_subtitle_one
import org.churchpresenter.resources.generated.resources.shortcut_conflicts_title
import org.churchpresenter.resources.generated.resources.shortcut_description_context_menu
import org.churchpresenter.resources.generated.resources.shortcut_description_go_live
import org.churchpresenter.resources.generated.resources.shortcut_description_reorder_item
import org.churchpresenter.resources.generated.resources.shortcut_key_double_click
import org.churchpresenter.resources.generated.resources.shortcut_key_right_click
import org.churchpresenter.resources.generated.resources.shortcut_key_shift_drag
import org.churchpresenter.resources.generated.resources.shortcut_scope_mouse_hint
import org.churchpresenter.resources.generated.resources.shortcut_search_by_key
import org.churchpresenter.resources.generated.resources.shortcut_search_match_many
import org.churchpresenter.resources.generated.resources.shortcut_search_match_one
import org.churchpresenter.resources.generated.resources.shortcut_search_placeholder
import org.churchpresenter.resources.generated.resources.shortcut_search_press_prompt
import org.churchpresenter.resources.generated.resources.shortcut_search_results
import org.churchpresenter.resources.generated.resources.shortcut_settings_reset_all
import org.churchpresenter.resources.generated.resources.shortcut_unsaved_many
import org.churchpresenter.resources.generated.resources.shortcut_unsaved_one
import org.churchpresenter.resources.generated.resources.symbol_cancel
import org.churchpresenter.resources.generated.resources.symbol_ok
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.ui.SearchField
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.app.churchpresenter.utils.searchText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Test tag for the reset-everything button, which several tests need to locate. */
internal const val SHORTCUT_RESET_ALL_TAG = "shortcut_reset_all"

/** The "no results" line, which has no other stable handle once the list is empty. */
internal const val SHORTCUT_NO_RESULTS_TAG = "shortcut_no_results"

/** The "Press key" toggle. */
internal const val SHORTCUT_PRESS_MODE_TAG = "shortcut_press_mode"

/** The panel that listens for a key while "Press key" is on. */
internal const val SHORTCUT_PRESS_PANEL_TAG = "shortcut_press_panel"

/** The conflicts filter, which is also the conflict count. */
internal const val SHORTCUT_CONFLICTS_FILTER_TAG = "shortcut_conflicts_filter"

/** The "n changes not saved" line, which is absent entirely while nothing is pending. */
internal const val SHORTCUT_UNSAVED_TAG = "shortcut_unsaved"

/** The heading above the list, which names whatever is being shown. */
internal const val SHORTCUT_SECTION_TITLE_TAG = "shortcut_section_title"

/** The per-action row's keycaps, tagged by action so a test can read one row's binding. */
internal fun shortcutChipTag(action: ShortcutAction) = "shortcut_chip_${action.name}"

/** The listening chip, which replaces the keycaps while a row is being rebound. */
internal fun shortcutRecordingTag(action: ShortcutAction) = "shortcut_recording_${action.name}"

/**
 * The per-action Reset/Clear button.
 *
 * Tagged per action because every row carries one, so "the Clear button" matches ~40 nodes.
 */
internal fun shortcutRevertTag(action: ShortcutAction) = "shortcut_revert_${action.name}"

/** One rail entry. Null is the mouse section, which has no scope behind it. */
internal fun shortcutCategoryTag(scope: ShortcutScope?) = "shortcut_category_${scope?.name ?: "MOUSE"}"

/** Opens wide enough for the category rail and a full-width row beside it. */
private val DIALOG_WIDTH = 900.dp
private val DIALOG_HEIGHT = 720.dp

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
            position = centeredOnMainWindow(mainWindowState, DIALOG_WIDTH, DIALOG_HEIGHT),
            width = DIALOG_WIDTH,
            height = DIALOG_HEIGHT
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
 * The list is **one category at a time**, picked from the rail, rather than every scope in one long
 * scroll — with the rebindable actions the app now has, the old form ran to some forty-five rows
 * between the first heading and the last. A filter overrides the selection and spans every
 * category, which is why filtered rows carry their category as a tag: three of them read
 * "Play / Pause" and only the tag says which one is which.
 *
 * Edits are **pending** until Apply or OK, and the footer says how many are outstanding.
 *
 * A binding that clashes with another is **recorded and reported, but cannot be saved**: the row
 * names what already answers to that combination, the rail dots the category holding it, the
 * toolbar counts the lot, and Apply and OK stay disabled until none are left. The capture dialog
 * this replaced could only refuse the chord as it was typed, which said nothing about the conflicts
 * already in the map and left no way to look at them.
 *
 * Mouse gestures are their own category and are written out by hand — they are not key bindings,
 * are not rebindable, and have no registry entry to render from.
 */
@Composable
internal fun KeyboardShortcutsDialogContent(
    initialSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentSettings by remember { mutableStateOf(initialSettings) }
    // View state only. None of it may reach currentSettings, or what Apply saves would depend on
    // whether the user happened to be searching at the time.
    var query by remember { mutableStateOf("") }
    // "Press key" mode: filter by pressing a combination rather than describing it. The two filters
    // are mutually exclusive — each clears the other — because a text query and a pressed chord
    // narrowing the same list at once has no sensible reading.
    var pressMode by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf<KeyChord?>(null) }
    var conflictsOnly by remember { mutableStateOf(false) }
    var selectedScope by remember { mutableStateOf<ShortcutScope?>(ShortcutScope.entries.first()) }
    var recording by remember { mutableStateOf<ShortcutAction?>(null) }
    val pressFocus = remember { FocusRequester() }

    val shortcuts = remember(currentSettings.keyboardShortcutSettings) {
        ShortcutMap.from(currentSettings.keyboardShortcutSettings)
    }
    val savedShortcuts = remember(initialSettings.keyboardShortcutSettings) {
        ShortcutMap.from(initialSettings.keyboardShortcutSettings)
    }
    val conflicts = remember(shortcuts) { shortcuts.conflicts() }
    val unsavedCount = remember(shortcuts, savedShortcuts) {
        ShortcutAction.entries.count { shortcuts.chordsFor(it) != savedShortcuts.chordsFor(it) }
    }

    // Resolved in composition because descriptions and key labels both come from string resources;
    // the match itself is plain Kotlin below.
    val haystacks: Map<ShortcutAction, String> = ShortcutAction.entries.associateWith { action ->
        "${stringResource(action.descriptionRes)} ${shortcuts.searchText(action)}".lowercase()
    }

    // The mouse rows are plain strings rather than registry entries, so they match on their own
    // resolved text.
    val mouseRows = listOf(
        stringResource(Res.string.shortcut_key_double_click) to stringResource(Res.string.shortcut_description_go_live),
        stringResource(Res.string.shortcut_key_right_click) to stringResource(Res.string.shortcut_description_context_menu),
        stringResource(Res.string.shortcut_key_shift_drag) to stringResource(Res.string.shortcut_description_reorder_item),
    )

    val searching = query.isNotBlank() || pressed != null
    val filtering = searching || conflictsOnly

    val visibleActions = remember(query, haystacks, pressed, shortcuts, conflicts, conflictsOnly, selectedScope) {
        val chord = pressed
        val needle = query.trim().lowercase()
        when {
            conflictsOnly -> ShortcutAction.entries.filter { it in conflicts }
            // Exact chord match, the same question `conflictFor` asks: what is *this* combination
            // already doing? A looser match would fold Ctrl+← in with ← and stop answering it.
            chord != null -> ShortcutAction.entries.filter { chord in shortcuts.chordsFor(it) }
            needle.isNotEmpty() -> ShortcutAction.entries.filter { needle in haystacks.getValue(it) }
            else -> ShortcutAction.entries.filter { it.scope == selectedScope }
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

    val nothingMatched = visibleActions.isEmpty() && visibleMouseRows.isEmpty()
    val categories = ShortcutAction.entries.groupBy { it.scope }.map { (scope, actions) ->
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        onValueChange = { query = it; conflictsOnly = false },
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
                        conflictsOnly = false
                    },
                    label = { Text(stringResource(Res.string.shortcut_search_by_key), maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag(SHORTCUT_PRESS_MODE_TAG),
                )
                ConflictsFilterChip(
                    count = conflicts.size,
                    selected = conflictsOnly,
                    onClick = {
                        conflictsOnly = !conflictsOnly
                        query = ""
                        pressed = null
                        pressMode = false
                    },
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ShortcutCategoryRail(
                    categories = categories,
                    selected = selectedScope,
                    enabled = !filtering,
                    onSelect = { scope ->
                        selectedScope = scope
                        query = ""
                        pressed = null
                        pressMode = false
                        conflictsOnly = false
                        recording = null
                    },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val listScroll = rememberScrollState()
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionHeading(
                        title = sectionTitle(conflictsOnly, searching, selectedScope),
                        subtitle = sectionSubtitle(
                            conflictsOnly = conflictsOnly,
                            searching = searching,
                            scope = selectedScope,
                            conflictCount = conflicts.size,
                            matchCount = visibleActions.size + visibleMouseRows.size,
                        ),
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(listScroll)
                                // Room down the right for the scrollbar, which floats over the list.
                                .padding(start = 14.dp, end = 20.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            visibleActions.forEach { action ->
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
                                    categoryName = if (filtering) stringResource(action.scope.titleRes) else null,
                                    recording = recording == action,
                                    onRecord = { recording = action },
                                    onStopRecording = { recording = null },
                                    onCaptured = { chord ->
                                        editOverrides { it + (action.name to listOf(chord)) }
                                        recording = null
                                    },
                                    onRevert = {
                                        // One control, two meanings: put a customized row back, or
                                        // unbind an untouched one.
                                        if (shortcuts.isCustomized(action)) {
                                            editOverrides { it - action.name }
                                        } else {
                                            editOverrides { it + (action.name to emptyList()) }
                                        }
                                        recording = null
                                    },
                                )
                            }

                            visibleMouseRows.forEach { (keys, description) ->
                                ShortcutGestureRow(
                                    gesture = keys,
                                    description = description,
                                    categoryName = if (filtering) {
                                        stringResource(Res.string.shortcut_category_mouse)
                                    } else {
                                        null
                                    },
                                )
                            }

                            if (nothingMatched) {
                                // Names whichever filter is active — the typed text, or the chord that
                                // was pressed. "No results found for \"\"" would be the obvious bug.
                                val describedFilter = pressed?.label() ?: query
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
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listScroll),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { editOverrides { emptyMap() }; recording = null },
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

                TextButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                    Text("${stringResource(Res.string.symbol_cancel)} ${stringResource(Res.string.cancel)}")
                }
                // A map with two actions on one combination cannot be saved: one of them would
                // simply never fire, and which one is an accident of registry order. The toolbar's
                // count is the way back to the rows that have to be settled first.
                val savable = conflicts.isEmpty()
                OutlinedButton(
                    shape = RoundedCornerShape(6.dp),
                    enabled = savable,
                    onClick = { onSave(currentSettings) },
                ) {
                    Text(stringResource(Res.string.apply))
                }
                Button(
                    shape = RoundedCornerShape(6.dp),
                    enabled = savable,
                    onClick = { onSave(currentSettings); onDismiss() }
                ) {
                    Text("${stringResource(Res.string.symbol_ok)} ${stringResource(Res.string.ok)}")
                }
            }
        }
    }
}

/** What the list is currently showing, above it. */
@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 15.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.testTag(SHORTCUT_SECTION_TITLE_TAG),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun sectionTitle(conflictsOnly: Boolean, searching: Boolean, scope: ShortcutScope?): String = when {
    conflictsOnly -> stringResource(Res.string.shortcut_conflicts_title)
    searching -> stringResource(Res.string.shortcut_search_results)
    scope != null -> stringResource(scope.titleRes)
    else -> stringResource(Res.string.shortcut_category_mouse)
}

@Composable
private fun sectionSubtitle(
    conflictsOnly: Boolean,
    searching: Boolean,
    scope: ShortcutScope?,
    conflictCount: Int,
    matchCount: Int,
): String = when {
    conflictsOnly -> when (conflictCount) {
        0 -> stringResource(Res.string.shortcut_conflicts_none)
        1 -> stringResource(Res.string.shortcut_conflicts_subtitle_one)
        else -> stringResource(Res.string.shortcut_conflicts_subtitle_many, conflictCount)
    }
    searching -> if (matchCount == 1) {
        stringResource(Res.string.shortcut_search_match_one)
    } else {
        stringResource(Res.string.shortcut_search_match_many, matchCount)
    }
    scope != null -> stringResource(scope.hintRes)
    else -> stringResource(Res.string.shortcut_scope_mouse_hint)
}

/**
 * The conflict count, which is also the filter that collects them.
 *
 * Disabled when there are none: a filter that can only ever produce an empty list is a dead
 * control, and the label already says "No conflicts".
 */
@Composable
private fun ConflictsFilterChip(count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = count > 0,
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(Res.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        label = {
            Text(
                text = when (count) {
                    0 -> stringResource(Res.string.shortcut_conflicts_none)
                    1 -> stringResource(Res.string.shortcut_conflicts_one)
                    else -> stringResource(Res.string.shortcut_conflicts_many, count)
                },
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = Modifier.testTag(SHORTCUT_CONFLICTS_FILTER_TAG),
    )
}
