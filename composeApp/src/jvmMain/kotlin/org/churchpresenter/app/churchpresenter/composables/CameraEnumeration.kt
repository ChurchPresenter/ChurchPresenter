package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.scene.SceneSource
import java.io.File

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

internal fun listCameraDevicesWithDeckLink(deckLinkDeviceFormat: String = "DeckLink: %1\$s"): List<CameraDevice> =
    listCameraDevicesWithDeckLinkListing(deckLinkDeviceFormat).devices

/** [listCameraDevicesWithDeckLink], keeping what enumeration found — see [CameraEnumerationFacts]. */
internal fun listCameraDevicesWithDeckLinkListing(
    deckLinkDeviceFormat: String = "DeckLink: %1\$s",
): CameraListing {
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

    val deckLinkCount = devices.size
    val hasDeckLink = devices.any { it.isDeckLink }
    val listing = listCameraDevices()
    listing.devices.filterNot { cam ->
        hasDeckLink && cam.name.lowercase().contains("decklink")
    }.let { devices.addAll(it) }

    System.err.println("[Camera] Found ${devices.size} total device(s) ($deckLinkCount DeckLink)")
    return CameraListing(devices, listing.facts.copy(deckLinkCount = deckLinkCount))
}

internal data class CameraFormat(
    val width: Int,
    val height: Int,
    val fps: Int,
    val displayName: String = "${width}x${height} @ ${fps}fps",
    val encodedValue: String = "${width}x${height}@${fps}"
)

/** What a camera runs at when its listing names a size but no rate. Every capture device does 30. */
private const val DEFAULT_CAMERA_FPS = 30

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

/** How long to wait for the format probe. It answers in well under a second or it is not going to. */
private const val AVF_FORMAT_PROBE_TIMEOUT_S = 5L

/**
 * Asks a device what it supports, by asking it for a frame size nothing can produce.
 *
 * **AVFoundation has no `-list_formats`** — that is a v4l2 option, and ffmpeg rejects the whole
 * command line with `Unrecognized option 'list_formats'` before it opens anything. So this ran on
 * every Mac and returned nothing every time, which is why the Video Format menu only ever offered
 * "Auto", and why the "choose a specific one under Video Format" the unsupported-format error tells
 * the operator to do pointed at an empty menu.
 *
 * A camera constrained to a fixed set of modes answers `1x1` by refusing it and printing
 * `Supported modes:` followed by every mode it has. A screen-capture pseudo-device instead accepts
 * any size and reports nothing — correctly, since it has no mode list to give.
 *
 * **No output file is named on purpose.** ffmpeg opens the input, discovers it has nowhere to write,
 * says `At least one output file must be specified` and exits — measured at 0.34s — so the probe
 * cannot turn into a capture session that never ends.
 */
internal fun avfoundationFormatsFrom(deviceIndex: String, run: CommandRunner): List<CameraFormat> =
    parseAvfoundationFormats(
        run(
            listOf(
                FfmpegBinary.path, "-f", "avfoundation",
                "-video_size", "1x1", "-i", "$deviceIndex:none",
            ),
            AVF_FORMAT_PROBE_TIMEOUT_S,
        ).output
    )

/**
 * Frame rates as avfoundation prints them, which is not how ffmpeg prints them anywhere else.
 *
 * A mode line reads `1920x1080@[30.000030 30.000030]fps` — a *range*, whose upper bound is the
 * rate worth asking for — while every other listing this file parses writes a plain `60 fps`. Both
 * are matched here; the bracketed form is tried first because its own closing bracket is what stops
 * the plain form from matching it.
 */
private val AVF_BRACKETED_FPS = Regex("""\[\s*(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s*]\s*fps""")
private val AVF_PLAIN_FPS = Regex("""(\d+(?:\.\d+)?)\s*fps""")

private fun avfoundationFpsIn(line: String): Int? {
    AVF_BRACKETED_FPS.find(line)?.let { match ->
        val low = match.groupValues[1].toDoubleOrNull()
        val high = match.groupValues[2].toDoubleOrNull()
        val best = listOfNotNull(low, high).maxOrNull()
        if (best != null) return best.toInt()
    }
    return AVF_PLAIN_FPS.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
}

