package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_dialog_subtitle
import churchpresenter.composeapp.generated.resources.customize_dialog_title
import churchpresenter.composeapp.generated.resources.customize_done
import churchpresenter.composeapp.generated.resources.customize_hint_off
import churchpresenter.composeapp.generated.resources.customize_hint_on
import churchpresenter.composeapp.generated.resources.customize_hint_stage_off
import churchpresenter.composeapp.generated.resources.customize_hint_stage_on
import churchpresenter.composeapp.generated.resources.customize_lower_third_orientation
import churchpresenter.composeapp.generated.resources.customize_lower_third_vertical_hint
import churchpresenter.composeapp.generated.resources.customize_pane_header
import churchpresenter.composeapp.generated.resources.customize_reset_to_global
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_lower_third_vertical
import churchpresenter.composeapp.generated.resources.stage_monitor
import churchpresenter.composeapp.generated.resources.tab_dictionary
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 1080.dp
private val BODY_HEIGHT = 520.dp
private val RAIL_WIDTH = 176.dp
private val OVERRIDDEN_DOT = 6.dp
private const val FOLLOWING_GLOBAL_ALPHA = 0.45f

/**
 * One category of an output's own appearance — a row of the dialog's left rail.
 *
 * Each maps to one nullable override on [ScreenAssignment] and one section of [AppSettings], and
 * each is switched on and off independently: a screen may want its own stage-monitor zones while
 * still following everyone else's Bible styling.
 */
private enum class CustomizePane(val icon: ImageVector) {
    STAGE_MONITOR(Icons.Filled.Tv),
    BIBLE(Icons.Filled.MenuBook),
    SONGS(Icons.Filled.MusicNote),
    LOWER_THIRD(Icons.Filled.Subtitles),
    DICTIONARY(Icons.Filled.Book);

    /** True once this output has appearance of its own for this category. */
    fun isOverridden(assignment: ScreenAssignment): Boolean = when (this) {
        STAGE_MONITOR -> assignment.stageMonitorOverride != null
        BIBLE -> assignment.bibleOverride != null
        SONGS -> assignment.songOverride != null
        LOWER_THIRD -> assignment.streamingOverride != null
        DICTIONARY -> assignment.dictionaryOverride != null
    }

    /** [assignment] with this category's override taken from [edited], or cleared when [on] is false. */
    fun applied(assignment: ScreenAssignment, edited: AppSettings, on: Boolean = true): ScreenAssignment =
        when (this) {
            STAGE_MONITOR -> assignment.copy(
                stageMonitorOverride = if (on) edited.stageMonitorSettings else null,
            )
            BIBLE -> assignment.copy(bibleOverride = if (on) edited.bibleSettings else null)
            SONGS -> assignment.copy(songOverride = if (on) edited.songSettings else null)
            LOWER_THIRD -> assignment.copy(streamingOverride = if (on) edited.streamingSettings else null)
            DICTIONARY -> assignment.copy(dictionaryOverride = if (on) edited.dictionarySettings else null)
        }
}

/** The categories [displayMode] can actually use, in rail order. */
private fun customizePanes(displayMode: String): List<CustomizePane> =
    if (displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
        // A stage monitor draws its own zones and the dictionary card; it never draws the
        // full-screen or lower-third Bible and Song profiles.
        listOf(CustomizePane.STAGE_MONITOR, CustomizePane.DICTIONARY)
    } else {
        listOf(
            CustomizePane.BIBLE,
            CustomizePane.SONGS,
            CustomizePane.LOWER_THIRD,
            CustomizePane.DICTIONARY,
        )
    }

@Composable
private fun CustomizePane.label(): String = when (this) {
    CustomizePane.STAGE_MONITOR -> stringResource(Res.string.stage_monitor)
    CustomizePane.BIBLE -> stringResource(Res.string.customize_bible)
    CustomizePane.SONGS -> stringResource(Res.string.customize_songs)
    CustomizePane.LOWER_THIRD -> stringResource(Res.string.display_lower_third)
    CustomizePane.DICTIONARY -> stringResource(Res.string.tab_dictionary)
}

/**
 * One output's own Stage Monitor / Bible / Song / lower-third / dictionary appearance, opened from
 * the Customize button on its row of the Projection settings tab.
 *
 * Laid out as a category rail beside a single pane rather than a tab strip, because each category
 * carries its own on/off: the pane header's switch is what decides whether this screen has settings
 * of its own for the category showing, and the rail marks the ones that do. A pane whose switch is
 * off is dimmed and inert — it shows the global values, which is exactly what that output draws.
 *
 * Which categories appear follows the output's display mode, because that decides which settings it
 * can obey: offering a stage monitor a full-screen Bible font size is offering a control that does
 * nothing.
 *
 * It reuses the existing settings tabs rather than reimplementing them. They already have the
 * signature `(settings, onSettingsChange)` and know nothing about outputs; this dialog hands them a
 * **draft** [AppSettings] — the global document with this output's overrides already resolved into
 * it — and folds what they return back into the assignment as that one category's override.
 *
 * A Material3 `AlertDialog` rather than a `DialogWindow`, so it composes in a headless test.
 */
