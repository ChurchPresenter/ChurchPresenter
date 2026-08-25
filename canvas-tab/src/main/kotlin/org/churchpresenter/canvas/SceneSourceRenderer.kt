package org.churchpresenter.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.ui.PictureDecoder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.canvas_image_not_found
import org.churchpresenter.resources.generated.resources.canvas_placeholder_qr
import org.churchpresenter.resources.generated.resources.canvas_placeholder_camera
import org.churchpresenter.resources.generated.resources.canvas_placeholder_camera_default
import org.churchpresenter.resources.generated.resources.canvas_camera_error_decklink_in_use
import org.churchpresenter.resources.generated.resources.canvas_camera_error_ffmpeg_missing
import org.churchpresenter.resources.generated.resources.canvas_camera_error_unsupported_device
import org.churchpresenter.resources.generated.resources.canvas_camera_error_unavailable
import org.churchpresenter.resources.generated.resources.canvas_video_vlc_load_failed
import org.churchpresenter.resources.generated.resources.canvas_video_vlc_not_found
import org.churchpresenter.resources.generated.resources.canvas_video_no_selection
import org.churchpresenter.resources.generated.resources.canvas_video_file_not_found
import org.churchpresenter.resources.generated.resources.canvas_video_loading
import org.churchpresenter.resources.generated.resources.canvas_placeholder_screen_capture
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ui.Utils.parseHexColor
import org.churchpresenter.ui.Utils.systemFontFamilyOrDefault
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import org.churchpresenter.ui.readCommandOutput
import org.churchpresenter.ui.CommandRunner

private const val FRAME_INTERVAL_MS = 16L
private const val PLAYER_SETTLE_MS = 100L
private const val VOLUME_PERCENT_SCALE = 100
private const val URL_DEBOUNCE_MS = 800L
private const val ERROR_TEXT_COLOR = 0xFFFF8888
private const val POLL_INTERVAL_MS = 1000L
private const val MIN_CAPTURE_INTERVAL_MS = 33L
private const val WINDOW_BOUNDS_FIELDS = 4
private const val BOUNDS_WIDTH_INDEX = 2
private const val BOUNDS_HEIGHT_INDEX = 3

// libvlc media options. Written out per branch rather than collected into a list and spread: play()
// is a Java vararg, so a spread copies the array on every call, and building the list allocated one
// more object again for what is at most two constant strings.
private const val VLC_OPT_TIGHT_CLOCK = ":clock-jitter=0"
private const val VLC_OPT_LOOP = ":input-repeat=65535"

@Composable
fun SceneSourceRenderer(
    source: SceneSource,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f
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
        is SceneSource.CameraSource -> CameraSourceContent(source, modifier)
        is SceneSource.ScreenCaptureSource -> ScreenCaptureSourceContent(source, modifier)
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
        Text(
            text = source.text,
            color = textColor,
            fontSize = (source.fontSize * fontScale).sp,
            fontFamily = fontFamily,
            fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = align,
            lineHeight = (source.fontSize * fontScale * lineHeightMultiplier).sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp)
        )
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
    val videoSupport = LocalCanvasVideoSupport.current
    val file = remember(source.filePath) { if (source.filePath.isNotBlank()) File(source.filePath) else null }
    if (file == null || !file.exists() || !videoSupport.available) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (videoSupport.loadFailed) stringResource(Res.string.canvas_video_vlc_load_failed)
                       else if (!videoSupport.available) stringResource(Res.string.canvas_video_vlc_not_found)
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

    // Only re-create browser when id or viewport size changes
    val browserFlows = remember(source.id, source.renderWidth, source.renderHeight) {
        SharedBrowserFrameCache.acquire(
            source.id, source.url, source.renderWidth, source.renderHeight,
            source.customCss, source.fps, source.forceTransparent
        )
    }
    DisposableEffect(source.id) {
        onDispose {
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

    val frame by browserFlows.frame.collectAsState()
    val error by browserFlows.error.collectAsState()

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

    var displayText by remember { mutableStateOf("") }

    val totalSeconds = source.targetHour * 3600 + source.targetMinute * 60 + source.targetSecond

    if (source.mode == "countdown") {
        // Sync TimerStateManager when duration fields change
        LaunchedEffect(totalSeconds) {
            TimerStateManager.onDurationChanged(source.id, totalSeconds)
        }

        // Tick loop lives here so it runs even when the source is not selected
        val timerState = TimerStateManager.getState(source.id, totalSeconds)
        LaunchedEffect(timerState.isRunning) {
            while (timerState.isRunning) {
                delay(POLL_INTERVAL_MS)
                TimerStateManager.tick(source.id)
            }
        }

        val remaining = TimerStateManager.getState(source.id, totalSeconds).remainingSeconds
        val h = remaining / 3600
        val m = (remaining % 3600) / 60
        val s = remaining % 60
        displayText = buildString {
            if (source.showHours) append("%02d:".format(h))
            append("%02d".format(m))
            if (source.showSeconds) append(":%02d".format(s))
        }
    } else {
        LaunchedEffect(source.timeFormat, source.showHours, source.showSeconds) {
            while (isActive) {
                val now = LocalTime.now()
                val pattern = buildString {
                    if (source.showHours) {
                        append(if (source.timeFormat == "12h") "hh:" else "HH:")
                    }
                    append("mm")
                    if (source.showSeconds) append(":ss")
                    if (source.timeFormat == "12h") append(" a")
                }
                displayText = now.format(DateTimeFormatter.ofPattern(pattern))
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = fontColor,
            fontSize = (source.fontSize * fontScale).sp,
            fontFamily = fontFamily,
            fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal
        )
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
) {
    val deckLink = LocalCanvasDeckLink.current
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

    // Use shared cache so canvas preview and presenter output share one capture process
    val cameraFlows = remember(source.devicePath, source.videoFormat, source.videoConnection, source.deckLinkIndex) {
        SharedCameraFrameCache.acquire(source, deckLink)
    }
    DisposableEffect(source.devicePath, source.videoFormat, source.videoConnection, source.deckLinkIndex) {
        onDispose {
            SharedCameraFrameCache.release(source, deckLink)
        }
    }

    val frame by cameraFlows.frame.collectAsState()
    val failure by cameraFlows.error.collectAsState()
    val error = failure?.let { cameraFailureMessage(it) }

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
            Text(
                text = error
                    ?: if (source.deviceName.isNotEmpty()) stringResource(Res.string.canvas_placeholder_camera, source.deviceName)
                       else stringResource(Res.string.canvas_placeholder_camera_default),
                color = if (error != null) Color(ERROR_TEXT_COLOR) else Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The wording for a [CameraFailure] — the cache reports a reason, the strings live here. */
@Composable
private fun cameraFailureMessage(failure: CameraFailure): String = stringResource(
    when (failure) {
        CameraFailure.DECKLINK_INPUT_IN_USE -> Res.string.canvas_camera_error_decklink_in_use
        CameraFailure.FFMPEG_MISSING -> Res.string.canvas_camera_error_ffmpeg_missing
        CameraFailure.UNSUPPORTED_DEVICE_PATH -> Res.string.canvas_camera_error_unsupported_device
        CameraFailure.DEVICE_UNAVAILABLE -> Res.string.canvas_camera_error_unavailable
    }
)

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
                textAlign = align,
                lineHeight = (source.fontSize * fontScale * lineHeightMultiplier).sp,
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
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