internal fun parseAvfoundationFormats(output: String): List<CameraFormat> {
    val formats = mutableSetOf<Triple<Int, Int, Int>>()
    val sizePattern = Regex("""(\d{3,5})x(\d{3,5})""")
    for (line in output.lines()) {
        val sizeMatch = sizePattern.find(line)
        val w = sizeMatch?.groupValues?.get(1)?.toIntOrNull()
        val h = sizeMatch?.groupValues?.get(2)?.toIntOrNull()
        if (w == null || h == null) continue
        formats.add(Triple(w, h, avfoundationFpsIn(line) ?: DEFAULT_CAMERA_FPS))
    }
    return formats.toSortedFormats()
}

internal fun isFfmpegAvailable(): Boolean = FfmpegBinary.isAvailable

/**
 * Re-resolves ffmpeg and reports whether it is available now.
 *
 * For the settings row, after the operator has pointed [FfmpegBinary.customPath] somewhere else.
 * Runs `ffmpeg -version`, so it belongs on `Dispatchers.IO` and never on a click handler.
 */
internal fun recheckFfmpegAvailability(): Boolean = FfmpegBinary.recheck()

/**
 * The impure edge of enumeration: the real OS, the real commands, the real clock.
 *
 * [enumerateCameras] stays pure over its runner, so everything about *which* tool answers is driven
 * from a fake in tests. The two facts that cannot be known there — whether ffmpeg resolved on this
 * machine, and when this ran — are filled in here.
 */
private fun listCameraDevices(): CameraListing {
    val listing = enumerateCameras(System.getProperty("os.name", "").lowercase(), ::readCommandOutput)
    val devices = listing.devices
    System.err.println("[Camera] Found ${devices.size} camera device(s):")
    devices.forEach { System.err.println("[Camera]   ${it.displayName} -> ${it.path}") }
    return listing.copy(
        facts = listing.facts.copy(
            ffmpegAvailable = isFfmpegAvailable(),
            enumeratedAtMs = System.currentTimeMillis(),
        )
    )
}

internal fun cameraDevicesFor(osName: String, run: CommandRunner): List<CameraDevice> =
    enumerateCameras(osName, run).devices

/**
 * The cameras this machine has, and how they were found.
 *
 * [cameraDevicesFor] is this without the second half, and remains the entry point for everything
 * that only wants the list. The facts exist so a camera that fails to open can be reported with the
 * context that explains why — see [CameraEnumerationFacts].
 *
 * `ffmpegAvailable` and the timestamp are filled by the impure caller ([listCameraDevices]), not
 * here: keeping this function a pure fold over the runner's output is what lets the whole
 * enumeration be driven from a `FakeCommandRunner`.
 */
internal fun enumerateCameras(osName: String, run: CommandRunner): CameraListing = when {
    osName.contains("linux") -> listLinuxCameras().asListing(CameraEnumerator.V4L2_SYSFS)
    osName.contains("win") -> windowsListing(run)
    osName.contains("mac") -> macListing(run)
    else -> emptyList<CameraDevice>().asListing(CameraEnumerator.UNSUPPORTED_OS)
}

/**
 * A listing from devices that came from one tool, with the counts filled in from which tool it was.
 *
 * A fallback enumerator's devices are counted as [CameraEnumerationFacts.fallbackListedCount] and
 * ffmpeg's as [CameraEnumerationFacts.ffmpegListedCount], because the question a report has to
 * answer is which of the two produced the name that failed.
 */
