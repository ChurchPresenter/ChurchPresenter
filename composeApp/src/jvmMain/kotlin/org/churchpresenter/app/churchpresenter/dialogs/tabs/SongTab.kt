package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector

@Composable
fun SongTab() {
    var showTitle by remember { mutableStateOf(true) }
    var titleDisplay by remember { mutableStateOf("On first page") }
    var titleFontSize by remember { mutableStateOf("33") }
    var minFontSize by remember { mutableStateOf("10") }
    var maxFontSize by remember { mutableStateOf("30") }
    var translation by remember { mutableStateOf("80%") }
    var lineSpacing by remember { mutableStateOf("1") }
    var paragraphSpacing by remember { mutableStateOf("1.3") }
    var maxLineCount by remember { mutableStateOf("unlimited") }
    var wordWrap by remember { mutableStateOf(false) }
    var increaseTopMargin by remember { mutableStateOf(true) }

    // Chord settings
    var showChords by remember { mutableStateOf(false) }
    var chordFontSize by remember { mutableStateOf("100%") }
    var chordMinFontSize by remember { mutableStateOf("35") }
    var chordMaxFontSize by remember { mutableStateOf("40") }
    var chordLanguages by remember { mutableStateOf("All languages") }
    var transposeB by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left column
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        ) {
            // Title section
            Text(
                text = "Title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showTitle,
                    onCheckedChange = { showTitle = it }
                )
                Text("Show title:")
                Spacer(modifier = Modifier.width(16.dp))
                DropdownSelector(
                    modifier = Modifier.width(140.dp),
                    label = "",
                    items = listOf("On first page", "On every page", "Never"),
                    selected = titleDisplay,
                    onSelectedChange = { titleDisplay = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font size:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = titleFontSize,
                    onValueChange = { titleFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text section
            Text(
                text = "Text",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font size:", modifier = Modifier.width(80.dp))
                Text("Min:", modifier = Modifier.width(40.dp))
                OutlinedTextField(
                    value = minFontSize,
                    onValueChange = { minFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Max:", modifier = Modifier.width(40.dp))
                OutlinedTextField(
                    value = maxFontSize,
                    onValueChange = { maxFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Translation:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Max. line count:", modifier = Modifier.width(100.dp))
                Text(maxLineCount)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Line spacing:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = lineSpacing,
                    onValueChange = { lineSpacing = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(16.dp))
                Checkbox(
                    checked = wordWrap,
                    onCheckedChange = { wordWrap = it }
                )
                Text("Wordwrap")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paragraph spacing:", modifier = Modifier.width(120.dp))
                OutlinedTextField(
                    value = paragraphSpacing,
                    onValueChange = { paragraphSpacing = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = increaseTopMargin,
                    onCheckedChange = { increaseTopMargin = it }
                )
                Text("Increase top-margin on pages without title")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chords section
            Text(
                text = "Chords",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showChords,
                    onCheckedChange = { showChords = it }
                )
                Text("Show")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font size:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = chordFontSize,
                    onValueChange = { chordFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Min:", modifier = Modifier.width(40.dp))
                OutlinedTextField(
                    value = chordMinFontSize,
                    onValueChange = { chordMinFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Max:", modifier = Modifier.width(40.dp))
                OutlinedTextField(
                    value = chordMaxFontSize,
                    onValueChange = { chordMaxFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show", modifier = Modifier.width(40.dp))
                DropdownSelector(
                    modifier = Modifier.width(140.dp),
                    label = "",
                    items = listOf("All languages", "English only", "Local language"),
                    selected = chordLanguages,
                    onSelectedChange = { chordLanguages = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = transposeB,
                    onCheckedChange = { transposeB = it }
                )
                Text("Transpose: Add natural sign to \"B\"")
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // Right column
        Column(
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        ) {
            // Song number section
            Text(
                text = "Songnumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            var showSongNumber by remember { mutableStateOf(false) }
            var songNumberOnFirstPage by remember { mutableStateOf(true) }
            var songNumberPosition by remember { mutableStateOf("Bottom Right") }
            var songNumberField by remember { mutableStateOf("Song Number") }
            var songNumberFontSize by remember { mutableStateOf("33") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showSongNumber,
                    onCheckedChange = { showSongNumber = it }
                )
                Text("Show")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = songNumberOnFirstPage,
                    onClick = { songNumberOnFirstPage = true }
                )
                Text("Only on 1st page")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font size:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = songNumberFontSize,
                    onValueChange = { songNumberFontSize = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Position:", modifier = Modifier.width(60.dp))
                DropdownSelector(
                    modifier = Modifier.width(140.dp),
                    label = "",
                    items = listOf("Bottom Right", "Bottom Left", "Top Right", "Top Left"),
                    selected = songNumberPosition,
                    onSelectedChange = { songNumberPosition = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Field:", modifier = Modifier.width(60.dp))
                DropdownSelector(
                    modifier = Modifier.width(140.dp),
                    label = "",
                    items = listOf("Song Number", "Page Number", "Custom"),
                    selected = songNumberField,
                    onSelectedChange = { songNumberField = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Copyright section
            Text(
                text = "Copyright",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            var showCopyright by remember { mutableStateOf(true) }
            var copyrightPage by remember { mutableStateOf("On last page") }
            var ccliLicense by remember { mutableStateOf("") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showCopyright,
                    onCheckedChange = { showCopyright = it }
                )
                Text("Show")
                Spacer(modifier = Modifier.width(16.dp))
                DropdownSelector(
                    modifier = Modifier.width(140.dp),
                    label = "",
                    items = listOf("On last page", "On every page", "On first page"),
                    selected = copyrightPage,
                    onSelectedChange = { copyrightPage = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CCLI License:", modifier = Modifier.width(100.dp))
                OutlinedTextField(
                    value = ccliLicense,
                    onValueChange = { ccliLicense = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Other section
            Text(
                text = "Other",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            var textPreviewNearSong by remember { mutableStateOf(false) }
            var textPreviewReview by remember { mutableStateOf(false) }
            var showVerseSequence by remember { mutableStateOf(false) }
            var useLargestFont by remember { mutableStateOf(false) }
            var useSelectedLanguage by remember { mutableStateOf(false) }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = textPreviewNearSong,
                        onCheckedChange = { textPreviewNearSong = it }
                    )
                    Text("Text preview near song")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = textPreviewReview,
                        onCheckedChange = { textPreviewReview = it }
                    )
                    Text("Text preview/review")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showVerseSequence,
                        onCheckedChange = { showVerseSequence = it }
                    )
                    Text("Show verse sequence on presentation")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useLargestFont,
                        onCheckedChange = { useLargestFont = it }
                    )
                    Text("Use largest possible font size for each page")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useSelectedLanguage,
                        onCheckedChange = { useSelectedLanguage = it }
                    )
                    Text("Use selected language for all songs")
                }
            }
        }
    }
}