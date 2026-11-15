package org.churchpresenter.slides.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.churchpresenter.slides.viewmodel.PresentationDocumentDiscovery
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.presentation_documents_scanning
import org.churchpresenter.strings.generated.resources.presentation_documents_empty
import org.churchpresenter.strings.generated.resources.presentation_documents_word_renderer
import org.jetbrains.compose.resources.stringResource
import java.io.File

private const val DOCUMENT_RESCAN_INTERVAL_MS = 30_000L

@Composable
internal fun PresentationTabScope.PresentationDocuments(viewModel: PresentationViewModel) {
    val directories = remember(appSettings.presentationStorageDirectory) {
        listOf(appSettings.presentationStorageDirectory).filter { it.isNotBlank() }
    }
    val documents = viewModel.discoveredDocuments
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(directories, viewModel.documentRefreshToken) {
        do {
            scanning = true
            try {
                viewModel.discoveredDocuments = withContext(Dispatchers.IO) {
                    PresentationDocumentDiscovery.discover(directories.map(::File))
                }
            } finally {
                scanning = false
            }
            delay(DOCUMENT_RESCAN_INTERVAL_MS)
        } while (isActive)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        PresentationQuickPlay(viewModel, documents) {
            Text(
                directories.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (viewModel.selectedPresentation?.extension?.lowercase() in PresentationLoader.WORD_EXTENSIONS) {
                Text(
                    stringResource(Res.string.presentation_documents_word_renderer),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (scanning || documents.isEmpty()) {
                val status = if (scanning) {
                    Res.string.presentation_documents_scanning
                } else {
                    Res.string.presentation_documents_empty
                }
                Text(stringResource(status), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
