package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper

@Composable
fun OptionsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AppThemeWrapper {
        if (isVisible) {
            val windowState = rememberWindowState(
                position = WindowPosition(300.dp, 100.dp),
                width = 750.dp,
                height = 550.dp
            )

            Window(
                onCloseRequest = onDismiss,
                title = "Options",
                state = windowState,
                resizable = false
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    OptionsDialogContent(
                        onDismiss = onDismiss,
                        onSave = onSave
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsDialogContent(
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Song",
        "Bible",
        "Text Settings and Colors",
        "Background",
        "Background Images",
        "Folders",
        "Projection",
        "Other"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Title
        Text(
            text = "Options",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider()

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> SongTab()
                1 -> BibleTabContent()
                2 -> TextSettingsTab()
                3 -> BackgroundTab()
                4 -> BackgroundImagesTab()
                5 -> FoldersTab()
                6 -> ProjectionTab()
                7 -> OtherTab()
            }
        }

        HorizontalDivider()

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("✓ OK", color = Color.White)
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53E3E)
                )
            ) {
                Text("✗ Cancel", color = Color.White)
            }
        }
    }
}

@Composable
private fun SongTab() {
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

@Composable
private fun BibleTabContent() {
    var showVerseNumbers by remember { mutableStateOf(true) }
    var showBookNames by remember { mutableStateOf(true) }
    var verseNumberPosition by remember { mutableStateOf("Before text") }
    var bibleFontSize by remember { mutableStateOf("24") }
    var minFontSize by remember { mutableStateOf("16") }
    var maxFontSize by remember { mutableStateOf("36") }
    var lineSpacing by remember { mutableStateOf("1.2") }
    var paragraphSpacing by remember { mutableStateOf("1.5") }
    var textAlignment by remember { mutableStateOf("Center") }
    var showChapterHeadings by remember { mutableStateOf(false) }
    var highlightSearchTerms by remember { mutableStateOf(true) }
    var versesPerSlide by remember { mutableStateOf("1") }
    var autoAdvance by remember { mutableStateOf(false) }
    var autoAdvanceDelay by remember { mutableStateOf("5") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left column
        Column(
            modifier = Modifier.weight(1f).padding(end = 16.dp)
        ) {
            // Display Settings
            Text(
                text = "Display Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showVerseNumbers,
                    onCheckedChange = { showVerseNumbers = it }
                )
                Text("Show verse numbers")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showBookNames,
                    onCheckedChange = { showBookNames = it }
                )
                Text("Show book names")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verse number position:", modifier = Modifier.width(140.dp))
                DropdownSelector(
                    modifier = Modifier.width(120.dp),
                    label = "",
                    items = listOf("Before text", "After text", "Superscript"),
                    selected = verseNumberPosition,
                    onSelectedChange = { verseNumberPosition = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Settings
            Text(
                text = "Font Settings",
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
                Text("Line spacing:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = lineSpacing,
                    onValueChange = { lineSpacing = it },
                    modifier = Modifier.width(60.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(16.dp))
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
                Text("Text alignment:", modifier = Modifier.width(100.dp))
                DropdownSelector(
                    modifier = Modifier.width(120.dp),
                    label = "",
                    items = listOf("Left", "Center", "Right", "Justify"),
                    selected = textAlignment,
                    onSelectedChange = { textAlignment = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced Settings
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showChapterHeadings,
                    onCheckedChange = { showChapterHeadings = it }
                )
                Text("Show chapter headings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = highlightSearchTerms,
                    onCheckedChange = { highlightSearchTerms = it }
                )
                Text("Highlight search terms")
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // Right column
        Column(
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        ) {
            // Presentation Settings
            Text(
                text = "Presentation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verses per slide:", modifier = Modifier.width(120.dp))
                DropdownSelector(
                    modifier = Modifier.width(80.dp),
                    label = "",
                    items = listOf("1", "2", "3", "4", "5"),
                    selected = versesPerSlide,
                    onSelectedChange = { versesPerSlide = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-advance Settings
            Text(
                text = "Auto-advance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = autoAdvance,
                    onCheckedChange = { autoAdvance = it }
                )
                Text("Enable auto-advance")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delay (seconds):", modifier = Modifier.width(120.dp))
                OutlinedTextField(
                    value = autoAdvanceDelay,
                    onValueChange = { autoAdvanceDelay = it },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    enabled = autoAdvance
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bible Version Settings
            Text(
                text = "Bible Version",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.height(8.dp))

            var selectedBible by remember { mutableStateOf("King James Version") }
            var enableParallel by remember { mutableStateOf(false) }
            var parallelBible by remember { mutableStateOf("New International Version") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Primary Bible:", modifier = Modifier.width(100.dp))
                DropdownSelector(
                    modifier = Modifier.width(180.dp),
                    label = "",
                    items = listOf(
                        "King James Version",
                        "New International Version",
                        "English Standard Version",
                        "New American Standard",
                        "Русский Синодальный"
                    ),
                    selected = selectedBible,
                    onSelectedChange = { selectedBible = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = enableParallel,
                    onCheckedChange = { enableParallel = it }
                )
                Text("Show parallel translation")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (enableParallel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Secondary Bible:", modifier = Modifier.width(120.dp))
                    DropdownSelector(
                        modifier = Modifier.width(160.dp),
                        label = "",
                        items = listOf(
                            "New International Version",
                            "English Standard Version",
                            "King James Version",
                            "New American Standard",
                            "Русский Синодальный"
                        ),
                        selected = parallelBible,
                        onSelectedChange = { parallelBible = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSettingsTab() {
    Text(
        text = "Text settings and colors will be implemented here",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun BackgroundTab() {
    Text(
        text = "Background settings will be implemented here",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun BackgroundImagesTab() {
    Text(
        text = "Background images settings will be implemented here",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun FoldersTab() {
    Text(
        text = "Folders settings will be implemented here",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun ProjectionTab() {
    Text(
        text = "Projection settings will be implemented here",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun OtherTab() {
    Text(
        text = "Other settings will be implemented here",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}

@Preview
@Composable
private fun OptionsDialogPreview() {
    Card(
        modifier = Modifier
            .width(750.dp)
            .height(550.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        OptionsDialogContent(
            onDismiss = {},
            onSave = {}
        )
    }
}

