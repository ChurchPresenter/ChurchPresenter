package org.churchpresenter.web.tabs

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
import org.churchpresenter.web.presenter.EmbeddedWebView
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.State
import org.cef.browser.CefBrowser
import org.churchpresenter.sharedui.composables.bibleListCard

/** The output picker and the preview: the live mirror, the embedded browser, or a hint. */
@Composable
internal fun WebTabScope.WebPreviewCard(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
        // ── Preview WebView ────────────────────────────────────────────────
        outputPicker(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
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
    val webSnapshot = output?.webSnapshot?.value
    val liveBrowser = output?.liveBrowser?.value
    if (webSnapshot != null) {
        WebSnapshotImage(webSnapshot, liveBrowser)
    } else {
        WebSnapshotWaiting()
    }
}

@Composable
private fun WebSnapshotImage(webSnapshot: ImageBitmap, liveBrowser: CefBrowser?) {
    val input = remember(liveBrowser) { liveBrowser?.let(::CefBrowserInput) }
    val imageSizeState = remember { mutableStateOf(IntSize.Zero) }
    var imageSize by imageSizeState
    Image(
        bitmap = webSnapshot,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { imageSize = it }
            .forwardBrowserInput(input, imageSizeState),
        contentScale = ContentScale.Fit
    )
}

/**
 * Forwards what the operator does over the mirrored image to the live browser: presses, releases and
 * throttled moves, the wheel, and keys. With no [input] it forwards nothing.
 */
internal fun Modifier.forwardBrowserInput(input: BrowserInput?, imageSizeState: State<IntSize>): Modifier =
    forwardMouse(input, imageSizeState)
        .forwardWheel(input, imageSizeState)
        .forwardKeys(input)

/** Forwards presses, releases and throttled moves to the live browser. */
private fun Modifier.forwardMouse(input: BrowserInput?, imageSizeState: State<IntSize>): Modifier =
    pointerInput(input) {
        if (input == null) return@pointerInput
        val throttle = MoveThrottle()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val pos = event.changes.firstOrNull()?.position
                input.forwardPointer(event.type, pos, imageSizeState.value, throttle, System.currentTimeMillis())
            }
        }
    }

/** Forwards the wheel to the live browser. */
private fun Modifier.forwardWheel(input: BrowserInput?, imageSizeState: State<IntSize>): Modifier =
    pointerInput(input) {
        if (input == null) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull()
                    val now = System.currentTimeMillis()
                    input.forwardScroll(change?.position, change?.scrollDelta, imageSizeState.value, now)
                }
            }
        }
    }

/** Forwards key presses and releases to the live browser, consuming each one it sends. */
private fun Modifier.forwardKeys(input: BrowserInput?): Modifier =
    onKeyEvent { keyEvent ->
        if (input == null || input.showingSize() == null) return@onKeyEvent false
        val event = keyEventFor(keyEvent.type, input.component, keyEvent.key.nativeKeyCode, System.currentTimeMillis())
            ?: return@onKeyEvent false
        input.sendKey(event)
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
