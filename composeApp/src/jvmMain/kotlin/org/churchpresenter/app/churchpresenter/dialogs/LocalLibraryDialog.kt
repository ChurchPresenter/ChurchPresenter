package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.stock_library_empty
import org.churchpresenter.resources.generated.resources.stock_library_search_placeholder
import org.churchpresenter.resources.generated.resources.stock_library_title_photos
import org.churchpresenter.resources.generated.resources.stock_library_title_videos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.ui.SettingsTextField
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

private const val THUMB_ASPECT_W = 4f
private const val THUMB_ASPECT_H = 3f

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")
private const val BUNDLED_BACKGROUNDS_PATH = "files/backgrounds"

/** Tags the decoded thumbnail [Image] so tests can wait for the async load to finish. */
internal const val LIBRARY_THUMBNAIL_IMAGE_TAG = "library_thumbnail_image"

/** An entry in the local library grid — either a file the user already downloaded, or one shipped with the app. */
internal sealed interface LibraryEntry {
    val name: String
    val key: String
}
internal data class DownloadedEntry(val file: File) : LibraryEntry {
    override val name get() = file.name
    override val key get() = file.absolutePath
}
internal data class BundledEntry(override val name: String) : LibraryEntry {
    override val key get() = "bundled:$name"
}

/** Combines downloaded and bundled entries (bundled ones already downloaded under the same name are
 * hidden), then applies the search filter. */
internal fun libraryEntries(
    downloadedFiles: List<File>,
    bundledFileNames: List<String>,
    searchQuery: String,
): List<LibraryEntry> {
    val downloadedNames = downloadedFiles.map { it.name }.toSet()
    val downloaded: List<LibraryEntry> = downloadedFiles.map { DownloadedEntry(it) }
    val bundled: List<LibraryEntry> = bundledFileNames
        .filter { it !in downloadedNames }
        .sorted()
        .map { BundledEntry(it) }
    val combined = downloaded + bundled
    return if (searchQuery.isBlank()) combined else combined.filter { it.name.contains(searchQuery, ignoreCase = true) }
}

/** Lists the user's already-downloaded stock files of [mediaType] from [storageDir], newest first. */
internal fun scanDownloadedFiles(storageDir: File, mediaType: StockMediaClient.StockMediaType): List<File> {
    val extensions = if (mediaType == StockMediaClient.StockMediaType.PHOTO) IMAGE_EXTENSIONS else VIDEO_EXTENSIONS
    return storageDir.listFiles { file -> file.isFile && file.extension.lowercase() in extensions }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

/** Names of the bundled backgrounds shipped with the app for [mediaType] (only photos ship any; videos never do). */
internal suspend fun loadBundledFileNames(mediaType: StockMediaClient.StockMediaType): List<String> {
    if (mediaType != StockMediaClient.StockMediaType.PHOTO) return emptyList()
    return try {
        Res.readBytes("$BUNDLED_BACKGROUNDS_PATH/index.txt")
            .toString(Charsets.UTF_8)
            .lines()
            .filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Copies a bundled background out of app resources into the stock library folder the first time it's picked. */
internal suspend fun materializeBundledEntry(fileName: String): File = withContext(Dispatchers.IO) {
    val dir = File(System.getProperty("user.home"), ".churchpresenter/stock-backgrounds")
    dir.mkdirs()
    val target = File(dir, fileName)
    if (!target.exists()) {
        target.writeBytes(Res.readBytes("$BUNDLED_BACKGROUNDS_PATH/$fileName"))
    }
    target
}

/**
 * Lets the user pick a previously downloaded stock photo/video from the app's local
 * library (~/.churchpresenter/stock-backgrounds/) without hitting the network again.
 */
@Composable
fun LocalLibraryDialog(
    mediaType: StockMediaClient.StockMediaType,
    onDismiss: () -> Unit,
    onMediaSelected: (filePath: String) -> Unit
) {
    val titleRes = if (mediaType == StockMediaClient.StockMediaType.PHOTO) {
        Res.string.stock_library_title_photos
    } else {
        Res.string.stock_library_title_videos
    }

    val allFiles = remember(mediaType) {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/stock-backgrounds")
        scanDownloadedFiles(dir, mediaType)
    }

    var bundledFileNames by remember(mediaType) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(mediaType) {
        bundledFileNames = loadBundledFileNames(mediaType)
    }

    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, 1100.dp, 800.dp),
        width = 1100.dp,
        height = 800.dp
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(titleRes),
        resizable = true
    ) {
        LocalLibraryDialogContent(
            mediaType = mediaType,
            downloadedFiles = allFiles,
            bundledFileNames = bundledFileNames,
            onDismiss = onDismiss,
            onMediaSelected = onMediaSelected,
        )
    }
}

@Composable
internal fun LocalLibraryDialogContent(
    mediaType: StockMediaClient.StockMediaType,
    downloadedFiles: List<File>,
    bundledFileNames: List<String>,
    onDismiss: () -> Unit,
    onMediaSelected: (filePath: String) -> Unit
) {
    val titleRes = if (mediaType == StockMediaClient.StockMediaType.PHOTO) {
        Res.string.stock_library_title_photos
    } else {
        Res.string.stock_library_title_videos
    }

    var searchQuery by remember { mutableStateOf("") }
    val entries: List<LibraryEntry> = remember(downloadedFiles, bundledFileNames, searchQuery) {
        libraryEntries(downloadedFiles, bundledFileNames, searchQuery)
    }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SettingsTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.stock_library_search_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.stock_library_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            LibraryThumbnail(
                                entry = entry,
                                isVideo = mediaType == StockMediaClient.StockMediaType.VIDEO,
                                onClick = {
                                    when (entry) {
                                        is DownloadedEntry -> {
                                            onMediaSelected(entry.file.absolutePath)
                                            onDismiss()
                                        }
                                        is BundledEntry -> coroutineScope.launch {
                                            val file = materializeBundledEntry(entry.name)
                                            onMediaSelected(file.absolutePath)
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = gridState)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        }
    }
}

/** Decodes an entry's thumbnail image, or `null` if the file is missing/corrupt/unreadable. */
internal suspend fun loadThumbnailBitmap(entry: LibraryEntry): ImageBitmap? = try {
    val bytes = when (entry) {
        is DownloadedEntry -> entry.file.readBytes()
        is BundledEntry -> Res.readBytes("$BUNDLED_BACKGROUNDS_PATH/${entry.name}")
    }
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

@Composable
private fun LibraryThumbnail(
    entry: LibraryEntry,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    var bitmap by remember(entry.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.key) {
        if (!isVideo) {
            bitmap = withContext(Dispatchers.IO) { loadThumbnailBitmap(entry) }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(THUMB_ASPECT_W / THUMB_ASPECT_H)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        when {
            isVideo -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag(LIBRARY_THUMBNAIL_IMAGE_TAG)
                )
            }
            else -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(20.dp))
            }
        }
    }
}
