package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.customize
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_count
import churchpresenter.composeapp.generated.resources.customize_dialog_subtitle
import churchpresenter.composeapp.generated.resources.customize_dialog_title
import churchpresenter.composeapp.generated.resources.customize_done
import churchpresenter.composeapp.generated.resources.customize_pane_header
import churchpresenter.composeapp.generated.resources.customize_reset_to_global
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.customize_tooltip_none
import churchpresenter.composeapp.generated.resources.customize_tooltip_overwritten
import churchpresenter.composeapp.generated.resources.customize_tooltip_separator
import churchpresenter.composeapp.generated.resources.stage_monitor
import churchpresenter.composeapp.generated.resources.tab_dictionary
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 1240.dp

private val BODY_HEIGHT = 520.dp

/**
 * One category of an output's own appearance — a row of the dialog's left rail.
 *
 * Each maps to one nullable override on [ScreenAssignment] and one section of [AppSettings], and
 * each is switched on and off independently: a screen may want its own stage-monitor zones while
 * still following everyone else's Bible styling.
 */
internal enum class CustomizePane(val icon: ImageVector, val hasOverride: Boolean = true) {
    STAGE_MONITOR(Icons.Filled.Tv),
    BIBLE(Icons.Filled.MenuBook),
    SONGS(Icons.Filled.MusicNote),
    BACKGROUND(Icons.Filled.Wallpaper),
    DICTIONARY(Icons.Filled.Book);

    /** True once this output has appearance of its own for this category. */
    fun isOverridden(assignment: ScreenAssignment): Boolean = when (this) {
        STAGE_MONITOR -> assignment.stageMonitorOverride != null
        BIBLE -> assignment.bibleOverride != null
        SONGS -> assignment.songOverride != null
        BACKGROUND -> assignment.backgroundOverride != null
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
            BACKGROUND -> assignment.copy(backgroundOverride = if (on) edited.backgroundSettings else null)
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
            CustomizePane.DICTIONARY,
            CustomizePane.BACKGROUND,
        )
    }

@Composable
internal fun CustomizePane.label(): String = when (this) {
    CustomizePane.STAGE_MONITOR -> stringResource(Res.string.stage_monitor)
    CustomizePane.BIBLE -> stringResource(Res.string.customize_bible)
    CustomizePane.SONGS -> stringResource(Res.string.customize_songs)
    CustomizePane.BACKGROUND -> stringResource(Res.string.background)
    CustomizePane.DICTIONARY -> stringResource(Res.string.tab_dictionary)
}

