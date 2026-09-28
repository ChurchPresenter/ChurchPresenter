package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiReceiver
import org.churchpresenter.ndi.NdiSourceInfo
import java.awt.image.BufferedImage

/**
 * The receiving side of the Canvas for NDI: one connection per distinct source, however many layers
 * are drawing it.
 *
 * Everything but the keying is [ReceivedFrameCache]'s, shared with OMT. A class rather than the
 * object it is reached through, so the whole of it is testable: [openReceiver] is the seam, and a
 * test builds one over a `FakeNdiLibrary` with no runtime installed.
 */
open class NdiFrameCache(
    openReceiver: (NdiSourceInfo, NdiBandwidth) -> NdiReceiver?,
) : ReceivedFrameCache<SceneSource.NdiSource>(
    openReceiver = { source ->
        openReceiver(NdiSourceInfo(source.sourceName, source.sourceAddress), NdiBandwidth.of(source.lowBandwidth))
            ?.let(::NdiPictureReceiver)
    },
    logTag = "[NDI Input]",
) {
    override val resource: SharedResource get() = SharedResource.NDI_RECEIVER

    /**
     * The bandwidth is part of the key: two layers of the same source at different bandwidths are
     * two different streams from the sender, and folding them together would silently give one of
     * them the wrong picture.
     */
    override fun keyFor(source: SceneSource.NdiSource): String =
        "${source.sourceName}|${source.sourceAddress}|${source.lowBandwidth}"

    override fun labelOf(source: SceneSource.NdiSource): String = source.sourceName

    /** What [source] resolves to on the network. */
    internal fun infoFor(source: SceneSource.NdiSource): NdiSourceInfo =
        NdiSourceInfo(source.sourceName, source.sourceAddress)
}

/** An [NdiReceiver] as the cache drives it. */
private class NdiPictureReceiver(private val receiver: NdiReceiver) : NetworkPictureReceiver {
    override fun open(): Boolean = receiver.open()

    override fun receive(): BufferedImage? =
        receiver.receive()?.let { frame -> argbImage(frame.pixels, frame.width, frame.height) }

    override fun close() = receiver.close()
}

/**
 * The one cache the app draws NDI layers from, over the app's one runtime.
 *
 * Holds no logic of its own — everything it does is [NdiFrameCache]'s, which is an ordinary class a
 * test builds over a fake library.
 */
object SharedNdiFrameCache : NdiFrameCache(
    openReceiver = { source, bandwidth -> NdiManager.createReceiver(source, bandwidth, RECEIVER_NAME) },
)

/**
 * What the *sender* sees this app called in its own connection list.
 *
 * Worth naming rather than leaving to the runtime: an operator looking at why their camera has an
 * extra receiver should see which app it is.
 */
private const val RECEIVER_NAME = "ChurchPresenter Canvas"
