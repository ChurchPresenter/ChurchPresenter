package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.presenter.OmtManager
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.omt.OmtReceiver
import java.awt.image.BufferedImage

/**
 * The receiving side of the Canvas for OMT: one connection per distinct source, however many layers
 * are drawing it. Everything but the keying is [ReceivedFrameCache]'s, shared with NDI.
 *
 * [openReceiver] is the seam, as [NdiFrameCache]'s is: a test builds one over a `FakeOmtLibrary`.
 */
open class OmtFrameCache(
    openReceiver: (address: String, preview: Boolean) -> OmtReceiver?,
) : ReceivedFrameCache<SceneSource.OmtSource>(
    openReceiver = { source -> openReceiver(source.sourceAddress, source.preview)?.let(::OmtPictureReceiver) },
    logTag = "[OMT Input]",
) {
    override val resource: SharedResource get() = SharedResource.OMT_RECEIVER

    /** The preview flag is part of the key: the preview and the full picture are two streams. */
    override fun keyFor(source: SceneSource.OmtSource): String = "${source.sourceAddress}|${source.preview}"

    override fun labelOf(source: SceneSource.OmtSource): String = source.sourceAddress
}

/** An [OmtReceiver] as the cache drives it. */
private class OmtPictureReceiver(private val receiver: OmtReceiver) : NetworkPictureReceiver {
    override fun open(): Boolean = receiver.open()

    override fun receive(): BufferedImage? =
        receiver.receive()?.let { frame -> argbImage(frame.pixels, frame.width, frame.height) }

    override fun close() = receiver.close()
}

/** The one cache the app draws OMT layers from, over the app's one library. */
object SharedOmtFrameCache : OmtFrameCache(
    openReceiver = { address, preview -> OmtManager.createReceiver(address, preview) },
)