private fun List<CameraDevice>.asListing(enumerator: CameraEnumerator): CameraListing {
    val fromFallback = enumerator == CameraEnumerator.PNP_FALLBACK ||
        enumerator == CameraEnumerator.SYSTEM_PROFILER_FALLBACK
    return CameraListing(
        devices = this,
        facts = CameraEnumerationFacts(
            enumerator = enumerator,
            ffmpegListedCount = if (fromFallback) 0 else size,
            fallbackListedCount = if (fromFallback) size else 0,
            deckLinkCount = 0,
            ffmpegAvailable = false,
            enumeratedAtMs = 0L,
            names = mapTo(mutableSetOf()) { it.name.lowercase() },
        ),
    )
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

/**
 * Camera-class PnP devices, by their friendly name.
 *
 * `PNPClass = 'Image'` is deliberately **not** included. It covers flatbed scanners and other
 * still-image devices, which were being offered to the operator as cameras. What it also covers is
 * a handful of pre-UVC webcams that register under `Image` rather than `Camera`; losing those is
 * acceptable because this query is only the fallback below, and on that path nothing is openable
 * anyway.
 */
private const val PNP_CAMERA_QUERY =
    "Get-CimInstance Win32_PnPEntity | Where-Object { \$_.PNPClass -eq 'Camera' } | " +
        "Select-Object -ExpandProperty Name"

/**
 * How long to wait for ffmpeg's DirectShow listing.
 *
 * It instantiates and queries every registered DirectShow video filter, so a box carrying an OBS
 * virtual camera, an NDI filter and a vendor capture driver genuinely takes a couple of seconds.
 * This is a ceiling for a pathological machine, not a budget.
 */
private const val DSHOW_LIST_TIMEOUT_S = 10L

/**
 * How long to wait for the PnP query.
 *
 * More generous than the ffmpeg one because `powershell.exe` costs a second or three to start even
 * with `-NoProfile`, and `Get-CimInstance Win32_PnPEntity` walks the whole device tree through WMI.
 * Only ever paid on the fallback path.
 */
private const val PNP_QUERY_TIMEOUT_S = 15L

/**
 * The machine's cameras, preferring ffmpeg's DirectShow listing outright over the PnP one.
 *
 * **These two tools do not name devices in the same space, so their results must never be merged.**
 * ffmpeg enumerates the DirectShow video filters it can open and prints the `FriendlyName` needed to
 * open one; `Get-CimInstance Win32_PnPEntity` enumerates hardware Windows knows about and prints a
 * PnP friendly name, which is a label rather than an address. [buildFfmpegCommand] turns whatever is
 * stored here into `-f dshow -i video=<name>`, so a name ffmpeg never listed produces
 * "Could not find video device" — which [classifyCameraFfmpegStderr] reads as
 * `DEVICE_NOT_FOUND`, telling the operator their camera "is no longer connected" about a device that
 * was never openable in the first place.
 *
 * The PnP query is therefore only a fallback for when ffmpeg listed nothing, which in practice means
 * ffmpeg is not installed. Capture needs ffmpeg too, so those entries can never be opened; they
 * exist so the operator still sees their camera named beside the `canvas_camera_ffmpeg_required`
 * hint telling them what to install, rather than an empty list.
 *
 * This mirrors [parseMacCameras], which resolved the same conflict between ffmpeg and
 * `system_profiler` the same way and for the same reason.
 */
internal fun windowsCamerasFrom(run: CommandRunner): List<CameraDevice> = windowsListing(run).devices

/** [windowsCamerasFrom], keeping which tool answered. */
internal fun windowsListing(run: CommandRunner): CameraListing {
    val dshowOutput = run(
        listOf(FfmpegBinary.path, "-list_devices", "true", "-f", "dshow", "-i", "dummy"),
        DSHOW_LIST_TIMEOUT_S,
    ).output
    val fromDshow = dshowCameras(dshowOutput)
    // Not merely discarded when ffmpeg answered — not run at all. It is the slowest call on this
    // path and the common case has no use for it.
    if (fromDshow.isNotEmpty()) return fromDshow.asListing(CameraEnumerator.DSHOW)

    val pnpOutput = run(
        listOf("powershell", "-NoProfile", "-Command", PNP_CAMERA_QUERY),
        PNP_QUERY_TIMEOUT_S,
    ).output
    return pnpFallbackCameras(pnpOutput).asListing(CameraEnumerator.PNP_FALLBACK)
}

/** The pure form of [windowsCamerasFrom]'s decision, which is what the tests drive. */
internal fun parseWindowsCameras(dshowOutput: String, pnpOutput: String): List<CameraDevice> =
    windowsListingOf(dshowOutput, pnpOutput).devices

/** [parseWindowsCameras], keeping which tool answered. */
internal fun windowsListingOf(dshowOutput: String, pnpOutput: String): CameraListing {
    val fromDshow = dshowCameras(dshowOutput)
    return if (fromDshow.isNotEmpty()) fromDshow.asListing(CameraEnumerator.DSHOW)
    else pnpFallbackCameras(pnpOutput).asListing(CameraEnumerator.PNP_FALLBACK)
}

/**
 * The devices in ffmpeg's `-list_devices` output, in both shapes it has printed them.
 *
 * `(none)` counts alongside `(video)`: an untyped capture card is still a camera to us.
 */
private fun dshowCameras(dshowOutput: String): List<CameraDevice> {
    val devices = mutableListOf<CameraDevice>()
    val seenNames = mutableSetOf<String>()

    fun add(name: String) {
        if (name.lowercase() !in seenNames) {
            devices.add(windowsCameraDevice(name))
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
    return devices
}

/** The PnP names, when ffmpeg named nothing. Unopenable by construction — see [windowsCamerasFrom]. */
private fun pnpFallbackCameras(pnpOutput: String): List<CameraDevice> {
    val seenNames = mutableSetOf<String>()
    return pnpOutput.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && seenNames.add(it.lowercase()) }
        .map { windowsCameraDevice(it) }
}

private fun windowsCameraDevice(name: String) =
    CameraDevice(name = name, path = "dshow://:dshow-vdev=$name", displayName = name)

internal fun macCamerasFrom(run: CommandRunner): List<CameraDevice> = macListing(run).devices

/** [macCamerasFrom], keeping which tool answered. */
internal fun macListing(run: CommandRunner): CameraListing {
    val profilerOutput = run(listOf("system_profiler", "SPCameraDataType", "-detailLevel", "mini"), 0L).output
    val listDevices = listOf(FfmpegBinary.path, "-f", "avfoundation", "-list_devices", "true", "-i", "")
    val ffmpegOutput = run(listDevices, 0L).output
    return macListingOf(profilerOutput, ffmpegOutput)
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

/**
 * The AVFoundation video devices ffmpeg listed, addressed by **the index ffmpeg itself printed**.
 *
 * The bracketed index is the only address AVFoundation accepts, and it counts every video device —
 * virtual cameras and screen captures included. Nothing else can reconstruct it: a machine with
 * OBS, NDI and a capture card installed numbers that card 2 while `system_profiler`, which sees
 * only real hardware, would call it 0. That was issue #431 — the app opened a virtual camera
 * nobody was feeding and drew a grey box.
 *
 * The quoted `"Name" (video)` branch is the older listing shape, which prints no index. There the
 * position within this section is the address, which is correct precisely because the section
 * enumerates the same devices in the same order.
 */
private fun avfoundationCamerasFrom(ffmpegOutput: String): List<CameraDevice> {
    val devices = mutableListOf<CameraDevice>()
    val seenNames = mutableSetOf<String>()
    var isVideo = false

    for (line in ffmpegOutput.lines()) {
        val quotedMatch = Regex("\"(.+?)\"\\s+\\(video\\)").find(line)
        if (quotedMatch != null) {
            val name = quotedMatch.groupValues[1]
            if (name.lowercase() !in seenNames) {
                devices.add(
                    CameraDevice(name = name, path = "avfoundation://${devices.size}", displayName = name)
                )
                seenNames.add(name.lowercase())
            }
            continue
        }

        if (line.contains("AVFoundation video devices")) isVideo = true
        else if (line.contains("AVFoundation audio devices")) isVideo = false
        else if (isVideo) addIndexedAvfDevice(line, devices, seenNames)
    }

    return devices
}

/** The camera names `system_profiler` reports, in the order it reports them. */
private fun systemProfilerCameraNames(systemProfilerOutput: String): List<String> =
    systemProfilerOutput.lines()
        .filter { it.contains(":") && !it.trim().startsWith("Camera") && it.trim().endsWith(":") }
        .map { it.trim().removeSuffix(":") }

/**
 * The Mac's cameras, preferring ffmpeg's listing outright over `system_profiler`'s.
 *
 * **These two tools do not share an index space, so their results must never be merged.** ffmpeg
 * enumerates everything AVFoundation will open and prints the index needed to open it;
 * `system_profiler` enumerates physical hardware and prints no index at all. Numbering the latter
 * by position invents an address, and an invented address opens the wrong device rather than
 * failing — see [avfoundationCamerasFrom].
 *
 * `system_profiler` is therefore only a fallback for when ffmpeg listed nothing, which in practice
 * means ffmpeg is not installed. Capture needs ffmpeg too, so those entries can never be opened;
 * they exist so the operator still sees their camera named beside the `canvas_camera_ffmpeg_hint`
 * telling them what to install, rather than an empty list.
 */
internal fun parseMacCameras(systemProfilerOutput: String, ffmpegOutput: String): List<CameraDevice> =
    macListingOf(systemProfilerOutput, ffmpegOutput).devices

/** [parseMacCameras], keeping which tool answered. */
internal fun macListingOf(systemProfilerOutput: String, ffmpegOutput: String): CameraListing {
    val fromFfmpeg = avfoundationCamerasFrom(ffmpegOutput)
    if (fromFfmpeg.isNotEmpty()) return fromFfmpeg.asListing(CameraEnumerator.AVFOUNDATION)

    return systemProfilerCameraNames(systemProfilerOutput)
        .mapIndexed { index, name -> CameraDevice(name = name, path = "avfoundation://$index", displayName = name) }
        .asListing(CameraEnumerator.SYSTEM_PROFILER_FALLBACK)
}
