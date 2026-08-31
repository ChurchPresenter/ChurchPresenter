package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.abs
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_image_not_found
import churchpresenter.composeapp.generated.resources.canvas_placeholder_qr
import churchpresenter.composeapp.generated.resources.canvas_placeholder_camera
import churchpresenter.composeapp.generated.resources.canvas_placeholder_camera_default
import churchpresenter.composeapp.generated.resources.canvas_video_vlc_load_failed
import churchpresenter.composeapp.generated.resources.canvas_video_vlc_not_found
import churchpresenter.composeapp.generated.resources.canvas_video_no_selection
import churchpresenter.composeapp.generated.resources.canvas_video_file_not_found
import churchpresenter.composeapp.generated.resources.canvas_video_loading
import churchpresenter.composeapp.generated.resources.canvas_placeholder_ndi
import churchpresenter.composeapp.generated.resources.canvas_placeholder_ndi_default
import churchpresenter.composeapp.generated.resources.canvas_placeholder_ndi_waiting
import churchpresenter.composeapp.generated.resources.canvas_placeholder_screen_capture
import org.churchpresenter.core.models.scene.ClockModes
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.WindowsWindowCapture
import org.churchpresenter.app.churchpresenter.utils.X11WindowCapture
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import androidx.compose.foundation.Canvas
import org.churchpresenter.app.churchpresenter.utils.TimerStateManager
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke

private const val FRAME_INTERVAL_MS = 16L
private const val PLAYER_SETTLE_MS = 100L
private const val VOLUME_PERCENT_SCALE = 100
private const val URL_DEBOUNCE_MS = 800L
private const val ERROR_TEXT_COLOR = 0xFFFF8888
private const val POLL_INTERVAL_MS = 1000L
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_DAY = 86400
private const val PERCENT_SCALE = 100f
/** A curve of 100% spends half a circle on the line; more than a full circle would overlap itself. */
private const val MAX_CURVE_TURNS = 2f
/** How much room a bent reference line gets: its own height, plus the room the bend needs. */
private const val REFERENCE_ROWS = 3f
private const val MIN_CAPTURE_INTERVAL_MS = 33L
private const val WINDOW_BOUNDS_FIELDS = 4
private const val BOUNDS_WIDTH_INDEX = 2
private const val BOUNDS_HEIGHT_INDEX = 3

// libvlc media options. Written out per branch rather than collected into a list and spread: play()
// is a Java vararg, so a spread copies the array on every call, and building the list allocated one
// more object again for what is at most two constant strings.
private const val VLC_OPT_TIGHT_CLOCK = ":clock-jitter=0"
private const val VLC_OPT_LOOP = ":input-repeat=65535"

/**
 * Draws one scene source.
 *
 * [showDiagnostics] is what separates the editor from the audience: a camera that will not open
 * says so in red on the canvas the operator is working in, and shows the ordinary placeholder on
 * the presenter output, where a troubleshooting sentence in front of a congregation would be worse
 * than the missing picture it explains.
 */
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

/**
 * Tracking as Compose wants it: [TextUnit.Unspecified] for none at all, rather than a spacing of
 * zero. The two are not the same to the text shaper — asking for zero re-shapes the line and moves
 * it by a pixel, which would change every scene that has never touched the setting.
 */
private fun trackingOf(percent: Float): TextUnit =
    if (percent == 0f) TextUnit.Unspecified else (percent / PERCENT_SCALE).em

/** Underline, strike-through, both, or neither — as Compose wants it. */
private fun textDecorationOf(underline: Boolean, strikethrough: Boolean): TextDecoration? = when {
    underline && strikethrough ->
        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strikethrough -> TextDecoration.LineThrough
    else -> null
}

