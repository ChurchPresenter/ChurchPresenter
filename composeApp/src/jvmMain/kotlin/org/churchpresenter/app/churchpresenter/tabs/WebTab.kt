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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.WebBookmark
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.EmbeddedWebView
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.awt.GraphicsEnvironment
import org.churchpresenter.app.churchpresenter.presenter.WebNavController
import org.churchpresenter.app.churchpresenter.presenter.rememberWebNavController
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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

    // Zoom level (0.0 = 100%, each ±1.0 ≈ 1.2x scale change)
    var zoomLevel by remember { mutableStateOf(0.0) }
    var isMobileView by remember { mutableStateOf(false) }

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

    // Sync URL bar from presenter when the presenter navigates (Mirror mode clicks)
    val presenterUrl = presenterManager?.websiteUrl?.value ?: ""
    val presenterTitle = presenterManager?.webPageTitle?.value ?: ""
    LaunchedEffect(presenterUrl) {
        if (isLive && !useInteractivePreview && presenterUrl.isNotBlank()) {
            urlInput = presenterUrl
            liveUrl = presenterUrl
        }
    }
    LaunchedEffect(presenterTitle) {
        if (isLive && !useInteractivePreview && presenterTitle.isNotBlank()) {
            pageTitle = presenterTitle
            if (liveUrl.isNotBlank()) onUpdateScheduleTitle?.invoke(liveUrl, presenterTitle)
        }
    }

    // Apply zoom level when presenter browser becomes available
    val liveBrowserRef = presenterManager?.liveBrowser?.value
    LaunchedEffect(liveBrowserRef) {
        if (liveBrowserRef != null && isLive) {
            liveBrowserRef.setZoomLevel(zoomLevel)
        }
    }

    // Keep the presenter in sync whenever the preview navigates to a new page
    fun onPreviewNavigated(newUrl: String) {
        urlInput = newUrl
        liveUrl = newUrl
        presenterManager?.setWebsiteUrl(newUrl)
        // Directly navigate the presenter browser so it updates immediately
        if (isLive) {
            presenterManager?.liveBrowser?.value?.loadURL(newUrl)
        }
    }

    fun onTitleChanged(title: String) {
        pageTitle = title
        presenterManager?.setWebPageTitle(title)
        // Update the schedule item title if it was added before the page finished loading
        if (liveUrl.isNotBlank()) onUpdateScheduleTitle?.invoke(liveUrl, title)
    }

    val navController = rememberWebNavController()

    fun applyZoom(level: Double) {
        zoomLevel = level
        val browser = if (isLive && !useInteractivePreview)
            presenterManager?.liveBrowser?.value else navController.browser
        browser?.setZoomLevel(level)
    }

    val bookmarks = appSettings.webBookmarks
    val currentUrlNormalised = normaliseUrl(urlInput)
    val isBookmarked = bookmarks.any { it.url == currentUrlNormalised }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Toolbar: nav + URL + actions (1 or 2 rows based on width) ──
        // Approximate width consumed by nav buttons + zoom + desktop toggle + action buttons
        val navButtonsWidth = 440.dp   // 4 icon buttons + zoom controls + desktop toggle
        val actionButtonsWidth = 320.dp // bookmark + Add to Schedule + Go Live
        val minUrlWidth = 200.dp

        val hasSecondaryDisplay = remember {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size > 1
        }

        // Shared composables for URL bar and action buttons
        val urlBar: @Composable RowScope.() -> Unit = {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = minUrlWidth)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            val url = normaliseUrl(urlInput)
                            urlInput = url
                            liveUrl = url
                            presenterManager?.setWebsiteUrl(url)
                            if (isLive) {
                                presenterManager?.liveBrowser?.value?.loadURL(url)
                            }
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
        }

        val actionButtons: @Composable RowScope.() -> Unit = {
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
                enabled = urlInput.isNotBlank() && hasSecondaryDisplay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(Res.string.web_go_live), style = MaterialTheme.typography.labelMedium)
            }
        }

        val onMobileToggle: (Boolean) -> Unit = { mobile ->
            isMobileView = mobile
            navController.setMobileEmulation(mobile)
            // Also toggle on the live browser if presenting
            if (isLive) {
                presenterManager?.liveBrowser?.value?.let { liveBrowser ->
                    // The live browser uses a separate NavController, so override UA + reload directly
                    liveBrowser.reload()
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val singleRow = maxWidth >= navButtonsWidth + minUrlWidth + actionButtonsWidth

            if (singleRow) {
                // Everything on one line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavButtons(navController, presenterManager, isLive, useInteractivePreview,
                        zoomLevel, isMobileView, ::applyZoom, onMobileToggle)
                    urlBar()
                    actionButtons()
                }
            } else {
                // Two rows: nav + actions on top, URL bar below
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavButtons(navController, presenterManager, isLive, useInteractivePreview,
                            zoomLevel, isMobileView, ::applyZoom, onMobileToggle)
                        Spacer(Modifier.weight(1f))
                        actionButtons()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        urlBar()
                    }
                }
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
                            presenterManager?.setWebsiteUrl(bookmark.url)
                            if (isLive) {
                                presenterManager?.liveBrowser?.value?.loadURL(bookmark.url)
                            }
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
                        text = stringResource(if (useInteractivePreview) Res.string.interactive_mode else Res.string.mirror_mode),
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
                                // Forward mouse events via CefBrowser_N.sendMouseEvent (reflection)
                                if (liveBrowser == null) return@pointerInput
                                val sendMouse = findMethod(liveBrowser, "sendMouseEvent", java.awt.event.MouseEvent::class.java)
                                var lastMoveTime = 0L
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (sendMouse == null) continue
                                        if (imageSize.width <= 0 || imageSize.height <= 0) continue
                                        val comp = liveBrowser.getUIComponent()
                                        if (!comp.isShowing || comp.width <= 0 || comp.height <= 0) continue
                                        val scaleX = comp.width.toFloat() / imageSize.width
                                        val scaleY = comp.height.toFloat() / imageSize.height
                                        val pos = event.changes.firstOrNull()?.position ?: continue
                                        val bx = (pos.x * scaleX).toInt().coerceIn(0, comp.width - 1)
                                        val by = (pos.y * scaleY).toInt().coerceIn(0, comp.height - 1)
                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                val now = System.currentTimeMillis()
                                                javax.swing.SwingUtilities.invokeLater {
                                                    try {
                                                        if (!comp.isShowing) return@invokeLater
                                                        sendMouse.invoke(liveBrowser, java.awt.event.MouseEvent(
                                                            comp, java.awt.event.MouseEvent.MOUSE_ENTERED,
                                                            now, 0, bx, by, 0, false
                                                        ))
                                                        sendMouse.invoke(liveBrowser, java.awt.event.MouseEvent(
                                                            comp, java.awt.event.MouseEvent.MOUSE_MOVED,
                                                            now, 0, bx, by, 0, false
                                                        ))
                                                        sendMouse.invoke(liveBrowser, java.awt.event.MouseEvent(
                                                            comp, java.awt.event.MouseEvent.MOUSE_PRESSED,
                                                            now,
                                                            java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                                                            bx, by, 1, false, java.awt.event.MouseEvent.BUTTON1
                                                        ))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            PointerEventType.Release -> {
                                                val now = System.currentTimeMillis()
                                                javax.swing.SwingUtilities.invokeLater {
                                                    try {
                                                        if (!comp.isShowing) return@invokeLater
                                                        sendMouse.invoke(liveBrowser, java.awt.event.MouseEvent(
                                                            comp, java.awt.event.MouseEvent.MOUSE_RELEASED,
                                                            now, 0, bx, by, 1, false, java.awt.event.MouseEvent.BUTTON1
                                                        ))
                                                        sendMouse.invoke(liveBrowser, java.awt.event.MouseEvent(
                                                            comp, java.awt.event.MouseEvent.MOUSE_CLICKED,
                                                            now, 0, bx, by, 1, false, java.awt.event.MouseEvent.BUTTON1
                                                        ))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            PointerEventType.Move -> {
                                                val now = System.currentTimeMillis()
                                                if (now - lastMoveTime < 50) continue // Throttle to ~20fps
                                                lastMoveTime = now
                                                javax.swing.SwingUtilities.invokeLater {
                                                    try {
                                                        if (!comp.isShowing) return@invokeLater
                                                        sendMouse.invoke(liveBrowser, java.awt.event.MouseEvent(
                                                            comp, java.awt.event.MouseEvent.MOUSE_MOVED,
                                                            now, 0, bx, by, 0, false
                                                        ))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            else -> continue
                                        }
                                    }
                                }
                            }
                            .pointerInput(liveBrowser) {
                                // Forward scroll via CefBrowser_N.sendMouseWheelEvent (reflection)
                                if (liveBrowser == null) return@pointerInput
                                val sendWheel = findMethod(liveBrowser, "sendMouseWheelEvent", java.awt.event.MouseWheelEvent::class.java)
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type != PointerEventType.Scroll) continue
                                        if (sendWheel == null) continue
                                        val comp = liveBrowser.getUIComponent()
                                        if (!comp.isShowing || comp.width <= 0 || comp.height <= 0) continue
                                        if (imageSize.width <= 0 || imageSize.height <= 0) continue
                                        val scaleX = comp.width.toFloat() / imageSize.width
                                        val scaleY = comp.height.toFloat() / imageSize.height
                                        val change = event.changes.firstOrNull() ?: continue
                                        val pos = change.position
                                        val scroll = change.scrollDelta
                                        val bx = (pos.x * scaleX).toInt().coerceIn(0, comp.width - 1)
                                        val by = (pos.y * scaleY).toInt().coerceIn(0, comp.height - 1)
                                        val vRotation = -(scroll.y * 15).toInt().coerceIn(-100, 100)
                                        val hRotation = -(scroll.x * 15).toInt().coerceIn(-100, 100)
                                        if (vRotation == 0 && hRotation == 0) continue
                                        javax.swing.SwingUtilities.invokeLater {
                                            try {
                                                if (!comp.isShowing) return@invokeLater
                                                if (vRotation != 0) {
                                                    sendWheel.invoke(liveBrowser, java.awt.event.MouseWheelEvent(
                                                        comp, java.awt.event.MouseWheelEvent.MOUSE_WHEEL,
                                                        System.currentTimeMillis(), 0, bx, by,
                                                        0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                                        1, vRotation
                                                    ))
                                                }
                                                if (hRotation != 0) {
                                                    sendWheel.invoke(liveBrowser, java.awt.event.MouseWheelEvent(
                                                        comp, java.awt.event.MouseWheelEvent.MOUSE_WHEEL,
                                                        System.currentTimeMillis(),
                                                        java.awt.event.InputEvent.SHIFT_DOWN_MASK,
                                                        bx, by,
                                                        0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                                        1, hRotation
                                                    ))
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (liveBrowser == null) return@onKeyEvent false
                                val sendKey = findMethod(liveBrowser, "sendKeyEvent", java.awt.event.KeyEvent::class.java)
                                    ?: return@onKeyEvent false
                                val awtType = when (keyEvent.type) {
                                    KeyEventType.KeyDown -> java.awt.event.KeyEvent.KEY_PRESSED
                                    KeyEventType.KeyUp -> java.awt.event.KeyEvent.KEY_RELEASED
                                    else -> return@onKeyEvent false
                                }
                                val nativeCode = keyEvent.key.nativeKeyCode
                                val comp = liveBrowser.getUIComponent()
                                if (!comp.isShowing) return@onKeyEvent false
                                val now = System.currentTimeMillis()
                                javax.swing.SwingUtilities.invokeLater {
                                    try {
                                        if (!comp.isShowing) return@invokeLater
                                        sendKey.invoke(liveBrowser, java.awt.event.KeyEvent(
                                            comp, awtType, now, 0,
                                            nativeCode, nativeCode.toChar()
                                        ))
                                    } catch (_: Exception) {}
                                }
                                true
                            },
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Show spinner while waiting for first snapshot; after 3s show help text
                    var showHint by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(7000)
                        showHint = true
                    }
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            if (showHint) {
                                Spacer(Modifier.height(12.dp))
                                if (System.getProperty("os.name", "").lowercase().contains("mac")) {
                                    Text(
                                        "If this persists, grant Screen Recording permission\nin System Settings > Privacy & Security > Screen Recording",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        "Waiting for snapshot...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (liveUrl.isNotBlank()) {
                EmbeddedWebView(
                    url = liveUrl,
                    modifier = Modifier.fillMaxSize(),
                    onUrlChanged = { newUrl -> onPreviewNavigated(newUrl) },
                    onTitleChanged = { title -> onTitleChanged(title) },
                    navController = navController,
                    onBrowserCreated = { browser -> browser.setZoomLevel(zoomLevel) }
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

@Composable
private fun RowScope.NavButtons(
    navController: WebNavController,
    presenterManager: PresenterManager?,
    isLive: Boolean,
    useInteractivePreview: Boolean,
    zoomLevel: Double,
    isMobileView: Boolean,
    applyZoom: (Double) -> Unit,
    onMobileToggle: (Boolean) -> Unit
) {
    // Back
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_arrow_left),
        text = stringResource(Res.string.web_back),
        onClick = {
            val live = presenterManager?.liveBrowser?.value
            if (isLive && !useInteractivePreview && live != null) live.goBack() else navController.goBack()
        },
        iconTint = MaterialTheme.colorScheme.onSurface
    )
    // Forward
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_arrow_right),
        text = stringResource(Res.string.web_forward),
        onClick = {
            val live = presenterManager?.liveBrowser?.value
            if (isLive && !useInteractivePreview && live != null) live.goForward() else navController.goForward()
        },
        iconTint = MaterialTheme.colorScheme.onSurface
    )
    // Refresh
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_refresh),
        text = stringResource(Res.string.web_refresh),
        onClick = {
            val live = presenterManager?.liveBrowser?.value
            if (isLive && !useInteractivePreview && live != null) live.reload() else navController.browser?.reload()
        },
        iconTint = MaterialTheme.colorScheme.onSurface
    )
    // Clear cache
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_clear_cache),
        text = stringResource(Res.string.web_clear_cache),
        onClick = {
            val cacheDir = java.io.File(System.getProperty("user.home"), ".churchpresenter/webview-cache")
            if (cacheDir.exists()) cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        },
        iconTint = MaterialTheme.colorScheme.error
    )

    // Zoom out
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_arrow_down),
        text = stringResource(Res.string.web_zoom_out),
        onClick = { applyZoom(zoomLevel - 0.5) },
        iconTint = MaterialTheme.colorScheme.onSurface,
        buttonSize = 32.dp,
        iconSize = 16.dp
    )
    // Zoom percentage
    Text(
        text = "${(Math.pow(1.2, zoomLevel) * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Zoom in
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_arrow_up),
        text = stringResource(Res.string.web_zoom_in),
        onClick = { applyZoom(zoomLevel + 0.5) },
        iconTint = MaterialTheme.colorScheme.onSurface,
        buttonSize = 32.dp,
        iconSize = 16.dp
    )
    // Mobile / Desktop toggle
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isMobileView) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onMobileToggle(!isMobileView) }
    ) {
        Text(
            text = stringResource(if (isMobileView) Res.string.mobile_view else Res.string.desktop_view),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** Walk up the class hierarchy to find a declared method and make it accessible. */
private fun findMethod(obj: Any, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
    var c: Class<*>? = obj.javaClass
    while (c != null) {
        try {
            val m = c.getDeclaredMethod(name, *paramTypes)
            m.isAccessible = true
            return m
        } catch (_: NoSuchMethodException) { c = c.superclass }
    }
    return null
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
