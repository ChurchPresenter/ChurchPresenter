package org.churchpresenter.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import org.churchpresenter.strings.generated.resources.canvas_browser_no_url
import org.churchpresenter.slides.utils.PictureDecoder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_image_not_found
import org.churchpresenter.strings.generated.resources.show_qr_code
import org.churchpresenter.strings.generated.resources.canvas_video_vlc_load_failed
import org.churchpresenter.strings.generated.resources.canvas_video_vlc_not_found
import org.churchpresenter.strings.generated.resources.canvas_video_no_selection
import org.churchpresenter.strings.generated.resources.canvas_video_file_not_found
import org.churchpresenter.strings.generated.resources.canvas_video_loading
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.utils.Utils.parseHexColor

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import androidx.compose.foundation.Canvas

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import org.churchpresenter.media.composables.isVlcAvailable
import org.churchpresenter.media.composables.isVlcLoadFailed
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.churchpresenter.core.models.scene.PathPoint

private const val URL_DEBOUNCE_MS = 800L

internal const val SOURCE_ERROR_TEXT_COLOR = 0xFFFF8888

/**
 * Draws one scene source.
 *
 * [showDiagnostics] is what separates the editor from the audience: a camera that will not open
 * says so in red on the canvas the operator is working in, and shows the ordinary placeholder on
 * the presenter output, where a troubleshooting sentence in front of a congregation would be worse
 * than the missing picture it explains.
 */
/** The smallest font size a canvas layer is drawn at, whatever it was saved with. */
private const val MIN_DRAWN_FONT_SIZE = 1

/**
 * [size] scaled to the canvas, never below [MIN_DRAWN_FONT_SIZE].
 *
 * A layer saved with a font size of 0 gives a line height of 0 too, and Compose hands Skia the
 * height as line height over font size: 0 ÷ 0, which Skia refuses with `IllegalStateException:
 * Check failed.` on the event thread -- the whole app down mid-service (Sentry
 * CHURCH-PRESENTER-DESKTOP-8Z). A negative one fails the same way. The size fields now refuse both,
 * but a scene saved before that, or sent over Instance Link, still has to draw.
 */
internal fun drawnFontSize(size: Int, scale: Float): TextUnit = (size.coerceAtLeast(MIN_DRAWN_FONT_SIZE) * scale).sp

@Composable
fun SceneSourceRenderer(
    source: SceneSource,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    showDiagnostics: Boolean = true
) {
    when (source) {
        is SceneSource.ImageSource -> ImageSourceContent(source, modifier)
        is SceneSource.TextSource -> TextSourceContent(source, modifier, fontScale)
        is SceneSource.ColorSource -> ColorSourceContent(source, modifier)
        is SceneSource.VideoSource -> VideoSourceContent(source, modifier)
        is SceneSource.BrowserSource -> BrowserSourceContent(source, modifier)
        is SceneSource.ShapeSource -> ShapeSourceContent(source, modifier, fontScale)
        is SceneSource.ClockSource -> ClockSourceContent(source, modifier, fontScale)
        is SceneSource.QRCodeSource -> QRCodeSourceContent(source, modifier)
        is SceneSource.CameraSource -> CameraSourceContent(source, modifier, showDiagnostics)
        is SceneSource.ScreenCaptureSource -> ScreenCaptureSourceContent(source, modifier)
        is SceneSource.NdiSource -> NdiSourceContent(source, modifier)
        is SceneSource.OmtSource -> OmtSourceContent(source, modifier)
        is SceneSource.BibleSource -> BibleSourceContent(source, modifier, fontScale)
    }
}

