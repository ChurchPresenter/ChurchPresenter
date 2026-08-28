package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_bible_translation
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bible_block_and_transition
import churchpresenter.composeapp.generated.resources.bible_cross_references_enable
import churchpresenter.composeapp.generated.resources.bible_editing
import churchpresenter.composeapp.generated.resources.bible_split_browse_mode
import churchpresenter.composeapp.generated.resources.bible_miscellaneous
import churchpresenter.composeapp.generated.resources.bible_split_long_verses
import churchpresenter.composeapp.generated.resources.bible_translation
import churchpresenter.composeapp.generated.resources.bible_translation_divider
import churchpresenter.composeapp.generated.resources.bible_translation_spacing
import churchpresenter.composeapp.generated.resources.bible_translations
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.move_translation_down
import churchpresenter.composeapp.generated.resources.move_translation_up
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.scanning_directory
import churchpresenter.composeapp.generated.resources.text_margins
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ScanningRow
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.rememberBibleFolderListing
import org.churchpresenter.app.churchpresenter.composables.rememberBiblePreviewVerses
import org.churchpresenter.app.churchpresenter.composables.rememberDropdownWidthFor
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.moveBibleTranslation
import org.churchpresenter.settings.removeBibleTranslation
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The rail is a fixed column of cards; the styling side takes whatever is left. */
private val RAIL_MIN_WIDTH = 300.dp
private val RAIL_MAX_WIDTH = 360.dp

/** [ActionIconButton]'s own default size, which the reorder buttons take and their gaps stand in for. */
private val REORDER_BUTTON_SIZE = 34.dp

private const val MARGIN_MAX = 500
private const val SPACING_MAX = 200
private const val TRANSITION_MIN_MS = 100f
private const val TRANSITION_MAX_MS = 2000f
private const val TRANSITION_STEP_MS = 50f

private val TARGET_BUTTON_WIDTH = 110.dp

/** The gap between a slot's picker and the buttons beside it. */
private val ROW_GAP = 6.dp

/** Past two translations every row keeps three button slots, a gap standing in for a missing one. */
private const val REORDER_SLOTS_PADDED = 3

/** A picker narrower than this is not worth showing a name in; the rail has a minimum width anyway. */
private val PICKER_MIN_WIDTH = 120.dp

/** Six chips do not fit one line beside anything else, so past three they wrap onto a second. */
private val CHIP_WIDTH = 82.dp
private const val CHIP_COMPACT_COLUMNS = 3

/**
 * The Bible tab of the settings dialog.
 *
 * The stack can hold up to [Constants.MAX_BIBLE_TRANSLATIONS] translations, each with a full
 * appearance profile of its own: verse and reference, full screen and lower third. Laid out as four
 * copies of every control that is an unreadable amount of scrolling, so the tab instead keeps one
 * set of controls and three selectors above them -- which translation, which output, which element
 * -- and shows what the current selection actually looks like in the preview between them.
 *
 * The rail on the left holds what belongs to the stack as a whole rather than to any one
 * translation: which Bibles are in it and in what order, how they are spaced, how the block sits on
 * the screen and how it arrives.
 */
