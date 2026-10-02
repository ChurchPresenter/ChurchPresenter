package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.sharedui.utils.FfmpegBinary
import org.churchpresenter.sharedui.utils.CommandRunner

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
 * `system_profiler` is therefore only a fallback for when ffmpeg listed nothing. Capture needs ffmpeg
 * whatever named the device, so those entries can never be opened; they exist so the operator sees
 * their camera named beside a hint explaining that, rather than an empty list.
 *
 * Since the app ships ffmpeg, reaching this on a Mac most often means AVFoundation enumerated
 * nothing — and the usual cause of that is a privacy refusal, which is what makes the camera
 * privacy button beside this picker the useful thing on screen. The accompanying sentence is
 * `canvas_camera_unopenable_listing`; this doc used to name `canvas_camera_ffmpeg_hint`, which was
 * superseded and is kept defined only so fourteen locales' translations are not orphaned.
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
