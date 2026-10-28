package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteApproval
import org.churchpresenter.server.RemoteActivityNotification
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
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

/**
 * Carries out the operator's [outcome] for this drawing request: refused at once, shown at once
 * with a notice for a trusted device, or put to the operator as a prompt in [prompts].
 *
 * A prompt stays only while the request is open: when it is answered — or the device ends its
 * drawing first, which withdraws it — the prompt leaves [prompts] on its own. Pulled out of
 * `RemoteDrawingWiring` so each outcome can be tested without a window.
 *
 * @param allow What Allow does: put the drawing up and answer yes.
 * @param notify Tells the operator a trusted device's drawing went up.
 */
internal fun PendingDrawingRequest.settle(
    outcome: RemoteApproval,
    allow: () -> Unit,
    notify: (RemoteActivityNotification) -> Unit,
    prompts: MutableList<Triple<RemoteEvent, () -> Unit, () -> Unit>>,
    scope: CoroutineScope,
) {
    when (outcome) {
        RemoteApproval.Reject -> decision.complete(false)
        is RemoteApproval.Approve -> {
            allow()
            notify(outcome.notification)
        }
        is RemoteApproval.Ask -> {
            val deny: () -> Unit = { decision.complete(false) }
            val entry = Triple(outcome.event, allow, deny)
            prompts.add(entry)
            scope.launch {
                decision.await()
                prompts.remove(entry)
            }
        }
    }
}
