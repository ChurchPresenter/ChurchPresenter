package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.scene.SceneSource
import java.io.File
import org.churchpresenter.ui.readCommandOutput
import org.churchpresenter.ui.CommandRunner

internal fun selectedCameraName(devices: List<CameraDevice>, source: SceneSource.CameraSource): String =
    if (source.isDeckLink) {
        devices.find { it.isDeckLink && it.deckLinkIndex == source.deckLinkIndex }?.displayName
            ?: devices.first().displayName
    } else {
        devices.find { !it.isDeckLink && it.path == source.devicePath }?.displayName
            ?: if (source.devicePath.isNotEmpty()) source.devicePath else devices.first().displayName
    }

internal fun cameraSourceOn(
    source: SceneSource.CameraSource,
    device: CameraDevice
): SceneSource.CameraSource = source.copy(
    devicePath = device.path,
    deviceName = device.name,
    videoFormat = "",
    videoConnection = 0,
    isDeckLink = device.isDeckLink,
    deckLinkIndex = device.deckLinkIndex
)

internal fun selectedConnectionName(
    connections: List<DeckLinkManager.VideoConnection>,
    videoConnection: Int
): String = connections.find { it.value == videoConnection }?.name ?: connections.first().name

internal fun selectedModeName(
    modes: List<DeckLinkManager.InputMode>,
    videoFormat: String,
    autoLabel: String
): String =
    if (videoFormat.isEmpty()) autoLabel
    else modes.find { it.encodedValue == videoFormat }?.name ?: autoLabel

internal fun selectedFormatName(
    formats: List<CameraFormat>,
    videoFormat: String,
    autoLabel: String
): String =
    if (videoFormat.isEmpty()) autoLabel
    else formats.find { it.encodedValue == videoFormat }?.displayName ?: autoLabel

internal data class CameraDevice(
    val name: String,
    val path: String,
    val displayName: String,
    val isDeckLink: Boolean = false,
    val deckLinkIndex: Int = -1
)

internal fun listCameraDevicesWithDeckLink(deckLinkDeviceFormat: String = "DeckLink: %1\$s"): List<CameraDevice> {
    val devices = mutableListOf<CameraDevice>()

    if (DeckLinkManager.isAvailable()) {
        val deckLinkDevices = DeckLinkManager.listDevices()
        for (device in deckLinkDevices) {
            devices.add(CameraDevice(
                name = device.name,
                path = "decklink://${device.index}",
                displayName = deckLinkDeviceFormat.format(device.name),
                isDeckLink = true,
                deckLinkIndex = device.index
            ))
        }
    }

    val hasDeckLink = devices.any { it.isDeckLink }
    listCameraDevices().filterNot { cam ->
        hasDeckLink && cam.name.lowercase().contains("decklink")
    }.let { devices.addAll(it) }

    System.err.println("[Camera] Found ${devices.size} total device(s) (${devices.count { it.isDeckLink }} DeckLink)")
    return devices
}

internal data class CameraFormat(
    val width: Int,
    val height: Int,
    val fps: Int,
    val displayName: String = "${width}x${height} @ ${fps}fps",
    val encodedValue: String = "${width}x${height}@${fps}"
)

private fun Set<Triple<Int, Int, Int>>.toSortedFormats(): List<CameraFormat> =
    sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.first * it.second }.thenByDescending { it.third })
        .map { (w, h, fps) -> CameraFormat(w, h, fps) }

private val cameraFormatCache = mutableMapOf<String, List<CameraFormat>>()

internal fun listCameraFormats(devicePath: String, deviceName: String): List<CameraFormat> {
    cameraFormatCache[devicePath]?.let { return it }
    val formats = cameraFormatsFor(
        System.getProperty("os.name", "").lowercase(), devicePath, deviceName, ::readCommandOutput
    )
    System.err.println("[Camera] Found ${formats.size} format(s) for $deviceName")
    formats.forEach { System.err.println("[Camera]   ${it.displayName}") }
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
    val result = run(listOf("ffmpeg", "-f", "dshow", "-list_options", "true", "-i", "video=$name"), 5L)
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
        run(listOf("ffmpeg", "-f", "v4l2", "-list_formats", "all", "-i", device), 0L).output
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
        val fps = fpsPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 30
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
            val fps = fpsMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: 30
            formats.add(Triple(lastW, lastH, fps))
        }
    }
    return formats.toSortedFormats()
}

internal fun avfoundationFormatsFrom(deviceIndex: String, run: CommandRunner): List<CameraFormat> =
    parseAvfoundationFormats(
        run(
            listOf("ffmpeg", "-f", "avfoundation", "-list_formats", "all", "-i", "$deviceIndex:none"),
            0L,
        ).output
    )

internal fun parseAvfoundationFormats(output: String): List<CameraFormat> {
    val formats = mutableSetOf<Triple<Int, Int, Int>>()
    val sizePattern = Regex("""(\d{3,5})x(\d{3,5})""")
    val fpsPattern = Regex("""(\d+(?:\.\d+)?)\s*fps""")
    for (line in output.lines()) {
        val sizeMatch = sizePattern.find(line)
        val w = sizeMatch?.groupValues?.get(1)?.toIntOrNull()
        val h = sizeMatch?.groupValues?.get(2)?.toIntOrNull()
        if (w == null || h == null) continue
        val fps = fpsPattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 30
        formats.add(Triple(w, h, fps))
    }
    return formats.toSortedFormats()
}

internal fun isFfmpegAvailable(): Boolean {
    return try {
        val process = ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        process.waitFor() == 0
    } catch (_: Exception) { false }
}