@Composable
internal fun OutputCustomizeDialog(
    screenLabel: String,
    assignment: ScreenAssignment,
    globalSettings: AppSettings,
    onApply: (ScreenAssignment) -> Unit,
    onDismiss: () -> Unit,
) {
    val panes = customizePanes(assignment.displayMode)
    var selected by remember(assignment.displayMode) { mutableStateOf(panes.first()) }
    val pane = if (selected in panes) selected else panes.first()

    // Seeded from the resolved settings so a pane opens showing what this output currently draws —
    // the global values until the category is switched on, its own once it is.
    var draft by remember(assignment) { mutableStateOf(globalSettings.resolvedFor(assignment)) }

    fun edit(transform: (AppSettings) -> AppSettings) {
        val edited = transform(draft)
        draft = edited
        // An edit switches the category on if it was not already: the operator has just said what
        // this screen should look like, and storing that is the whole point of having typed it.
        onApply(pane.applied(assignment, edited))
    }

    fun setOverridden(on: Boolean) {
        val next = pane.applied(assignment, draft, on)
        // Turning a category off puts the global values back on screen, not the ones just abandoned.
        draft = globalSettings.resolvedFor(next)
        onApply(next)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DIALOG_WIDTH),
        shape = RoundedCornerShape(14.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            CustomizeDialogHeader(
                screenLabel = screenLabel,
                customized = panes.count { it.isOverridden(assignment) },
                total = panes.size,
                onDismiss = onDismiss,
            )
        },
        text = {
            Row(modifier = Modifier.fillMaxWidth().height(BODY_HEIGHT)) {
                CustomizeRail(
                    panes = panes,
                    selected = pane,
                    assignment = assignment,
                    onSelect = { selected = it },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CustomizePaneBody(
                    pane = pane,
                    overridden = pane.isOverridden(assignment),
                    draft = draft,
                    assignment = assignment,
                    onOverriddenChange = ::setOverridden,
                    onSettingsChange = ::edit,
                    onAssignmentChange = onApply,
                )
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    shape = RoundedCornerShape(8.dp),
                    onClick = { setOverridden(false) },
                    enabled = pane.isOverridden(assignment),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.customize_reset_to_global),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.customize_done),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
    )
}

