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
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.PathPoint
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.Image
import java.io.File
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
            if (file.exists()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
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
        val brush = Brush.linearGradient(
            colorStops = arrayOf(0f to color1, pos to color2, 1f to color2),
            start = Offset(0.5f - 0.5f * cos(angleRad).toFloat(), 0.5f - 0.5f * sin(angleRad).toFloat()) * 1000f,
            end = Offset(0.5f + 0.5f * cos(angleRad).toFloat(), 0.5f + 0.5f * sin(angleRad).toFloat()) * 1000f
        )
        Box(modifier = modifier.fillMaxSize().background(brush))
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
    // In preview mode, show a placeholder. In presenter mode, a real VideoPlayer would be used.
    Box(
        modifier = modifier.fillMaxSize().background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPresenter) "Video: ${source.name}" else "Video: ${File(source.filePath).name}",
            color = Color.White,
            fontSize = 14.sp
        )
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

    // Build fill brush (solid or gradient)
    val fillBrush: Brush? = if (source.isGradient) {
        val color2 = parseHexColor(source.gradientColor2).copy(alpha = source.gradientColor2Opacity)
        val angleRad = Math.toRadians(source.gradientAngle.toDouble())
        val pos = source.gradientPosition.coerceIn(0.001f, 0.999f)
        Brush.linearGradient(
            colorStops = arrayOf(0f to fillColor, pos to color2, 1f to color2),
            start = Offset(0.5f - 0.5f * cos(angleRad).toFloat(), 0.5f - 0.5f * sin(angleRad).toFloat()) * 1000f,
            end = Offset(0.5f + 0.5f * cos(angleRad).toFloat(), 0.5f + 0.5f * sin(angleRad).toFloat()) * 1000f
        )
    } else if (fillColor.alpha > 0f) {
        Brush.linearGradient(listOf(fillColor, fillColor))
    } else null

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (source.shapeType) {
            "rectangle" -> {
                if (fillBrush != null) {
                    drawRect(brush = fillBrush, size = size)
                }
                drawRect(color = strokeColor, size = size, style = stroke)
            }
            "ellipse" -> {
                if (fillBrush != null) {
                    drawOval(brush = fillBrush, size = size)
                }
                drawOval(color = strokeColor, size = size, style = stroke)
            }
            "line" -> {
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            "arrow" -> {
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Arrowhead
                val arrowSize = (strokeWidth * 4f).coerceAtLeast(12f)
                val angle = kotlin.math.atan2(h, w)
                val ax1 = w - arrowSize * kotlin.math.cos(angle - 0.4f)
                val ay1 = h - arrowSize * kotlin.math.sin(angle - 0.4f)
                val ax2 = w - arrowSize * kotlin.math.cos(angle + 0.4f)
                val ay2 = h - arrowSize * kotlin.math.sin(angle + 0.4f)
                val arrowPath = Path().apply {
                    moveTo(w, h)
                    lineTo(ax1, ay1)
                    moveTo(w, h)
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
            org.jetbrains.skia.Image.makeFromEncoded(
                java.io.ByteArrayOutputStream().also {
                    javax.imageio.ImageIO.write(img, "PNG", it)
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
            Text("QR", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CameraSourceContent(
    source: SceneSource.CameraSource,
    modifier: Modifier,
    isPresenter: Boolean
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (source.deviceName.isNotEmpty()) "Camera: ${source.deviceName}" else "Camera",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ScreenCaptureSourceContent(source: SceneSource.ScreenCaptureSource, modifier: Modifier) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source.captureMode, source.captureX, source.captureY, source.captureWidth, source.captureHeight, source.captureInterval, source.windowTitle) {
        try {
            val robot = Robot()
            while (isActive) {
                val rect = if (source.captureMode == "window" && source.windowTitle.isNotBlank()) {
                    findWindowBounds(source.windowTitle)
                } else {
                    Rectangle(source.captureX, source.captureY, source.captureWidth, source.captureHeight)
                }
                if (rect != null && rect.width > 0 && rect.height > 0) {
                    val capture = robot.createScreenCapture(rect)
                    val skiaImage = org.jetbrains.skia.Image.makeFromEncoded(
                        java.io.ByteArrayOutputStream().also {
                            javax.imageio.ImageIO.write(capture, "PNG", it)
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
            Text("Screen Capture", color = Color.White, fontSize = 14.sp)
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
    return try {
        // Try xdotool first
        val idProcess = ProcessBuilder("xdotool", "search", "--name", title)
            .redirectErrorStream(true).start()
        val windowId = idProcess.inputStream.bufferedReader().readText().lines()
            .firstOrNull { it.isNotBlank() } ?: return null
        idProcess.waitFor()

        val geomProcess = ProcessBuilder("xdotool", "getwindowgeometry", "--shell", windowId)
            .redirectErrorStream(true).start()
        val geomOutput = geomProcess.inputStream.bufferedReader().readText()
        geomProcess.waitFor()

        val sizeProcess = ProcessBuilder("xdotool", "getwindowfocus", "getwindowgeometry", "--shell", windowId)
            .redirectErrorStream(true).start()
        val sizeOutput = sizeProcess.inputStream.bufferedReader().readText()
        sizeProcess.waitFor()

        var x = 0; var y = 0; var w = 0; var h = 0
        for (line in (geomOutput + "\n" + sizeOutput).lines()) {
            when {
                line.startsWith("X=") -> x = line.substringAfter("=").toIntOrNull() ?: 0
                line.startsWith("Y=") -> y = line.substringAfter("=").toIntOrNull() ?: 0
                line.startsWith("WIDTH=") -> w = line.substringAfter("=").toIntOrNull() ?: 0
                line.startsWith("HEIGHT=") -> h = line.substringAfter("=").toIntOrNull() ?: 0
            }
        }
        if (w > 0 && h > 0) Rectangle(x, y, w, h) else null
    } catch (_: Exception) { null }
}

private fun findWindowsWindowBounds(title: String): Rectangle? {
    return try {
        val script = """
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            public class Win32 {
                [DllImport("user32.dll")]
                public static extern IntPtr FindWindow(string lpClassName, string lpWindowName);
                [DllImport("user32.dll")]
                public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
                [StructLayout(LayoutKind.Sequential)]
                public struct RECT { public int Left, Top, Right, Bottom; }
            }
"@
            ${"$"}hwnd = [Win32]::FindWindow(${"$"}null, "$title")
            ${"$"}rect = New-Object Win32+RECT
            [Win32]::GetWindowRect(${"$"}hwnd, [ref]${"$"}rect)
            "${"$"}(${"$"}rect.Left),${"$"}(${"$"}rect.Top),${"$"}(${"$"}rect.Right - ${"$"}rect.Left),${"$"}(${"$"}rect.Bottom - ${"$"}rect.Top)"
        """.trimIndent()
        val process = ProcessBuilder("powershell", "-Command", script)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        val parts = output.split(",").mapNotNull { it.toIntOrNull() }
        if (parts.size == 4 && parts[2] > 0 && parts[3] > 0) {
            Rectangle(parts[0], parts[1], parts[2], parts[3])
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
