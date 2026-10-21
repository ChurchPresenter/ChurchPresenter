package org.churchpresenter.appsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.RaisedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_song_samples
import org.churchpresenter.strings.generated.resources.bible_catalog_button
import org.churchpresenter.strings.generated.resources.browse_directory
import org.churchpresenter.strings.generated.resources.bible_catalog_folder_not_writable
import org.churchpresenter.strings.generated.resources.convert
import org.churchpresenter.strings.generated.resources.detected_files_label
import org.churchpresenter.strings.generated.resources.detected_files_less
import org.churchpresenter.strings.generated.resources.detected_files_more
import org.churchpresenter.strings.generated.resources.file_not_supported_short
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_download
import org.churchpresenter.icons.generated.resources.ic_warning
import org.churchpresenter.strings.generated.resources.no_files_detected
import org.churchpresenter.strings.generated.resources.scanning_directory
import org.churchpresenter.strings.generated.resources.song_folder_with_count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.composables.ScanningRow
import org.churchpresenter.bibletab.catalog.BibleCatalogBrowserDialog
import org.churchpresenter.profiles.FileManager
import org.churchpresenter.bibleformats.catalog.BibleInstallSupport
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

/**
 * The indented strip under a folder's row, where that folder reports on its own contents: what it
 * holds under the path field, its [action] under the Browse button ([actionStart], measured from
 * the row; until it is known the action simply follows the chips), and anything [below] both.
 */
@Composable
private fun StorageDetail(
    actionStart: Dp?,
    action: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
    contents: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = STORAGE_DETAIL_INDENT, end = 16.dp, bottom = 11.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(STORAGE_ROW_GAP)) {
            val width = actionStart?.let {
                (it + STORAGE_ROW_PADDING - STORAGE_DETAIL_INDENT - STORAGE_ROW_GAP).coerceAtLeast(0.dp)
            }
            Box(
                modifier = (if (width != null) Modifier.width(width) else Modifier.weight(1f, fill = false))
                    .heightIn(min = DETAIL_ACTION_HEIGHT),
                contentAlignment = Alignment.CenterStart,
            ) { contents() }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { action() }
        }
        below()
    }
}

/** A row's Browse button, which reports where it starts so the detail strip below can line up with it. */
@Composable
internal fun BrowseButton(onClick: () -> Unit, onPlaced: (Dp) -> Unit) {
    val density = LocalDensity.current
    RaisedButton(
        onClick = onClick,
        modifier = Modifier
            .height(CONTROL_HEIGHT)
            .onPlaced { onPlaced(with(density) { it.positionInParent().x.toDp() }) },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = AppShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(text = stringResource(Res.string.browse_directory), style = MaterialTheme.typography.labelMedium)
    }
}

/** The way out of an empty folder — fetch a Bible, drop in the sample songs. */
@Composable
private fun DetailAction(
    text: String,
    icon: DrawableResource,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // A raised accent button: in a strip of detected-file chips it is the one thing to press.
    RaisedButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShape(9.dp),
        contentPadding = PaddingValues(horizontal = 13.dp),
        modifier = modifier.height(DETAIL_ACTION_HEIGHT),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 7.dp),
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

