package org.churchpresenter.canvas

import org.churchpresenter.bibletab.loadChapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_font_color
import org.churchpresenter.strings.generated.resources.canvas_source_bible
import org.churchpresenter.strings.generated.resources.canvas_bible_version
import org.churchpresenter.strings.generated.resources.canvas_bible_verse_text
import org.churchpresenter.strings.generated.resources.canvas_bible_reference
import org.churchpresenter.strings.generated.resources.canvas_bible_insert
import org.churchpresenter.strings.generated.resources.canvas_bible_start_verse
import org.churchpresenter.strings.generated.resources.canvas_bible_end_verse
import org.churchpresenter.strings.generated.resources.canvas_bible_ref_font_size
import org.churchpresenter.strings.generated.resources.canvas_bible_ref_color
import org.churchpresenter.strings.generated.resources.bible_no_primary_title
import org.churchpresenter.strings.generated.resources.book
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.canvas_clock_font_size
import org.churchpresenter.strings.generated.resources.canvas_text_bg_color
import org.churchpresenter.strings.generated.resources.canvas_letter_spacing
import org.churchpresenter.strings.generated.resources.canvas_text_curve
import org.churchpresenter.strings.generated.resources.canvas_font
import org.churchpresenter.strings.generated.resources.canvas_align_horizontal
import org.churchpresenter.strings.generated.resources.canvas_align_vertical
import org.churchpresenter.strings.generated.resources.canvas_verse_style
import org.churchpresenter.strings.generated.resources.canvas_reference_style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.bible.Bible
import androidx.compose.runtime.produceState
import java.io.File
import org.churchpresenter.bible.readTranslationTitle
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.sharedui.composables.ColorPickerField
import org.churchpresenter.sharedui.composables.FontSettingsDropdown
import org.churchpresenter.sharedui.composables.HorizontalAlignmentButtons
import org.churchpresenter.sharedui.composables.PropertyIntField
import org.churchpresenter.sharedui.composables.PropertySliderWithInput
import org.churchpresenter.sharedui.composables.StyledTextField
import org.churchpresenter.sharedui.composables.TextStyleButtons
import org.churchpresenter.sharedui.composables.VerticalAlignmentButtons
import org.churchpresenter.sharedui.composables.previewLinesFrom

/** Two full turns of curve either way; past that the line runs into itself. */
/** The font name needs the room; its size is three digits. */
private const val FONT_NAME_WEIGHT = 2f

/** The book name needs the room; chapter and verses are a few digits each. */
private const val BOOK_WEIGHT = 2f
private const val MAX_TEXT_CURVE = 200f

/** Tracking, as a percentage of the font size. */
private const val MIN_LETTER_SPACING = -20f
private const val MAX_LETTER_SPACING = 100f

@Composable
internal fun BibleProperties(
    source: SceneSource.BibleSource,
    onUpdate: (SceneSource) -> Unit,
    appSettings: AppSettings?
) {
    val availableFonts = rememberSystemFonts()

    val storageDir = appSettings?.bibleSettings?.storageDirectory ?: ""
    val customNames = appSettings?.bibleSettings?.customNames().orEmpty()
    val bibleOptions by produceState(emptyList<Pair<String, String>>(), storageDir, customNames) {
        value = withContext(Dispatchers.IO) {
            if (storageDir.isEmpty()) emptyList()
            else bibleFilesInDirectory(storageDir)
                .map { fileName ->
                    fileName to readTranslationTitle(File(storageDir, fileName), customNames[fileName])
                }
        }
    }

    var selectedBibleFile by remember {
        mutableStateOf(appSettings?.bibleSettings?.translationList()?.firstOrNull()?.fileName ?: "")
    }

    val bibleVm = remember(appSettings, selectedBibleFile) {
        appSettings?.let {
            val settings = it.copy(
                bibleSettings = it.bibleSettings.withTranslations(
                    listOf(BibleTranslationSettings(fileName = selectedBibleFile)),
                ),
            )
            BibleViewModel(settings)
        }
    }

    val bible = bibleVm?.primaryBible?.value

    Text(
        stringResource(Res.string.canvas_source_bible),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (bibleOptions.isNotEmpty()) {
        DropdownSelector(
            label = stringResource(Res.string.canvas_bible_version),
            value = selectedBibleFile,
            options = bibleOptions,
            onValueChange = { selectedBibleFile = it },
            modifier = Modifier.fillMaxWidth()
        )
    }

    BibleVersePicker(
        bibleVm = bibleVm,
        source = source,
        onUpdate = onUpdate,
        noBibleConfigured = appSettings == null || storageDir.isEmpty(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))

    BibleTextFields(source, onUpdate)
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))

    BibleVerseStyle(source, onUpdate, bible, availableFonts)
    Spacer(modifier = Modifier.height(4.dp))

    BibleReferenceStyle(source, onUpdate)
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                stringResource(Res.string.canvas_align_horizontal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalAlignmentButtons(
                selectedAlignment = source.horizontalAlignment,
                onAlignmentChange = { onUpdate(source.copy(horizontalAlignment = it)) },
                leftValue = "left",
                centerValue = "center",
                rightValue = "right"
            )
        }
        Column {
            Text(
                stringResource(Res.string.canvas_align_vertical),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VerticalAlignmentButtons(
                selectedAlignment = source.verticalAlignment,
                onAlignmentChange = { onUpdate(source.copy(verticalAlignment = it)) },
                topValue = "top",
                middleValue = "center",
                bottomValue = "bottom"
            )
        }
    }
}

