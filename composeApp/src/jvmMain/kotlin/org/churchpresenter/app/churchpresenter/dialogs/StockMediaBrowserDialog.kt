package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.stock_photo_browse_photos_title
import churchpresenter.composeapp.generated.resources.stock_photo_browse_videos_title
import churchpresenter.composeapp.generated.resources.stock_photo_browse_tooltip
import churchpresenter.composeapp.generated.resources.stock_photo_download_error_generic
import churchpresenter.composeapp.generated.resources.stock_photo_download_error_network
import churchpresenter.composeapp.generated.resources.stock_photo_error_generic
import churchpresenter.composeapp.generated.resources.stock_photo_error_invalid_key
import churchpresenter.composeapp.generated.resources.stock_photo_error_network
import churchpresenter.composeapp.generated.resources.stock_photo_error_rate_limited
import churchpresenter.composeapp.generated.resources.stock_photo_get_key
import churchpresenter.composeapp.generated.resources.stock_photo_get_key_hint
import churchpresenter.composeapp.generated.resources.stock_photo_key_required_hint
import churchpresenter.composeapp.generated.resources.stock_photo_load_more
import churchpresenter.composeapp.generated.resources.stock_photo_no_results
import churchpresenter.composeapp.generated.resources.stock_photo_pexels_key_label
import churchpresenter.composeapp.generated.resources.stock_photo_pixabay_key_label
import churchpresenter.composeapp.generated.resources.stock_photo_search_placeholder_photo
import churchpresenter.composeapp.generated.resources.stock_photo_search_placeholder_video
import churchpresenter.composeapp.generated.resources.stock_photo_source_pexels
import churchpresenter.composeapp.generated.resources.stock_photo_source_pixabay
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.churchpresenter.app.churchpresenter.viewmodel.StockDownloadError
import org.churchpresenter.app.churchpresenter.viewmodel.StockMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.StockSearchError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.net.URI

/**
 * Lets the user search Pexels/Pixabay and download a photo or video directly into
 * the background file pickers. Each source gets its own tab with its own API key
 * field and search results, backed by its own [StockMediaViewModel] so switching
 * tabs preserves each source's search state.
 */
