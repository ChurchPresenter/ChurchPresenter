package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_image_not_found
import churchpresenter.composeapp.generated.resources.canvas_placeholder_qr
import churchpresenter.composeapp.generated.resources.canvas_placeholder_camera
import churchpresenter.composeapp.generated.resources.canvas_placeholder_camera_default
import churchpresenter.composeapp.generated.resources.canvas_placeholder_screen_capture
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.PathPoint
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.WindowsWindowCapture
import org.churchpresenter.app.churchpresenter.utils.X11WindowCapture
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import java.io.ByteArrayInputStream
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
import androidx.compose.ui.geometry.Size

@Composable
fun SceneSourceRenderer(
    source: SceneSource,
    modifier: Modifier = Modifier,
    isPresenter: Boolean = false
) {
    when (source) {
        is SceneSource.ImageSource -> ImageSourceContent(source, modifier)
        is SceneSource.TextSource -> TextSourceContent(source, modifier)
        is SceneSource.ColorSource -> ColorSourceContent(source, modifier)
        is SceneSource.VideoSource -> VideoSourceContent(source, modifier, isPresenter)
        is SceneSource.BrowserSource -> BrowserSourceContent(source, modifier, isPresenter)
        is SceneSource.ShapeSource -> ShapeSourceContent(source, modifier)
        is SceneSource.ClockSource -> ClockSourceContent(source, modifier)
        is SceneSource.QRCodeSource -> QRCodeSourceContent(source, modifier)
        is SceneSource.CameraSource -> CameraSourceContent(source, modifier, isPresenter)
        is SceneSource.ScreenCaptureSource -> ScreenCaptureSourceContent(source, modifier)
    }
}

