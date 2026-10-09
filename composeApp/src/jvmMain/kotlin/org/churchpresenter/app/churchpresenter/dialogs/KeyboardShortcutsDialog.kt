package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import org.churchpresenter.theme.components.RaisedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_warning
import org.churchpresenter.strings.generated.resources.menu_keyboard_shortcuts
import org.churchpresenter.strings.generated.resources.shortcut_category_mouse
import org.churchpresenter.strings.generated.resources.shortcut_conflicts_many
import org.churchpresenter.strings.generated.resources.shortcut_conflicts_none
import org.churchpresenter.strings.generated.resources.shortcut_conflicts_one
import org.churchpresenter.strings.generated.resources.shortcut_conflicts_subtitle_many
import org.churchpresenter.strings.generated.resources.shortcut_conflicts_subtitle_one
import org.churchpresenter.strings.generated.resources.shortcut_conflicts_title
import org.churchpresenter.strings.generated.resources.shortcut_scope_mouse_hint
import org.churchpresenter.strings.generated.resources.shortcut_search_match_many
import org.churchpresenter.strings.generated.resources.shortcut_search_match_one
import org.churchpresenter.strings.generated.resources.shortcut_search_results
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.ShortcutScope
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.theme.ProvideUiFontScale
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
    /** Off, the actions of features that are dev mode only are left out. */
    devMode: Boolean = true,
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
        title = stringResource(Res.string.menu_keyboard_shortcuts),
        resizable = true
    ) {
        ProvideUiFontScale {
            // This window's own spotlight: Wick's shortcut answers ring the row they name.
            GuideSpotlightHost(Modifier.fillMaxSize()) {
                KeyboardShortcutsDialogContent(
                    initialSettings = settings,
                    onSave = onSave,
                    onDismiss = onDismiss,
                    devMode = devMode,
                )
            }
        }
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
    devMode: Boolean = true,
) {
    var currentSettings by remember { mutableStateOf(initialSettings) }
    // View state only. None of it may reach currentSettings, or what Apply saves would depend on
    // whether the user happened to be searching at the time.
    val filter = remember { ShortcutFilter() }
    val pressFocus = remember { FocusRequester() }
    FollowShortcutFocus(filter)

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
    val visible = rememberVisibleShortcuts(filter, shortcuts, conflicts, devMode)

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

            ShortcutsToolbar(filter, conflicts.size, pressFocus)

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ShortcutCategoryRail(
                    categories = visible.categories,
                    selected = filter.selectedScope,
                    enabled = !filter.filtering,
                    onSelect = filter::selectScope,
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val listScroll = rememberScrollState()
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionHeading(
                        title = sectionTitle(filter.conflictsOnly, filter.searching, filter.selectedScope),
                        subtitle = sectionSubtitle(
                            conflictsOnly = filter.conflictsOnly,
                            searching = filter.searching,
                            scope = filter.selectedScope,
                            conflictCount = conflicts.size,
                            matchCount = visible.actions.size + visible.mouseRows.size,
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
                            ShortcutRows(filter, visible, shortcuts, conflicts, ::editOverrides)
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listScroll),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }

            ShortcutsFooter(
                unsavedCount = unsavedCount,
                // A map with two actions on one combination cannot be saved: one of them would
                // simply never fire, and which one is an accident of registry order. The toolbar's
                // count is the way back to the rows that have to be settled first.
                savable = conflicts.isEmpty(),
                onResetAll = { editOverrides { emptyMap() }; filter.recording = null },
                onDismiss = onDismiss,
                onApply = { onSave(currentSettings) },
                onOk = { onSave(currentSettings); onDismiss() },
            )
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
                .background(MaterialTheme.colorScheme.primary, AppShape(2.dp))
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
internal fun ConflictsFilterChip(count: Int, selected: Boolean, onClick: () -> Unit) {
    RaisedFilterChip(
        selected = selected,
        enabled = count > 0,
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(IconRes.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
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
