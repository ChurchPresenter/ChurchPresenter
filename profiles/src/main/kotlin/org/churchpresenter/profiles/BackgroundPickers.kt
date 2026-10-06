package org.churchpresenter.profiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import org.churchpresenter.canvas.CenteredGlyphLine
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.atem_upload_background_1_tooltip
import org.churchpresenter.strings.generated.resources.atem_upload_background_2_tooltip
import org.churchpresenter.strings.generated.resources.atem_upload_background_unreadable
import org.churchpresenter.strings.generated.resources.stock_library_tooltip
import org.churchpresenter.strings.generated.resources.stock_photo_browse_tooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.churchpresenter.media.composables.FileVideoPicker
import org.churchpresenter.media.data.StockMediaClient
import org.churchpresenter.media.dialogs.StockMediaBrowserDialog
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.atem.AtemFrameEncoder
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.settings.AtemSettings
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** The panel's own tooltip, for a control that is present but cannot be used. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HintTooltip(hint: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        content()
    }
}

// ── The quick tray's shelf ───────────────────────────────────────────────────────────────────

/**
 * Scales [src] to cover a [dw]×[dh] box (uniform scale by the larger of the two axis ratios,
 * so the result never falls short of either dimension) then crops the centered overflow — no
 * distortion, unlike a plain non-uniform stretch to the exact target size.
 */
private fun coverCropArgb(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
    val srcImg = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
    srcImg.setRGB(0, 0, sw, sh, src, 0, sw)
    val scale = maxOf(dw.toDouble() / sw, dh.toDouble() / sh)
    val scaledW = (sw * scale).roundToInt().coerceAtLeast(dw)
    val scaledH = (sh * scale).roundToInt().coerceAtLeast(dh)
    val scaledImg = BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_ARGB)
    val g = scaledImg.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(srcImg, 0, 0, scaledW, scaledH, null)
    } finally {
        g.dispose()
    }
    val cropX = (scaledW - dw) / 2
    val cropY = (scaledH - dh) / 2
    val dst = IntArray(dw * dh)
    scaledImg.getRGB(cropX, cropY, dw, dh, dst, 0, dw)
    return dst
}

/**
 * Uploads [imagePath] as a single still frame to the ATEM media pool, into [slot] — one of the
 * two background slots ([AtemSettings.backgroundSlot1]/[AtemSettings.backgroundSlot2]), separate from the
 * lower-third still/clip slots uploaded from [org.churchpresenter.app.churchpresenter.tabs.LowerThird].
 * Publishes progress through the shared [AtemUploadStatus] so it's visible anywhere that already
 * observes it (e.g. the Lower Third tab's upload bar, if open).
 */
private suspend fun uploadBackgroundToAtem(atemSettings: AtemSettings, imagePath: String, slot: Int) {
    val file = File(imagePath)
    val name = file.nameWithoutExtension
    val argb = withContext(Dispatchers.IO) {
        val img = ImageIO.read(file) ?: throw IOException(getString(Res.string.atem_upload_background_unreadable))
        val w = atemSettings.renderWidth
        val h = atemSettings.renderHeight
        val src = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, src, 0, img.width)
        if (img.width == w && img.height == h) src
        else coverCropArgb(src, img.width, img.height, w, h)
    }
    val frame = withContext(Dispatchers.IO) {
        AtemFrameEncoder.encodeFrame(atemSettings.renderWidth, atemSettings.renderHeight, argb)
    }
    val id = AtemUploadStatus.begin(name, clip = false, slot + 1)
    try {
        val client = AtemClient(atemSettings.host, atemSettings.port)
        withContext(Dispatchers.IO) { client.connect() }
        try {
            client.uploadStillEncoded(slot, frame, name) { p -> AtemUploadStatus.progress(id, p) }
        } finally {
            client.disconnect()
        }
        AtemUploadStatus.complete(id)
        delay(PREVIEW_DEBOUNCE_MS)
        AtemUploadStatus.clear(id)
    } catch (e: IOException) {
        // The ATEM link (AtemProtocolException is one).
        failUpload(id, e)
    } catch (e: IllegalStateException) {
        failUpload(id, e)
    }
}

