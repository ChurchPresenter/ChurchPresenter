package org.churchpresenter.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.sharedui.utils.FfmpegBinary
import org.churchpresenter.sharedui.utils.CommandRunner

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
 * The PnP query is therefore only a fallback for when ffmpeg listed nothing. Capture needs ffmpeg
 * whatever named the device, so those entries can never be opened; they exist so the operator sees
 * their camera named beside a hint explaining that, rather than an empty list.
 *
 * What that hint has to say changed when the app started shipping ffmpeg. "Install ffmpeg" was the
 * answer while the fallback meant a machine without it; now ffmpeg is present and listed nothing
 * anyway, which is a different problem — a driver that exposes no DirectShow filter, or a device the
 * frame server has claimed. `canvas_camera_unopenable_listing` is the sentence for that state, and
 * `CameraEnumerator.listsUnopenableDevices` is what selects it.
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
