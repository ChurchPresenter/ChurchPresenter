/*
 * Which camera something is pointed at, in a form that can be stored.
 *
 * A camera reaches the screen through two quite different doors — a Canvas layer, which carries a
 * [SceneSource.CameraSource], and a background, which carries one of these — and the capture behind
 * both is shared and ref-counted, keyed on the device's identity alone. So the two doors have to
 * agree, field for field, on what a device *is*: a background that stored `videoFormat = ""` while
 * a Canvas layer on the same device stored "1920x1080@30" would produce a second key, a second
 * capture, and a device the operating system then refuses to open twice.
 *
 * Hence one type and one conversion, rather than six fields copied by hand at each end.
 */
package org.churchpresenter.core.models.camera

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.scene.SceneSource

/** The id [asCameraSource] gives the source it builds; nothing keys on it. */
private const val BACKGROUND_CAMERA_SOURCE_ID = "background-camera"

/**
 * A camera device, as a background stores it.
 *
 * The fields mirror [SceneSource.CameraSource]'s exactly, and are stored rather than resolved: a
 * device is named by the path its enumerator produced, which differs per platform
 * (`avfoundation://0`, `v4l2:///dev/video2`, `dshow://…`, `decklink://1`).
 *
 * [deviceName] is not decoration. A `.song` file travels between machines, where a bare path means
 * a *different* camera rather than a missing one, so the name is what a caller checks before
 * opening anything.
 */
@Serializable
data class CameraDeviceRef(
    val devicePath: String = "",
    val deviceName: String = "",
    /** "1920x1080@30", or blank for whatever the device offers. */
    val videoFormat: String = "",
    /** DeckLink only — which physical input on the card. */
    val videoConnection: Int = 0,
    val isDeckLink: Boolean = false,
    val deckLinkIndex: Int = -1,
) {
    /** True once a device has actually been chosen. */
    val isSet: Boolean get() = devicePath.isNotBlank()
}

/**
 * This device as the scene source the shared frame cache is keyed on.
 *
 * The four fields the cache hashes — path, format, connection and DeckLink index — are copied
 * straight across, which is what lets a background and a Canvas layer on one device share a single
 * capture instead of fighting over it.
 */
fun CameraDeviceRef.asCameraSource(): SceneSource.CameraSource = SceneSource.CameraSource(
    id = BACKGROUND_CAMERA_SOURCE_ID,
    name = deviceName,
    devicePath = devicePath,
    deviceName = deviceName,
    videoFormat = videoFormat,
    videoConnection = videoConnection,
    isDeckLink = isDeckLink,
    deckLinkIndex = deckLinkIndex,
)

/** The same device, as a background stores it. */
fun SceneSource.CameraSource.asDeviceRef(): CameraDeviceRef = CameraDeviceRef(
    devicePath = devicePath,
    deviceName = deviceName,
    videoFormat = videoFormat,
    videoConnection = videoConnection,
    isDeckLink = isDeckLink,
    deckLinkIndex = deckLinkIndex,
)
