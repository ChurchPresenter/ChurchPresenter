/*
 * The left half of the Background panel: the Colors / Images / Videos categories and the grid of
 * tiles under them — the design's library, with the app's own stock backgrounds standing where the
 * mockup drew placeholder art.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background_category_cameras
import churchpresenter.composeapp.generated.resources.song_background_browse
import churchpresenter.composeapp.generated.resources.song_background_category_colors
import churchpresenter.composeapp.generated.resources.song_background_category_images
import churchpresenter.composeapp.generated.resources.song_background_category_videos
import churchpresenter.composeapp.generated.resources.song_background_option_count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path

/** The design's library column: the category row, then the only part of the panel that scrolls. */
@Composable
internal fun SongBackgroundLibrary(
    background: SongBackground,
    onChange: (SongBackground) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The cameras to offer, or null to ask this machine. A test passes a list: enumeration shells
     * out to ffmpeg and answers differently on every machine, which is neither fast nor repeatable.
     */
    devices: List<CameraDevice>? = null,
) {
    var category by remember(background.type) { mutableStateOf(categoryOf(background.type)) }
    val entries = rememberMediaEntries(category)
    val cameras = rememberCameras(category, devices)
    val count = when (category) {
        SongBackgroundType.COLOR -> SONG_BACKGROUND_COLORS.size
        SongBackgroundType.CAMERA -> cameras.size
        else -> entries.size + 1
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CategoryPill(stringResource(Res.string.song_background_category_colors),
                category == SongBackgroundType.COLOR) { category = SongBackgroundType.COLOR }
            CategoryPill(stringResource(Res.string.song_background_category_images),
                category == SongBackgroundType.IMAGE) { category = SongBackgroundType.IMAGE }
            CategoryPill(stringResource(Res.string.song_background_category_videos),
                category == SongBackgroundType.VIDEO) { category = SongBackgroundType.VIDEO }
            CategoryPill(stringResource(Res.string.song_background_category_cameras),
                category == SongBackgroundType.CAMERA) { category = SongBackgroundType.CAMERA }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.song_background_option_count, count),
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TILE_MIN_WIDTH),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (category == SongBackgroundType.COLOR) {
                items(SONG_BACKGROUND_COLORS) { entry ->
                    SwatchTile(
                        label = stringResource(entry.label),
                        selected = entry.selects(background, SONG_BACKGROUND_NAMED_COLORS),
                        badge = if (entry.own) SwatchBadge.PLUS else SwatchBadge.NONE,
                        onClick = { onChange(entry.applyTo(background)) },
                    ) {
                        ColorTileFill(entry, background)
                    }
                }
            } else if (category == SongBackgroundType.CAMERA) {
                cameraTiles(cameras, background, onChange)
            } else {
                mediaTiles(category, entries, background, onChange)
            }
        }
    }
}

/** The tiles for Images and Videos: the stock library, with Browse… as the last tile. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.mediaTiles(
    category: String,
    entries: List<LibraryEntry>,
    background: SongBackground,
    onChange: (SongBackground) -> Unit,
) {
    val isVideo = category == SongBackgroundType.VIDEO
    val chosen = if (isVideo) background.video else background.image
    items(entries) { entry ->
        val scope = rememberCoroutineScope()
        val path = (entry as? DownloadedEntry)?.file?.absolutePath
        SwatchTile(
            label = entry.name,
            selected = path != null && path == chosen,
            badge = if (isVideo) SwatchBadge.PLAY else SwatchBadge.NONE,
            onClick = {
                scope.launch {
                    val file = when (entry) {
                        is DownloadedEntry -> entry.file
                        is BundledEntry -> materializeBundledEntry(entry.name)
                    }
                    onChange(mediaBackground(background, isVideo, file.absolutePath))
                }
            },
        ) {
            MediaTileFill(entry, isVideo)
        }
    }
    item {
        val scope = rememberCoroutineScope()
        val label = stringResource(Res.string.song_background_browse)
        val browsedIsChosen = chosen.isNotBlank() && entries.none {
            (it as? DownloadedEntry)?.file?.absolutePath == chosen
        }
        SwatchTile(
            label = if (browsedIsChosen) File(chosen).name else label,
            selected = browsedIsChosen,
            badge = SwatchBadge.PLUS,
            onClick = {
                scope.launch {
                    browseForMedia(isVideo, label)?.let { onChange(mediaBackground(background, isVideo, it)) }
                }
            },
        ) {
            if (browsedIsChosen && !isVideo) {
                MediaTileFill(DownloadedEntry(File(chosen)), false)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}

@Composable
private fun ColorTileFill(entry: ColorSwatchDef, background: SongBackground) {
    val modifier = Modifier.fillMaxSize()
    when {
        entry.own -> Box(modifier.background(parseHexColor(background.color)))
        entry.gradient -> Box(
            modifier.background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(parseHexColor(entry.color), parseHexColor(entry.colorEnd.orEmpty()))
                )
            )
        )
        else -> Box(modifier.background(parseHexColor(entry.color)))
    }
}

@Composable
private fun MediaTileFill(entry: LibraryEntry, isVideo: Boolean) {
    if (isVideo) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    var bitmap by remember(entry.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.key) { bitmap = withContext(Dispatchers.IO) { loadThumbnailBitmap(entry) } }
    val shot = bitmap
    if (shot == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
    } else {
        Image(
            bitmap = shot,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The stock library's entries for [category], bundled ones included. */
@Composable
private fun rememberMediaEntries(category: String): List<LibraryEntry> {
    if (category == SongBackgroundType.COLOR) return emptyList()
    val mediaType = if (category == SongBackgroundType.VIDEO) StockMediaClient.StockMediaType.VIDEO
                    else StockMediaClient.StockMediaType.PHOTO
    val downloaded = remember(category) {
        scanDownloadedFiles(File(System.getProperty("user.home"), STOCK_BACKGROUNDS_DIR), mediaType)
    }
    var bundled by remember(category) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(category) { bundled = loadBundledFileNames(mediaType) }
    return remember(downloaded, bundled) { libraryEntries(downloaded, bundled, "") }
}

/** Which category tab a background of [type] belongs to; a gradient is a colour. */
private fun categoryOf(type: String): String = when (type) {
    SongBackgroundType.IMAGE -> SongBackgroundType.IMAGE
    SongBackgroundType.VIDEO -> SongBackgroundType.VIDEO
    SongBackgroundType.CAMERA -> SongBackgroundType.CAMERA
    else -> SongBackgroundType.COLOR
}

/** [background] pointing at [path], as a picture or a clip. */
private fun mediaBackground(background: SongBackground, isVideo: Boolean, path: String): SongBackground =
    if (isVideo) background.copy(type = SongBackgroundType.VIDEO, video = path)
    else background.copy(type = SongBackgroundType.IMAGE, image = path)

private suspend fun browseForMedia(isVideo: Boolean, filterLabel: String): String? {
    val filter = if (isVideo) {
        FileNameExtensionFilter(filterLabel, "mp4", "mov", "avi", "mkv", "webm")
    } else {
        FileNameExtensionFilter(filterLabel, "jpg", "jpeg", "png", "gif", "bmp", "webp")
    }
    return FileChooser.platformInstance.chooseSingle(
        path = Path(System.getProperty("user.home")),
        filters = listOf(filter),
        title = "",
        selectDirectory = false,
    )?.toString()
}

private val TILE_MIN_WIDTH = 82.dp
private const val STOCK_BACKGROUNDS_DIR = ".churchpresenter/stock-backgrounds"
