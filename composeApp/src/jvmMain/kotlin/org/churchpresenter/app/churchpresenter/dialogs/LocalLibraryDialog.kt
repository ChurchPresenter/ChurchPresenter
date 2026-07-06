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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.stock_library_empty
import churchpresenter.composeapp.generated.resources.stock_library_search_placeholder
import churchpresenter.composeapp.generated.resources.stock_library_title_photos
import churchpresenter.composeapp.generated.resources.stock_library_title_videos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")
private const val BUNDLED_BACKGROUNDS_PATH = "files/backgrounds"

/** An entry in the local library grid — either a file the user already downloaded, or one shipped with the app. */
private sealed interface LibraryEntry {
    val name: String
    val key: String
}
private data class DownloadedEntry(val file: File) : LibraryEntry {
    override val name get() = file.name
    override val key get() = file.absolutePath
}
private data class BundledEntry(override val name: String) : LibraryEntry {
    override val key get() = "bundled:$name"
}

/** Copies a bundled background out of app resources into the stock library folder the first time it's picked. */
private suspend fun materializeBundledEntry(fileName: String): File = withContext(Dispatchers.IO) {
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
        val extensions = if (mediaType == StockMediaClient.StockMediaType.PHOTO) IMAGE_EXTENSIONS else VIDEO_EXTENSIONS
        val dir = File(System.getProperty("user.home"), ".churchpresenter/stock-backgrounds")
        dir.listFiles { file -> file.isFile && file.extension.lowercase() in extensions }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    var bundledFileNames by remember(mediaType) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(mediaType) {
        bundledFileNames = if (mediaType == StockMediaClient.StockMediaType.PHOTO) {
            try {
                Res.readBytes("$BUNDLED_BACKGROUNDS_PATH/index.txt")
                    .toString(Charsets.UTF_8)
                    .lines()
                    .filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val entries: List<LibraryEntry> = remember(allFiles, bundledFileNames, searchQuery) {
        val downloadedNames = allFiles.map { it.name }.toSet()
        val downloaded: List<LibraryEntry> = allFiles.map { DownloadedEntry(it) }
        val bundled: List<LibraryEntry> = bundledFileNames
            .filter { it !in downloadedNames }
            .sorted()
            .map { BundledEntry(it) }
        val combined = downloaded + bundled
        if (searchQuery.isBlank()) {
            combined
        } else {
            combined.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

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
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val bytes = when (entry) {
                        is DownloadedEntry -> entry.file.readBytes()
                        is BundledEntry -> Res.readBytes("$BUNDLED_BACKGROUNDS_PATH/${entry.name}")
                    }
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(4f / 3f)
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
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(20.dp))
            }
        }
    }
}