@Composable
internal fun BibleStorageDetail(
    directory: String,
    files: MutableState<List<String>?>,
    actionStart: Dp?,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    if (directory.isEmpty()) return
    val found = files.value
    if (found == null) {
        StorageDetail(actionStart) { ScanningRow(stringResource(Res.string.scanning_directory)) }
        return
    }
    var showCatalog by remember(directory) { mutableStateOf(false) }
    var notWritable by remember(directory) { mutableStateOf(false) }
    StorageDetail(
        actionStart = actionStart,
        action = {
            // Offered only once a real folder is in place: downloads are written the moment they
            // finish, so there must be no doubt about where they are going.
            if (java.io.File(directory).isDirectory) {
                DetailAction(
                    stringResource(Res.string.bible_catalog_button),
                    IconRes.drawable.ic_download,
                    modifier = Modifier.guideTarget(GuideTargets.BIBLE_DOWNLOAD),
                ) {
                    // Probed on click, not on composition: it writes a file, and the answer can
                    // change while the settings are open (a drive unplugged, permissions fixed).
                    notWritable = !BibleInstallSupport.usableDirectory(java.io.File(directory))
                    showCatalog = !notWritable
                }
            }
            if (notWritable) {
                Text(
                    text = stringResource(Res.string.bible_catalog_folder_not_writable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        CollapsibleChipRow(
            chips = found,
            resetKey = directory,
            lead = { DetectedLabel(empty = found.isEmpty()) },
        )
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

@Composable
internal fun SongsStorageDetail(directory: String, scan: SongScan, actionStart: Dp?) {
    if (directory.isEmpty()) return
    if (scan.scanning) {
        StorageDetail(actionStart) { ScanningRow(stringResource(Res.string.scanning_directory)) }
        return
    }
    val samplesScope = rememberCoroutineScope()
    var copyingSamples by remember { mutableStateOf(false) }
    val samplePrompts = songSamplePrompts()
    val folderChips = scan.folders.map { (name, count) ->
        stringResource(Res.string.song_folder_with_count, name, count)
    }
    StorageDetail(
        actionStart = actionStart,
        action = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailAction(
                    stringResource(Res.string.add_song_samples),
                    IconRes.drawable.ic_add,
                    enabled = !copyingSamples,
                ) {
                    if (samplePrompts.confirmOverwrite(directory)) {
                        copyingSamples = true
                        samplesScope.launch {
                            val count = withContext(Dispatchers.IO) { copySongSamples(directory) }
                            copyingSamples = false
                            afterDispatch { samplePrompts.reportCopied(count) }
                        }
                    }
                }
                if (copyingSamples) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        },
        below = { if (scan.unsupported.isNotEmpty()) UnsupportedSongFiles(directory, scan.unsupported) },
    ) {
        CollapsibleChipRow(
            chips = folderChips,
            resetKey = directory,
            lead = { DetectedLabel(empty = scan.folders.isEmpty() && scan.unsupported.isEmpty()) },
        )
    }
}

/**
 * The old `.sps` libraries in the songs folder, a row each: the first [UNSUPPORTED_SHOWN], then a
 * toggle for the rest, so a folder of dozens does not push the other storage rows off the screen.
 */
@Composable
private fun UnsupportedSongFiles(directory: String, files: List<String>) {
    var expanded by remember(directory) { mutableStateOf(false) }
    val shown = if (expanded) files else files.take(UNSUPPORTED_SHOWN)
    Column(
        modifier = Modifier.padding(top = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        shown.forEach { UnsupportedSongFileRow(directory, it) }
        if (files.size > UNSUPPORTED_SHOWN) {
            if (expanded) {
                MoreLessToggle(stringResource(Res.string.detected_files_less), expanded = true) { expanded = false }
            } else {
                MoreLessToggle(
                    stringResource(Res.string.detected_files_more, files.size - UNSUPPORTED_SHOWN),
                    expanded = false,
                ) { expanded = true }
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
            .clip(AppShape(7.dp))
            .background(MaterialTheme.semantic.warningContainer)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_warning),
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
            RaisedButton(
                onClick = {
                    if (!prompts.confirmOverwrite(directory, fileName)) return@RaisedButton
                    converting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SpsConverter().convertSpsToSongFiles(
                                java.io.File(directory, fileName).absolutePath,
                                directory,
                            )
                        }
                        converting = false
                        afterDispatch { prompts.report(result) }
                    }
                },
                modifier = Modifier.height(22.dp),
                shape = AppShape(6.dp),
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

/** How many unsupported files show before the rest fold behind a toggle. */
private const val UNSUPPORTED_SHOWN = 3

/** The detail action's height, which is also the least height of the strip beside it. */
private val DETAIL_ACTION_HEIGHT = 30.dp