/** Marks upload [id] failed, then lets [e] carry on to whoever started it. */
private fun failUpload(id: Long, e: Exception): Nothing {
    AtemUploadStatus.fail(id, e.message)
    throw e
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = tooltip,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        KeyIconButton(onClick = onClick) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

/**
 * One of the two independent "upload background to ATEM slot N" buttons in [ImagePickerRow].
 * Each carries its own busy/error state so clicking one never disables or affects the other —
 * an operator can push the same image to both background slots back to back.
 */
@Composable
private fun AtemUploadIconButton(
    badge: String,
    tooltip: String,
    imagePath: String,
    atemSettings: AtemSettings,
    slot: Int
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    } else {
        Box {
            TooltipIconButton(Icons.Default.CloudUpload, error ?: tooltip) {
                error = null
                busy = true
                scope.launch {
                    // Reading the picture and the ATEM link fail with I/O errors; an image the
                    // encoder cannot take, or a client in the wrong state, with the runtime ones.
                    try {
                        uploadBackgroundToAtem(atemSettings, imagePath, slot)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        error = e.message ?: tooltip
                    } catch (e: IllegalArgumentException) {
                        error = e.message ?: tooltip
                    } catch (e: IllegalStateException) {
                        error = e.message ?: tooltip
                    } finally {
                        busy = false
                    }
                }
            }
            // The digit is centred by the circle's own Box, on a line box trimmed to the glyph: a bare
            // Text kept labelSmall's 16sp line inside the 12dp circle, and sat low or clipped.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp, end = 4.dp)
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        lineHeightStyle = CenteredGlyphLine,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun ImagePickerRow(
    imagePath: String,
    onImagePathChange: (String) -> Unit,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    atemSettings: AtemSettings = AtemSettings(),
    modifier: Modifier = Modifier
) {
    var showBrowser by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val browseTooltip = stringResource(Res.string.stock_photo_browse_tooltip)
    val libraryTooltip = stringResource(Res.string.stock_library_tooltip)
    val uploadTooltip1 = stringResource(Res.string.atem_upload_background_1_tooltip)
    val uploadTooltip2 = stringResource(Res.string.atem_upload_background_2_tooltip)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileImagePicker(
            imagePath = imagePath,
            onImagePathChange = onImagePathChange,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(Icons.Default.PhotoLibrary, libraryTooltip) { showLibrary = true }
        TooltipIconButton(Icons.Default.Search, browseTooltip) { showBrowser = true }
        if (atemSettings.host.isNotBlank() && imagePath.isNotBlank()) {
            AtemUploadIconButton(
                badge = "1",
                tooltip = uploadTooltip1,
                imagePath = imagePath,
                atemSettings = atemSettings,
                slot = atemSettings.backgroundSlot1
            )
            AtemUploadIconButton(
                badge = "2",
                tooltip = uploadTooltip2,
                imagePath = imagePath,
                atemSettings = atemSettings,
                slot = atemSettings.backgroundSlot2
            )
        }
    }
    if (showBrowser) {
        StockMediaBrowserDialog(
            mediaType = StockMediaClient.StockMediaType.PHOTO,
            pexelsApiKey = pexelsApiKey,
            onPexelsApiKeyChange = onPexelsApiKeyChange,
            pixabayApiKey = pixabayApiKey,
            onPixabayApiKeyChange = onPixabayApiKeyChange,
            onDismiss = { showBrowser = false },
            onMediaDownloaded = onImagePathChange
        )
    }
    if (showLibrary) {
        LocalLibraryDialog(
            mediaType = StockMediaClient.StockMediaType.PHOTO,
            onDismiss = { showLibrary = false },
            onMediaSelected = onImagePathChange
        )
    }
}

@Composable
internal fun VideoPickerRow(
    videoPath: String,
    onVideoPathChange: (String) -> Unit,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBrowser by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val browseTooltip = stringResource(Res.string.stock_photo_browse_tooltip)
    val libraryTooltip = stringResource(Res.string.stock_library_tooltip)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileVideoPicker(
            videoPath = videoPath,
            onVideoPathChange = onVideoPathChange,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(Icons.Default.PhotoLibrary, libraryTooltip) { showLibrary = true }
        TooltipIconButton(Icons.Default.Search, browseTooltip) { showBrowser = true }
    }
    if (showBrowser) {
        StockMediaBrowserDialog(
            mediaType = StockMediaClient.StockMediaType.VIDEO,
            pexelsApiKey = pexelsApiKey,
            onPexelsApiKeyChange = onPexelsApiKeyChange,
            pixabayApiKey = pixabayApiKey,
            onPixabayApiKeyChange = onPixabayApiKeyChange,
            onDismiss = { showBrowser = false },
            onMediaDownloaded = onVideoPathChange
        )
    }
    if (showLibrary) {
        LocalLibraryDialog(
            mediaType = StockMediaClient.StockMediaType.VIDEO,
            onDismiss = { showLibrary = false },
            onMediaSelected = onVideoPathChange
        )
    }
}

private const val PREVIEW_DEBOUNCE_MS = 800L


/**
 * Settings → Background: every surface the app draws a background on, one at a time.
 *
 * The rail down the left lists all six — the two Defaults, then Bible and Songs in their
 * full-screen and lower-third shapes — each with a chip showing what that surface actually
 * projects, inheritance followed. Picking one opens it in the editor beside the rail: what it is,
 * how it looks, and a preview of the output it produces. The quick tray's own backgrounds sit
 * under that preview, which is where the operator meets them.
 *
 * The four cards this replaced showed the same six surfaces at once, each with its own type
 * dropdown and its own set of sliders, and none of them showed what the surface would project.
 */