/** Icon badge, the screen's name, how many of its categories are customized, and Close. */
@Composable
private fun CustomizeDialogHeader(
    screenLabel: String,
    customized: Int,
    total: Int,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.customize_dialog_title, screenLabel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.customize_dialog_subtitle, customized, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(CUSTOMIZE_STATUS_TAG),
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.customize_done),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The left rail: one row per category, dotted where this output has settings of its own. */
@Composable
private fun CustomizeRail(
    panes: List<CustomizePane>,
    selected: CustomizePane,
    assignment: ScreenAssignment,
    onSelect: (CustomizePane) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        panes.forEach { pane ->
            val isSelected = pane == selected
            val ink = if (isSelected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .clickable { onSelect(pane) }
                    .padding(horizontal = 10.dp)
                    .testTag(railTag(pane.name)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(pane.icon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
                Text(
                    text = pane.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // A screen with settings of its own for this category is worth seeing without
                // opening it, which is what the dot is.
                if (pane.isOverridden(assignment)) {
                    Box(
                        modifier = Modifier
                            .size(OVERRIDDEN_DOT)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * The selected category: its on/off header, then the settings themselves.
 *
 * When the category is off the settings still render — they are the global values, which is what
 * this output is actually drawing — but dimmed and swallowing input, so what is on screen stays
 * true without inviting an edit that would silently switch the category on.
 */
@Composable
private fun CustomizePaneBody(
    pane: CustomizePane,
    overridden: Boolean,
    draft: AppSettings,
    assignment: ScreenAssignment,
    onOverriddenChange: (Boolean) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onAssignmentChange: (ScreenAssignment) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.customize_pane_header, pane.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = paneHint(pane, overridden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = overridden,
                onCheckedChange = onOverriddenChange,
                modifier = Modifier.testTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().alpha(if (overridden) 1f else FOLLOWING_GLOBAL_ALPHA)) {
                CompositionLocalProvider(
                    LocalOutputStyleScope provides OutputStyleScope.forDisplayMode(assignment.displayMode),
                ) {
                    CustomizePaneContent(pane, draft, assignment, onSettingsChange, onAssignmentChange)
                }
            }
            if (!overridden) {
                // Swallows clicks rather than disabling each control: the panes are whole settings
                // tabs, and there is no `enabled` to thread through a hundred of them.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
        }
    }
}

@Composable
private fun paneHint(pane: CustomizePane, overridden: Boolean): String =
    if (pane == CustomizePane.STAGE_MONITOR) {
        if (overridden) stringResource(Res.string.customize_hint_stage_on)
        else stringResource(Res.string.customize_hint_stage_off)
    } else {
        if (overridden) stringResource(Res.string.customize_hint_on)
        else stringResource(Res.string.customize_hint_off)
    }

/** The existing settings tab that edits this category, handed the draft unchanged. */
@Composable
private fun CustomizePaneContent(
    pane: CustomizePane,
    draft: AppSettings,
    assignment: ScreenAssignment,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onAssignmentChange: (ScreenAssignment) -> Unit,
) {
    when (pane) {
        CustomizePane.STAGE_MONITOR ->
            StageMonitorSettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        CustomizePane.BIBLE ->
            BibleSettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        CustomizePane.SONGS ->
            SongSettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        CustomizePane.DICTIONARY ->
            DictionarySettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        CustomizePane.LOWER_THIRD -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (assignment.isLowerThird) {
                LowerThirdOrientationSection(
                    displayMode = assignment.displayMode,
                    onDisplayModeChange = { mode -> onAssignmentChange(assignment.copy(displayMode = mode)) },
                )
            }
            LowerThirdWindowPositionSection(settings = draft, onSettingsChange = onSettingsChange)
        }
    }
}

/**
 * Whether this output's lower third is a band across the bottom or a strip down the side.
 *
 * Lives here rather than in the Display Mode dropdown because the two orientations are one mode:
 * they share every styling field, and only the band's geometry differs. The dropdown says what the
 * output *is*; this says how it is shaped.
 *
 * A checkbox rather than a pair of buttons because there are exactly two shapes and the horizontal
 * band is the ordinary one — so the question is "is this one the vertical variant?", which is what
 * a checkbox asks.
 */
@Composable
private fun LowerThirdOrientationSection(
    displayMode: String,
    onDisplayModeChange: (String) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.customize_lower_third_orientation)) {
        LabeledCheckbox(
            checked = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
            onCheckedChange = { vertical ->
                onDisplayModeChange(
                    if (vertical) Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
                    else Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                )
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.display_lower_third_vertical),
            supporting = stringResource(Res.string.customize_lower_third_vertical_hint),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(LOWER_THIRD_VERTICAL_TAG),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The Customize button for one output row, and the dialog it opens.
 *
 * Sits beside the row's Content Outputs button: the two are its pair of "open a dialog about this
 * output" controls, and one of them drifting off to the row's far edge would read as belonging to
 * whatever column it landed next to. Tinted once the output has appearance of its own, so a
 * customized row is recognizable without opening it.
 *
 * Shared by the screen-assignment card and the browser-source card: both are rows over the same
 * [ScreenAssignment] type, and both write back through their own `withAssignment`-style updater,
 * which is what [onApply] is.
 */
@Composable
internal fun CustomizeOutputCell(
    assignment: ScreenAssignment,
    screenLabel: String,
    settings: AppSettings,
    onApply: (ScreenAssignment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val tint = if (assignment.isCustomized) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedButton(
        shape = RoundedCornerShape(6.dp),
        onClick = { showDialog = true },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.customize),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (showDialog) {
        OutputCustomizeDialog(
            screenLabel = screenLabel,
            assignment = assignment,
            globalSettings = settings,
            onApply = onApply,
            onDismiss = { showDialog = false },
        )
    }
}

/** Test handle for one rail row, by `CustomizePane` name. */
internal fun railTag(paneName: String): String = "customize_rail_$paneName"

/** Test handle for the "N of M customized" line in the header. */
internal const val CUSTOMIZE_STATUS_TAG = "customize_status"

/** Test handle for the selected category's on/off switch. */
internal const val CUSTOMIZE_OVERRIDE_SWITCH_TAG = "customize_override_switch"

/** Test handle for the lower third's vertical/horizontal checkbox. */
internal const val LOWER_THIRD_VERTICAL_TAG = "customize_lower_third_vertical"

/**
 * The mode the Display Mode dropdown should show as selected for an output in [mode].
 *
 * The dropdown offers one Lower Third entry, so a vertical output has to be recognized as that
 * entry — matching on the stored mode alone finds nothing and falls through to the Full Screen
 * label, which would report the wrong mode for every vertical output.
 */
internal fun shownDisplayMode(mode: String): String =
    if (mode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL)
        Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
    else mode

/**
 * The mode to store when the operator picks [picked] on an output currently in [current].
 *
 * Picking Lower Third on an output that is already a vertical strip leaves it vertical: the
 * dropdown entry means "be a lower third", and the orientation it already has is not something the
 * operator just asked to change.
 */
internal fun pickedDisplayMode(picked: String, current: String): String =
    if (picked == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL &&
        current == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
    ) current else picked
