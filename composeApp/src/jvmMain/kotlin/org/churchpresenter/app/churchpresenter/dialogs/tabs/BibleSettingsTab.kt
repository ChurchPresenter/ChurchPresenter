package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_bible_translation
import churchpresenter.composeapp.generated.resources.bible_cross_references_enable
import churchpresenter.composeapp.generated.resources.bible_custom_abbreviation
import churchpresenter.composeapp.generated.resources.bible_custom_name
import churchpresenter.composeapp.generated.resources.bible_miscellaneous
import churchpresenter.composeapp.generated.resources.bible_split_browse_mode
import churchpresenter.composeapp.generated.resources.bible_translation
import churchpresenter.composeapp.generated.resources.bible_translations
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.move_translation_down
import churchpresenter.composeapp.generated.resources.move_translation_up
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.scanning_directory
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.ScanningRow
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.rememberBibleFolderListing
import org.churchpresenter.app.churchpresenter.composables.rememberDropdownWidthFor
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.moveBibleTranslation
import org.churchpresenter.settings.removeBibleTranslation
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** [ActionIconButton]'s own default size, which the reorder buttons take and their gaps stand in for. */
private val REORDER_BUTTON_SIZE = 34.dp

/** Past two translations every row keeps three button slots, a gap standing in for a missing one. */
private const val REORDER_SLOTS_PADDED = 3

/** A picker narrower than this is not worth showing a name in. */
private val PICKER_MIN_WIDTH = 120.dp

/** The gap between a slot's picker and the buttons beside it. */
private val ROW_GAP = 6.dp

/**
 * The Bible tab of the settings dialog.
 *
 * Content only: which Bibles are in the stack and in what order, and the two flags
 * ([org.churchpresenter.settings.BibleSettings.splitBrowseMode]/
 * [org.churchpresenter.settings.BibleSettings.crossReferencesEnabled]) that decide what the live
 * Bible tab itself offers, rather than how anything looks. Everything about how a translation is
 * styled -- font, colour, margins, transitions, bilingual layout and the rest -- moved to the
 * Profiles tab with the rest of an output's appearance; a profile styles the stack this tab builds,
 * it does not choose what is in it.
 */
@Composable
fun BibleSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    // Null while the folder is still being read. Walking it and reading a header out of every module
    // used to happen inline here, so the whole dialog waited on it before painting once per open.
    val listing = rememberBibleFolderListing(settings.bibleSettings.storageDirectory)
    val bibleFilesInDirectory = listing?.files.orEmpty()
    // The renames are applied over the scan rather than baked into it, so typing in a name field
    // re-labels every picker on the next frame without walking the Bible folder again.
    val bibleFileDisplayNames = listing?.namesWith(settings.bibleSettings.customNames()).orEmpty()

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
    ) {
        Box(modifier = Modifier.widthIn(max = 420.dp).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = SettingsScrollbarGutter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranslationsSection(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    bibleFilesInDirectory = bibleFilesInDirectory,
                    bibleFileDisplayNames = bibleFileDisplayNames,
                    moduleTitles = listing?.titles.orEmpty(),
                    scanning = listing == null,
                )
                MiscellaneousSection(settings, onSettingsChange)
            }
            SettingsScrollbar(scrollState)
        }
    }
}

