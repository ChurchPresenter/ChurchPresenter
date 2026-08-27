package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bible_selection
import churchpresenter.composeapp.generated.resources.bible_multi_layout
import churchpresenter.composeapp.generated.resources.bible_translation_divider
import churchpresenter.composeapp.generated.resources.bible_translation_spacing
import churchpresenter.composeapp.generated.resources.bible_split_browse_mode
import churchpresenter.composeapp.generated.resources.bible_cross_references
import churchpresenter.composeapp.generated.resources.bible_cross_references_enable
import churchpresenter.composeapp.generated.resources.bible_transition_settings
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.show_abbreviation
import churchpresenter.composeapp.generated.resources.bible_custom_name
import churchpresenter.composeapp.generated.resources.bible_custom_abbreviation
import churchpresenter.composeapp.generated.resources.bible_custom_name_hint
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.add_bible_translation
import churchpresenter.composeapp.generated.resources.bible_translation
import churchpresenter.composeapp.generated.resources.move_translation_up
import churchpresenter.composeapp.generated.resources.move_translation_down
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.bible_transition_settings
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.scanning_directory
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.text_margins
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.vertical_alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size

import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.rememberDropdownWidthFor
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.ScanningRow
import org.churchpresenter.app.churchpresenter.composables.rememberBibleFolderListing
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import churchpresenter.composeapp.generated.resources.auto_fit
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.moveBibleTranslation
import org.churchpresenter.settings.removeBibleTranslation
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox

private const val COLUMN_WEIGHT = 0.48f

/** [ActionIconButton]'s own default size, which the reorder buttons take and their gaps stand in for. */
private val REORDER_BUTTON_SIZE = 34.dp

/**
 * The name box against the abbreviation box and its switch: a title is a sentence, "KJV" is not.
 *
 * The switch's label wraps onto two lines at this width, as several of the checkboxes in the left
 * column already do; taking the width back off the abbreviation box instead truncates its own
 * label to "ABBREVIATI...", which is worse.
 */
private const val NAME_FIELD_WEIGHT = 0.42f
private const val ABBREVIATION_FIELD_WEIGHT = 0.24f
private const val SHOW_ABBREVIATION_WEIGHT = 0.34f

/** Dim enough to read as "not typed yet", solid enough to read at all. */
private const val PLACEHOLDER_ALPHA = 0.6f

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

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = SettingsScrollbarGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(COLUMN_WEIGHT).widthIn(min = 400.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeftColumn(
                    settings,
                    onSettingsChange,
                    bibleFilesInDirectory,
                    bibleFileDisplayNames,
                    scanning = listing == null
                )
            }
            Column(
                modifier = Modifier.weight(COLUMN_WEIGHT).widthIn(min = 400.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val translations = settings.bibleSettings.translationList()
                run {
                    // Only the first is open on arrival; the rest are one header row each until asked for.
                    var expandedTranslation by remember { mutableStateOf(0) }
                    translations.forEachIndexed { index, translation ->
                        TranslationStyleSection(
                            settings = settings,
                            index = index,
                            translation = translation,
                            displayName = bibleFileDisplayNames[translation.fileName]
                                ?: translation.fileName.substringBeforeLast('.'),
                            moduleTitle = listing?.titles?.get(translation.fileName).orEmpty(),
                            expanded = expandedTranslation == index,
                            onExpandedChange = { open -> expandedTranslation = if (open) index else -1 },
                            onSettingsChange = onSettingsChange,
                            availableFonts = availableFonts,
                            presenterManager = presenterManager,
                        )
                    }
                }
            }
        }
        SettingsScrollbar(scrollState)
    }
}

