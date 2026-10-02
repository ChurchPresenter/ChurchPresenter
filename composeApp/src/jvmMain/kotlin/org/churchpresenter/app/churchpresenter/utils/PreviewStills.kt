package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.composables.SharedCameraFrameCache
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.camera.asCameraSource
import java.io.File
import org.churchpresenter.slides.utils.PictureDecoder

/**
 * The still pictures a settings preview draws a background with: a picture decoded at the size it
 * is shown at, a clip's first frame, a camera's snapshot.
 *
 * A preview is a few hundred dp wide, so decoding a 6000-pixel photograph whole for it -- which the
 * Background tab's tile did -- spends tens of megabytes on pixels nobody sees, every time the tile
 * is composed. Here a picture is decoded once at the size asked for and kept, a handful at a time,
 * keyed by its path, its modification time and that size.
 */
internal object PreviewStills {

    /** Enough for every surface a profile shows at two preview sizes; stills are small. */
    private const val MAX_PICTURES = 24

    /** How long a camera is given to deliver a first frame before the preview settles for a glyph. */
    private const val SNAPSHOT_TIMEOUT_MS = 4_000L

    private val pictures = object : LinkedHashMap<String, ImageBitmap>(MAX_PICTURES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX_PICTURES
    }

    private val snapshots = mutableMapOf<String, ImageBitmap>()

    /**
     * [file] decoded to fit [maxWidth]×[maxHeight], or null when it cannot be read. Blocks: call it
     * off the UI thread.
     */
    fun picture(file: File, maxWidth: Int, maxHeight: Int): ImageBitmap? {
        if (!file.isFile) return null
        val key = "${file.absolutePath}:${file.lastModified()}:${maxWidth}x$maxHeight"
        synchronized(pictures) { pictures[key] }?.let { return it }
        val decoded = PictureDecoder.decodeScaledOrNull(file, maxWidth, maxHeight)?.toComposeImageBitmap()
            ?: return null
        synchronized(pictures) { pictures[key] = decoded }
        return decoded
    }

    /** [video]'s first frame at preview size -- see [VideoFirstFrame]. Blocks. */
    fun videoFrame(video: File, maxWidth: Int, maxHeight: Int): ImageBitmap? =
        VideoFirstFrame.extract(video)?.let { picture(it, maxWidth, maxHeight) }

    /**
     * One frame from [camera], taken by opening it, waiting for its first picture and letting it go
     * again -- or null when it is not set up or sends nothing in time.
     *
     * Kept for the rest of the session once taken. A preview beside the settings holding the device
     * open would keep a capture running for as long as the Profiles tab is on screen, and would take
     * the camera from anything else that wanted it; a picture a moment old shows the framing, which
     * is what the preview is there to check.
     */
    suspend fun cameraSnapshot(camera: CameraDeviceRef): ImageBitmap? {
        if (!camera.isSet) return null
        val source = camera.asCameraSource()
        val key = SharedCameraFrameCache.keyFor(source)
        synchronized(snapshots) { snapshots[key] }?.let { return it }
        val flows = SharedCameraFrameCache.acquire(source)
        val frame = try {
            withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { flows.frame.first { it != null } }
        } finally {
            SharedCameraFrameCache.release(source)
        }
        if (frame != null) synchronized(snapshots) { snapshots[key] = frame }
        return frame
    }
}

private const val LOAD_FACTOR = 0.75f
