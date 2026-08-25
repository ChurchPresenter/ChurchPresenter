package org.churchpresenter.canvas


import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.ui.ColorPickerField
import org.churchpresenter.ui.FontSettingsDropdown
import org.churchpresenter.ui.tidyPreviewLines
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.canvas_font_color
import org.churchpresenter.resources.generated.resources.canvas_source_bible
import org.churchpresenter.resources.generated.resources.canvas_bible_version
import org.churchpresenter.resources.generated.resources.canvas_bible_verse_text
import org.churchpresenter.resources.generated.resources.canvas_bible_reference
import org.churchpresenter.resources.generated.resources.canvas_bible_insert
import org.churchpresenter.resources.generated.resources.canvas_bible_start_verse
import org.churchpresenter.resources.generated.resources.canvas_bible_end_verse
import org.churchpresenter.resources.generated.resources.canvas_bible_ref_font_size
import org.churchpresenter.resources.generated.resources.canvas_bible_ref_color
import org.churchpresenter.resources.generated.resources.bible_no_primary_title
import org.churchpresenter.resources.generated.resources.book
import org.churchpresenter.resources.generated.resources.chapter
import org.churchpresenter.resources.generated.resources.canvas_clock_font_size
import org.churchpresenter.resources.generated.resources.canvas_text_bg_color
import org.churchpresenter.resources.generated.resources.canvas_text_bold
import org.churchpresenter.resources.generated.resources.canvas_text_italic
import org.churchpresenter.resources.generated.resources.canvas_line_spacing
import org.churchpresenter.resources.generated.resources.canvas_font
import org.churchpresenter.resources.generated.resources.canvas_align_horizontal
import org.churchpresenter.resources.generated.resources.canvas_align_vertical
import org.churchpresenter.resources.generated.resources.canvas_verse_style
import org.churchpresenter.resources.generated.resources.canvas_reference_style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ui.rememberSystemFonts
import java.io.File
import org.churchpresenter.bible.Bible
import org.churchpresenter.ui.DropdownSelector
import org.churchpresenter.ui.HorizontalAlignmentButtons
import org.churchpresenter.ui.LabeledCheckbox
import org.churchpresenter.ui.SlimSlider
import org.churchpresenter.ui.StyledTextField
import org.churchpresenter.ui.VerticalAlignmentButtons

/**
 * The properties panel for a Bible scene source: which translation, which passage, and how the verse
 * and its reference are drawn.
 *
 * It lives here rather than in `:composeApp` for one reason worth stating: it was a slot the app
 * filled, the slot was left empty when the tab moved out, and a Bible source silently had no verse
 * picker at all. Nothing in a build, a gate or a screenshot said so, because an empty slot renders
 * as an empty panel. It reads its own translation through [CanvasBibleBrowser], so there is nothing
 * left to forget to pass in.
 */