@Composable
private fun TranslationStyleSection(
    settings: AppSettings,
    index: Int,
    translation: BibleTranslationSettings,
    displayName: String,
    moduleTitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager? = null,
) {
    fun update(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { app ->
            app.copy(bibleSettings = app.bibleSettings.updateTranslation(index, transform))
        }
    }
    // Collapsed by default past the first: each translation carries the full appearance profile a
    // single Bible used to get, so four of them open at once is an unreadable amount of scrolling.
    SettingsSection(
        title = "${stringResource(Res.string.bible_translation, index + 1)} — $displayName",
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        TranslationNameSection(
            translation = translation,
            moduleTitle = moduleTitle,
            update = ::update,
        )
        TranslationTextSection(settings, translation, ::update, availableFonts, presenterManager)
        TranslationReferenceSection(translation, ::update, availableFonts)
    }
}

/**
 * What this church calls the translation, as against what its `.spb` header calls it.
 *
 * Both boxes are blank until the operator types in one, and both show what the module gives itself
 * as their placeholder: a blank field means "keep using that", so the placeholder is the live value
 * rather than a hint about one. The abbreviation is the string that labels every verse on screen,
 * which is why it is editable separately -- a module titled "King James Version" abbreviates itself
 * to "KJV" whatever the church actually puts under its scripture.
 */