/** Book, chapter and verses from the panel's own translation, inserted into the source as its text. */
@Composable
private fun BibleVersePicker(
    bibleVm: BibleViewModel?,
    source: SceneSource.BibleSource,
    onUpdate: (SceneSource) -> Unit,
    noBibleConfigured: Boolean,
) {
    val bible = bibleVm?.primaryBible?.value
    val books = bibleVm?.books?.value ?: emptyList()
    val verses = bibleVm?.verses?.value ?: emptyList()
    val selectedBookIndex = bibleVm?.selectedBookIndex?.value ?: 0
    val selectedChapter = bibleVm?.selectedChapter?.value ?: 1
    if (books.isNotEmpty()) {
        val chapterCount = bible?.getChapterCount(bible.getBookId(selectedBookIndex)) ?: 0
        var startVerse by remember(selectedBookIndex, selectedChapter) { mutableStateOf(1) }
        var endVerse by remember(selectedBookIndex, selectedChapter) { mutableStateOf(1) }

        // Book, chapter and the verse range on one row, the way a reference is read.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DropdownSelector(
                label = stringResource(Res.string.book),
                value = books.getOrElse(selectedBookIndex) { "" },
                options = books.map { it to it },
                onValueChange = { bookName ->
                    val idx = books.indexOf(bookName)
                    if (idx >= 0) bibleVm?.loadChapter(idx, 1)
                },
                modifier = Modifier.weight(BOOK_WEIGHT)
            )
            if (chapterCount > 0) {
                DropdownSelector(
                    label = stringResource(Res.string.chapter),
                    value = selectedChapter.toString(),
                    options = (1..chapterCount).map { it.toString() to it.toString() },
                    onValueChange = { bibleVm?.loadChapter(selectedBookIndex, it.toIntOrNull() ?: 1) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (verses.isNotEmpty()) {
                StyledTextField(
                    value = startVerse.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { sv ->
                            startVerse = sv.coerceIn(1, verses.size)
                            if (endVerse < startVerse) endVerse = startVerse
                        }
                    },
                    label = stringResource(Res.string.canvas_bible_start_verse),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                StyledTextField(
                    value = endVerse.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { ev ->
                            endVerse = ev.coerceIn(startVerse, verses.size)
                        }
                    },
                    label = stringResource(Res.string.canvas_bible_end_verse),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        if (verses.isNotEmpty()) {
            RaisedButton(
                onClick = {
                    val bookName = books.getOrElse(selectedBookIndex) { "" }
                    val bookId = bible?.getBookId(selectedBookIndex) ?: return@RaisedButton
                    val verseTexts = (startVerse..endVerse).mapNotNull { vNum ->
                        bible.getVerseDetails(bookId, selectedChapter, vNum)?.second
                    }
                    val combinedText = verseTexts.joinToString(" ")
                    val reference = if (startVerse == endVerse) {
                        "$bookName $selectedChapter:$startVerse"
                    } else {
                        "$bookName $selectedChapter:$startVerse-$endVerse"
                    }
                    onUpdate(source.copy(verseText = combinedText, referenceText = reference))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape(8.dp)
            ) {
                Text(stringResource(Res.string.canvas_bible_insert))
            }
        }
    } else if (noBibleConfigured) {
        Text(
            stringResource(Res.string.bible_no_primary_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The verse and reference the source shows, editable by hand. */
@Composable
private fun BibleTextFields(source: SceneSource.BibleSource, onUpdate: (SceneSource) -> Unit) {
    var verseTextValue by remember(source.verseText) { mutableStateOf(source.verseText) }
    StyledTextField(
        value = verseTextValue,
        onValueChange = {
            verseTextValue = it
            onUpdate(source.copy(verseText = it))
        },
        label = stringResource(Res.string.canvas_bible_verse_text),
        singleLine = false,
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth()
    )

    var refTextValue by remember(source.referenceText) { mutableStateOf(source.referenceText) }
    StyledTextField(
        value = refTextValue,
        onValueChange = {
            refTextValue = it
            onUpdate(source.copy(referenceText = it))
        },
        label = stringResource(Res.string.canvas_bible_reference),
        modifier = Modifier.fillMaxWidth()
    )
}

/** The verse text's face: font, size, spacing, curve, colours and style. */
@Composable
private fun BibleVerseStyle(
    source: SceneSource.BibleSource,
    onUpdate: (SceneSource) -> Unit,
    bible: Bible?,
    availableFonts: List<String>,
) {
    Text(
        stringResource(Res.string.canvas_verse_style),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FontSettingsDropdown(
            label = stringResource(Res.string.canvas_font),
            value = source.fontFamily,
            fonts = availableFonts,
            fillWidth = true,
            // This panel loads its own translation, which is the one this source will project — so
            // the font preview quotes that rather than whatever the main window happens to have open.
            previewLines = remember(bible) { previewLinesFrom(listOfNotNull(bible)) },
            onValueChange = { onUpdate(source.copy(fontFamily = it)) },
            modifier = Modifier.weight(FONT_NAME_WEIGHT)
        )
        PropertyIntField(
            stringResource(Res.string.canvas_clock_font_size),
            source.fontSize,
            MIN_FONT_SIZE..MAX_FONT_SIZE,
            Modifier.weight(1f)
        ) { onUpdate(source.copy(fontSize = it)) }
    }
    PropertySliderWithInput(
        stringResource(Res.string.canvas_letter_spacing),
        source.letterSpacing, MIN_LETTER_SPACING, MAX_LETTER_SPACING, "%"
    ) { v -> onUpdate(source.copy(letterSpacing = v)) }
    PropertySliderWithInput(
        stringResource(Res.string.canvas_text_curve),
        source.curve, -MAX_TEXT_CURVE, MAX_TEXT_CURVE, "%"
    ) { v -> onUpdate(source.copy(curve = v)) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorPickerField(
            color = source.fontColor,
            onColorChange = { onUpdate(source.copy(fontColor = it)) },
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.canvas_font_color)
        )
        ColorPickerField(
            color = source.backgroundColor,
            onColorChange = { onUpdate(source.copy(backgroundColor = it)) },
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.canvas_text_bg_color)
        )
    }
    // The same four faces the Bible settings tab offers, and no shadow button: a canvas source
    // has no shadow to turn on.
    TextStyleButtons(
        bold = source.bold,
        italic = source.italic,
        underline = source.underline,
        shadow = false,
        onBoldChange = { onUpdate(source.copy(bold = it)) },
        onItalicChange = { onUpdate(source.copy(italic = it)) },
        onUnderlineChange = { onUpdate(source.copy(underline = it)) },
        onShadowChange = {},
        strikethrough = source.strikethrough,
        onStrikethroughChange = { onUpdate(source.copy(strikethrough = it)) },
        showShadow = false,
        outline = source.outline,
        onOutlineChange = { onUpdate(source.copy(outline = it)) },
    )
}

/** The reference line's face: size, colour and style. */
@Composable
private fun BibleReferenceStyle(source: SceneSource.BibleSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_reference_style),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PropertyIntField(
            stringResource(Res.string.canvas_bible_ref_font_size),
            source.referenceFontSize,
            MIN_FONT_SIZE..MAX_FONT_SIZE,
            Modifier.weight(1f)
        ) { onUpdate(source.copy(referenceFontSize = it)) }
        ColorPickerField(
            color = source.referenceFontColor,
            onColorChange = { onUpdate(source.copy(referenceFontColor = it)) },
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.canvas_bible_ref_color)
        )
    }
    TextStyleButtons(
        bold = source.referenceBold,
        italic = source.referenceItalic,
        underline = source.referenceUnderline,
        shadow = false,
        onBoldChange = { onUpdate(source.copy(referenceBold = it)) },
        onItalicChange = { onUpdate(source.copy(referenceItalic = it)) },
        onUnderlineChange = { onUpdate(source.copy(referenceUnderline = it)) },
        onShadowChange = {},
        strikethrough = source.referenceStrikethrough,
        onStrikethroughChange = { onUpdate(source.copy(referenceStrikethrough = it)) },
        showShadow = false,
        outline = source.referenceOutline,
        onOutlineChange = { onUpdate(source.copy(referenceOutline = it)) },
    )
}
