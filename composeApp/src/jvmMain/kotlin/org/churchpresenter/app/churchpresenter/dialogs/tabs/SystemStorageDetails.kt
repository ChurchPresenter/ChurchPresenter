package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_song_samples
import churchpresenter.composeapp.generated.resources.bible_catalog_button
import churchpresenter.composeapp.generated.resources.convert
import churchpresenter.composeapp.generated.resources.detected_files_label
import churchpresenter.composeapp.generated.resources.file_not_supported_short
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_download
import churchpresenter.composeapp.generated.resources.ic_warning
import churchpresenter.composeapp.generated.resources.no_files_detected
import churchpresenter.composeapp.generated.resources.scanning_directory
import churchpresenter.composeapp.generated.resources.song_folder_with_count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.ScanningRow
import org.churchpresenter.app.churchpresenter.data.SpsConverter
import org.churchpresenter.app.churchpresenter.dialogs.BibleCatalogBrowserDialog
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.withInstalledBible
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Bibles in the chosen folder, or null while the scan is still running.
 *
 * The distinction is the whole point: the scan runs on Dispatchers.IO and takes seconds against a
 * network share, and rendering "no files detected" for that whole time reads as a verdict rather
 * than a wait — someone who has just picked the right folder is told, in red, that it is empty.
 * Mutable because installing a Bible from the catalogue adds to it without a rescan.
 */
@Composable
internal fun rememberBibleFiles(fileManager: FileManager, directory: String): MutableState<List<String>?> {
    val files = remember(directory) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(directory) {
        files.value = if (directory.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { fileManager.getBibleFilesInDirectory(directory) }
        }
    }
    return files
}

/** What is in the songs folder: the songbook folders, and the old `.sps` files that are not usable yet. */
internal class SongScan(
    val scanning: Boolean,
    val folders: List<Pair<String, Int>>,
    val unsupported: List<String>,
)

@Composable
internal fun rememberSongScan(fileManager: FileManager, directory: String): SongScan {
    var scan by remember(directory) {
        mutableStateOf(SongScan(scanning = directory.isNotEmpty(), folders = emptyList(), unsupported = emptyList()))
    }
    LaunchedEffect(directory) {
        if (directory.isEmpty()) {
            scan = SongScan(scanning = false, folders = emptyList(), unsupported = emptyList())
            return@LaunchedEffect
        }
        scan = SongScan(scanning = true, folders = emptyList(), unsupported = emptyList())
        try {
            // Two scans, one verdict: "no songs here" is only true once BOTH have landed, so the
            // spinner stays up across the pair rather than flickering between them.
            val sps = withContext(Dispatchers.IO) { fileManager.getSongFilesInDirectory(directory) }
            val folders = withContext(Dispatchers.IO) { fileManager.getSongFoldersInDirectory(directory) }
            scan = SongScan(scanning = false, folders = folders, unsupported = sps)
        } finally {
            // finally, so a cancelled or failed scan does not leave the spinner up forever.
            if (scan.scanning) scan = SongScan(scanning = false, folders = emptyList(), unsupported = emptyList())
        }
    }
    return scan
}

/** The indented strip under a folder's row, where that folder reports on its own contents. */
@Composable
private fun StorageDetail(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = STORAGE_DETAIL_INDENT, end = 16.dp, bottom = 11.dp),
    ) { content() }
}

/** A file the folder holds, named as it is on disk. */
@Composable
private fun DetectedChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The way out of an empty folder — fetch a Bible, drop in the sample songs. */
@Composable
private fun DetailAction(text: String, icon: DrawableResource, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 11.dp),
        modifier = Modifier.height(26.dp),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** What was found, or that nothing was — the label every detail strip opens with. */
@Composable
private fun DetectedLabel(empty: Boolean) {
    Text(
        text = if (empty) {
            stringResource(Res.string.no_files_detected)
        } else {
            stringResource(Res.string.detected_files_label)
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (empty) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BibleStorageDetail(
    directory: String,
    files: MutableState<List<String>?>,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    if (directory.isEmpty()) return
    val found = files.value
    StorageDetail {
        if (found == null) {
            ScanningRow(stringResource(Res.string.scanning_directory))
            return@StorageDetail
        }
        var showCatalog by remember(directory) { mutableStateOf(false) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DetectedLabel(empty = found.isEmpty())
            found.forEach { DetectedChip(it) }
            // Offered only once a real folder is in place: downloads are written the moment they
            // finish, so there must be no doubt about where they are going.
            if (java.io.File(directory).isDirectory) {
                DetailAction(stringResource(Res.string.bible_catalog_button), Res.drawable.ic_download) {
                    showCatalog = true
                }
            }
        }
        if (showCatalog) {
            BibleCatalogBrowserDialog(
                storageDirectory = directory,
                onDismiss = { showCatalog = false },
                onBibleInstalled = { fileName ->
                    onSettingsChange { s -> s.withInstalledBible(fileName) }
                    files.value = found + fileName
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongsStorageDetail(directory: String, scan: SongScan) {
    if (directory.isEmpty()) return
    StorageDetail {
        if (scan.scanning) {
            ScanningRow(stringResource(Res.string.scanning_directory))
            return@StorageDetail
        }
        val samplesScope = rememberCoroutineScope()
        var copyingSamples by remember { mutableStateOf(false) }
        val samplePrompts = songSamplePrompts()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DetectedLabel(empty = scan.folders.isEmpty() && scan.unsupported.isEmpty())
            scan.folders.forEach { (name, count) ->
                DetectedChip(stringResource(Res.string.song_folder_with_count, name, count))
            }
            DetailAction(stringResource(Res.string.add_song_samples), Res.drawable.ic_add, enabled = !copyingSamples) {
                if (samplePrompts.confirmOverwrite(directory)) {
                    copyingSamples = true
                    samplesScope.launch {
                        val count = withContext(Dispatchers.IO) { copySongSamples(directory) }
                        copyingSamples = false
                        samplePrompts.reportCopied(count)
                    }
                }
            }
            if (copyingSamples) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        if (scan.unsupported.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                scan.unsupported.forEach { UnsupportedSongFileRow(directory, it) }
            }
        }
    }
}

/** An old `.sps` library sitting in the songs folder, and the one click that turns it into songs. */
@Composable
private fun UnsupportedSongFileRow(directory: String, fileName: String) {
    val scope = rememberCoroutineScope()
    var converting by remember(fileName) { mutableStateOf(false) }
    val prompts = conversionPrompts()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.semantic.warningContainer)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.semantic.onWarningContainer,
        )
        Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.semantic.onWarningContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.file_not_supported_short),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (converting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Button(
                onClick = {
                    if (!prompts.confirmOverwrite(directory, fileName)) return@Button
                    converting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SpsConverter().convertSpsToSongFiles(
                                java.io.File(directory, fileName).absolutePath,
                                directory,
                            )
                        }
                        converting = false
                        prompts.report(result)
                    }
                },
                modifier = Modifier.height(22.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.convert),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
