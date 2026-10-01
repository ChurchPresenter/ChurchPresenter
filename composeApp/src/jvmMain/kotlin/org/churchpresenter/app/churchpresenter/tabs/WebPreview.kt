package org.churchpresenter.app.churchpresenter.tabs

import java.awt.Component
import java.lang.reflect.Method
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.web_preview_hint
import org.churchpresenter.strings.generated.resources.web_snapshot_screen_recording_hint
import org.churchpresenter.strings.generated.resources.web_snapshot_waiting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.presenter.EmbeddedWebView
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.jetbrains.compose.resources.stringResource
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.State
import org.cef.browser.CefBrowser

/** The output picker and the preview: the live mirror, the embedded browser, or a hint. */
@Composable
internal fun WebTabScope.WebPreviewCard(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
        // ── Preview WebView ────────────────────────────────────────────────
        PreviewOutputPicker(
            settings = appSettings,
            tabId = Constants.PREVIEW_TAB_WEB,
            mode = Presenting.WEBSITE,
            onSettingsChange = onSettingsChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        // Fit preview to remaining space while keeping the output's aspect ratio
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
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
                    shape = AppShape(4.dp)
                )
        ) {
            if (isLive && !useInteractivePreview) {
                WebMirrorPreview()
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

/** Mirror mode: the presenter's screenshot, with input forwarded to the live browser. */
@Composable
private fun WebTabScope.WebMirrorPreview() {
    val webSnapshot = presenterManager?.webSnapshot?.value
    val liveBrowser = presenterManager?.liveBrowser?.value
    if (webSnapshot != null) {
        WebSnapshotImage(webSnapshot, liveBrowser)
    } else {
        WebSnapshotWaiting()
    }
}

@Composable
private fun WebSnapshotImage(webSnapshot: ImageBitmap, liveBrowser: CefBrowser?) {
    val imageSizeState = remember { mutableStateOf(IntSize.Zero) }
    var imageSize by imageSizeState
    Image(
        bitmap = webSnapshot,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { imageSize = it }
            .forwardMouse(liveBrowser, imageSizeState)
            .forwardWheel(liveBrowser, imageSizeState)
            .forwardKeys(liveBrowser),
        contentScale = ContentScale.Fit
    )
}

/** Forwards presses, releases and throttled moves to the live browser. */
private fun Modifier.forwardMouse(liveBrowser: CefBrowser?, imageSizeState: State<IntSize>): Modifier =
    pointerInput(liveBrowser) {
        val imageSize by imageSizeState
        // Forward mouse events via CefBrowser_N.sendMouseEvent (reflection)
        if (liveBrowser == null) return@pointerInput
        val sendMouse = findMethod(liveBrowser, "sendMouseEvent", MouseEvent::class.java)
        var lastMoveTime = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val comp = liveBrowser.getUIComponent()
                val pos = event.changes.firstOrNull()?.position
                val compReady = comp.isShowing && comp.width > 0 && comp.height > 0
                val sizeReady = imageSize.width > 0 && imageSize.height > 0
                val ready = compReady && sizeReady
                if (sendMouse == null || pos == null || !ready) continue
                val scaleX = comp.width.toFloat() / imageSize.width
                val scaleY = comp.height.toFloat() / imageSize.height
                val bx = (pos.x * scaleX).toInt().coerceIn(0, comp.width - 1)
                val by = (pos.y * scaleY).toInt().coerceIn(0, comp.height - 1)
                when (event.type) {
                    PointerEventType.Press -> {
                        val now = System.currentTimeMillis()
                        sendMouseLater(sendMouse, liveBrowser, comp) {
                            listOf(
                                MouseEvent(comp, MouseEvent.MOUSE_ENTERED, now, 0, bx, by, 0, false),
                                MouseEvent(comp, MouseEvent.MOUSE_MOVED, now, 0, bx, by, 0, false),
                                MouseEvent(
                                    comp, MouseEvent.MOUSE_PRESSED,
                                    now,
                                    InputEvent.BUTTON1_DOWN_MASK,
                                    bx, by, 1, false, MouseEvent.BUTTON1
                                ),
                            )
                        }
                    }
                    PointerEventType.Release -> {
                        val now = System.currentTimeMillis()
                        sendMouseLater(sendMouse, liveBrowser, comp) {
                            listOf(
                                MouseEvent(
                                    comp, MouseEvent.MOUSE_RELEASED,
                                    now, 0, bx, by, 1, false, MouseEvent.BUTTON1
                                ),
                                MouseEvent(
                                    comp, MouseEvent.MOUSE_CLICKED,
                                    now, 0, bx, by, 1, false, MouseEvent.BUTTON1
                                ),
                            )
                        }
                    }
                    PointerEventType.Move -> {
                        val now = System.currentTimeMillis()
                        // Throttle to ~20fps
                        if (now - lastMoveTime >= WEB_MOUSE_MOVE_THROTTLE_MS) {
                            lastMoveTime = now
                            sendMouseLater(sendMouse, liveBrowser, comp) {
                                listOf(MouseEvent(comp, MouseEvent.MOUSE_MOVED, now, 0, bx, by, 0, false))
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

/** Sends the mouse [events] to the live browser on the AWT thread, while its component is still showing. */
private fun sendMouseLater(
    sendMouse: Method,
    liveBrowser: CefBrowser,
    comp: Component,
    events: () -> List<MouseEvent>,
) {
    SwingUtilities.invokeLater {
        try {
            if (!comp.isShowing) return@invokeLater
            events().forEach { sendMouse.invoke(liveBrowser, it) }
        } catch (_: Exception) {}
    }
}

private fun Modifier.forwardWheel(liveBrowser: CefBrowser?, imageSizeState: State<IntSize>): Modifier =
    pointerInput(liveBrowser) {
        val imageSize by imageSizeState
        // Forward scroll via CefBrowser_N.sendMouseWheelEvent (reflection)
        if (liveBrowser == null) return@pointerInput
        val sendWheel = findMethod(liveBrowser, "sendMouseWheelEvent", MouseWheelEvent::class.java)
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val comp = liveBrowser.getUIComponent()
                val change = event.changes.firstOrNull()
                val compReady = comp.isShowing && comp.width > 0 && comp.height > 0
                val sizeReady = imageSize.width > 0 && imageSize.height > 0
                val ready = compReady && sizeReady &&
                    event.type == PointerEventType.Scroll
                if (sendWheel == null || change == null || !ready) continue
                val scaleX = comp.width.toFloat() / imageSize.width
                val scaleY = comp.height.toFloat() / imageSize.height
                val pos = change.position
                val scroll = change.scrollDelta
                val bx = (pos.x * scaleX).toInt().coerceIn(0, comp.width - 1)
                val by = (pos.y * scaleY).toInt().coerceIn(0, comp.height - 1)
                val vRotation = -(scroll.y * 15).toInt().coerceIn(-100, 100)
                val hRotation = -(scroll.x * 15).toInt().coerceIn(-100, 100)
                if (vRotation != 0 || hRotation != 0) {
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
    }

/** Forwards key presses and releases, through CefBrowser_N.sendKeyEvent. */
private fun Modifier.forwardKeys(liveBrowser: CefBrowser?): Modifier =
    onKeyEvent { keyEvent ->
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
    }

/** A spinner while the first snapshot is awaited, and after a while a hint about why it may not come. */
@Composable
private fun WebSnapshotWaiting() {
    // Show spinner while waiting for first snapshot; after 3s show help text
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WEB_SNAPSHOT_RETRY_DELAY_MS)
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
                        stringResource(Res.string.web_snapshot_screen_recording_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        stringResource(Res.string.web_snapshot_waiting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
