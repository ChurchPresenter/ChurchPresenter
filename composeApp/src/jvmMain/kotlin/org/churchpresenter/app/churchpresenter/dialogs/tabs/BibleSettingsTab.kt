package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun BibleSettingsTab() {
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