/** Which Bibles are in the stack, in what order, and the picker that adds the next one. */
@Composable
private fun TranslationsSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    bibleFilesInDirectory: List<String>,
    bibleFileDisplayNames: Map<String, String>,
    /** Each module's own header title, with no rename applied -- what a blank name box falls back to. */
    moduleTitles: Map<String, String>,
    scanning: Boolean,
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
                        moduleTitle = moduleTitles[translation.fileName].orEmpty(),
                        pickerWidth = pickerWidth,
                        noneStr = noneStr,
                        onSettingsChange = onSettingsChange,
                    )
                }
                val unselectedFiles = bibleFilesInDirectory.filter { candidate ->
                    translations.none { it.fileName == candidate }
                }
                // Hidden at the cap as well as when nothing is left to add: `addTranslation` refuses
                // past it, and a picker that answers a selection by doing nothing is worse than no
                // picker. Until the folder has been read there is nothing to offer yet — say so,
                // because an absent picker otherwise reads as "this folder holds no other translations".
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
                        },
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
    moduleTitle: String,
    pickerWidth: Dp,
    noneStr: String,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    // Every Bible except the ones other slots already hold. The stack is keyed by file name, so
    // picking a duplicate used to collapse two slots into one and take the other's fonts and colors
    // with it, silently and with no undo.
    val slotOptions = listOf(noneStr) + bibleFilesInDirectory
        .filter { candidate ->
            candidate == translation.fileName || translations.none { it.fileName == candidate }
        }
        .map { fileName -> bibleFileDisplayNames[fileName] ?: fileName }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            onClick = { onSettingsChange { app -> app.removeBibleTranslation(index) } },
            tooltipText = stringResource(Res.string.remove),
            painter = painterResource(Res.drawable.ic_delete),
        )
    }
    TranslationIdentityRow(
        index = index,
        translation = translation,
        moduleTitle = moduleTitle,
        onSettingsChange = onSettingsChange,
    )
    }
}

/**
 * What this church calls the translation, as against what its `.spb` header calls it.
 *
 * Here rather than on the Profiles tab because these two are **global**: `withSparseBibleOverride`
 * pins them to the document, so a profile could never carry its own. They had drifted into the
 * styling panel's header and became unreachable when that header stopped being drawn -- which left
 * no way at all to rename a module or fix the abbreviation printed beside a verse.
 *
 * Both boxes stand empty until something is typed, and both show the module's own value as their
 * placeholder: blank means "keep using that", so the placeholder is the live value rather than a
 * hint about one.
 */
@Composable
private fun TranslationIdentityRow(
    index: Int,
    translation: BibleTranslationSettings,
    moduleTitle: String,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    fun edit(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { app ->
            app.copy(bibleSettings = app.bibleSettings.updateTranslation(index, transform))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = IDENTITY_ROW_INSET),
    ) {
        SettingsTextField(
            value = translation.customName,
            onValueChange = { typed -> edit { it.copy(customName = typed) } },
            label = stringResource(Res.string.bible_custom_name),
            placeholder = { Text(moduleTitle.ifBlank { translation.fileName.substringBeforeLast('.') }) },
            modifier = Modifier.weight(1f),
            fillWidth = true,
        )
        SettingsTextField(
            value = translation.customAbbreviation,
            onValueChange = { typed -> edit { it.copy(customAbbreviation = typed) } },
            label = stringResource(Res.string.bible_custom_abbreviation),
            // The module's own abbreviation, which is exactly what a blank box falls back to on
            // screen -- so the placeholder is the live value rather than a hint about one.
            placeholder = { Text(defaultTranslationAbbreviation(moduleTitle, translation.fileName)) },
            modifier = Modifier.width(ABBREVIATION_FIELD_WIDTH),
            fillWidth = true,
        )
    }
}

/** Indented under its own slot's picker, so the pair reads as belonging to the row above. */
private val IDENTITY_ROW_INSET = 4.dp

/** Wide enough for the longest abbreviation anyone writes; the name beside it takes the rest. */
private val ABBREVIATION_FIELD_WIDTH = 96.dp

/**
 * The two flags that decide what the live Bible tab itself offers -- content, not appearance, so
 * they stay here rather than moving to the Profiles tab with everything else this section used to
 * carry (translation spacing, bilingual layout, long-verse splitting, the divider -- all styling,
 * now on the Profiles tab's Bible pane).
 */
@Composable
private fun MiscellaneousSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.bible_miscellaneous)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                checked = settings.bibleSettings.crossReferencesEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChange { s ->
                        s.copy(bibleSettings = s.bibleSettings.copy(crossReferencesEnabled = enabled))
                    }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.bible_cross_references_enable),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