@Composable
private fun TextSourceContent(source: SceneSource.TextSource, modifier: Modifier, fontScale: Float = 1f) {
    val bgColor = if (source.backgroundColor.equals("#00000000", ignoreCase = true))
        Color.Transparent
    else
        parseHexColor(source.backgroundColor)
    val textColor = parseHexColor(source.fontColor)
    val fontFamily = remember(source.fontFamily) { systemFontFamilyOrDefault(source.fontFamily) }
    val align = when (source.horizontalAlignment) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }
    val lineHeightMultiplier = source.lineSpacing / 100f
    val verticalAlign = when (source.verticalAlignment) {
        "top" -> Alignment.TopCenter
        "bottom" -> Alignment.BottomCenter
        else -> Alignment.Center
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor).clipToBounds(),
        contentAlignment = verticalAlign
    ) {
        if (source.curve != 0f) {
            CurvedText(
                text = source.text,
                curve = source.curve,
                style = TextStyle(
                    color = textColor,
                    fontSize = (source.fontSize * fontScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(source.underline, source.strikethrough),
                    letterSpacing = trackingOf(source.letterSpacing),
                ),
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } else {
            Text(
                text = source.text,
                color = textColor,
                fontSize = (source.fontSize * fontScale).sp,
                fontFamily = fontFamily,
                fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = textDecorationOf(source.underline, source.strikethrough),
                textAlign = align,
                lineHeight = (source.fontSize * fontScale * lineHeightMultiplier).sp,
                letterSpacing = trackingOf(source.letterSpacing),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

/**
 * One line of text bent around a circle: [curve] is a percentage, positive arching the line over
 * the circle and negative cupping it under, and 100 spends a half circle on it.
 *
 * Compose has no text-on-a-path, so each glyph is measured and drawn on its own, rotated to the
 * angle its own centre sits at. That is also why the line cannot wrap — newlines become spaces.
 */
@Composable
internal fun CurvedText(text: String, curve: Float, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val glyphs = remember(text, style) {
        text.replace('\n', ' ').map { measurer.measure(AnnotatedString(it.toString()), style) }
    }
    val widths = remember(glyphs) { glyphs.map { it.size.width.toFloat() } }
    val lineWidth = widths.sum()
    val lineHeight = remember(glyphs) { glyphs.maxOfOrNull { it.size.height.toFloat() } ?: 0f }

    Canvas(modifier) {
        if (lineWidth <= 0f) return@Canvas
        val sweep = (abs(curve) / PERCENT_SCALE).coerceAtMost(MAX_CURVE_TURNS) * PI.toFloat()
        val radius = lineWidth / sweep
        // How far the ends fall away from the middle of the arc, which is what it costs in height.
        val sagitta = radius * (1f - cos(sweep / 2f))
        val arch = curve > 0f
        val extentTop = (size.height - (sagitta + lineHeight)) / 2f
        val apexTop = if (arch) extentTop else extentTop + sagitta
        val pivot = Offset(size.width / 2f, if (arch) apexTop + radius else apexTop - radius)

        var travelled = 0f
        glyphs.forEachIndexed { index, glyph ->
            val centre = travelled + widths[index] / 2f
            val angle = Math.toDegrees(((centre - lineWidth / 2f) / radius).toDouble()).toFloat()
            rotate(degrees = if (arch) angle else -angle, pivot = pivot) {
                drawText(glyph, topLeft = Offset(size.width / 2f - widths[index] / 2f, apexTop))
            }
            travelled += widths[index]
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

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }
    val frameVersion = remember { mutableStateOf(0L) }
    val bufferedImageHolder = remember { mutableStateOf<BufferedImage?>(null) }

    val factory = remember {
        try { MediaPlayerFactory("--no-video-title-show") } catch (t: Throwable) {
            CrashReporter.reportException(t, "SceneSourceRenderer: VLC MediaPlayerFactory init failed"); null
        }
    } ?: return

    val mediaPlayer = remember(factory) {
        try { factory.mediaPlayers().newEmbeddedMediaPlayer() } catch (_: Throwable) { null }
    } ?: return

    // Convert frames off VLC's render thread to avoid blocking audio pipeline
    LaunchedEffect(Unit) {
        var lastVersion = 0L
        while (isActive) {
            val v = frameVersion.value
            if (v != lastVersion) {
                lastVersion = v
                val img = bufferedImageHolder.value
                if (img != null) {
                    currentFrame.value = img.toComposeImageBitmap()
                }
            }
            delay(FRAME_INTERVAL_MS)
        }
    }

    DisposableEffect(source.filePath) {
        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                bufferedImageHolder.value = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
        }

        val renderCallback = RenderCallback { _, nativeBuffers, _ ->
            val img = bufferedImageHolder.value ?: return@RenderCallback
            if (nativeBuffers == null || nativeBuffers.isEmpty()) return@RenderCallback
            val pixelData = (img.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
            try {
                val buf = nativeBuffers[0] ?: return@RenderCallback
                buf.rewind()
                buf.asIntBuffer().get(pixelData, 0, pixelData.size.coerceAtMost(buf.remaining() / 4))
                frameVersion.value++
            } catch (_: Throwable) { }
        }

        mediaPlayer.videoSurface().set(
            factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
        )

        onDispose {
            try {
                mediaPlayer.controls().stop()
                mediaPlayer.release()
                factory.release()
            } catch (_: Throwable) { }
        }
    }

    LaunchedEffect(source.filePath, source.loop, source.volume) {
        delay(PLAYER_SETTLE_MS)
        try {
            mediaPlayer.audio().setVolume((source.volume * VOLUME_PERCENT_SCALE).toInt())
            if (source.loop) mediaPlayer.media().play(file.absolutePath, VLC_OPT_TIGHT_CLOCK, VLC_OPT_LOOP)
            else mediaPlayer.media().play(file.absolutePath, VLC_OPT_TIGHT_CLOCK)
        } catch (_: Throwable) { }
    }

    val frame = currentFrame.value
    if (frame != null) {
        Image(
            bitmap = frame,
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
            Text(text = "Browser: no URL", color = Color.White, fontSize = 14.sp)
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
            source.id, source.url, source.renderWidth, source.renderHeight,
            source.customCss, source.fps, source.forceTransparent
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
                color = if (error != null) Color(ERROR_TEXT_COLOR) else Color.White,
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
    val gradientColor2 = if (source.isGradient) parseHexColor(source.gradientColor2).copy(alpha = source.gradientColor2Opacity) else null
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
                val p0 = source.points.getOrNull(0)
                val p1 = source.points.getOrNull(1)
                val startPt = if (p0 != null) Offset(p0.x * w, p0.y * h) else Offset(0f, 0f)
                val endPt = if (p1 != null) Offset(p1.x * w, p1.y * h) else Offset(w, h)
                drawLine(
                    color = strokeColor,
                    start = startPt,
                    end = endPt,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            "arrow" -> {
                val p0 = source.points.getOrNull(0)
                val p1 = source.points.getOrNull(1)
                val startPt = if (p0 != null) Offset(p0.x * w, p0.y * h) else Offset(0f, 0f)
                val endPt = if (p1 != null) Offset(p1.x * w, p1.y * h) else Offset(w, h)
                drawLine(
                    color = strokeColor,
                    start = startPt,
                    end = endPt,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Arrowhead
                val arrowSize = (strokeWidth * 4f).coerceAtLeast(arrowMinPx)
                val dx = endPt.x - startPt.x
                val dy = endPt.y - startPt.y
                val angle = kotlin.math.atan2(dy, dx)
                val ax1 = endPt.x - arrowSize * kotlin.math.cos(angle - 0.4f)
                val ay1 = endPt.y - arrowSize * kotlin.math.sin(angle - 0.4f)
                val ax2 = endPt.x - arrowSize * kotlin.math.cos(angle + 0.4f)
                val ay2 = endPt.y - arrowSize * kotlin.math.sin(angle + 0.4f)
                val arrowPath = Path().apply {
                    moveTo(endPt.x, endPt.y)
                    lineTo(ax1, ay1)
                    moveTo(endPt.x, endPt.y)
                    lineTo(ax2, ay2)
                }
                drawPath(
                    arrowPath,
                    color = strokeColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
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

@Composable
private fun ClockSourceContent(source: SceneSource.ClockSource, modifier: Modifier, fontScale: Float = 1f) {
    val bgColor = parseHexColor(source.backgroundColor)
    val fontColor = parseHexColor(source.fontColor)
    val fontFamily = systemFontFamilyOrDefault(source.fontFamily)

    val displayText = when (source.mode) {
        ClockModes.COUNTDOWN -> countdownText(source)
        ClockModes.COUNT_UP -> countUpText(source)
        ClockModes.TARGET_TIME -> targetTimeText(source)
        else -> wallClockText(source)
    }

    val style = TextStyle(
        color = fontColor,
        fontSize = (source.fontSize * fontScale).sp,
        fontFamily = fontFamily,
        fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = textDecorationOf(source.underline, source.strikethrough),
        letterSpacing = trackingOf(source.letterSpacing)
    )

    Box(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (source.curve != 0f) {
            CurvedText(displayText, source.curve, style, Modifier.fillMaxSize())
        } else {
            Text(text = displayText, style = style)
        }
    }
}

/** hh:mm:ss, dropping either end as the source asks. */
private fun formatElapsed(seconds: Int, showHours: Boolean, showSeconds: Boolean): String = buildString {
    if (showHours) append("%02d:".format(seconds / SECONDS_PER_HOUR))
    append("%02d".format((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE))
    if (showSeconds) append(":%02d".format(seconds % SECONDS_PER_MINUTE))
}

/**
 * Seconds from now until the next occurrence of a time of day — tomorrow's, once today's has been
 * and gone, which is what keeps a "service starts at 10:00" countdown from going negative.
 */
private fun secondsUntilTimeOfDay(hour: Int, minute: Int, second: Int): Int {
    val target = hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second
    val diff = target - LocalTime.now().toSecondOfDay()
    return if (diff > 0) diff else diff + SECONDS_PER_DAY
}

/** The countdown's remaining time, or its expiry message once it has run out. */
@Composable
private fun countdownText(source: SceneSource.ClockSource): String {
    val totalSeconds = source.targetHour * SECONDS_PER_HOUR +
        source.targetMinute * SECONDS_PER_MINUTE + source.targetSecond

    // Sync TimerStateManager when duration fields change
    LaunchedEffect(totalSeconds) {
        TimerStateManager.onDurationChanged(source.id, totalSeconds)
    }

    val remaining = TimerStateManager.getState(source.id, totalSeconds).remainingSeconds
    val expired = remaining == 0 && totalSeconds > 0
    return if (expired && source.expiredText.isNotBlank()) source.expiredText
    else formatElapsed(remaining, source.showHours, source.showSeconds)
}

/** A stopwatch: seeded at zero and counted up by the same shared state the countdown uses. */
@Composable
private fun countUpText(source: SceneSource.ClockSource): String {
    return formatElapsed(
        TimerStateManager.getState(source.id, 0).remainingSeconds,
        source.showHours,
        source.showSeconds
    )
}

/** Counts down to a time of day off the wall clock, so it needs no transport of its own. */
@Composable
private fun targetTimeText(source: SceneSource.ClockSource): String {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(
        source.targetTimeHour, source.targetTimeMinute, source.targetTimeSecond,
        source.showHours, source.showSeconds
    ) {
        while (isActive) {
            val remaining =
                secondsUntilTimeOfDay(source.targetTimeHour, source.targetTimeMinute, source.targetTimeSecond)
            text = formatElapsed(remaining, source.showHours, source.showSeconds)
            delay(POLL_INTERVAL_MS)
        }
    }
    return text
}

/** The wall clock itself, in the source's own 12h/24h format. */
@Composable
private fun wallClockText(source: SceneSource.ClockSource): String {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(source.timeFormat, source.showHours, source.showSeconds) {
        while (isActive) {
            val pattern = buildString {
                if (source.showHours) {
                    append(if (source.timeFormat == "12h") "hh:" else "HH:")
                }
                append("mm")
                if (source.showSeconds) append(":ss")
                if (source.timeFormat == "12h") append(" a")
            }
            text = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))
            delay(POLL_INTERVAL_MS)
        }
    }
    return text
}

/**
 * The verse over its reference, both bent by [SceneSource.BibleSource.curve].
 *
 * A bent line cannot wrap, so the verse is one line however long it is — which is the trade the
 * curve asks for, and why it is off by default.
 */
@Composable
private fun CurvedBibleText(
    source: SceneSource.BibleSource,
    textColor: Color,
    refColor: Color,
    fontFamily: FontFamily,
    fontScale: Float
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        CurvedText(
            text = source.verseText.ifEmpty { "Select a verse..." },
            curve = source.curve,
            style = TextStyle(
                color = if (source.verseText.isEmpty()) Color.Gray else textColor,
                fontSize = (source.fontSize * fontScale).sp,
                fontFamily = fontFamily,
                fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = textDecorationOf(source.underline, source.strikethrough),
                letterSpacing = trackingOf(source.letterSpacing),
            ),
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        if (source.referenceText.isNotEmpty()) {
            CurvedText(
                text = source.referenceText,
                curve = source.curve,
                style = TextStyle(
                    color = refColor,
                    fontSize = (source.referenceFontSize * fontScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = if (source.referenceBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.referenceItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(
                        source.referenceUnderline,
                        source.referenceStrikethrough
                    ),
                    letterSpacing = trackingOf(source.letterSpacing),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((source.referenceFontSize * fontScale * REFERENCE_ROWS).dp)
            )
        }
    }
}

@Composable
private fun QRCodeSourceContent(source: SceneSource.QRCodeSource, modifier: Modifier) {
    val bgColor = parseHexColor(source.backgroundColor)
    val fgColor = parseHexColor(source.foregroundColor)

    val qrContent = remember(source.contentType, source.content, source.wifiSsid, source.wifiPassword, source.wifiEncryption, source.wifiHidden) {
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

    val bitmap = remember(qrContent, source.foregroundColor, source.backgroundColor, source.transparentBackground, source.errorCorrection) {
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
            Text(stringResource(Res.string.canvas_placeholder_qr), color = Color.White, fontSize = 14.sp)
        }
    }
}


@Composable
private fun CameraSourceContent(
    source: SceneSource.CameraSource,
    modifier: Modifier,
    showDiagnostics: Boolean,
) {
    if (source.devicePath.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (source.deviceName.isNotEmpty()) stringResource(Res.string.canvas_placeholder_camera, source.deviceName)
                       else stringResource(Res.string.canvas_placeholder_camera_default),
                color = Color.White,
                fontSize = 14.sp
            )
        }
        return
    }

    // Use shared cache so canvas preview and presenter output share one capture process.
    //
    // Acquired in the effect rather than in `remember`: a composition that is abandoned before its
    // effects run still discards what `remember` produced, and it does so without calling any
    // `onDispose`. Acquiring there leaked the refcount — and with it the ffmpeg process holding the
    // device open, which the *next* acquire then found busy. That is the reported
    // "Error opening input: Input/output error" on a camera nothing else is using.
    var cameraFlows by remember { mutableStateOf<SharedCameraFrameCache.CameraFlows?>(null) }
    DisposableEffect(source.devicePath, source.videoFormat, source.videoConnection, source.deckLinkIndex) {
        cameraFlows = SharedCameraFrameCache.acquire(source)
        onDispose {
            cameraFlows = null
            SharedCameraFrameCache.release(source)
        }
    }

    // Stand-ins for the one composition pass before the effect has acquired: collecting needs a
    // flow, and a conditional `collectAsState` would move with the acquire.
    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val noFailure = remember { MutableStateFlow<CameraFailure?>(null) }
    val frame by (cameraFlows?.frame ?: noFrame).collectAsState()
    val error by (cameraFlows?.error ?: noFailure).collectAsState()

    if (frame != null) {
        Image(
            bitmap = frame!!,
            contentDescription = source.deviceName,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            val shownError = error?.takeIf { showDiagnostics }
            Text(
                text = shownError?.let { stringResource(cameraFailureStringRes(it)) }
                    ?: if (source.deviceName.isNotEmpty()) stringResource(Res.string.canvas_placeholder_camera, source.deviceName)
                       else stringResource(Res.string.canvas_placeholder_camera_default),
                color = if (shownError != null) Color(ERROR_TEXT_COLOR) else Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A live NDI source from the network.
 *
 * Drawn with [ContentScale.Fit] rather than the camera's `Crop`: an NDI source is as often a
 * graphics or slide feed from another machine as it is a camera, and cropping one of those loses
 * content the operator put there on purpose. The layer's own box is how they choose the framing.
 */
@Composable
private fun NdiSourceContent(source: SceneSource.NdiSource, modifier: Modifier) {
    if (source.sourceName.isBlank() && source.sourceAddress.isBlank()) {
        NdiPlaceholder(stringResource(Res.string.canvas_placeholder_ndi_default), modifier)
        return
    }

    // Acquired in the effect, for the reason spelled out in [CameraSourceContent].
    var flows by remember { mutableStateOf<NdiFrameCache.NdiFlows?>(null) }
    DisposableEffect(source.sourceName, source.sourceAddress, source.lowBandwidth) {
        flows = SharedNdiFrameCache.acquire(source)
        onDispose {
            flows = null
            SharedNdiFrameCache.release(source)
        }
    }

    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val notConnected = remember { MutableStateFlow(false) }
    val frame by (flows?.frame ?: noFrame).collectAsState()
    val connected by (flows?.connected ?: notConnected).collectAsState()
    val label = source.sourceName.ifBlank { source.sourceAddress }

    val shown = frame
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        // Connected but with nothing on the wire yet is "waiting"; not connected is a runtime that
        // is not installed or a source that has gone away, and the two read differently on purpose.
        NdiPlaceholder(
            text = if (connected) stringResource(Res.string.canvas_placeholder_ndi_waiting, label)
                   else stringResource(Res.string.canvas_placeholder_ndi, label),
            modifier = modifier,
        )
    }
}

@Composable
private fun NdiPlaceholder(text: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScreenCaptureSourceContent(source: SceneSource.ScreenCaptureSource, modifier: Modifier) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source.captureMode, source.captureX, source.captureY, source.captureWidth, source.captureHeight, source.captureInterval, source.windowTitle, source.windowId) {
        try {
            val robot = Robot()
            while (isActive) {
                val capture: BufferedImage? = if (source.captureMode == "window" && source.windowId.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        val wid = source.windowId.removePrefix("0x").toLongOrNull(16) ?: 0L
                        // Try platform-specific occluded capture, fall back to Robot + bounds
                        WindowsWindowCapture.captureWindow(wid)
                            ?: X11WindowCapture.captureWindow(wid)
                            ?: run {
                                val rect = findWindowBounds(source.windowTitle)
                                if (rect != null && rect.width > 0 && rect.height > 0) robot.createScreenCapture(rect) else null
                            }
                    }
                } else if (source.captureMode == "window" && source.windowTitle.isNotBlank()) {
                    val rect = withContext(Dispatchers.IO) { findWindowBounds(source.windowTitle) }
                    if (rect != null && rect.width > 0 && rect.height > 0) robot.createScreenCapture(rect) else null
                } else {
                    val rect = Rectangle(source.captureX, source.captureY, source.captureWidth, source.captureHeight)
                    if (rect.width > 0 && rect.height > 0) robot.createScreenCapture(rect) else null
                }
                if (capture != null) {
                    frame = capture.toComposeImageBitmap()
                }
                delay(source.captureInterval.toLong().coerceAtLeast(MIN_CAPTURE_INTERVAL_MS))
            }
        } catch (_: Exception) {
            // Robot may fail in headless/restricted environments
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val currentFrame = frame
        if (currentFrame != null) {
            Image(
                painter = BitmapPainter(currentFrame),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(stringResource(Res.string.canvas_placeholder_screen_capture), color = Color.White, fontSize = 14.sp)
        }
    }
}

private fun findWindowBounds(windowTitle: String): Rectangle? =
    windowBoundsFor(System.getProperty("os.name", "").lowercase(), windowTitle, ::readCommandOutput)

/**
 * Where the window called [windowTitle] currently sits, or `null` when no window by that name is
 * open — which is the ordinary case after the operator picks a window and then closes it.
 *
 * [osName] is a parameter rather than read from `os.name` here so a test can ask for each platform's
 * lookup without swapping the system property, which skiko latches JVM-wide.
 */
internal fun windowBoundsFor(osName: String, windowTitle: String, run: CommandRunner): Rectangle? {
    return try {
        when {
            osName.contains("linux") -> linuxWindowBoundsFrom(windowTitle, run)
            osName.contains("win") -> findWindowsWindowBounds(windowTitle)
            osName.contains("mac") -> macWindowBoundsFrom(windowTitle, run)
            else -> null
        }
    } catch (_: Exception) { null }
}

/**
 * Where [title]'s window sits on X11, found by walking the root stacking list with `xprop` and asking
 * `xwininfo` about the one whose `_NET_WM_NAME` matches exactly.
 *
 * The match is exact rather than a prefix or contains: the operator picked this title out of a list
 * built the same way, and two windows of an application routinely differ only by a suffix. A window
 * whose geometry comes back with a zero width or height is skipped rather than returned, so the walk
 * continues to the next candidate — a mapped-but-unrealised window reports exactly that.
 */
internal fun linuxWindowBoundsFrom(title: String, run: CommandRunner): Rectangle? {
    val listOutput = run(listOf("xprop", "-root", "_NET_CLIENT_LIST_STACKING"), 0L).output
    val windowIds = Regex("0x[0-9a-fA-F]+").findAll(listOutput).map { it.value }.toList()

    for (wid in windowIds) {
        val nameOutput = run(listOf("xprop", "-id", wid, "_NET_WM_NAME"), 0L).output
        val name = Regex("\"(.+)\"").find(nameOutput)?.groupValues?.get(1)
        if (name != title) continue

        val bounds = parseXwininfoBounds(run(listOf("xwininfo", "-id", wid), 0L).output)
        if (bounds != null) return bounds
    }
    return null
}

/**
 * The geometry in one `xwininfo -id` report, whose interesting lines are four labelled integers among
 * a page of other properties. "Absolute" is the position on the screen rather than within the parent,
 * which is what a capture needs.
 *
 * `null` when the report carries no usable size, so the caller keeps looking rather than capturing an
 * empty rectangle.
 */
internal fun parseXwininfoBounds(output: String): Rectangle? {
    var x = 0; var y = 0; var w = 0; var h = 0
    for (line in output.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Absolute upper-left X:") -> x = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            trimmed.startsWith("Absolute upper-left Y:") -> y = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            trimmed.startsWith("Width:") -> w = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            trimmed.startsWith("Height:") -> h = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
        }
    }
    return if (w > 0 && h > 0) Rectangle(x, y, w, h) else null
}

private fun findWindowsWindowBounds(title: String): Rectangle? {
    return try {
        // Find the window by title using JNA EnumWindows
        val windows = WindowsWindowCapture.listWindows()
        val win = windows.find { it.title == title }
        if (win != null) {
            WindowsWindowCapture.getWindowBounds(win.hwnd)
        } else null
    } catch (_: Exception) { null }
}

/** The AppleScript that returns `x,y,w,h` for the first visible window named [title]. */
internal fun macWindowBoundsScript(title: String): String = """
    tell application "System Events"
        repeat with proc in (every process whose visible is true)
            repeat with win in (every window of proc)
                if name of win is "$title" then
                    set {x, y} to position of win
                    set {w, h} to size of win
                    return "" & x & "," & y & "," & w & "," & h
                end if
            end repeat
        end repeat
    end tell
""".trimIndent()

/**
 * Where [title]'s window sits on macOS, which only System Events can answer.
 *
 * The script returns the four numbers on one comma-separated line, and anything else means no window
 * matched — an empty answer, or an error message osascript wrote to the stream instead.
 */
internal fun macWindowBoundsFrom(title: String, run: CommandRunner): Rectangle? {
    val output = run(listOf("osascript", "-e", macWindowBoundsScript(title)), 0L).output.trim()
    val parts = output.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size == WINDOW_BOUNDS_FIELDS &&
        parts[BOUNDS_WIDTH_INDEX] > 0 && parts[BOUNDS_HEIGHT_INDEX] > 0
    ) {
        Rectangle(parts[0], parts[1], parts[BOUNDS_WIDTH_INDEX], parts[BOUNDS_HEIGHT_INDEX])
    } else null
}

@Composable
private fun BibleSourceContent(source: SceneSource.BibleSource, modifier: Modifier, fontScale: Float = 1f) {
    val bgColor = if (source.backgroundColor.equals("#00000000", ignoreCase = true))
        Color.Transparent
    else
        parseHexColor(source.backgroundColor)
    val textColor = parseHexColor(source.fontColor)
    val refColor = parseHexColor(source.referenceFontColor)
    val fontFamily = remember(source.fontFamily) { systemFontFamilyOrDefault(source.fontFamily) }
    val align = when (source.horizontalAlignment) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }
    val lineHeightMultiplier = source.lineSpacing / 100f
    val verticalAlign = when (source.verticalAlignment) {
        "top" -> Alignment.TopStart
        "bottom" -> Alignment.BottomStart
        else -> Alignment.Center
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor).clipToBounds(),
        contentAlignment = verticalAlign
    ) {
        if (source.curve != 0f) {
            CurvedBibleText(source, textColor, refColor, fontFamily, fontScale)
            return@Box
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = when (source.horizontalAlignment) {
                "left" -> Alignment.Start
                "right" -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
        ) {
            Text(
                text = source.verseText.ifEmpty { "Select a verse..." },
                color = if (source.verseText.isEmpty()) Color.Gray else textColor,
                fontSize = (source.fontSize * fontScale).sp,
                fontFamily = fontFamily,
                fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = textDecorationOf(source.underline, source.strikethrough),
                textAlign = align,
                lineHeight = (source.fontSize * fontScale * lineHeightMultiplier).sp,
                letterSpacing = trackingOf(source.letterSpacing),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (source.referenceText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = source.referenceText,
                    color = refColor,
                    fontSize = (source.referenceFontSize * fontScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = if (source.referenceBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (source.referenceItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecorationOf(
                        source.referenceUnderline,
                        source.referenceStrikethrough
                    ),
                    textAlign = align,
                    letterSpacing = trackingOf(source.letterSpacing),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

