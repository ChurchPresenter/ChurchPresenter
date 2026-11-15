package org.churchpresenter.slides.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.select_presentation_file_button
import org.churchpresenter.strings.generated.resources.presentation_documents_folder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PresentationResourceMenu(
    onFiles: () -> Unit,
    onFolder: () -> Unit,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        anchor { expanded = true }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.select_presentation_file_button)) }, onClick = {
                expanded = false
                onFiles()
            })
            DropdownMenuItem(text = { Text(stringResource(Res.string.presentation_documents_folder)) }, onClick = {
                expanded = false
                onFolder()
            })
        }
    }
}
