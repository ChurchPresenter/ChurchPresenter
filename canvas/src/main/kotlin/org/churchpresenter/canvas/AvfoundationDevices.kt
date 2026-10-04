package org.churchpresenter.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.sharedui.utils.readCommandOutput

/** The scheme prefix every AVFoundation device path carries. */
private const val AVFOUNDATION_PREFIX = "avfoundation://"

/**
 * Re-finds [source]'s device by **name** in [known], because its stored index may no longer be it.
 *
 * AVFoundation has no name-based addressing: a device is opened by its position in the listing, and
 * that listing is not stable across launches. Virtual cameras (OBS, NDI, Meld) register when their
 * host app starts, and displays come and go, so a position that named a capture card yesterday can
 * name a different camera today — or `Capture screen 0`, which turns a camera layer into a screen
 * recording and makes macOS ask for Screen Recording permission at launch. That is issue #478, and
 * a real machine was observed listing 5 devices one minute and 6 the next.
 *
 * Issue #431 fixed *enumeration* to use the index ffmpeg itself prints. This is the other half:
 * a stored index is a snapshot of one enumeration, and it has to be re-checked against the current
 * one before anything is opened.
 *
 * [known] null means nothing has enumerated yet, which is [AvfResolution.NotApplicable] rather than
 * a refusal — the caller enumerates first and asks again. **Empty is the same answer**, and for the
 * more important reason: an enumeration that listed nothing has told us we do not know what this
 * machine has, not that the operator's camera is gone, and turning "we could not look" into a
 * refusal would black out a working camera to fix a rare one. A blank
 * [SceneSource.CameraSource.deviceName] — a settings file written before names were stored — is
 * unresolvable on the same principle.
 *
 * A refusal therefore needs a positive fact: a listing that has entries, and none of them this one.
 */
internal fun resolveAvfoundationDevice(
    source: SceneSource.CameraSource,
    known: List<CameraDevice>?,
): AvfResolution {
    if (!needsAvfResolution(source) || known.isNullOrEmpty()) return AvfResolution.NotApplicable

    val match = known.firstOrNull { !it.isDeckLink && it.name == source.deviceName }
    return if (match != null) AvfResolution.At(match.path) else AvfResolution.Gone
}

/**
 * Whether [source] is the kind of device [resolveAvfoundationDevice] can say anything about, asked
 * without an enumeration in hand.
 *
 * Separate because the capture path has to decide **whether to enumerate at all** before it has
 * anything to resolve against, and enumerating shells out to `ffmpeg` and `system_profiler` — far
 * too much to spend on a Windows camera or a DeckLink card that was never in this index space.
 */
internal fun needsAvfResolution(source: SceneSource.CameraSource): Boolean =
    source.devicePath.startsWith(AVFOUNDATION_PREFIX) && source.deviceName.isNotBlank()

/**
 * The name currently sitting at [source]'s stored index, for a report that has to say what we
 * refused to open. Blank when the index is past the end of the listing.
 *
 * Only ever used to describe a [AvfResolution.Gone] — the decision itself is the name match above,
 * so that a stored index landing on another *camera* is refused just as firmly as one landing on a
 * display. But which of the two happened is the difference between "your camera is unplugged" and
 * "we just stopped the app from recording your screen", and only the report can tell us that.
 */
internal fun avfDeviceNameAt(source: SceneSource.CameraSource, known: List<CameraDevice>?): String {
    val index = source.devicePath.removePrefix(AVFOUNDATION_PREFIX).toIntOrNull() ?: return ""
    return known?.getOrNull(index)?.name.orEmpty()
}

/**
 * [source] with its AVFoundation index re-checked against the current listing, or null when the
 * device it names is not there any more and nothing may be opened.
 *
 * **This is the first thing on the startup path that enumerates**, and that is the point of it. A
 * camera background renders as soon as the presenter window opens, long before any picker has run,
 * so [CameraDeviceCatalog] is empty and [cameraResolves] — which accepts a null catalog on
 * purpose — has let the stored index through unchecked. One `ffmpeg -list_devices` on the first
 * camera of the run is the only moment at which the check can be made at all.
 *
 * Top-level rather than a member of [SharedCameraFrameCache] so that object stays under its
 * function threshold, which is why the failure arrives by [onRefused] rather than by touching the
 * cache entry directly.
 *
 * @param onRefused run with what to show the operator when the device may not be opened.
 * [CameraFailure.DEVICE_NOT_FOUND] and not a constant of its own: its sentence — the camera is no
 * longer connected — is exactly true of a name that has left the listing, whether it left because
 * the card was unplugged or because something else now sits at its index.
 */
internal suspend fun avfSourceToOpen(
    source: SceneSource.CameraSource,
    gate: ReportOnce,
    onRefused: (CameraFailure) -> Unit,
): SceneSource.CameraSource? {
    if (!needsAvfResolution(source)) return source

    val catalogDevices = CameraDeviceCatalog.devices.value
    val listing = if (catalogDevices == null) {
        withContext(Dispatchers.IO) { macListing(::readCommandOutput) }
    } else null
    val known = catalogDevices ?: listing?.devices

    return when (val resolution = resolveAvfoundationDevice(source, known)) {
        is AvfResolution.At -> {
            if (resolution.devicePath != source.devicePath) {
                Log.info(
                    "Camera",
                    "'${source.deviceName}' has moved to ${resolution.devicePath} " +
                        "(saved as ${source.devicePath}) — opening it where it is now"
                )
            }
            // The cache key is built from the *stored* path (see `keyFor`), and `release` looks its
            // entry up by that same key. Only the command line follows the device.
            source.copy(devicePath = resolution.devicePath)
        }

        AvfResolution.Gone -> {
            val refused = avfDeviceNameAt(source, known)
            Log.warn(
                "Camera",
                "Refusing ${source.devicePath}: it holds " +
                    "'${refused.ifBlank { "nothing" }}' now, not '${source.deviceName}'"
            )
            onRefused(CameraFailure.DEVICE_NOT_FOUND)
            reportAvfIndexDrift(source, refused, CameraDeviceCatalog.lastEnumeration ?: listing?.facts, gate)
            null
        }

        AvfResolution.NotApplicable -> source
    }
}