private fun listCameraDevices(): List<CameraDevice> {
    val devices = cameraDevicesFor(System.getProperty("os.name", "").lowercase(), ::readCommandOutput)
    System.err.println("[Camera] Found ${devices.size} camera device(s):")
    devices.forEach { System.err.println("[Camera]   ${it.displayName} -> ${it.path}") }
    return devices
}

internal fun cameraDevicesFor(osName: String, run: CommandRunner): List<CameraDevice> = when {
    osName.contains("linux") -> listLinuxCameras()
    osName.contains("win") -> windowsCamerasFrom(run)
    osName.contains("mac") -> macCamerasFrom(run)
    else -> emptyList()
}

internal fun listLinuxCameras(
    devDir: File = File("/dev"),
    v4l2ClassDir: File = File("/sys/class/video4linux")
): List<CameraDevice> {
    return try {
        devDir.listFiles { f -> f.name.startsWith("video") }
            ?.sorted()
            ?.map { file ->
                val name = try {
                    val nameFile = File(v4l2ClassDir, "${file.name}/name")
                    if (nameFile.exists()) nameFile.readText().trim() else file.name
                } catch (_: Exception) { file.name }
                CameraDevice(
                    name = name,
                    path = "v4l2://${file.absolutePath}",
                    displayName = "$name (${file.name})"
                )
            } ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private const val PNP_CAMERA_QUERY =
    "Get-CimInstance Win32_PnPEntity | Where-Object { \$_.PNPClass -eq 'Camera' -or " +
        "\$_.PNPClass -eq 'Image' } | Select-Object -ExpandProperty Name"

internal fun windowsCamerasFrom(run: CommandRunner): List<CameraDevice> {
    val dshowOutput = run(listOf("ffmpeg", "-list_devices", "true", "-f", "dshow", "-i", "dummy"), 0L).output
    val pnpOutput = run(listOf("powershell", "-NoProfile", "-Command", PNP_CAMERA_QUERY), 0L).output
    return parseWindowsCameras(dshowOutput, pnpOutput)
}

internal fun parseWindowsCameras(dshowOutput: String, pnpOutput: String): List<CameraDevice> {
    val devices = mutableListOf<CameraDevice>()
    val seenNames = mutableSetOf<String>()

    fun add(name: String) {
        if (name.lowercase() !in seenNames) {
            devices.add(CameraDevice(name = name, path = "dshow://:dshow-vdev=$name", displayName = name))
            seenNames.add(name.lowercase())
        }
    }

    val namePattern = Regex("\"(.+?)\"\\s+\\((video|none)\\)")
    var isVideo = false
    for (line in dshowOutput.lines()) {

        val newMatch = namePattern.find(line)
        if (newMatch != null) {
            add(newMatch.groupValues[1])
            continue
        }

        if (line.contains("DirectShow video devices")) isVideo = true
        else if (line.contains("DirectShow audio devices")) isVideo = false
        else if (isVideo) {
            Regex("\"(.+?)\"").find(line)?.let { add(it.groupValues[1]) }
        }
    }

    pnpOutput.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { add(it) }

    return devices
}

internal fun macCamerasFrom(run: CommandRunner): List<CameraDevice> {
    val profilerOutput = run(listOf("system_profiler", "SPCameraDataType", "-detailLevel", "mini"), 0L).output
    val ffmpegOutput = run(listOf("ffmpeg", "-f", "avfoundation", "-list_devices", "true", "-i", ""), 0L).output
    return parseMacCameras(profilerOutput, ffmpegOutput)
}

/** Adds an `[0] Camera name` line from ffmpeg's AVFoundation listing, if it names a new device. */
private fun addIndexedAvfDevice(
    line: String,
    devices: MutableList<CameraDevice>,
    seenNames: MutableSet<String>,
) {
    val match = Regex("\\[(\\d+)]\\s+(.+)").find(line) ?: return
    val index = match.groupValues[1]
    val name = match.groupValues[2].trim()
    if (name.lowercase() in seenNames) return
    devices.add(CameraDevice(name = name, path = "avfoundation://$index", displayName = name))
    seenNames.add(name.lowercase())
}

internal fun parseMacCameras(systemProfilerOutput: String, ffmpegOutput: String): List<CameraDevice> {
    val devices = mutableListOf<CameraDevice>()
    val seenNames = mutableSetOf<String>()

    systemProfilerOutput.lines()
        .filter { it.contains(":") && !it.trim().startsWith("Camera") && it.trim().endsWith(":") }
        .map { it.trim().removeSuffix(":") }
        .forEachIndexed { index, name ->
            devices.add(CameraDevice(name = name, path = "avfoundation://$index", displayName = name))
            seenNames.add(name.lowercase())
        }

    var isVideo = false
    var deviceIndex = devices.size
    for (line in ffmpegOutput.lines()) {

        val newMatch = Regex("\"(.+?)\"\\s+\\(video\\)").find(line)
        if (newMatch != null) {
            val name = newMatch.groupValues[1]
            if (name.lowercase() !in seenNames) {
                devices.add(CameraDevice(name = name, path = "avfoundation://$deviceIndex", displayName = name))
                seenNames.add(name.lowercase())
                deviceIndex++
            }
            continue
        }

        if (line.contains("AVFoundation video devices")) isVideo = true
        else if (line.contains("AVFoundation audio devices")) isVideo = false
        else if (isVideo) addIndexedAvfDevice(line, devices, seenNames)
    }

    return devices
}
