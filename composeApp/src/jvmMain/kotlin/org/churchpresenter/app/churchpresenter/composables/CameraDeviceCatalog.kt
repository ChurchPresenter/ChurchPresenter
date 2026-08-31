/*
 * What cameras this machine has, asked once rather than per caller.
 *
 * `listCameraDevicesWithDeckLink` shells out to ffmpeg, PowerShell or system_profiler on every
 * call and caches nothing, so it takes a noticeable fraction of a second at best. The Canvas
 * property panel calls it straight from composition; nothing on a presenter path may do that, and
 * a picker that re-enumerates per tile would do it dozens of times over.
 */
package org.churchpresenter.app.churchpresenter.composables

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.camera.CameraDeviceRef

/**
 * The cameras this machine last reported, refreshed off the composition thread.
 *
 * [devices] is null until the first [refresh] finishes, which is a distinct state from "there are
 * none" and is treated as such by [cameraResolves].
 */
internal object CameraDeviceCatalog {

    private val _devices = MutableStateFlow<List<CameraDevice>?>(null)

    /** Null until something has actually looked; an empty list means this machine has no camera. */
    val devices: StateFlow<List<CameraDevice>?> = _devices.asStateFlow()

    /** Re-enumerates, on IO. Safe to call from a composition's `LaunchedEffect`. */
    suspend fun refresh(deckLinkDeviceFormat: String) {
        _devices.value = withContext(Dispatchers.IO) { listCameraDevicesWithDeckLink(deckLinkDeviceFormat) }
    }
}

/**
 * Whether [camera] names a device this machine actually has, and so may be opened.
 *
 * **A stored device path is not enough.** A `.song` file travels, and settings are exported and
 * imported, so `avfoundation://0` written on one machine names *a* camera on the next one — a
 * different camera, not a missing one. Opening it would put the wrong picture behind the lyrics,
 * which is a far worse failure than a black screen. So a match is on the device's **name**, or on
 * the card index for a DeckLink, where the index is the identity.
 *
 * [known] null means nothing has enumerated yet, and that **accepts**: rejecting before the first
 * answer arrives would drop a perfectly good camera back to the settings background for as long as
 * the enumeration takes, on every cold start. The capture layer draws black if the device turns out
 * not to open, so the cost of accepting too early is bounded and the cost of rejecting is a flash.
 */
internal fun cameraResolves(camera: CameraDeviceRef, known: List<CameraDevice>?): Boolean {
    if (!camera.isSet) return false
    if (known == null) return true
    return if (camera.isDeckLink && camera.deckLinkIndex >= 0) {
        known.any { it.isDeckLink && it.deckLinkIndex == camera.deckLinkIndex }
    } else {
        known.any { !it.isDeckLink && it.name == camera.deviceName }
    }
}