@Composable
private fun TranslationNameSection(
    translation: BibleTranslationSettings,
    moduleTitle: String,
    update: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
) {
    val fileName = translation.fileName
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            SettingsTextField(
                value = translation.customName,
                onValueChange = { typed -> update { it.copy(customName = typed) } },
                label = stringResource(Res.string.bible_custom_name),
                placeholder = { PlaceholderText(moduleTitle.ifBlank { fileName.substringBeforeLast('.') }) },
                modifier = Modifier.weight(NAME_FIELD_WEIGHT),
                fillWidth = true,
            )
            SettingsTextField(
                value = translation.customAbbreviation,
                onValueChange = { typed -> update { it.copy(customAbbreviation = typed) } },
                label = stringResource(Res.string.bible_custom_abbreviation),
                placeholder = {
                    PlaceholderText(defaultTranslationAbbreviation(moduleTitle, fileName))
                },
                modifier = Modifier.weight(ABBREVIATION_FIELD_WEIGHT),
                fillWidth = true,
            )
            // Beside the abbreviation rather than down in the reference section: this switch is
            // what decides whether the string in that box reaches the screen at all, and the two
            // read as one setting -- what to call it, and whether to show it.
            LabeledCheckbox(
                checked = translation.showAbbreviation,
                onCheckedChange = { update { t -> t.copy(showAbbreviation = it) } },
                label = stringResource(Res.string.show_abbreviation),
                modifier = Modifier.weight(SHOW_ABBREVIATION_WEIGHT).padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(Res.string.bible_custom_name_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The module's own value, shown in an empty name field as the thing that is still in force. */
@Composable
private fun PlaceholderText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
        maxLines = 1,
    )
}

@Composable
private fun LeftColumn(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    bibleFilesInDirectory: List<String>,
    bibleFileDisplayNames: Map<String, String>,
    scanning: Boolean
) {
    val noneStr = stringResource(Res.string.none)
    val bibleDisplayOptions = listOf(noneStr) + bibleFilesInDirectory.map { fileName ->
        bibleFileDisplayNames[fileName] ?: fileName
    }

    // Bible Selection
    SettingsSection(title = stringResource(Res.string.bible_selection)) {
        val translations = settings.bibleSettings.translationList()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // One width for every picker in the stack, so the reorder and delete buttons beside them
            // form a straight column instead of stepping in and out with each Bible's name length.
            val addTranslationLabel = stringResource(Res.string.add_bible_translation)
            val pickerWidth = rememberDropdownWidthFor(bibleDisplayOptions + addTranslationLabel)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            translations.forEachIndexed { index, translation ->
                // Every Bible except the ones other slots already hold. The stack is keyed by file
                // name, so picking a duplicate used to collapse two slots into one and take the
                // other's fonts and colours with it, silently and with no undo.
                val slotOptions = listOf(noneStr) + bibleFilesInDirectory
                    .filter { candidate ->
                        candidate == translation.fileName ||
                            translations.none { it.fileName == candidate }
                    }
                    .map { fileName -> bibleFileDisplayNames[fileName] ?: fileName }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DropdownSettingsField(
                        width = pickerWidth,
                        label = stringResource(Res.string.bible_translation, index + 1),
                        value = bibleFileDisplayNames[translation.fileName] ?: translation.fileName,
                        options = slotOptions,
                        onValueChange = { displayName ->
                            val fileName = if (displayName == noneStr) "" else
                                bibleFileDisplayNames.entries.find { it.value == displayName }?.key ?: displayName
                            onSettingsChange { app ->
                                // Setting a slot to None takes a translation out of the stack, so it
                                // has to carry the output selections with it exactly as the delete
                                // button does. Swapping which bible a slot holds does not: the
                                // positions are unchanged.
                                if (fileName.isEmpty()) {
                                    app.removeBibleTranslation(index)
                                } else {
                                    app.copy(
                                        bibleSettings = app.bibleSettings
                                            .updateTranslation(index) { it.copy(fileName = fileName) },
                                    )
                                }
                            }
                        },
                    )
                    // The first row has no "up" and the last no "down", so from three rows up a gap
                    // has to stand in for the missing button or the delete buttons step in and out
                    // along the column instead of forming one straight edge. One or two rows need
                    // no gap: every row is already short of the same one button, so they line up as
                    // they are and a placeholder would only add dead space.
                    val padsReorderButtons = translations.size > 2
                    if (index > 0) {
                        ActionIconButton(
                            onClick = { onSettingsChange { app ->
                                app.moveBibleTranslation(index, -1)
                            } },
                            tooltipText = stringResource(Res.string.move_translation_up),
                            painter = painterResource(Res.drawable.ic_arrow_up),
                        )
                    } else if (padsReorderButtons) {
                        Spacer(modifier = Modifier.size(REORDER_BUTTON_SIZE))
                    }
                    if (index < translations.lastIndex) {
                        ActionIconButton(
                            onClick = { onSettingsChange { app ->
                                app.moveBibleTranslation(index, 1)
                            } },
                            tooltipText = stringResource(Res.string.move_translation_down),
                            painter = painterResource(Res.drawable.ic_arrow_down),
                        )
                    } else if (padsReorderButtons) {
                        Spacer(modifier = Modifier.size(REORDER_BUTTON_SIZE))
                    }
                    ActionIconButton(
                        onClick = { onSettingsChange { app ->
                            app.removeBibleTranslation(index)
                        } },
                        tooltipText = stringResource(Res.string.remove),
                        painter = painterResource(Res.drawable.ic_delete),
                    )
                }
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
                        }
                    }
                )
            }
        }
        }
    }

    // Sits with the selection it describes, and on one row: two controls did not warrant two.
    SettingsSection(title = stringResource(Res.string.bible_multi_layout)) {
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
                range = 0..200,
            )
            Spacer(Modifier.width(4.dp))
            LabeledCheckbox(
                checked = settings.bibleSettings.multiTranslationDivider,
                onCheckedChange = { enabled ->
                    onSettingsChange { app ->
                        app.copy(bibleSettings = app.bibleSettings.copy(multiTranslationDivider = enabled))
                    }
                },
                label = stringResource(Res.string.bible_translation_divider),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }


    // Split Browse Mode
    SettingsSection(title = stringResource(Res.string.bible_split_browse_mode)) {
        LabeledCheckbox(
            checked = settings.bibleSettings.splitBrowseMode,
            onCheckedChange = { enabled ->
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(splitBrowseMode = enabled)) }
                },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.bible_split_browse_mode),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    SettingsSection(title = stringResource(Res.string.bible_cross_references)) {
        LabeledCheckbox(
            checked = settings.bibleSettings.crossReferencesEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(crossReferencesEnabled = enabled)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.bible_cross_references_enable),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    // Vertical Alignment
    SettingsSection(title = stringResource(Res.string.vertical_alignment)) {
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

    // Transition
    SettingsSection(title = stringResource(Res.string.bible_transition_settings)) {
        val msSuffix = stringResource(Res.string.milliseconds_suffix)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.transition_duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(120.dp)
            )
            SlimSlider(
                value = settings.bibleSettings.transitionDuration,
                onValueChange = { rawValue ->
                    val snapped = (rawValue / 50f).toInt() * 50f
                    onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(transitionDuration = snapped)) }
                },
                valueRange = 100f..2000f,
                modifier = Modifier.weight(1f),
                trailingLabel = "${settings.bibleSettings.transitionDuration.toInt()}$msSuffix"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledCheckbox(
                checked = settings.bibleSettings.fadeIn,
                onCheckedChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(fadeIn = it)) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LabeledCheckbox(
                checked = settings.bibleSettings.fadeOut,
                onCheckedChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(fadeOut = it)) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_out),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LabeledCheckbox(
                checked = settings.bibleSettings.crossfade,
                onCheckedChange = { onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(crossfade = it)) } },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.animation_crossfade),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    // Text Margins
    SettingsSection(title = stringResource(Res.string.text_margins)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NumberSettingsTextField(modifier = Modifier.width(100.dp), label = stringResource(Res.string.top), initialText = settings.bibleSettings.marginTop, onValueChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginTop = value)) } }, range = 0..500)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NumberSettingsTextField(modifier = Modifier.width(100.dp), label = stringResource(Res.string.left), initialText = settings.bibleSettings.marginLeft, onValueChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginLeft = value)) } }, range = 0..500)
                TvScreenBox(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(180.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                NumberSettingsTextField(modifier = Modifier.width(100.dp), label = stringResource(Res.string.right), initialText = settings.bibleSettings.marginRight, onValueChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginRight = value)) } }, range = 0..500)
            }
            NumberSettingsTextField(modifier = Modifier.width(100.dp), label = stringResource(Res.string.bottom), initialText = settings.bibleSettings.marginBottom, onValueChange = { value -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(marginBottom = value)) } }, range = 0..500)
        }
    }
}

