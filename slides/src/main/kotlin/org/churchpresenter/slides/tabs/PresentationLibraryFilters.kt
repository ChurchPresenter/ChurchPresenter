package org.churchpresenter.slides.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.slides.viewmodel.HymnQuickPlay
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.hymn_quick_format
import org.churchpresenter.strings.generated.resources.hymn_quick_book
import org.churchpresenter.strings.generated.resources.hymn_quick_all
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.settings.ResourceDirectory
import org.churchpresenter.settings.resourceDirectories
import java.io.File

@Composable
internal fun PresentationTabScope.PresentationLibraryFilters(documents: List<File>) {
    val settings = appSettings.presentationSettings
    val root = File(appSettings.presentationStorageDirectory)
    val roots = appSettings.resourceDirectories(ResourceDirectory.PRESENTATIONS).map(::File)
        PresentationScreenChoice()
        QuickPlayChoice(stringResource(Res.string.hymn_quick_format), settings.quickPlayFormat,
            listOf("pptx", "pdf", "docx")) { format ->
            onSettingsChange { it.copy(presentationSettings = it.presentationSettings.copy(quickPlayFormat = format)) }
        }
        val all = stringResource(Res.string.hymn_quick_all)
        val books = documents.map { HymnQuickPlay.book(it, root, roots) }.distinct().sorted()
        QuickPlayChoice(stringResource(Res.string.hymn_quick_book), settings.quickPlayBook.ifEmpty { all },
            listOf(all) + books.filter { it.isNotEmpty() }) { book ->
            onSettingsChange {
                it.copy(presentationSettings = it.presentationSettings.copy(
                    quickPlayBook = book.takeUnless { it == all }.orEmpty(),
                ))
            }
        }
}

@Composable
private fun QuickPlayChoice(label: String, selected: String, choices: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.padding(vertical = 2.dp)) {
            Text("$label · $selected")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onSelect(choice) })
            }
        }
    }
}
