package org.churchpresenter.canvas

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.sharedui.utils.FfmpegBinary
import org.churchpresenter.sharedui.utils.CommandRunner
import org.churchpresenter.sharedui.utils.readCommandOutput

/**
 * One camera or capture device this machine can open.
 *
 * Public, unlike the enumeration around it, because the Canvas tab and its source panel take a list
 * of these as a parameter so a screenshot can pin what the picker shows -- the machine's real
 * hardware would otherwise decide what the committed image says.
 */
data class CameraDevice(
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
        // An output-only card has no input to open, so it is never offered as a camera.
        val deckLinkDevices = DeckLinkManager.listDevices().filter { DeckLinkManager.hasInput(it.index) }
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

    Log.info("Camera", "Found ${devices.size} total device(s) ($deckLinkCount DeckLink)")
    return CameraListing(devices, listing.facts.copy(deckLinkCount = deckLinkCount))
}

fun isFfmpegAvailable(): Boolean = FfmpegBinary.isAvailable

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
    Log.info("Camera", "Found ${devices.size} camera device(s):")
    devices.forEach { Log.info("Camera", "  ${it.displayName} -> ${it.path}") }
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
internal fun List<CameraDevice>.asListing(enumerator: CameraEnumerator): CameraListing {
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

/** Where a saved AVFoundation device is *now*, or why it must not be opened. */
internal sealed interface AvfResolution {

    /** The saved name is at this path — either the stored index, confirmed, or a corrected one. */
    data class At(val devicePath: String) : AvfResolution

    /** The saved name is not in the current listing. Whatever is at that index, it is not this. */
    data object Gone : AvfResolution

    /** Nothing to resolve: not an AVFoundation device, no saved name, or nothing has enumerated. */
    data object NotApplicable : AvfResolution
}