@Composable
private fun ImageSourceContent(source: SceneSource.ImageSource, modifier: Modifier) {
    val bitmap = remember(source.filePath) {
        try {
            val file = File(source.filePath)
            if (file.exists()) SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            else null
        } catch (_: Exception) { null }
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
private fun TextSourceContent(source: SceneSource.TextSource, modifier: Modifier) {
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

    Box(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = source.text,
            color = textColor,
            fontSize = source.fontSize.sp,
            fontFamily = fontFamily,
            fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = align,
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
    isPresenter: Boolean
) {
    val file = remember(source.filePath) { if (source.filePath.isNotBlank()) File(source.filePath) else null }
    if (file == null || !file.exists() || !isVlcAvailable) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (!isVlcAvailable) "VLC not found"
                       else if (file == null) "No video selected"
                       else "File not found: ${source.filePath}",
                color = Color.White,
                fontSize = 14.sp
            )
        }
        return
    }

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }

    val factory = remember {
        try { MediaPlayerFactory() } catch (_: Throwable) { null }
    } ?: return

    val mediaPlayer = remember(factory) {
        try { factory.mediaPlayers().newEmbeddedMediaPlayer() } catch (_: Throwable) { null }
    } ?: return

    DisposableEffect(source.filePath) {
        var bufferedImage: BufferedImage? = null

        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                bufferedImage = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) { }
        }

        val renderCallback = RenderCallback { _, nativeBuffers, _ ->
            val img = bufferedImage ?: return@RenderCallback
            if (nativeBuffers == null || nativeBuffers.isEmpty()) return@RenderCallback
            val pixelData = (img.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
            try {
                val buf = nativeBuffers[0] ?: return@RenderCallback
                buf.rewind()
                buf.asIntBuffer().get(pixelData, 0, pixelData.size.coerceAtMost(buf.remaining() / 4))
                currentFrame.value = img.toComposeImageBitmap()
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
        delay(100)
        try {
            mediaPlayer.audio().setVolume((source.volume * 100).toInt())
            val options = mutableListOf<String>()
            if (source.loop) options.add(":input-repeat=65535")
            mediaPlayer.media().play(file.absolutePath, *options.toTypedArray())
        } catch (_: Throwable) { }
    }

    val frame = currentFrame.value
    if (frame != null) {
        Image(
            bitmap = frame,
            contentDescription = source.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading...", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BrowserSourceContent(
    source: SceneSource.BrowserSource,
    modifier: Modifier,
    isPresenter: Boolean
) {
    // In preview mode, show a placeholder. In presenter mode, a real CefBrowser would be used.
    Box(
        modifier = modifier.fillMaxSize().background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Browser: ${source.url}",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ShapeSourceContent(source: SceneSource.ShapeSource, modifier: Modifier) {
    val strokeColor = parseHexColor(source.strokeColor).copy(alpha = source.strokeOpacity)
    val fillColor = parseHexColor(source.fillColor).copy(alpha = source.fillOpacity)
    val strokeWidth = source.strokeWidth
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
                val arrowSize = (strokeWidth * 4f).coerceAtLeast(12f)
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
private fun ClockSourceContent(source: SceneSource.ClockSource, modifier: Modifier) {
    val bgColor = parseHexColor(source.backgroundColor)
    val fontColor = parseHexColor(source.fontColor)
    val fontFamily = systemFontFamilyOrDefault(source.fontFamily)

    var displayText by remember { mutableStateOf("") }

    LaunchedEffect(source.mode, source.timeFormat, source.showHours, source.showSeconds, source.targetHour, source.targetMinute, source.targetSecond) {
        while (isActive) {
            val now = LocalTime.now()
            displayText = when (source.mode) {
                "countdown" -> {
                    val target = LocalTime.of(source.targetHour, source.targetMinute, source.targetSecond)
                    val remaining = java.time.Duration.between(now, target)
                    if (remaining.isNegative) {
                        if (source.showHours && source.showSeconds) "00:00:00"
                        else if (source.showHours) "00:00"
                        else if (source.showSeconds) "00:00"
                        else "00"
                    } else {
                        val h = remaining.toHours()
                        val m = remaining.toMinutesPart()
                        val s = remaining.toSecondsPart()
                        buildString {
                            if (source.showHours) { append("%02d:".format(h)) }
                            append("%02d".format(m))
                            if (source.showSeconds) { append(":%02d".format(s)) }
                        }
                    }
                }
                else -> {
                    val pattern = buildString {
                        if (source.showHours) {
                            append(if (source.timeFormat == "12h") "hh:" else "HH:")
                        }
                        append("mm")
                        if (source.showSeconds) append(":ss")
                        if (source.timeFormat == "12h") append(" a")
                    }
                    now.format(DateTimeFormatter.ofPattern(pattern))
                }
            }
            delay(1000)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = fontColor,
            fontSize = source.fontSize.sp,
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
    isPresenter: Boolean
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

    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    val processRef = remember { mutableStateOf<Process?>(null) }

    DisposableEffect(source.devicePath) {
        onDispose {
            processRef.value?.destroyForcibly()
            processRef.value = null
        }
    }

    LaunchedEffect(source.devicePath) {
        val path = source.devicePath
        val command = when {
            path.startsWith("dshow://") -> {
                val deviceName = path.removePrefix("dshow://").removePrefix(":dshow-vdev=")
                listOf("ffmpeg", "-f", "dshow", "-i", "video=$deviceName",
                    "-an", "-vf", "fps=30", "-f", "image2pipe", "-vcodec", "bmp", "-")
            }
            path.startsWith("v4l2://") -> {
                val device = path.removePrefix("v4l2://")
                listOf("ffmpeg", "-f", "v4l2", "-i", device,
                    "-an", "-vf", "fps=30", "-f", "image2pipe", "-vcodec", "bmp", "-")
            }
            path.startsWith("avfoundation://") -> {
                val index = path.removePrefix("avfoundation://")
                listOf("ffmpeg", "-f", "avfoundation", "-i", "$index:none",
                    "-an", "-vf", "fps=30", "-f", "image2pipe", "-vcodec", "bmp", "-")
            }
            else -> return@LaunchedEffect
        }

        val process = withContext(Dispatchers.IO) {
            try {
                ProcessBuilder(command).redirectErrorStream(false).start()
            } catch (_: Throwable) { null }
        } ?: return@LaunchedEffect
        processRef.value = process

        val inputStream = process.inputStream
        val headerBuf = ByteArray(2)

        while (isActive) {
            val bmpData = withContext(Dispatchers.IO) {
                try {
                    // Read BMP magic bytes "BM"
                    var read = 0
                    while (read < 2) {
                        val b = inputStream.read()
                        if (b == -1) return@withContext null
                        headerBuf[read++] = b.toByte()
                    }
                    if (headerBuf[0] != 'B'.code.toByte() || headerBuf[1] != 'M'.code.toByte()) return@withContext null

                    // Read BMP file size (bytes 2-5, little-endian)
                    val sizeBytes = ByteArray(4)
                    read = 0
                    while (read < 4) {
                        val r = inputStream.read(sizeBytes, read, 4 - read)
                        if (r == -1) return@withContext null
                        read += r
                    }
                    val fileSize = (sizeBytes[0].toInt() and 0xFF) or
                            ((sizeBytes[1].toInt() and 0xFF) shl 8) or
                            ((sizeBytes[2].toInt() and 0xFF) shl 16) or
                            ((sizeBytes[3].toInt() and 0xFF) shl 24)

                    // Read full BMP
                    val data = ByteArray(fileSize)
                    data[0] = 'B'.code.toByte()
                    data[1] = 'M'.code.toByte()
                    System.arraycopy(sizeBytes, 0, data, 2, 4)
                    var remaining = fileSize - 6
                    var offset = 6
                    while (remaining > 0) {
                        val r = inputStream.read(data, offset, remaining)
                        if (r == -1) return@withContext null
                        offset += r
                        remaining -= r
                    }
                    data
                } catch (_: Throwable) { null }
            } ?: break

            val img = withContext(Dispatchers.IO) {
                ImageIO.read(ByteArrayInputStream(bmpData))
            }
            if (img != null) {
                frame = img.toComposeImageBitmap()
            }
        }

        process.destroyForcibly()
    }

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
                text = if (source.deviceName.isNotEmpty()) stringResource(Res.string.canvas_placeholder_camera, source.deviceName)
                       else stringResource(Res.string.canvas_placeholder_camera_default),
                color = Color.White,
                fontSize = 14.sp
            )
        }
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
                    val skiaImage = SkiaImage.makeFromEncoded(
                        ByteArrayOutputStream().also {
                            ImageIO.write(capture, "PNG", it)
                        }.toByteArray()
                    )
                    frame = skiaImage.toComposeImageBitmap()
                }
                delay(source.captureInterval.toLong().coerceAtLeast(33))
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

private fun findWindowBounds(windowTitle: String): Rectangle? {
    val osName = System.getProperty("os.name", "").lowercase()
    return try {
        when {
            osName.contains("linux") -> findLinuxWindowBounds(windowTitle)
            osName.contains("win") -> findWindowsWindowBounds(windowTitle)
            osName.contains("mac") -> findMacWindowBounds(windowTitle)
            else -> null
        }
    } catch (_: Exception) { null }
}

private fun findLinuxWindowBounds(title: String): Rectangle? {
    // Primary: xprop + xwininfo (available on all X11 systems)
    try {
        // Find window ID by title using xprop on root's client list
        val listProcess = ProcessBuilder("xprop", "-root", "_NET_CLIENT_LIST_STACKING")
            .redirectErrorStream(true).start()
        val listOutput = listProcess.inputStream.bufferedReader().readText()
        listProcess.waitFor()
        val windowIds = Regex("0x[0-9a-fA-F]+").findAll(listOutput).map { it.value }.toList()

        for (wid in windowIds) {
            val nameProcess = ProcessBuilder("xprop", "-id", wid, "_NET_WM_NAME")
                .redirectErrorStream(true).start()
            val nameOutput = nameProcess.inputStream.bufferedReader().readText()
            nameProcess.waitFor()
            val name = Regex("\"(.+)\"").find(nameOutput)?.groupValues?.get(1) ?: continue
            if (name != title) continue

            // Found the window, get bounds with xwininfo
            val infoProcess = ProcessBuilder("xwininfo", "-id", wid)
                .redirectErrorStream(true).start()
            val infoOutput = infoProcess.inputStream.bufferedReader().readText()
            infoProcess.waitFor()

            var x = 0; var y = 0; var w = 0; var h = 0
            for (line in infoOutput.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Absolute upper-left X:") -> x = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                    trimmed.startsWith("Absolute upper-left Y:") -> y = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                    trimmed.startsWith("Width:") -> w = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                    trimmed.startsWith("Height:") -> h = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            if (w > 0 && h > 0) return Rectangle(x, y, w, h)
        }
    } catch (_: Exception) {}
    return null
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

private fun findMacWindowBounds(title: String): Rectangle? {
    return try {
        val script = """
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
        val process = ProcessBuilder("osascript", "-e", script)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        val parts = output.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size == 4 && parts[2] > 0 && parts[3] > 0) {
            Rectangle(parts[0], parts[1], parts[2], parts[3])
        } else null
    } catch (_: Exception) { null }
}

