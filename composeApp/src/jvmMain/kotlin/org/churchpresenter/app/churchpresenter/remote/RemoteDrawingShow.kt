package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.server.DrawingDesk
import org.churchpresenter.server.DrawingFrame
import org.churchpresenter.server.PendingDrawingRequest
import org.churchpresenter.sharedui.models.Presenting
import kotlin.coroutines.CoroutineContext

/**
 * What the app does with a remote device's drawing once the server has passed it on: put it live
 * when the operator allows it, replace its page as the device draws, and take it down when the device
 * ends it. The prompt itself is `RemoteDrawingWiring`'s; this is everything after it, in a class a
 * test can drive with a real [PresenterManager].
 *
 * Every page is decoded on [io] — never the event thread the outputs compose on — and only the
 * finished picture is written to the live state.
 */
internal class RemoteDrawingShow(
    private val drawings: DrawingDesk,
    private val presenter: PresenterManager,
    private val io: CoroutineContext = Dispatchers.IO,
    private val decode: (DrawingFrame) -> DrawingPage? = ::decodeDrawingPage,
) {

    /**
     * The operator allowed [request]: the device's latest page goes up and the request is answered
     * yes. Nothing happens — and the answer is no — if the device withdrew it meanwhile or its page
     * turns out not to be a picture.
     */
    suspend fun allow(request: PendingDrawingRequest) {
        if (request.decision.isCompleted) return
        val frame = drawings.approve(request)
        val page = frame?.let { withContext(io) { decode(it) } }
        if (page == null) {
            drawings.end(request.clientId)
            request.decision.complete(false)
            return
        }
        // The mode first, then the page: the page's own live-state notice then reports a drawing.
        presenter.setPresentingMode(Presenting.DRAWING)
        presenter.showDrawingFrame(page.bitmap, page.paper)
        presenter.setShowPresenterWindow(true)
        request.decision.complete(true)
    }

    /** A later page of a drawing: put up if it is still the one live once decoded, and dropped otherwise. */
    suspend fun refresh(frame: DrawingFrame) {
        if (!drawings.isLive(frame.clientId)) return
        val page = withContext(io) { decode(frame) } ?: return
        // Checked again after decoding: the operator may have moved on while it was.
        if (drawings.isLive(frame.clientId) && presenter.slideContent.value == Presenting.DRAWING) {
            presenter.showDrawingFrame(page.bitmap, page.paper)
        }
    }

    /** The device ended the drawing that was live: the outputs clear as for Clear Display, fading first. */
    fun ended() {
        if (presenter.slideContent.value == Presenting.DRAWING) presenter.requestClearDisplay()
    }
}