@Composable
fun BibleSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenterManager: PresenterManager? = null
) {
    val availableFonts = rememberSystemFonts()
    // Null while the folder is still being read. Walking it and reading a header out of every module
    // used to happen inline here, so the whole dialog waited on it before painting once per open.
    val listing = rememberBibleFolderListing(settings.bibleSettings.storageDirectory)
    val bibleFilesInDirectory = listing?.files.orEmpty()
    // The renames are applied over the scan rather than baked into it, so typing in a name field
    // re-labels every picker on the next frame without walking the Bible folder again.
    val bibleFileDisplayNames = listing?.namesWith(settings.bibleSettings.customNames()).orEmpty()

    var target by remember { mutableStateOf(BibleStyleTarget.FULL_SCREEN) }
    var element by remember { mutableStateOf(BibleStyleElement.TEXT) }
    var selected by remember { mutableStateOf(0) }

    val translations = settings.bibleSettings.translationList()
    // Removing the last translation, or the one being edited, must not leave the panel pointed past
    // the end of the stack -- so the selection is clamped on read rather than fixed up on delete.
    val selectedIndex = selected.coerceIn(0, (translations.size - 1).coerceAtLeast(0))

    // The rail scrolls on its own rather than the tab scrolling as a whole. Four cards do not fit
    // the dialog's height on a 1366x768 laptop, and when the whole Row scrolled they took the
    // styling side down with them -- the preview and the controls it illustrates ended up below the
    // fold on a tab that had room for both.
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.widthIn(min = RAIL_MIN_WIDTH, max = RAIL_MAX_WIDTH).fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(end = SettingsScrollbarGutter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeftRail(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    bibleFilesInDirectory = bibleFilesInDirectory,
                    bibleFileDisplayNames = bibleFileDisplayNames,
                    scanning = listing == null,
                    selectedIndex = selectedIndex,
                    onSelect = { selected = it },
                )
            }
            SettingsScrollbar(scrollState)
            }
            StylePane(
                settings = settings,
                onSettingsChange = onSettingsChange,
                translations = translations,
                selectedIndex = selectedIndex,
                onSelect = { selected = it },
                target = target,
                onTargetChange = { target = it },
                element = element,
                onElementChange = { element = it },
                moduleTitles = listing?.titles.orEmpty(),
                availableFonts = availableFonts,
                presenterManager = presenterManager,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** Everything that belongs to the stack rather than to one translation in it. */
@Composable
private fun LeftRail(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    bibleFilesInDirectory: List<String>,
    bibleFileDisplayNames: Map<String, String>,
    scanning: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    TranslationsSection(
        settings = settings,
        onSettingsChange = onSettingsChange,
        bibleFilesInDirectory = bibleFilesInDirectory,
        bibleFileDisplayNames = bibleFileDisplayNames,
        scanning = scanning,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
    )
    MiscellaneousSection(settings, onSettingsChange)
    BlockAndTransitionSection(settings, onSettingsChange)
    MarginsSection(settings, onSettingsChange)
}

/** Which Bibles are in the stack, in what order, and the picker that adds the next one. */
@Composable
private fun TranslationsSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    bibleFilesInDirectory: List<String>,
    bibleFileDisplayNames: Map<String, String>,
    scanning: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val noneStr = stringResource(Res.string.none)
    val translations = settings.bibleSettings.translationList()
    val addTranslationLabel = stringResource(Res.string.add_bible_translation)
    val bibleDisplayOptions = listOf(noneStr) + bibleFilesInDirectory.map { fileName ->
        bibleFileDisplayNames[fileName] ?: fileName
    }
    SettingsSection(
        title = stringResource(Res.string.bible_translations),
        headerTrailing = {
            Text(
                text = translations.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
        },
    ) {
        // One width for every picker in the stack, so the reorder and delete buttons beside them
        // form a straight column instead of stepping in and out with each Bible's name length.
        //
        // Capped at what the rail actually has left, though: the measured width runs to 280dp on a
        // name like "King James Version (1769)", and 280dp plus three 34dp buttons is wider than the
        // rail -- which the Row answered by squeezing the last child, so the delete button came out
        // a sliver. The buttons are fixed-size and cannot give, so the picker is what yields.
        val measured = rememberDropdownWidthFor(bibleDisplayOptions + addTranslationLabel)
        val buttonSlots = when {
            translations.size > 2 -> REORDER_SLOTS_PADDED
            translations.size > 1 -> 2
            else -> 1
        }
        val reserved = (REORDER_BUTTON_SIZE + ROW_GAP) * buttonSlots
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pickerWidth = minOf(measured, (maxWidth - reserved).coerceAtLeast(PICKER_MIN_WIDTH))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            translations.forEachIndexed { index, translation ->
                TranslationRow(
                    index = index,
                    translation = translation,
                    translations = translations,
                    bibleFilesInDirectory = bibleFilesInDirectory,
                    bibleFileDisplayNames = bibleFileDisplayNames,
                    pickerWidth = pickerWidth,
                    noneStr = noneStr,
                    onSettingsChange = onSettingsChange,
                    onSelect = onSelect,
                    selectedIndex = selectedIndex,
                )
            }
            val unselectedFiles = bibleFilesInDirectory.filter { candidate ->
                translations.none { it.fileName == candidate }
            }
            // Hidden at the cap as well as when nothing is left to add: `addTranslation` refuses past
            // it, and a picker that answers a selection by doing nothing is worse than no picker.
            // Until the folder has been read there is nothing to offer yet — say so, because an
            // absent picker otherwise reads as "this folder holds no other translations".
            if (scanning) {
                ScanningRow(stringResource(Res.string.scanning_directory))
            } else if (unselectedFiles.isNotEmpty() && translations.size < Constants.MAX_BIBLE_TRANSLATIONS) {
                DropdownSettingsField(
                    width = pickerWidth,
                    label = addTranslationLabel,
                    value = addTranslationLabel,
                    options = listOf(addTranslationLabel) +
                        unselectedFiles.map { bibleFileDisplayNames[it] ?: it },
                    onValueChange = { displayName ->
                        if (displayName != addTranslationLabel) {
                            val fileName = bibleFileDisplayNames.entries
                                .find { it.value == displayName }?.key ?: displayName
                            onSettingsChange { app ->
                                app.copy(bibleSettings = app.bibleSettings.addTranslation(fileName))
                            }
                            onSelect(translations.size)
                        }
                    }
                )
            }
        }
        }
    }
}