@Composable
internal fun BibleProperties(
    source: SceneSource.BibleSource,
    onUpdate: (SceneSource) -> Unit,
    appSettings: AppSettings?,
) {
    val availableFonts = rememberSystemFonts()

    val storageDir = appSettings?.bibleSettings?.storageDirectory ?: ""
    val bibleOptions by produceState(emptyList<Pair<String, String>>(), storageDir) {
        value = withContext(Dispatchers.IO) { bibleTranslationsIn(storageDir) }
    }

    var selectedBibleFile by remember {
        mutableStateOf(appSettings?.bibleSettings?.translationList()?.firstOrNull()?.fileName ?: "")
    }

    // Loading parses a whole translation, so it happens off the composition and only when the file
    // being browsed changes.
    val browser by produceState(CanvasBibleBrowser.Empty, storageDir, selectedBibleFile) {
        value = withContext(Dispatchers.IO) { CanvasBibleBrowser.of(storageDir, selectedBibleFile) }
    }

    val bible: Bible? = browser.bible
    val books = browser.books
    var selectedBookIndex by remember(browser) { mutableStateOf(0) }
    var selectedChapter by remember(browser) { mutableStateOf(1) }
    val verses = remember(browser, selectedBookIndex, selectedChapter) {
        browser.verses(selectedBookIndex, selectedChapter)
    }

    Text(stringResource(Res.string.canvas_source_bible), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (bibleOptions.isNotEmpty()) {
        DropdownSelector(
            label = stringResource(Res.string.canvas_bible_version),
            value = selectedBibleFile,
            options = bibleOptions,
            onValueChange = { selectedBibleFile = it },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (books.isNotEmpty()) {
        DropdownSelector(
            label = stringResource(Res.string.book),
            items = books,
            selected = books.getOrElse(selectedBookIndex) { "" },
            onSelectedChange = { bookName ->
                val idx = books.indexOf(bookName)
                if (idx >= 0) {
                    selectedBookIndex = idx
                    selectedChapter = 1
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        val chapterCount = browser.chapterCount(selectedBookIndex)
        if (chapterCount > 0) {
            DropdownSelector(
                label = stringResource(Res.string.chapter),
                items = (1..chapterCount).map { it.toString() },
                selected = selectedChapter.toString(),
                onSelectedChange = { selectedChapter = it.toIntOrNull() ?: 1 },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (verses.isNotEmpty()) {
            var startVerse by remember(selectedBookIndex, selectedChapter) { mutableStateOf(1) }
            var endVerse by remember(selectedBookIndex, selectedChapter) { mutableStateOf(1) }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

            Button(
                onClick = {
                    onUpdate(
                        source.copy(
                            verseText = browser.passage(selectedBookIndex, selectedChapter, startVerse, endVerse),
                            referenceText = browser.reference(
                                selectedBookIndex, selectedChapter, startVerse, endVerse,
                            ),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(Res.string.canvas_bible_insert))
            }
        }
    } else if (appSettings == null || storageDir.isEmpty()) {
        Text(
            stringResource(Res.string.bible_no_primary_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))

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

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(4.dp))

    Text(stringResource(Res.string.canvas_verse_style), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FontSettingsDropdown(
        label = stringResource(Res.string.canvas_font),
        value = source.fontFamily,
        fonts = availableFonts,
        fillWidth = true,
        // This panel loads its own translation, which is the one this source will project — so the
        // font preview quotes that rather than whatever the main window happens to have open.
        // Genesis 1:1 out of the translation this source will project, rather than whatever the
        // main window happens to have open. The same two calls `:composeApp`'s `previewLinesFrom`
        // makes; inlined because that helper is the app's and this module does not depend on it.
        previewLines = remember(bible) {
            tidyPreviewLines(listOfNotNull(bible?.getVerseDetails(1, 1, 1)?.second))
        },
        onValueChange = { onUpdate(source.copy(fontFamily = it)) },
        modifier = Modifier.fillMaxWidth()
    )
    PropertyTextField(stringResource(Res.string.canvas_clock_font_size), source.fontSize.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(fontSize = it)) }
    }
    ColorPickerField(
        color = source.fontColor,
        onColorChange = { onUpdate(source.copy(fontColor = it)) },
        label = stringResource(Res.string.canvas_font_color)
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledCheckbox(
            checked = source.bold,
            onCheckedChange = { onUpdate(source.copy(bold = it)) },
            label = stringResource(Res.string.canvas_text_bold),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledCheckbox(
            checked = source.italic,
            onCheckedChange = { onUpdate(source.copy(italic = it)) },
            label = stringResource(Res.string.canvas_text_italic),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(stringResource(Res.string.canvas_reference_style), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PropertyTextField(stringResource(Res.string.canvas_bible_ref_font_size), source.referenceFontSize.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(referenceFontSize = it)) }
    }
    ColorPickerField(
        color = source.referenceFontColor,
        onColorChange = { onUpdate(source.copy(referenceFontColor = it)) },
        label = stringResource(Res.string.canvas_bible_ref_color)
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledCheckbox(
            checked = source.referenceBold,
            onCheckedChange = { onUpdate(source.copy(referenceBold = it)) },
            label = stringResource(Res.string.canvas_text_bold),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledCheckbox(
            checked = source.referenceItalic,
            onCheckedChange = { onUpdate(source.copy(referenceItalic = it)) },
            label = stringResource(Res.string.canvas_text_italic),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    ColorPickerField(
        color = source.backgroundColor,
        onColorChange = { onUpdate(source.copy(backgroundColor = it)) },
        label = stringResource(Res.string.canvas_text_bg_color)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(stringResource(Res.string.canvas_align_horizontal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalAlignmentButtons(
                selectedAlignment = source.horizontalAlignment,
                onAlignmentChange = { onUpdate(source.copy(horizontalAlignment = it)) },
                leftValue = "left",
                centerValue = "center",
                rightValue = "right"
            )
        }
        Column {
            Text(stringResource(Res.string.canvas_align_vertical), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VerticalAlignmentButtons(
                selectedAlignment = source.verticalAlignment,
                onAlignmentChange = { onUpdate(source.copy(verticalAlignment = it)) },
                topValue = "top",
                middleValue = "center",
                bottomValue = "bottom"
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(stringResource(Res.string.canvas_line_spacing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SlimSlider(
            value = source.lineSpacing / 100f,
            onValueChange = { onUpdate(source.copy(lineSpacing = (it * 100).toInt())) },
            valueRange = 0.5f..3f,
            trailingLabel = "${source.lineSpacing}%",
            modifier = Modifier.weight(1f)
        )
    }
}