/**
 * One output's own Stage Monitor / Bible / Song / lower-third / dictionary appearance, opened from
 * the Customize button on its row of the Projection settings tab.
 *
 * Three columns. The **rail** picks a category, and closes with a card naming the screen being
 * edited, because a dialog that can be opened from any row of the Projection tab should say which
 * row it came from. The **control column** shows one element of that category at a time, chosen by
 * the chips above it — the panes used to stack every group in one scroll, which put six to nine
 * headings between the operator and the line they came to restyle. The **preview column** draws
 * what the output will actually show, with the settings that belong to the picture as a whole —
 * margins, fades, band geometry — on a strip beneath it.
 *
 * The category's on/off switch sits in the header rather than over the pane: it governs the whole
 * dialog body now that the body is three columns, and a switch buried in the middle column would
 * read as belonging to that column alone.
 *
 * Each category is switched on and off independently, and the rail marks the ones that are on. A
 * pane whose switch is off shows the global values dimmed and inert — which is exactly what that
 * output draws. **The preview is not dimmed with it**: it is a picture of what the screen shows,
 * and that is no less true for being inherited.
 *
 * Which categories appear follows the output's display mode, because that decides which settings it
 * can obey: offering a stage monitor a full-screen Bible font size is offering a control that does
 * nothing.
 *
 * It reuses the existing settings composables rather than reimplementing them — the panes are built
 * from `CustomizeForm`'s controls, and the preview is the same presenter-backed [BiblePreviewPanel]
 * and [SongPreviewPanel] the global tabs draw. This dialog hands them a **draft** [AppSettings] —
 * the global document with this output's overrides already resolved into it — and folds what they
 * return back into the assignment as that one category's override.
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

    val elements = customizeElements(pane)
    var pickedElement by remember(pane) { mutableStateOf(elements.firstOrNull()) }
    val element = pickedElement?.takeIf { it in elements } ?: elements.firstOrNull()

    // Which entry of the Bible stack the pane edits. Keyed on the stack itself so removing a
    // translation cannot leave the selection pointing past the end of the list.
    val stackSize = globalSettings.bibleSettings.translationList().size
    var pickedTranslation by remember(stackSize) { mutableIntStateOf(0) }
    val translationIndex = pickedTranslation.coerceIn(0, (stackSize - 1).coerceAtLeast(0))

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

    val overridden = pane.isOverridden(assignment)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DIALOG_WIDTH),
        shape = RoundedCornerShape(14.dp),
        // The card layer of the settings ramp, so the rail and the control column read as one
        // sheet with the preview column's page tone beside them — the same three layers the
        // Background, Song and Bible tabs are built from. M3's default is the field tone, which
        // would put the element chips on exactly the color they sit on.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            CustomizeDialogHeader(
                screenLabel = screenLabel,
                pane = pane,
                overridden = overridden,
                customized = panes.count { it.isOverridden(assignment) },
                total = panes.count { it.hasOverride },
                onOverriddenChange = ::setOverridden,
                onDismiss = onDismiss,
            )
        },
        text = {
            CompositionLocalProvider(
                LocalOutputStyleScope provides OutputStyleScope.forDisplayMode(assignment.displayMode),
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(BODY_HEIGHT)) {
                    CustomizeRail(
                        panes = panes,
                        selected = pane,
                        assignment = assignment,
                        screenLabel = screenLabel,
                        onSelect = { selected = it },
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CustomizeBody(
                        pane = pane,
                        element = element,
                        elements = elements,
                        live = overridden || !pane.hasOverride,
                        draft = draft,
                        assignment = assignment,
                        translationIndex = translationIndex,
                        onTranslationChange = { pickedTranslation = it },
                        onElementChange = { pickedElement = it },
                        onSettingsChange = ::edit,
                    )
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    shape = RoundedCornerShape(8.dp),
                    onClick = { setOverridden(false) },
                    enabled = overridden,
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.customize_reset_to_global),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = paneHint(pane, overridden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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

/**
 * Icon badge, the screen's name, how many of its categories are customized — and, on the right, the
 * selected category's own on/off switch.
 */
@Composable
private fun CustomizeDialogHeader(
    screenLabel: String,
    pane: CustomizePane,
    overridden: Boolean,
    customized: Int,
    total: Int,
    onOverriddenChange: (Boolean) -> Unit,
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
        if (pane.hasOverride) {
            Text(
                text = stringResource(Res.string.customize_pane_header, pane.label()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (overridden) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Switch(
                checked = overridden,
                onCheckedChange = onOverriddenChange,
                modifier = Modifier.testTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG),
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
    // Which of this output's categories have settings of their own. A plain tint said only
    // "something here is different"; the count says how much, and reads at a glance down a column
    // of rows — which is the question the Projection tab is actually being asked. The names say
    // *which*, which is the follow-up question, and they go in the tooltip rather than on the
    // button: four category names do not fit a table cell, and the count is what wants scanning.
    val overwritten = customizePanes(assignment.displayMode).filter { it.isOverridden(assignment) }
    val customized = overwritten.size
    // Every label resolved every time, so the number of `stringResource` calls does not change
    // with how many categories happen to be overwritten.
    val paneLabels = CustomizePane.entries.associateWith { it.label() }
    val tooltipText = if (customized > 0) {
        stringResource(
            Res.string.customize_tooltip_overwritten,
            overwritten.joinToString(stringResource(Res.string.customize_tooltip_separator)) {
                paneLabels.getValue(it)
            },
        )
    } else {
        stringResource(Res.string.customize_tooltip_none)
    }
    val tint = if (customized > 0) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    @OptIn(ExperimentalMaterial3Api::class)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = { showDialog = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (customized > 0) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (customized > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            ),
            modifier = modifier,
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (customized > 0) stringResource(Res.string.customize_count, customized)
                else stringResource(Res.string.customize),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (customized > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