/** One slot: which Bible it holds, where it sits in the order, and the way to take it out. */
@Composable
private fun TranslationRow(
    index: Int,
    translation: BibleTranslationSettings,
    translations: List<BibleTranslationSettings>,
    bibleFilesInDirectory: List<String>,
    bibleFileDisplayNames: Map<String, String>,
    pickerWidth: androidx.compose.ui.unit.Dp,
    noneStr: String,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSelect: (Int) -> Unit,
    selectedIndex: Int,
) {
    // Every Bible except the ones other slots already hold. The stack is keyed by file name, so
    // picking a duplicate used to collapse two slots into one and take the other's fonts and colors
    // with it, silently and with no undo.
    val slotOptions = listOf(noneStr) + bibleFilesInDirectory
        .filter { candidate ->
            candidate == translation.fileName || translations.none { it.fileName == candidate }
        }
        .map { fileName -> bibleFileDisplayNames[fileName] ?: fileName }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        DropdownSettingsField(
            width = pickerWidth,
            label = stringResource(Res.string.bible_translation, index + 1),
            value = bibleFileDisplayNames[translation.fileName] ?: translation.fileName,
            options = slotOptions,
            onValueChange = { displayName ->
                val fileName = if (displayName == noneStr) "" else
                    bibleFileDisplayNames.entries.find { it.value == displayName }?.key ?: displayName
                onSettingsChange { app ->
                    // Setting a slot to None takes a translation out of the stack, so it has to
                    // carry the output selections with it exactly as the delete button does.
                    // Swapping which bible a slot holds does not: the positions are unchanged.
                    if (fileName.isEmpty()) {
                        app.removeBibleTranslation(index)
                    } else {
                        app.copy(
                            bibleSettings = app.bibleSettings
                                .updateTranslation(index) { it.copy(fileName = fileName) },
                        )
                    }
                }
                if (fileName.isNotEmpty()) onSelect(index)
            },
        )
        // The first row has no "up" and the last no "down", so from three rows up a gap has to
        // stand in for the missing button or the delete buttons step in and out along the column
        // instead of forming one straight edge. One or two rows need no gap: every row is already
        // short of the same one button, so they line up as they are.
        val padsReorderButtons = translations.size > 2
        if (index > 0) {
            ActionIconButton(
                onClick = { onSettingsChange { app -> app.moveBibleTranslation(index, -1) } },
                tooltipText = stringResource(Res.string.move_translation_up),
                painter = painterResource(Res.drawable.ic_arrow_up),
            )
        } else if (padsReorderButtons) {
            Spacer(modifier = Modifier.size(REORDER_BUTTON_SIZE))
        }
        if (index < translations.lastIndex) {
            ActionIconButton(
                onClick = { onSettingsChange { app -> app.moveBibleTranslation(index, 1) } },
                tooltipText = stringResource(Res.string.move_translation_down),
                painter = painterResource(Res.drawable.ic_arrow_down),
            )
        } else if (padsReorderButtons) {
            Spacer(modifier = Modifier.size(REORDER_BUTTON_SIZE))
        }
        ActionIconButton(
            onClick = {
                onSettingsChange { app -> app.removeBibleTranslation(index) }
                if (selectedIndex >= index) onSelect((selectedIndex - 1).coerceAtLeast(0))
            },
            tooltipText = stringResource(Res.string.remove),
            painter = painterResource(Res.drawable.ic_delete),
        )
    }
}

/**
 * The settings that belong to the stack as a whole rather than to any one translation in it.
 *
 * Spacing and the divider are about how the translations sit against each other; split browse and
 * cross references are about the Bible tab. Too mixed a set to name for any one of them.
 */