@Composable
private fun ImageSourceContent(source: SceneSource.ImageSource, modifier: Modifier) {
    val bitmap = remember(source.filePath) {
        // PictureDecoder, not Skia directly — a scene image is a file the operator chose, and the
        // formats Skia refuses are ordinary camera and print output.
        val file = File(source.filePath)
        if (file.exists()) PictureDecoder.decodeOrNull(file)?.toComposeImageBitmap() else null
    }

    if (bitmap != null) {
        val scale = when (source.contentScale) {
            "FILL" -> ContentScale.Crop
            "STRETCH" -> ContentScale.FillBounds
            "NONE" -> ContentScale.None
            else -> ContentScale.Fit
        }
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = source.name,
            contentScale = scale,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(Res.string.canvas_image_not_found), color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ColorSourceContent(source: SceneSource.ColorSource, modifier: Modifier) {
    val color1 = parseHexColor(source.color).copy(alpha = source.sourceOpacity)
    if (source.isGradient) {
        val color2 = parseHexColor(source.gradientColor2).copy(alpha = source.gradientColor2Opacity)
        val angleRad = Math.toRadians(source.gradientAngle.toDouble())
        val pos = source.gradientPosition.coerceIn(0.001f, 0.999f)
        Box(modifier = modifier.fillMaxSize().drawBehind {
            val cx = 0.5f * size.width
            val cy = 0.5f * size.height
            val dx = 0.5f * cos(angleRad).toFloat() * size.width
            val dy = 0.5f * sin(angleRad).toFloat() * size.height
            val shift = (pos - 0.5f) * 2f
            val brush = Brush.linearGradient(
                colors = listOf(color1, color2),
                start = Offset(cx - dx + shift * dx, cy - dy + shift * dy),
                end = Offset(cx + dx + shift * dx, cy + dy + shift * dy)
            )
            drawRect(brush = brush, size = size)
        })
    } else {
        Box(modifier = modifier.fillMaxSize().background(color1))
    }
}

@Composable
private fun VideoSourceContent(
    source: SceneSource.VideoSource,
    modifier: Modifier,
) {
    val file = remember(source.filePath) { if (source.filePath.isNotBlank()) File(source.filePath) else null }
    if (file == null || !file.exists() || !isVlcAvailable) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isVlcLoadFailed) stringResource(Res.string.canvas_video_vlc_load_failed)
                       else if (!isVlcAvailable) stringResource(Res.string.canvas_video_vlc_not_found)
                       else if (file == null) stringResource(Res.string.canvas_video_no_selection)
                       else stringResource(Res.string.canvas_video_file_not_found, source.filePath),
                color = Color.White,
                fontSize = 14.sp
            )
        }
        return
    }

    // Through the shared cache: this composable is mounted once in the canvas editor, once in each
    // sidebar live preview and once on each presenter output, and each instance used to build its
    // own VLC factory, its own player and its own conversion loop — decoding the same file that
    // many times, and playing its audio that many times over itself.
    val spec = remember(source.filePath, source.loop) { SceneVideoSpec(source.filePath, source.loop) }
    var frames by remember { mutableStateOf<StateFlow<ImageBitmap?>?>(null) }
    DisposableEffect(spec) {
        frames = SharedSceneVideoCache.acquire(spec, source.volume)
        onDispose {
            frames = null
            SharedSceneVideoCache.release(spec)
        }
    }
    // Volume is not part of the key, so a change reaches the running decode without restarting it.
    LaunchedEffect(spec, source.volume) { SharedSceneVideoCache.setVolume(spec, source.volume) }

    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val frame by (frames ?: noFrame).collectAsState()

    val shown = frame
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = source.name,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(Res.string.canvas_video_loading), color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BrowserSourceContent(
    source: SceneSource.BrowserSource,
    modifier: Modifier,
) {
    if (source.url.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stringResource(Res.string.canvas_browser_no_url), color = Color.White, fontSize = 14.sp)
        }
        return
    }

    // Only re-create browser when id or viewport size changes.
    //
    // Acquired in the effect, for the reason spelled out in [CameraSourceContent] — and here the
    // dispose keys used to be narrower than the acquire keys, so a resize acquired a second time
    // and released neither. That leaked a whole headless browser per resize.
    var browserFlows by remember { mutableStateOf<SharedBrowserFrameCache.BrowserFlows?>(null) }
    DisposableEffect(source.id, source.renderWidth, source.renderHeight) {
        browserFlows = SharedBrowserFrameCache.acquire(
            source.id,
            SharedBrowserFrameCache.BrowserPage(
                source.url, source.renderWidth, source.renderHeight,
                source.customCss, source.fps, source.forceTransparent,
            ),
        )
        onDispose {
            browserFlows = null
            SharedBrowserFrameCache.release(source.id)
        }
    }

    // Debounce URL and CSS changes — navigate in-place instead of restarting Chrome
    LaunchedEffect(source.url, source.customCss, source.forceTransparent) {
        delay(URL_DEBOUNCE_MS) // debounce: wait for user to stop typing
        if (source.url.isNotBlank()) {
            SharedBrowserFrameCache.navigateTo(source.id, source.url, source.customCss, source.forceTransparent)
        }
    }

    // Transparent background toggle — apply immediately without navigation
    LaunchedEffect(source.forceTransparent) {
        SharedBrowserFrameCache.setTransparent(source.id, source.forceTransparent)
    }

    // FPS change — update capture interval without restart
    LaunchedEffect(source.fps) {
        SharedBrowserFrameCache.setFps(source.id, source.fps)
    }

    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val noError = remember { MutableStateFlow<String?>(null) }
    val frame by (browserFlows?.frame ?: noFrame).collectAsState()
    val error by (browserFlows?.error ?: noError).collectAsState()

    if (frame != null) {
        Image(
            bitmap = frame!!,
            contentDescription = source.name,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = error ?: "Loading: ${source.url}",
                color = if (error != null) Color(SOURCE_ERROR_TEXT_COLOR) else Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ShapeSourceContent(source: SceneSource.ShapeSource, modifier: Modifier, fontScale: Float = 1f) {
    val strokeColor = parseHexColor(source.strokeColor).copy(alpha = source.strokeOpacity)
    val fillColor = parseHexColor(source.fillColor).copy(alpha = source.fillOpacity)
    val density = LocalDensity.current
    val strokeWidth = with(density) { (source.strokeWidth * fontScale).dp.toPx() }
    val arrowMinPx = with(density) { (12f * fontScale).dp.toPx() }
    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    // Pre-compute gradient parameters outside Canvas (composable context)
    val gradientColor2 = if (source.isGradient) {
        parseHexColor(source.gradientColor2).copy(alpha = source.gradientColor2Opacity)
    } else {
        null
    }
    val gradientAngleRad = if (source.isGradient) Math.toRadians(source.gradientAngle.toDouble()) else 0.0
    val gradientPos = source.gradientPosition.coerceIn(0.001f, 0.999f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Build fill brush using actual shape size
        val fillBrush: Brush? = if (source.isGradient && gradientColor2 != null) {
            // Position shifts the midpoint: 0% = all color2, 50% = even blend, 100% = all color1
            val cx = 0.5f * w
            val cy = 0.5f * h
            val dx = 0.5f * cos(gradientAngleRad).toFloat() * w
            val dy = 0.5f * sin(gradientAngleRad).toFloat() * h
            // Shift start/end so the blend midpoint moves with gradientPos
            val shift = (gradientPos - 0.5f) * 2f
            Brush.linearGradient(
                colors = listOf(fillColor, gradientColor2),
                start = Offset(cx - dx + shift * dx, cy - dy + shift * dy),
                end = Offset(cx + dx + shift * dx, cy + dy + shift * dy)
            )
        } else if (fillColor.alpha > 0f) {
            Brush.linearGradient(listOf(fillColor, fillColor))
        } else null

        when (source.shapeType) {
            "rectangle" -> {
                if (fillBrush != null) {
                    drawRect(brush = fillBrush, size = size)
                }
                if (source.showStroke) {
                    drawRect(color = strokeColor, size = size, style = stroke)
                }
            }
            "ellipse" -> {
                if (fillBrush != null) {
                    drawOval(brush = fillBrush, size = size)
                }
                if (source.showStroke) {
                    drawOval(color = strokeColor, size = size, style = stroke)
                }
            }
            "line" -> {
                val (startPt, endPt) = lineEnds(source.points, w, h)
                drawLine(
                    color = strokeColor,
                    start = startPt,
                    end = endPt,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            "arrow" -> {
                val (startPt, endPt) = lineEnds(source.points, w, h)
                drawArrow(startPt, endPt, strokeColor, strokeWidth, arrowMinPx)
            }
            "freehand" -> {
                if (source.points.size >= 2) {
                    val path = Path().apply {
                        moveTo(source.points[0].x * w, source.points[0].y * h)
                        for (i in 1 until source.points.size) {
                            lineTo(source.points[i].x * w, source.points[i].y * h)
                        }
                    }
                    drawPath(path, color = strokeColor, style = stroke)
                }
            }
        }
    }
}

/** A line's two ends on a [w] by [h] box: its stored points, or corner to corner when it has none. */
private fun lineEnds(points: List<PathPoint>, w: Float, h: Float): Pair<Offset, Offset> {
    val p0 = points.getOrNull(0)
    val p1 = points.getOrNull(1)
    val startPt = if (p0 != null) Offset(p0.x * w, p0.y * h) else Offset(0f, 0f)
    val endPt = if (p1 != null) Offset(p1.x * w, p1.y * h) else Offset(w, h)
    return startPt to endPt
}

/** A line from [startPt] to [endPt] with an open arrowhead at [endPt], never smaller than [arrowMinPx]. */
private fun DrawScope.drawArrow(startPt: Offset, endPt: Offset, color: Color, strokeWidth: Float, arrowMinPx: Float) {
    drawLine(
        color = color,
        start = startPt,
        end = endPt,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    val arrowSize = (strokeWidth * ARROWHEAD_STROKES).coerceAtLeast(arrowMinPx)
    val angle = kotlin.math.atan2(endPt.y - startPt.y, endPt.x - startPt.x)
    val ax1 = endPt.x - arrowSize * kotlin.math.cos(angle - ARROWHEAD_SPREAD_RAD)
    val ay1 = endPt.y - arrowSize * kotlin.math.sin(angle - ARROWHEAD_SPREAD_RAD)
    val ax2 = endPt.x - arrowSize * kotlin.math.cos(angle + ARROWHEAD_SPREAD_RAD)
    val ay2 = endPt.y - arrowSize * kotlin.math.sin(angle + ARROWHEAD_SPREAD_RAD)
    val arrowPath = Path().apply {
        moveTo(endPt.x, endPt.y)
        lineTo(ax1, ay1)
        moveTo(endPt.x, endPt.y)
        lineTo(ax2, ay2)
    }
    drawPath(arrowPath, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

/** An arrowhead's arms are this many stroke widths long... */
private const val ARROWHEAD_STROKES = 4f

/** ...and this far either side of the line, in radians. */
private const val ARROWHEAD_SPREAD_RAD = 0.4f

@Composable
private fun QRCodeSourceContent(source: SceneSource.QRCodeSource, modifier: Modifier) {
    val bgColor = parseHexColor(source.backgroundColor)
    val fgColor = parseHexColor(source.foregroundColor)

    val qrContent = remember(
        source.contentType,
        source.content,
        source.wifiSsid,
        source.wifiPassword,
        source.wifiEncryption,
        source.wifiHidden
    ) {
        if (source.contentType == "wifi") {
            val encType = when (source.wifiEncryption) {
                "WPA", "WPA2", "WPA3" -> "WPA"
                "WEP" -> "WEP"
                else -> "nopass"
            }
            buildString {
                append("WIFI:T:$encType;S:${source.wifiSsid};")
                if (encType != "nopass") append("P:${source.wifiPassword};")
                if (source.wifiHidden) append("H:true;")
                append(";")
            }
        } else {
            source.content
        }
    }

    val bitmap = remember(
        qrContent,
        source.foregroundColor,
        source.backgroundColor,
        source.transparentBackground,
        source.errorCorrection
    ) {
        try {
            val ecLevel = when (source.errorCorrection) {
                "L" -> ErrorCorrectionLevel.L
                "Q" -> ErrorCorrectionLevel.Q
                "H" -> ErrorCorrectionLevel.H
                else -> ErrorCorrectionLevel.M
            }
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ecLevel,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 256, 256, hints)
            val w = matrix.width
            val h = matrix.height
            val fgArgb = (((fgColor.alpha * 255).toInt() shl 24) or
                    ((fgColor.red * 255).toInt() shl 16) or
                    ((fgColor.green * 255).toInt() shl 8) or
                    (fgColor.blue * 255).toInt())
            val bgArgb = if (source.transparentBackground) 0x00000000
            else (((bgColor.alpha * 255).toInt() shl 24) or
                    ((bgColor.red * 255).toInt() shl 16) or
                    ((bgColor.green * 255).toInt() shl 8) or
                    (bgColor.blue * 255).toInt())
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    img.setRGB(x, y, if (matrix.get(x, y)) fgArgb else bgArgb)
                }
            }
            SkiaImage.makeFromEncoded(
                ByteArrayOutputStream().also {
                    ImageIO.write(img, "PNG", it)
                }.toByteArray()
            ).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(if (source.transparentBackground) Color.Transparent else bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(stringResource(Res.string.show_qr_code), color = Color.White, fontSize = 14.sp)
        }
    }
}
