package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.WebBookmark
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.EmbeddedWebView
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.WebNavController
import org.churchpresenter.app.churchpresenter.presenter.rememberWebNavController
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities

@Composable
fun WebTab(
    modifier: Modifier = Modifier,
    presenterManager: PresenterManager? = null,
    selectedWebsiteItem: org.churchpresenter.app.churchpresenter.models.ScheduleItem.WebsiteItem? = null,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((url: String, title: String) -> Unit)? = null,
    onUpdateScheduleTitle: ((url: String, title: String) -> Unit)? = null
) {
    val previewAspectRatio = remember { presenterAspectRatio() }

    // Restore URL / title from PresenterManager so state survives tab switches
    val savedUrl = presenterManager?.websiteUrl?.value ?: ""
    val savedTitle = presenterManager?.webPageTitle?.value ?: ""

    // Current URL typed by the user
    var urlInput by remember { mutableStateOf(savedUrl.ifBlank { "https://" }) }
    // URL actually loaded in the preview / sent live
    var liveUrl by remember { mutableStateOf(savedUrl) }
    var pageTitle by remember { mutableStateOf(savedTitle) }

    // Derive isLive from the presenter mode — clears automatically on "Clear Display"
    val presentingMode = presenterManager?.presentingMode?.value ?: Presenting.NONE
    val isLive = presentingMode == Presenting.WEBSITE

    // Toggle between screenshot mirror (matched layout) and interactive local browser
    var useInteractivePreview by remember { mutableStateOf(false) }

    // Clear snapshot when no longer live
    LaunchedEffect(isLive) {
        if (!isLive) presenterManager?.setWebSnapshot(null)
    }

    // When a schedule item selects this tab, restore its URL and go live
    LaunchedEffect(selectedWebsiteItem) {
        selectedWebsiteItem?.let { item ->
            urlInput = item.url
            liveUrl = item.url
            pageTitle = item.title
            presenterManager?.setWebsiteUrl(item.url)
            presenterManager?.setWebPageTitle(item.title)
            presenterManager?.setPresentingMode(Presenting.WEBSITE)
        }
    }

    // Keep the presenter in sync whenever the preview navigates to a new page
    fun onPreviewNavigated(newUrl: String) {
        urlInput = newUrl
        presenterManager?.setWebsiteUrl(newUrl)
    }

    fun onTitleChanged(title: String) {
        pageTitle = title
        presenterManager?.setWebPageTitle(title)
        // Update the schedule item title if it was added before the page finished loading
        if (liveUrl.isNotBlank()) onUpdateScheduleTitle?.invoke(liveUrl, title)
    }

    val navController = rememberWebNavController()

    val bookmarks = appSettings.webBookmarks
    val currentUrlNormalised = normaliseUrl(urlInput)
    val isBookmarked = bookmarks.any { it.url == currentUrlNormalised }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── URL input row ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(onClick = { navController.goBack() }) {
                Text("<", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            // Forward
            IconButton(onClick = { navController.goForward() }) {
                Text(">", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            // Refresh
            IconButton(onClick = { navController.browser?.reload() }) {
                Text("R", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            // Clear cache
            IconButton(onClick = {
                val cacheDir = java.io.File(System.getProperty("user.home"), ".churchpresenter/webview-cache")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }) {
                Text("X", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            val url = normaliseUrl(urlInput)
                            urlInput = url
                            liveUrl = url
                            true
                        } else false
                    },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.web_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                label = { Text(stringResource(Res.string.web_url_label)) },
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Star bookmark toggle
            IconButton(
                onClick = {
                    val url = normaliseUrl(urlInput)
                    if (isBookmarked) {
                        onSettingsChange { s ->
                            s.copy(webBookmarks = s.webBookmarks.filter { it.url != url })
                        }
                    } else {
                        val title = pageTitle.ifBlank { url }
                        onSettingsChange { s ->
                            s.copy(webBookmarks = s.webBookmarks + WebBookmark(url = url, title = title))
                        }
                    }
                },
                enabled = urlInput.isNotBlank() && urlInput != "https://"
            ) {
                Text(
                    text = if (isBookmarked) "\u2605" else "\u2606",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isBookmarked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Add to Schedule
            if (onAddToSchedule != null) {
                Button(
                    onClick = {
                        val url = normaliseUrl(urlInput)
                        val title = pageTitle.ifBlank { url }
                        onAddToSchedule(url, title)
                    },
                    enabled = urlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(Res.string.web_add_to_schedule), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Go Live
            Button(
                onClick = {
                    val url = normaliseUrl(urlInput)
                    urlInput = url
                    liveUrl = url
                    presenterManager?.setWebsiteUrl(url)
                    presenterManager?.setPresentingMode(Presenting.WEBSITE)
                },
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(Res.string.web_go_live), style = MaterialTheme.typography.labelMedium)
            }

        }

        // ── Horizontal bookmarks bar ───────────────────────────────────────
        if (bookmarks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bookmarks.forEach { bookmark ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (liveUrl == bookmark.url) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clickable {
                            urlInput = bookmark.url
                            liveUrl = bookmark.url
                            pageTitle = bookmark.title
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = bookmark.title.ifBlank { bookmark.url },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                            Text(
                                text = "\u2715",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.clickable {
                                    onSettingsChange { s ->
                                        s.copy(webBookmarks = s.webBookmarks.filter { it.url != bookmark.url })
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Live badge + preview mode toggle ──────────────────────────────
        if (isLive) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = stringResource(Res.string.web_live_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (pageTitle.isNotBlank()) pageTitle else liveUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                // Toggle between screenshot mirror and interactive preview
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (useInteractivePreview) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { useInteractivePreview = !useInteractivePreview }
                ) {
                    Text(
                        text = if (useInteractivePreview) "Interactive" else "Mirror",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Preview WebView ────────────────────────────────────────────────
        // Fit preview to remaining space while keeping presenter aspect ratio
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Pick the largest size that fits both width and height constraints
            val maxW = maxWidth
            val maxH = maxHeight
            val fitByWidth = maxW
            val fitByWidthH = maxW / previewAspectRatio
            val (w, h) = if (fitByWidthH <= maxH) {
                fitByWidth to fitByWidthH
            } else {
                maxH * previewAspectRatio to maxH
            }
        Box(
            modifier = Modifier
                .size(w, h)
                .border(
                    width = if (isLive) 2.dp else 1.dp,
                    color = if (isLive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            if (isLive && !useInteractivePreview) {
                // Mirror mode: show presenter screenshot with input forwarding
                val webSnapshot = presenterManager?.webSnapshot?.value
                val liveBrowser = presenterManager?.liveBrowser?.value
                if (webSnapshot != null) {
                    var imageSize by remember { mutableStateOf(IntSize.Zero) }
                    Image(
                        bitmap = webSnapshot,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { imageSize = it }
                            .pointerInput(liveBrowser) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val comp = liveBrowser?.getUIComponent() ?: continue
                                        if (imageSize.width <= 0 || imageSize.height <= 0) continue
                                        val scaleX = comp.width.toFloat() / imageSize.width
                                        val scaleY = comp.height.toFloat() / imageSize.height
                                        val pos = event.changes.firstOrNull()?.position ?: continue
                                        val bx = (pos.x * scaleX).toInt()
                                        val by = (pos.y * scaleY).toInt()
                                        val awtId = when (event.type) {
                                            PointerEventType.Press -> MouseEvent.MOUSE_PRESSED
                                            PointerEventType.Release -> MouseEvent.MOUSE_RELEASED
                                            PointerEventType.Move -> MouseEvent.MOUSE_MOVED
                                            else -> continue
                                        }
                                        SwingUtilities.invokeLater {
                                            comp.dispatchEvent(
                                                MouseEvent(
                                                    comp, awtId, System.currentTimeMillis(),
                                                    0, bx, by, 1, false
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            .pointerInput(liveBrowser) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type != PointerEventType.Scroll) continue
                                        val comp = liveBrowser?.getUIComponent() ?: continue
                                        if (imageSize.width <= 0 || imageSize.height <= 0) continue
                                        val scaleX = comp.width.toFloat() / imageSize.width
                                        val scaleY = comp.height.toFloat() / imageSize.height
                                        val change = event.changes.firstOrNull() ?: continue
                                        val pos = change.position
                                        val scroll = change.scrollDelta
                                        SwingUtilities.invokeLater {
                                            comp.dispatchEvent(
                                                MouseWheelEvent(
                                                    comp, MouseWheelEvent.MOUSE_WHEEL,
                                                    System.currentTimeMillis(), 0,
                                                    (pos.x * scaleX).toInt(), (pos.y * scaleY).toInt(),
                                                    0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                                    3, scroll.y.toInt()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                val comp = liveBrowser?.getUIComponent() ?: return@onKeyEvent false
                                val awtId = when (keyEvent.type) {
                                    KeyEventType.KeyDown -> java.awt.event.KeyEvent.KEY_PRESSED
                                    KeyEventType.KeyUp -> java.awt.event.KeyEvent.KEY_RELEASED
                                    else -> return@onKeyEvent false
                                }
                                val nativeCode = keyEvent.key.nativeKeyCode
                                SwingUtilities.invokeLater {
                                    comp.dispatchEvent(
                                        java.awt.event.KeyEvent(
                                            comp, awtId, System.currentTimeMillis(), 0,
                                            nativeCode, nativeCode.toChar()
                                        )
                                    )
                                }
                                true
                            },
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (liveUrl.isNotBlank()) {
                EmbeddedWebView(
                    url = liveUrl,
                    modifier = Modifier.fillMaxSize(),
                    onUrlChanged = { newUrl -> onPreviewNavigated(newUrl) },
                    onTitleChanged = { title -> onTitleChanged(title) },
                    navController = navController
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.web_preview_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }
}

/** Prepend https:// if the user forgot the scheme. */
private fun normaliseUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.isNotBlank() -> "https://$trimmed"
        else -> trimmed
    }
}