@Composable
private fun MiscellaneousSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.bible_miscellaneous)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.bible_translation_spacing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberSettingsTextField(
                label = stringResource(Res.string.pixels_short),
                initialText = settings.bibleSettings.multiTranslationSpacing,
                onValueChange = { value ->
                    onSettingsChange { app ->
                        app.copy(bibleSettings = app.bibleSettings.copy(multiTranslationSpacing = value))
                    }
                },
                range = 0..SPACING_MAX,
            )
        }
        LabeledCheckbox(
            checked = settings.bibleSettings.multiTranslationDivider,
            onCheckedChange = { enabled ->
                onSettingsChange { s ->
                    s.copy(bibleSettings = s.bibleSettings.copy(multiTranslationDivider = enabled))
                }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.bible_translation_divider),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LabeledCheckbox(
            checked = settings.bibleSettings.splitBrowseMode,
            onCheckedChange = { enabled ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(splitBrowseMode = enabled)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.bible_split_browse_mode),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LabeledCheckbox(
            checked = settings.bibleSettings.splitLongVerses,
            onCheckedChange = { enabled ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(splitLongVerses = enabled)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.bible_split_long_verses),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LabeledCheckbox(
            checked = settings.bibleSettings.crossReferencesEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(crossReferencesEnabled = enabled)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.bible_cross_references_enable),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Where the block sits on the screen, and how it arrives and leaves. */
@Composable
private fun BlockAndTransitionSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.bible_block_and_transition)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.vertical_alignment).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            VerticalAlignmentButtons(
                selectedAlignment = settings.bibleSettings.verticalAlignment,
                onAlignmentChange = { value ->
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(verticalAlignment = value)) }
                },
                topValue = Constants.TOP,
                middleValue = Constants.MIDDLE,
                bottomValue = Constants.BOTTOM
            )
        }
        val msSuffix = stringResource(Res.string.milliseconds_suffix)
        ControlColumn(stringResource(Res.string.transition_duration), Modifier.fillMaxWidth()) {
            SlimSlider(
                value = settings.bibleSettings.transitionDuration,
                onValueChange = { rawValue ->
                    val snapped = (rawValue / TRANSITION_STEP_MS).toInt() * TRANSITION_STEP_MS
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(transitionDuration = snapped)) }
                },
                valueRange = TRANSITION_MIN_MS..TRANSITION_MAX_MS,
                modifier = Modifier.fillMaxWidth(),
                trailingLabel = "${settings.bibleSettings.transitionDuration.toInt()}$msSuffix"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledCheckbox(
                checked = settings.bibleSettings.fadeIn,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(fadeIn = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LabeledCheckbox(
                checked = settings.bibleSettings.fadeOut,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(fadeOut = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_out),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LabeledCheckbox(
                checked = settings.bibleSettings.crossfade,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(crossfade = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.animation_crossfade),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The four margins, as a plain grid.
 *
 * The screen mock-up this replaced showed where the margins were; the live preview above shows what
 * they do to the text, which is the same information and rather more of it.
 */
@Composable
private fun MarginsSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.text_margins)) {
        val bible = settings.bibleSettings
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlColumn(stringResource(Res.string.top), Modifier.weight(1f)) {
                    NumberSettingsTextField(
                        modifier = Modifier.fillMaxWidth(),
                        initialText = bible.marginTop,
                        onValueChange = { value ->
                            onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginTop = value)) }
                        },
                        range = 0..MARGIN_MAX,
                    )
                }
                ControlColumn(stringResource(Res.string.left), Modifier.weight(1f)) {
                    NumberSettingsTextField(
                        modifier = Modifier.fillMaxWidth(),
                        initialText = bible.marginLeft,
                        onValueChange = { value ->
                            onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginLeft = value)) }
                        },
                        range = 0..MARGIN_MAX,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlColumn(stringResource(Res.string.right), Modifier.weight(1f)) {
                    NumberSettingsTextField(
                        modifier = Modifier.fillMaxWidth(),
                        initialText = bible.marginRight,
                        onValueChange = { value ->
                            onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginRight = value)) }
                        },
                        range = 0..MARGIN_MAX,
                    )
                }
                ControlColumn(stringResource(Res.string.bottom), Modifier.weight(1f)) {
                    NumberSettingsTextField(
                        modifier = Modifier.fillMaxWidth(),
                        initialText = bible.marginBottom,
                        onValueChange = { value ->
                            onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginBottom = value)) }
                        },
                        range = 0..MARGIN_MAX,
                    )
                }
            }
        }
    }
}