@Composable
private fun TranslationTextSection(
    settings: AppSettings,
    translation: BibleTranslationSettings,
    update: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager? = null
) {
    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
                    label = stringResource(Res.string.full_screen),
                    modifier = Modifier.width(120.dp),
                    color = translation.textColor,
                    onColorChange = {
                        update { t -> t.copy(textColor = it) }
                    }
                )
                TextStyleButtons(
                    bold = translation.textBold,
                    italic = translation.textItalic,
                    underline = translation.textUnderline,
                    shadow = translation.textShadow,
                    onBoldChange = { update { t -> t.copy(textBold = it) } },
                    onItalicChange = { update { t -> t.copy(textItalic = it) } },
                    onUnderlineChange = { update { t -> t.copy(textUnderline = it) } },
                    onShadowChange = { update { t -> t.copy(textShadow = it) } }
                )
            }
            AnimatedVisibility(visible = translation.textShadow) {
                ShadowDetailRow(
                    shadowColor = translation.textShadowColor,
                    shadowSize = translation.textShadowSize,
                    shadowOpacity = translation.textShadowOpacity,
                    onColorChange = { update { t -> t.copy(textShadowColor = it) } },
                    onSizeChange = { update { t -> t.copy(textShadowSize = it) } },
                    onOpacityChange = { update { t -> t.copy(textShadowOpacity = it) } }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
                    label = stringResource(Res.string.lower_third_size),
                    modifier = Modifier.width(120.dp),
                    color = translation.lowerThirdTextColor,
                    onColorChange = {
                        update { t -> t.copy(lowerThirdTextColor = it) }
                    }
                )
                TextStyleButtons(
                    bold = translation.lowerThirdTextBold,
                    italic = translation.lowerThirdTextItalic,
                    underline = translation.lowerThirdTextUnderline,
                    shadow = translation.lowerThirdTextShadow,
                    onBoldChange = { update { t -> t.copy(lowerThirdTextBold = it) } },
                    onItalicChange = { update { t -> t.copy(lowerThirdTextItalic = it) } },
                    onUnderlineChange = { update { t -> t.copy(lowerThirdTextUnderline = it) } },
                    onShadowChange = { update { t -> t.copy(lowerThirdTextShadow = it) } }
                )
            }
            AnimatedVisibility(visible = translation.lowerThirdTextShadow) {
                ShadowDetailRow(
                    shadowColor = translation.lowerThirdTextShadowColor,
                    shadowSize = translation.lowerThirdTextShadowSize,
                    shadowOpacity = translation.lowerThirdTextShadowOpacity,
                    onColorChange = { update { t -> t.copy(lowerThirdTextShadowColor = it) } },
                    onSizeChange = { update { t -> t.copy(lowerThirdTextShadowSize = it) } },
                    onOpacityChange = { update { t -> t.copy(lowerThirdTextShadowOpacity = it) } }
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontSettingsDropdown(
                label = stringResource(Res.string.full_screen),
                value = translation.textFontType,
                fonts = availableFonts,
                onValueChange = {
                    update { t -> t.copy(textFontType = it) }
                }
            )
            FontSettingsDropdown(
                label = stringResource(Res.string.lower_third_size),
                value = translation.lowerThirdTextFontType,
                fonts = availableFonts,
                onValueChange = {
                    update { t -> t.copy(lowerThirdTextFontType = it) }
                }
            )
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val isPresentingBible = if (presenterManager != null) {
        remember { derivedStateOf {
            presenterManager.presentingMode.value == Presenting.BIBLE &&
            presenterManager.selectedVerses.value.let { it.isNotEmpty() && it.first().verseText.isNotBlank() }
        } }.value
    } else false
    val activeScreens = settings.projectionSettings.screenAssignments
    val hasFullscreenScreen = activeScreens.any { it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN }
    val hasLowerThirdScreen = activeScreens.any { it.isLowerThird }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NumberSettingsTextField(
                        label = stringResource(Res.string.full_screen),
                        initialText = translation.textFontSize,
                        onValueChange = { update { t -> t.copy(textFontSize = it) } },
                        range = 8..150
                    )
                    if (presenterManager != null) {
                        TextButton(
                            shape = RoundedCornerShape(6.dp),
                            enabled = isPresentingBible && hasFullscreenScreen,
                            onClick = {
                                val verses = presenterManager.selectedVerses.value
                                val verse = verses.firstOrNull() ?: return@TextButton
                                val text = verse.verseText
                                if (text.isBlank()) return@TextButton
                                val bs = settings.bibleSettings
                                val proj = settings.projectionSettings
                                val baseStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(bs.primaryBibleFontType),
                                    fontWeight = if (bs.primaryBibleBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (bs.primaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (bs.primaryBibleUnderline) TextDecoration.Underline else TextDecoration.None
                                )
                                val refStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(bs.primaryReferenceFontType),
                                    fontWeight = if (bs.primaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (bs.primaryReferenceItalic) FontStyle.Italic else FontStyle.Normal
                                )
                                val availW = 1920 - proj.windowLeft - proj.windowRight - bs.marginLeft - bs.marginRight
                                val availH = 1080 - proj.windowTop - proj.windowBottom - bs.marginTop - bs.marginBottom
                                val hasSecondary = verses.size > 1
                                val effectiveH = if (hasSecondary) availH / 2 else availH
                                val refText = "${verse.bookName} ${verse.chapter}:${verse.verseNumber}"
                                val refH = textMeasurer.measure(refText, refStyle.copy(fontSize = bs.primaryReferenceFontSize.sp), density = Density(1f)).size.height
                                val fullSize = calculateAutoFitFontSize(textMeasurer, text, baseStyle, availW, effectiveH - refH)
                                update { t -> t.copy(textFontSize = fullSize) }
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NumberSettingsTextField(
                        label = stringResource(Res.string.lower_third_size),
                        initialText = translation.lowerThirdTextFontSize,
                        onValueChange = { update { t -> t.copy(lowerThirdTextFontSize = it) } },
                        range = 8..150
                    )
                    if (presenterManager != null) {
                        TextButton(
                            shape = RoundedCornerShape(6.dp),
                            enabled = isPresentingBible && hasLowerThirdScreen,
                            onClick = {
                                val verses = presenterManager.selectedVerses.value
                                val verse = verses.firstOrNull() ?: return@TextButton
                                val text = verse.verseText
                                if (text.isBlank()) return@TextButton
                                val bs = settings.bibleSettings
                                val proj = settings.projectionSettings
                                val baseStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(bs.primaryBibleFontType),
                                    fontWeight = if (bs.primaryBibleBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (bs.primaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (bs.primaryBibleUnderline) TextDecoration.Underline else TextDecoration.None
                                )
                                val refStyle = TextStyle(
                                    fontFamily = systemFontFamilyOrDefault(bs.primaryReferenceFontType),
                                    fontWeight = if (bs.primaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (bs.primaryReferenceItalic) FontStyle.Italic else FontStyle.Normal
                                )
                                val availW = 1920 - proj.windowLeft - proj.windowRight - bs.marginLeft - bs.marginRight
                                val availH = 1080 - proj.windowTop - proj.windowBottom - bs.marginTop - bs.marginBottom
                                val ltH = (availH * proj.lowerThirdHeightPercent / 100f).toInt()
                                val refText = "${verse.bookName} ${verse.chapter}:${verse.verseNumber}"
                                val ltRefH = textMeasurer.measure(refText, refStyle.copy(fontSize = bs.primaryReferenceLowerThirdFontSize.sp), density = Density(1f)).size.height
                                val ltSize = calculateAutoFitFontSize(textMeasurer, text, baseStyle, availW, ltH - ltRefH)
                                update { t -> t.copy(lowerThirdTextFontSize = ltSize) }
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(stringResource(Res.string.auto_fit), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = translation.textHorizontalAlignment,
                    onAlignmentChange = { value -> update { t -> t.copy(textHorizontalAlignment = value) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = translation.lowerThirdTextHorizontalAlignment,
                    onAlignmentChange = { value -> update { t -> t.copy(lowerThirdTextHorizontalAlignment = value) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
        }
    }
}

@Composable
private fun TranslationReferenceSection(
    translation: BibleTranslationSettings,
    update: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
    availableFonts: List<String>,
) {
    SettingRow(stringResource(Res.string.color)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
                    label = stringResource(Res.string.full_screen),
                    modifier = Modifier.width(120.dp),
                    color = translation.referenceColor,
                    onColorChange = {
                        update { t -> t.copy(referenceColor = it) }
                    }
                )
                TextStyleButtons(
                    bold = translation.referenceBold,
                    italic = translation.referenceItalic,
                    underline = translation.referenceUnderline,
                    shadow = translation.referenceShadow,
                    onBoldChange = { update { t -> t.copy(referenceBold = it) } },
                    onItalicChange = { update { t -> t.copy(referenceItalic = it) } },
                    onUnderlineChange = { update { t -> t.copy(referenceUnderline = it) } },
                    onShadowChange = { update { t -> t.copy(referenceShadow = it) } }
                )
            }
            AnimatedVisibility(visible = translation.referenceShadow) {
                ShadowDetailRow(
                    shadowColor = translation.referenceShadowColor,
                    shadowSize = translation.referenceShadowSize,
                    shadowOpacity = translation.referenceShadowOpacity,
                    onColorChange = { update { t -> t.copy(referenceShadowColor = it) } },
                    onSizeChange = { update { t -> t.copy(referenceShadowSize = it) } },
                    onOpacityChange = { update { t -> t.copy(referenceShadowOpacity = it) } }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPickerField(
                    label = stringResource(Res.string.lower_third_size),
                    modifier = Modifier.width(120.dp),
                    color = translation.lowerThirdReferenceColor,
                    onColorChange = {
                        update { t -> t.copy(lowerThirdReferenceColor = it) }
                    }
                )
                TextStyleButtons(
                    bold = translation.lowerThirdReferenceBold,
                    italic = translation.lowerThirdReferenceItalic,
                    underline = translation.lowerThirdReferenceUnderline,
                    shadow = translation.lowerThirdReferenceShadow,
                    onBoldChange = { update { t -> t.copy(lowerThirdReferenceBold = it) } },
                    onItalicChange = { update { t -> t.copy(lowerThirdReferenceItalic = it) } },
                    onUnderlineChange = { update { t -> t.copy(lowerThirdReferenceUnderline = it) } },
                    onShadowChange = { update { t -> t.copy(lowerThirdReferenceShadow = it) } }
                )
            }
            AnimatedVisibility(visible = translation.lowerThirdReferenceShadow) {
                ShadowDetailRow(
                    shadowColor = translation.lowerThirdReferenceShadowColor,
                    shadowSize = translation.lowerThirdReferenceShadowSize,
                    shadowOpacity = translation.lowerThirdReferenceShadowOpacity,
                    onColorChange = { update { t -> t.copy(lowerThirdReferenceShadowColor = it) } },
                    onSizeChange = { update { t -> t.copy(lowerThirdReferenceShadowSize = it) } },
                    onOpacityChange = { update { t -> t.copy(lowerThirdReferenceShadowOpacity = it) } }
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.font_type)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontSettingsDropdown(
                label = stringResource(Res.string.full_screen),
                value = translation.referenceFontType,
                fonts = availableFonts,
                onValueChange = {
                    update { t -> t.copy(referenceFontType = it) }
                }
            )
            FontSettingsDropdown(
                label = stringResource(Res.string.lower_third_size),
                value = translation.lowerThirdReferenceFontType,
                fonts = availableFonts,
                onValueChange = {
                    update { t -> t.copy(lowerThirdReferenceFontType = it) }
                }
            )
        }
    }
    SettingRow(stringResource(Res.string.font_size)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberSettingsTextField(
                label = stringResource(Res.string.full_screen),
                initialText = translation.referenceFontSize,
                onValueChange = { update { t -> t.copy(referenceFontSize = it) } },
                range = 8..150
            )
            NumberSettingsTextField(
                label = stringResource(Res.string.lower_third_size),
                initialText = translation.lowerThirdReferenceFontSize,
                onValueChange = { update { t -> t.copy(lowerThirdReferenceFontSize = it) } },
                range = 8..150
            )
        }
    }
    SettingRow(stringResource(Res.string.position)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                PositionButtons(
                    selectedPosition = translation.referencePosition,
                    onPositionChange = { value ->
                        update { t -> t.copy(referencePosition = value) }
                    },
                    aboveValue = Constants.POSITION_ABOVE,
                    belowValue = Constants.POSITION_BELOW
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                PositionButtons(
                    selectedPosition = translation.lowerThirdReferencePosition,
                    onPositionChange = { value ->
                        update { t -> t.copy(lowerThirdReferencePosition = value) }
                    },
                    aboveValue = Constants.POSITION_ABOVE,
                    belowValue = Constants.POSITION_BELOW
                )
            }
        }
    }
    SettingRow(stringResource(Res.string.horizontal_alignment), width = 200.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.full_screen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = translation.referenceHorizontalAlignment,
                    onAlignmentChange = { value -> update { t -> t.copy(referenceHorizontalAlignment = value) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lower_third_size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                HorizontalAlignmentButtons(
                    selectedAlignment = translation.lowerThirdReferenceHorizontalAlignment,
                    onAlignmentChange = { value -> update { t -> t.copy(lowerThirdReferenceHorizontalAlignment = value) } },
                    leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
                )
            }
        }
    }
}