@Composable
fun StockMediaBrowserDialog(
    mediaType: StockMediaClient.StockMediaType,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onMediaDownloaded: (filePath: String) -> Unit
) {
    val pexelsViewModel = remember { StockMediaViewModel(mediaType, StockMediaClient.StockSource.PEXELS) }
    val pixabayViewModel = remember { StockMediaViewModel(mediaType, StockMediaClient.StockSource.PIXABAY) }
    DisposableEffect(Unit) {
        onDispose {
            pexelsViewModel.dispose()
            pixabayViewModel.dispose()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }

    val titleRes = if (mediaType == StockMediaClient.StockMediaType.PHOTO) {
        Res.string.stock_photo_browse_photos_title
    } else {
        Res.string.stock_photo_browse_videos_title
    }
    val searchPlaceholderRes = if (mediaType == StockMediaClient.StockMediaType.PHOTO) {
        Res.string.stock_photo_search_placeholder_photo
    } else {
        Res.string.stock_photo_search_placeholder_video
    }

    val onDownloadedAndClose: (String) -> Unit = { path ->
        onMediaDownloaded(path)
        onDismiss()
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
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(Res.string.stock_photo_source_pexels)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(Res.string.stock_photo_source_pixabay)) }
                    )
                }
                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedTab == 0) {
                        StockSourcePane(
                            viewModel = pexelsViewModel,
                            apiKey = pexelsApiKey,
                            onApiKeyChange = onPexelsApiKeyChange,
                            keyLabel = stringResource(Res.string.stock_photo_pexels_key_label),
                            sourceLabel = stringResource(Res.string.stock_photo_source_pexels),
                            signupUrl = "https://www.pexels.com/api/",
                            searchPlaceholder = stringResource(searchPlaceholderRes),
                            onMediaDownloaded = onDownloadedAndClose
                        )
                    } else {
                        StockSourcePane(
                            viewModel = pixabayViewModel,
                            apiKey = pixabayApiKey,
                            onApiKeyChange = onPixabayApiKeyChange,
                            keyLabel = stringResource(Res.string.stock_photo_pixabay_key_label),
                            sourceLabel = stringResource(Res.string.stock_photo_source_pixabay),
                            signupUrl = "https://pixabay.com/api/docs/",
                            searchPlaceholder = stringResource(searchPlaceholderRes),
                            onMediaDownloaded = onDownloadedAndClose
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
private fun StockSourcePane(
    viewModel: StockMediaViewModel,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    keyLabel: String,
    sourceLabel: String,
    signupUrl: String,
    searchPlaceholder: String,
    onMediaDownloaded: (filePath: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ApiKeyField(
            modifier = Modifier.fillMaxWidth(0.5f),
            label = keyLabel,
            value = apiKey,
            onValueChange = onApiKeyChange,
            signupUrl = signupUrl
        )
        Spacer(Modifier.height(12.dp))

        if (apiKey.isBlank()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.stock_photo_key_required_hint, sourceLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SettingsTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    placeholder = { Text(searchPlaceholder) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(apiKey) })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.search(apiKey) }) {
                    Icon(Icons.Default.Search, contentDescription = searchPlaceholder)
                }
            }
            Spacer(Modifier.height(12.dp))

            viewModel.searchError?.let { error ->
                Text(
                    text = stringResource(searchErrorStringRes(error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }
            viewModel.downloadError?.let { error ->
                val res = if (error == StockDownloadError.NETWORK_ERROR) {
                    Res.string.stock_photo_download_error_network
                } else {
                    Res.string.stock_photo_download_error_generic
                }
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    viewModel.isLoading && viewModel.items.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    viewModel.items.isEmpty() && viewModel.query.isNotBlank() && !viewModel.isLoading -> {
                        Text(
                            text = stringResource(Res.string.stock_photo_no_results),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(viewModel.items, key = { it.id }) { item ->
                                StockMediaThumbnail(
                                    item = item,
                                    isDownloading = viewModel.downloadingId == item.id,
                                    onDownload = {
                                        viewModel.download(item, onMediaDownloaded)
                                    }
                                )
                            }
                            if (viewModel.hasMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (viewModel.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        } else {
                                            TextButton(onClick = { viewModel.loadMore(apiKey) }) {
                                                Text(stringResource(Res.string.stock_photo_load_more))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockMediaThumbnail(
    item: StockMediaClient.StockMediaItem,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id) {
        val bytes = StockMediaClient.fetchThumbnailBytes(item.thumbnailUrl)
        bitmap = bytes?.let {
            try {
                SkiaImage.makeFromEncoded(it).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val downloadTooltip = stringResource(Res.string.stock_photo_browse_tooltip)

    Box(
        modifier = Modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(20.dp))
        }

        if (item.isVideo) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }

        IconButton(
            onClick = onDownload,
            enabled = !isDownloading,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Default.Download, contentDescription = downloadTooltip)
            }
        }
    }
}

private fun searchErrorStringRes(error: StockSearchError): StringResource = when (error) {
    StockSearchError.INVALID_KEY -> Res.string.stock_photo_error_invalid_key
    StockSearchError.RATE_LIMITED -> Res.string.stock_photo_error_rate_limited
    StockSearchError.NETWORK_ERROR -> Res.string.stock_photo_error_network
    StockSearchError.FAILURE -> Res.string.stock_photo_error_generic
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    signupUrl: String,
    modifier: Modifier = Modifier
) {
    var showKey by remember { mutableStateOf(false) }
    val getKeyStr = stringResource(Res.string.stock_photo_get_key)
    val hintStr = stringResource(Res.string.stock_photo_get_key_hint)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SettingsTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.weight(1f),
            fillWidth = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = hintStr,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        ) {
            TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(signupUrl)) } }) {
                Text(getKeyStr, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
