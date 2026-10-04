package org.churchpresenter.canvas

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.sharedui.utils.FfmpegBinary
import org.churchpresenter.sharedui.utils.CommandRunner
import org.churchpresenter.sharedui.utils.readCommandOutput

data class CameraFormat(
    val width: Int,
    val height: Int,
    val fps: Int,
    val displayName: String = "${width}x${height} @ ${fps}fps",
    val encodedValue: String = "${width}x${height}@${fps}"
)

/** What a camera runs at when its listing names a size but no rate. Every capture device does 30. */
internal const val DEFAULT_CAMERA_FPS = 30

internal fun Set<Triple<Int, Int, Int>>.toSortedFormats(): List<CameraFormat> =
    sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.first * it.second }.thenByDescending { it.third })
        .map { (w, h, fps) -> CameraFormat(w, h, fps) }

private val cameraFormatCache = mutableMapOf<String, List<CameraFormat>>()

fun listCameraFormats(devicePath: String, deviceName: String): List<CameraFormat> {
    cameraFormatCache[devicePath]?.let { return it }
    val formats = cameraFormatsFor(
        System.getProperty("os.name", "").lowercase(), devicePath, deviceName, ::readCommandOutput
    )
    Log.info("Camera", "Found ${formats.size} format(s) for $deviceName")
    formats.forEach { Log.info("Camera", "  ${it.displayName}") }
    if (formats.isNotEmpty()) cameraFormatCache[devicePath] = formats
    return formats
}

internal fun cameraFormatsFor(
    osName: String,
    devicePath: String,
    deviceName: String,
    run: CommandRunner,
): List<CameraFormat> = when {
    osName.contains("win") && devicePath.startsWith("dshow://") ->
        dshowFormatsFrom(deviceName, run)
    osName.contains("linux") && devicePath.startsWith("v4l2://") ->
        v4l2FormatsFrom(devicePath.removePrefix("v4l2://"), run)
    osName.contains("mac") && devicePath.startsWith("avfoundation://") ->
        avfoundationFormatsFrom(devicePath.removePrefix("avfoundation://"), run)
    else -> emptyList()
}

internal fun dshowFormatsFrom(deviceName: String, run: CommandRunner): List<CameraFormat> {
    val name = deviceName.removePrefix(":dshow-vdev=")
    val result = run(listOf(FfmpegBinary.path, "-f", "dshow", "-list_options", "true", "-i", "video=$name"), 5L)
    return parseDshowFormats(result.output)
}

internal fun parseDshowFormats(output: String): List<CameraFormat> {
    val formats = mutableSetOf<Triple<Int, Int, Int>>()
    val sizePattern = Regex("""s=(\d+)x(\d+)""")
    val fpsPattern = Regex("""fps=(\d+)""")
    for (line in output.lines()) {
        val sizeMatch = if (line.contains("s=")) sizePattern.find(line) else null
        val w = sizeMatch?.groupValues?.get(1)?.toIntOrNull()
        val h = sizeMatch?.groupValues?.get(2)?.toIntOrNull()
        if (w == null || h == null) continue
        fpsPattern.findAll(line).forEach { fm ->
            fm.groupValues[1].toIntOrNull()?.let { formats.add(Triple(w, h, it)) }
        }
    }
    return formats.toSortedFormats()
}

internal fun v4l2FormatsFrom(device: String, run: CommandRunner): List<CameraFormat> {
    val fromFfmpeg = parseV4l2Formats(
        run(listOf(FfmpegBinary.path, "-f", "v4l2", "-list_formats", "all", "-i", device), 0L).output
    )
    if (fromFfmpeg.isNotEmpty()) return fromFfmpeg
    return parseV4l2CtlFormats(
        run(listOf("v4l2-ctl", "--list-formats-ext", "-d", device), 0L).output
    )
}

internal fun parseV4l2Formats(output: String): List<CameraFormat> {
    val formats = mutableSetOf<Triple<Int, Int, Int>>()
    val sizePattern = Regex("""(\d{3,5})x(\d{3,5})""")
    val fpsPattern = Regex("""(\d+(?:\.\d+)?)\s*fps""")
    for (line in output.lines()) {
        val sizeMatch = sizePattern.find(line)
        val w = sizeMatch?.groupValues?.get(1)?.toIntOrNull()
        val h = sizeMatch?.groupValues?.get(2)?.toIntOrNull()
        if (w == null || h == null) continue
        val fps = fpsPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: DEFAULT_CAMERA_FPS
        formats.add(Triple(w, h, fps))
    }
    return formats.toSortedFormats()
}

internal fun parseV4l2CtlFormats(output: String): List<CameraFormat> {
    val formats = mutableSetOf<Triple<Int, Int, Int>>()
    val sizePattern = Regex("""(\d{3,5})x(\d{3,5})""")
    val fpsPattern = Regex("""(\d+(?:\.\d+)?)\s*fps""")
    var lastW = 0
    var lastH = 0
    for (line in output.lines()) {
        val sizeMatch = sizePattern.find(line)
        if (sizeMatch != null) {
            lastW = sizeMatch.groupValues[1].toIntOrNull() ?: 0
            lastH = sizeMatch.groupValues[2].toIntOrNull() ?: 0
        }
        val fpsMatch = fpsPattern.find(line)
        if (fpsMatch != null && lastW > 0 && lastH > 0) {
            val fps = fpsMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: DEFAULT_CAMERA_FPS
            formats.add(Triple(lastW, lastH, fps))
        }
    }
    return formats.toSortedFormats()
}