/** The output switch, the translation chips, the preview and the controls under it. */
@Composable
private fun StylePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    translations: List<BibleTranslationSettings>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    target: BibleStyleTarget,
    onTargetChange: (BibleStyleTarget) -> Unit,
    element: BibleStyleElement,
    onElementChange: (BibleStyleElement) -> Unit,
    moduleTitles: Map<String, String>,
    availableFonts: List<String>,
    presenterManager: PresenterManager?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    // One real verse per module, so each block of the preview quotes its own translation.
    val previewVerses = rememberBiblePreviewVerses(
        settings.bibleSettings.storageDirectory,
        translations.map { it.fileName },
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TargetSwitchRow(
            settings = settings,
            translations = translations,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            target = target,
            onTargetChange = onTargetChange,
            moduleTitles = moduleTitles,
        )
        // Centred and capped rather than filling the pane: see PREVIEW_MAX_HEIGHT.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BiblePreviewPanel(
                settings = settings,
                translations = translations,
                target = target,
                verses = previewVerses,
                modifier = Modifier.width(
                    minOf(maxWidth, PREVIEW_MAX_HEIGHT * previewOutputSize(settings).aspectRatio),
                ),
            )
        }
        // An `if`, not an early `return@Column`. Returning out of a composable content lambda skips
        // the rest of its group without closing it, and the slot table then carries state across
        // into whatever composes there next -- which is how the controls below ended up wired to a
        // previous selection's state.
        val translation = translations.getOrNull(selectedIndex)
        if (translation == null) return@Column
        val style = translation.elementStyle(element, target)
        val verses = presenterManager?.selectedVerses?.value.orEmpty()
        val canMeasure = presenterManager != null &&
            presenterManager.presentingMode.value == Presenting.BIBLE &&
            verses.firstOrNull()?.verseText?.isNotBlank() == true &&
            settings.projectionSettings.screenAssignments.any {
                if (target.isLowerThird) it.isLowerThird else it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN
            }
        fun update(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
            onSettingsChange { app ->
                app.copy(bibleSettings = app.bibleSettings.updateTranslation(selectedIndex, transform))
            }
        }
        SettingsSection(title = stringResource(Res.string.bible_editing)) {
            // Keyed on what the panel is pointed at: the controls below are one set standing for
            // four stored profiles, and without this Compose keeps the subtree across a switch and
            // hands each control the state of whichever control held its slot before.
            key(selectedIndex, element, target) {
            BibleTypographyPanel(
                translation = translation,
                moduleTitle = moduleTitles[translation.fileName].orEmpty(),
                element = element,
                onElementChange = onElementChange,
                style = style,
                onStyleChange = { edited -> update { it.withElementStyle(element, target, edited) } },
                onTranslationChange = { transform -> update(transform) },
                onReset = { update { it.withElementStyle(element, target, defaultElementStyle(element, target)) } },
                availableFonts = availableFonts,
                // Auto-fit measures a verse, so it is offered on the verse and not on its reference.
                autoFit = if (element == BibleStyleElement.TEXT && presenterManager != null) {
                    {
                        val fitted = autoFitFontSize(
                            textMeasurer = textMeasurer,
                            settings = settings,
                            verses = verses,
                            translation = translation,
                            target = target,
                        )
                        update { it.withElementStyle(element, target, style.copy(fontSize = fitted)) }
                    }
                } else {
                    null
                },
                autoFitEnabled = canMeasure,
            )
            }
        }
    }
}

/**
 * Which output is being styled, which translation, and what that output actually is -- all on one
 * row, as the design has it.
 *
 * The chips take whatever the row has left and fold onto a second line of their own when that is
 * not enough for all [Constants.MAX_BIBLE_TRANSLATIONS] of them, rather than squeezing until their
 * abbreviations truncate. At the dialog's full width all six still sit beside the switch.
 */
@Composable
private fun TargetSwitchRow(
    settings: AppSettings,
    translations: List<BibleTranslationSettings>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    target: BibleStyleTarget,
    onTargetChange: (BibleStyleTarget) -> Unit,
    moduleTitles: Map<String, String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(BibleStyleTarget.FULL_SCREEN, stringResource(Res.string.full_screen)),
                SegmentedButtonItem(BibleStyleTarget.LOWER_THIRD, stringResource(Res.string.lower_third_size)),
            ),
            selectedValue = target,
            onValueChange = onTargetChange,
            buttonWidth = TARGET_BUTTON_WIDTH,
            buttonHeight = 34.dp,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
        if (translations.size > 1) {
            Text(
                text = stringResource(Res.string.bible_editing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedButton(
                items = translations.mapIndexed { index, translation ->
                    val abbreviation = translation.customAbbreviation.ifBlank {
                        defaultTranslationAbbreviation(
                            moduleTitles[translation.fileName].orEmpty(),
                            translation.fileName,
                        )
                    }
                    SegmentedButtonItem(index, "${index + 1} · $abbreviation")
                },
                selectedValue = selectedIndex,
                onValueChange = onSelect,
                buttonWidth = CHIP_WIDTH,
                buttonHeight = 34.dp,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                compactColumns = CHIP_COMPACT_COLUMNS,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = scopeNote(settings, target),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
