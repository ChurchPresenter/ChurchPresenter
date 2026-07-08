package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.desktop_view
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_left
import churchpresenter.composeapp.generated.resources.ic_arrow_right
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_clear_cache
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_web
import churchpresenter.composeapp.generated.resources.interactive_mode
import churchpresenter.composeapp.generated.resources.mirror_mode
import churchpresenter.composeapp.generated.resources.mobile_view
import churchpresenter.composeapp.generated.resources.web_bookmark_add
import churchpresenter.composeapp.generated.resources.web_bookmark_remove
import churchpresenter.composeapp.generated.resources.web_add_to_schedule
import churchpresenter.composeapp.generated.resources.web_back
import churchpresenter.composeapp.generated.resources.web_engine_unavailable_body
import churchpresenter.composeapp.generated.resources.web_engine_unavailable_title
import churchpresenter.composeapp.generated.resources.web_engine_unavailable_macos_body
import churchpresenter.composeapp.generated.resources.web_engine_unavailable_macos_title
import churchpresenter.composeapp.generated.resources.web_clear_cache
import churchpresenter.composeapp.generated.resources.web_forward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Warning
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.web_go_live
import churchpresenter.composeapp.generated.resources.web_focus_first_input
import churchpresenter.composeapp.generated.resources.web_live_badge
import churchpresenter.composeapp.generated.resources.web_preview_hint
import churchpresenter.composeapp.generated.resources.web_type_to_page_placeholder
import churchpresenter.composeapp.generated.resources.web_refresh
import churchpresenter.composeapp.generated.resources.web_url_hint
import churchpresenter.composeapp.generated.resources.web_zoom_in
import churchpresenter.composeapp.generated.resources.web_zoom_out
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.WebBookmark
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.EmbeddedWebView
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import org.churchpresenter.app.churchpresenter.presenter.WebNavController
import org.churchpresenter.app.churchpresenter.presenter.rememberWebNavController
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebTab(
    modifier: Modifier = Modifier,
    presenterManager: PresenterManager? = null,
    selectedWebsiteItem: ScheduleItem.WebsiteItem? = null,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((url: String, title: String) -> Unit)? = null,
    onUpdateScheduleTitle: ((url: String, title: String) -> Unit)? = null
) {
    // JCEF's native engine can fail to load at startup (broken chrome_elf.dll, missing
    // VC++ runtime, etc.). CefManager.init() catches that and leaves the engine down for
    // the whole session, so show an actionable panel instead of dead browser chrome.
    if (!CefManager.initialized) {
        WebEngineUnavailable(modifier)
        return
    }

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

    // Local buffer for the "Type to page" field shown in live mirror mode.
    // We cannot forward raw keystrokes to the live CefBrowser on macOS/Linux —
    // native event routing drops injected events when the browser window isn't
    // the OS key window. Instead we diff this buffer on every change and inject
    // the delta into the live page via CefBrowser.executeJavaScript, which is an
    // in-process Chromium API that works identically on all platforms.
    var typeBuffer by remember { mutableStateOf("") }
    // Clear the buffer whenever we leave live mode so stale text doesn't
    // re-inject when the user goes live again on a different page.
    LaunchedEffect(isLive) { if (!isLive) typeBuffer = "" }

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

        val hasSecondaryDisplay = rememberScreenDevices().size > 1
        // Web can only go live if at least one regular (non-DeckLink) fill output has showWebsite enabled
        val hasWebCapableOutput = remember(appSettings.projectionSettings) {
            val proj = appSettings.projectionSettings
            val assignments = (0 until proj.screenAssignments.size).map { proj.getAssignment(it) }
            assignments.any { it.targetType != "decklink" && it.targetDisplay >= 0 && it.showWebsite }
        }

        // Shared composables for URL bar and action buttons
        val urlBar: @Composable RowScope.() -> Unit = {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = minUrlWidth)
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_web),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 11.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (urlInput.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.web_url_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                if (urlInput.isNotEmpty() && urlInput != "https://") {
                    IconButton(
                        onClick = { urlInput = "" },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val actionButtons: @Composable RowScope.() -> Unit = {
            // Star bookmark toggle
            ActionIconButton(
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
                enabled = urlInput.isNotBlank() && urlInput != "https://",
                tooltipText = stringResource(if (isBookmarked) Res.string.web_bookmark_remove else Res.string.web_bookmark_add),
                icon = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                containerColor = if (isBookmarked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isBookmarked) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Add to Schedule
            if (onAddToSchedule != null) {
                AddToScheduleButton(
                    onClick = {
                        val url = normaliseUrl(urlInput)
                        val title = pageTitle.ifBlank { url }
                        onAddToSchedule(url, title)
                    },
                    enabled = urlInput.isNotBlank(),
                    tooltipText = stringResource(Res.string.web_add_to_schedule)
                )
            }

            // Go Live
            val goLiveEnabled = urlInput.isNotBlank() && hasSecondaryDisplay && hasWebCapableOutput
            GoLiveButton(
                onClick = {
                    val url = normaliseUrl(urlInput)
                    urlInput = url
                    liveUrl = url
                    presenterManager?.setWebsiteUrl(url)
                    presenterManager?.setPresentingMode(Presenting.WEBSITE)
                },
                enabled = goLiveEnabled,
                tooltipText = stringResource(Res.string.web_go_live)
            )
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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

            // ── "Type to page" input for the live mirror ──
            // Only useful in mirror mode — interactive mode already accepts native typing.
            if (!useInteractivePreview) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            BasicTextField(
                                value = typeBuffer,
                                onValueChange = { next ->
                                    val browser = presenterManager?.liveBrowser?.value
                                    if (browser == null) { typeBuffer = next; return@BasicTextField }
                                    val old = typeBuffer
                                    val common = commonPrefixLength(old, next)
                                    val toDelete = old.length - common
                                    val toInsert = next.substring(common)
                                    repeat(toDelete) { browser.executeJavaScript(JS_BACKSPACE, "", 0) }
                                    toInsert.forEach { ch -> browser.executeJavaScript(jsInsert(ch), "", 0) }
                                    typeBuffer = next
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                            presenterManager?.liveBrowser?.value
                                                ?.executeJavaScript(JS_ENTER, "", 0)
                                            typeBuffer = ""
                                            true
                                        } else false
                                    },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (typeBuffer.isEmpty()) {
                                        Text(
                                            text = stringResource(Res.string.web_type_to_page_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        if (typeBuffer.isNotEmpty()) {
                            IconButton(onClick = { typeBuffer = "" }, modifier = Modifier.size(30.dp)) {
                                Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TooltipIconButton(
                        painter = painterResource(Res.drawable.ic_cast),
                        text = stringResource(Res.string.web_focus_first_input),
                        onClick = {
                            presenterManager?.liveBrowser?.value
                                ?.executeJavaScript(JS_FOCUS_FIRST_INPUT, "", 0)
                        }
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
                                                SwingUtilities.invokeLater {
                                                    try {
                                                        if (!comp.isShowing) return@invokeLater
                                                        sendMouse.invoke(liveBrowser, MouseEvent(
                                                            comp, MouseEvent.MOUSE_ENTERED,
                                                            now, 0, bx, by, 0, false
                                                        ))
                                                        sendMouse.invoke(liveBrowser, MouseEvent(
                                                            comp, MouseEvent.MOUSE_MOVED,
                                                            now, 0, bx, by, 0, false
                                                        ))
                                                        sendMouse.invoke(liveBrowser, MouseEvent(
                                                            comp, MouseEvent.MOUSE_PRESSED,
                                                            now,
                                                            InputEvent.BUTTON1_DOWN_MASK,
                                                            bx, by, 1, false, MouseEvent.BUTTON1
                                                        ))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            PointerEventType.Release -> {
                                                val now = System.currentTimeMillis()
                                                SwingUtilities.invokeLater {
                                                    try {
                                                        if (!comp.isShowing) return@invokeLater
                                                        sendMouse.invoke(liveBrowser, MouseEvent(
                                                            comp, MouseEvent.MOUSE_RELEASED,
                                                            now, 0, bx, by, 1, false, MouseEvent.BUTTON1
                                                        ))
                                                        sendMouse.invoke(liveBrowser, MouseEvent(
                                                            comp, MouseEvent.MOUSE_CLICKED,
                                                            now, 0, bx, by, 1, false, MouseEvent.BUTTON1
                                                        ))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            PointerEventType.Move -> {
                                                val now = System.currentTimeMillis()
                                                if (now - lastMoveTime < 50) continue // Throttle to ~20fps
                                                lastMoveTime = now
                                                SwingUtilities.invokeLater {
                                                    try {
                                                        if (!comp.isShowing) return@invokeLater
                                                        sendMouse.invoke(liveBrowser, MouseEvent(
                                                            comp, MouseEvent.MOUSE_MOVED,
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
                                        SwingUtilities.invokeLater {
                                            try {
                                                if (!comp.isShowing) return@invokeLater
                                                if (vRotation != 0) {
                                                    sendWheel.invoke(liveBrowser, MouseWheelEvent(
                                                        comp, MouseWheelEvent.MOUSE_WHEEL,
                                                        System.currentTimeMillis(), 0, bx, by,
                                                        0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                                        1, vRotation
                                                    ))
                                                }
                                                if (hRotation != 0) {
                                                    sendWheel.invoke(liveBrowser, MouseWheelEvent(
                                                        comp, MouseWheelEvent.MOUSE_WHEEL,
                                                        System.currentTimeMillis(),
                                                        InputEvent.SHIFT_DOWN_MASK,
                                                        bx, by,
                                                        0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
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
                                val sendKey = findMethod(liveBrowser, "sendKeyEvent", AwtKeyEvent::class.java)
                                    ?: return@onKeyEvent false
                                val awtType = when (keyEvent.type) {
                                    KeyEventType.KeyDown -> AwtKeyEvent.KEY_PRESSED
                                    KeyEventType.KeyUp -> AwtKeyEvent.KEY_RELEASED
                                    else -> return@onKeyEvent false
                                }
                                val nativeCode = keyEvent.key.nativeKeyCode
                                val comp = liveBrowser.getUIComponent()
                                if (!comp.isShowing) return@onKeyEvent false
                                val now = System.currentTimeMillis()
                                SwingUtilities.invokeLater {
                                    try {
                                        if (!comp.isShowing) return@invokeLater
                                        sendKey.invoke(liveBrowser, AwtKeyEvent(
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
                        delay(7000)
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

/**
 * Shown in place of the browser when JCEF's native engine failed to load at startup.
 * Points the user at the Microsoft Visual C++ Redistributable, the most common cause.
 */
@Composable
private fun WebEngineUnavailable(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (CefManager.macOsUnsupported) Res.string.web_engine_unavailable_macos_title
                else Res.string.web_engine_unavailable_title
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (CefManager.macOsUnsupported) Res.string.web_engine_unavailable_macos_body
                else Res.string.web_engine_unavailable_body
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
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
    ActionIconButton(
        onClick = {
            val live = presenterManager?.liveBrowser?.value
            if (isLive && !useInteractivePreview && live != null) live.goBack() else navController.goBack()
        },
        tooltipText = stringResource(Res.string.web_back),
        painter = painterResource(Res.drawable.ic_arrow_left),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Forward
    ActionIconButton(
        onClick = {
            val live = presenterManager?.liveBrowser?.value
            if (isLive && !useInteractivePreview && live != null) live.goForward() else navController.goForward()
        },
        tooltipText = stringResource(Res.string.web_forward),
        painter = painterResource(Res.drawable.ic_arrow_right),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Refresh
    ActionIconButton(
        onClick = {
            val live = presenterManager?.liveBrowser?.value
            if (isLive && !useInteractivePreview && live != null) live.reload() else navController.browser?.reload()
        },
        tooltipText = stringResource(Res.string.web_refresh),
        painter = painterResource(Res.drawable.ic_refresh),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Clear cache
    ActionIconButton(
        onClick = {
            val cacheDir = java.io.File(System.getProperty("user.home"), ".churchpresenter/webview-cache")
            if (cacheDir.exists()) cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        },
        tooltipText = stringResource(Res.string.web_clear_cache),
        painter = painterResource(Res.drawable.ic_clear_cache),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )

    // Zoom out
    ActionIconButton(
        onClick = { applyZoom(zoomLevel - 0.5) },
        tooltipText = stringResource(Res.string.web_zoom_out),
        painter = painterResource(Res.drawable.ic_arrow_down),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Zoom percentage
    Text(
        text = "${(Math.pow(1.2, zoomLevel) * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Zoom in
    ActionIconButton(
        onClick = { applyZoom(zoomLevel + 0.5) },
        tooltipText = stringResource(Res.string.web_zoom_in),
        painter = painterResource(Res.drawable.ic_arrow_up),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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

// ─────────────────────────────────────────────────────────────────────────────
// Type-to-page JS helpers.
//
// Rationale: CefBrowser.sendKeyEvent routes through the native OS event system
// (Win32 message queue / NSEvent / X11), which only accepts synthesised keys on
// Windows. On macOS/Linux keystrokes injected from the main window never reach
// the live browser on the secondary display because that window isn't the OS
// "key window". CefBrowser.executeJavaScript runs Chromium's in-process JS
// engine and therefore works identically on all three platforms.
//
// Limitation: the injected edits target document.activeElement. Pages using
// <canvas>-based editors (Google Docs, Figma) that don't use real DOM inputs
// won't receive characters this way — they need raw keystrokes we cannot
// cross-window forward. The JS_FOCUS_FIRST_INPUT helper covers the common case.
// ─────────────────────────────────────────────────────────────────────────────

private fun commonPrefixLength(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

/** Encode a Kotlin [Char] as a JSON string literal, safe to splice into JS. */
private fun jsEncode(ch: Char): String = buildString {
    append('"')
    when (ch) {
        '\\' -> append("\\\\")
        '"'  -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
    }
    append('"')
}

private fun jsInsert(ch: Char): String = """
    (function(ch){
      var el=document.activeElement; if(!el) return;
      if (el.isContentEditable) { document.execCommand('insertText', false, ch); return; }
      if (el.tagName==='INPUT' || el.tagName==='TEXTAREA') {
        var s = el.selectionStart != null ? el.selectionStart : el.value.length;
        var e = el.selectionEnd   != null ? el.selectionEnd   : el.value.length;
        el.setRangeText(ch, s, e, 'end');
        el.dispatchEvent(new InputEvent('input', {data: ch, inputType: 'insertText', bubbles: true}));
      }
    })(${jsEncode(ch)});
""".trimIndent()

private const val JS_BACKSPACE = """
    (function(){
      var el=document.activeElement; if(!el) return;
      if (el.isContentEditable) { document.execCommand('delete', false); return; }
      if (el.tagName==='INPUT' || el.tagName==='TEXTAREA') {
        var s=el.selectionStart, e=el.selectionEnd;
        if (s===e && s>0) { el.setRangeText('', s-1, s, 'end'); }
        else              { el.setRangeText('', s,   e, 'end'); }
        el.dispatchEvent(new InputEvent('input', {inputType: 'deleteContentBackward', bubbles: true}));
      }
    })();
"""

private const val JS_ENTER = """
    (function(){
      var el=document.activeElement; if(!el) return;
      var down = new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true});
      var cancelled = !el.dispatchEvent(down);
      el.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true}));
      if (!cancelled && el.form) {
        if (el.form.requestSubmit) el.form.requestSubmit();
        else el.form.submit();
      }
    })();
"""

private const val JS_FOCUS_FIRST_INPUT = """
    (function(){
      var el=document.querySelector('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]),textarea,[contenteditable=true]');
      if (el) el.focus();
    })();
"""
