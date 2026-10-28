package org.churchpresenter.server

import kotlinx.coroutines.CompletableDeferred

/**
 * One picture of a page drawn on a remote device's canvas, as it arrived.
 *
 * @property png The page as a PNG, undecoded — the app decodes it off the event thread.
 * @property paper The page's background as `#RRGGBB`, which fills the screen around a page of
 *   another shape; null when the device sent none or sent something that is not a colour.
 * @property title What the device calls the drawing, for the operator's prompt; may be blank.
 */
class DrawingFrame(
    val clientId: String,
    val png: ByteArray,
    val paper: String?,
    val title: String,
)

/**
 * A device asking to put its drawing on the outputs. The operator answers it exactly as they answer
 * a project request (`POST /api/project`): [decision] completes true for Allow, false for Deny —
 * and false too when the device withdraws the request first.
 */
class PendingDrawingRequest(val clientId: String, val title: String) {
    val decision: CompletableDeferred<Boolean> = CompletableDeferred()
}

/**
 * Where the server keeps track of remote drawings: the requests waiting for the operator, the
 * latest page each waiting device has sent, and whose drawing is on the outputs now.
 *
 * **Which device's drawing is live is decided here**, under one lock, and nowhere else. It becomes
 * a device's at the moment the operator's approval is applied ([approve], called by the app on the
 * event thread before it puts the page up), and stops being anyone's when the live slide turns to
 * anything else ([slideChanged], from every live-state broadcast) or the device ends it ([end]).
 * Deciding it from the broadcast alone would race: a page arriving between the approval and the
 * broadcast that follows it would find the drawing "not live" and be dropped — and the device told
 * the screen had moved on.
 */
class DrawingDesk {

    private val lock = Any()
    private val waiting = HashMap<String, Waiting>()
    private var liveClient: String? = null

    private class Waiting(val request: PendingDrawingRequest, var latest: DrawingFrame)

    /**
     * A device going live: the request to put to the operator, holding [frame] as the page so far.
     * A device that was already waiting has its earlier request returned too, for the caller to
     * decline — only its latest Go Live is put to the operator.
     */
    fun ask(frame: DrawingFrame): Pair<PendingDrawingRequest, PendingDrawingRequest?> = synchronized(lock) {
        val request = PendingDrawingRequest(frame.clientId, frame.title)
        val replaced = waiting.put(frame.clientId, Waiting(request, frame))?.request
        request to replaced
    }

    /**
     * Keeps [frame] as the latest page of the device's waiting request, so the page the operator
     * approves is the one being drawn now. False when the device has no request waiting.
     */
    fun hold(frame: DrawingFrame): Boolean = synchronized(lock) {
        val entry = waiting[frame.clientId] ?: return false
        entry.latest = frame
        true
    }

    /** Whether [clientId]'s drawing is the one on the outputs. */
    fun isLive(clientId: String): Boolean = synchronized(lock) { liveClient == clientId }

    /**
     * The operator has allowed [request]: its device's drawing becomes the live one. Returns the
     * latest page it sent, to put up — or null when the request was withdrawn or replaced meanwhile,
     * in which case nothing goes live.
     */
    fun approve(request: PendingDrawingRequest): DrawingFrame? = synchronized(lock) {
        val entry = waiting[request.clientId]?.takeIf { it.request === request } ?: return null
        waiting.remove(request.clientId)
        liveClient = request.clientId
        entry.latest
    }

    /** [request] has been answered, one way or the other: it waits no longer. */
    fun settle(request: PendingDrawingRequest) = synchronized(lock) {
        if (waiting[request.clientId]?.request === request) waiting.remove(request.clientId)
    }

    /** Takes back [clientId]'s waiting request, if it has one, for the caller to decline. */
    fun withdraw(clientId: String): PendingDrawingRequest? = synchronized(lock) {
        waiting.remove(clientId)?.request
    }

    /** [clientId] has ended its drawing. Returns whether it was the live one, for the caller to clear. */
    fun end(clientId: String): Boolean = synchronized(lock) {
        val wasLive = liveClient == clientId
        if (wasLive) liveClient = null
        wasLive
    }

    /**
     * What the outputs' slide shows changed to [liveSlide] (a `Presenting` name). Anything but a
     * drawing means the drawing that was live is not any more: a later page from its device is not
     * put back up, and its next Go Live asks the operator again.
     */
    fun slideChanged(liveSlide: String) = synchronized(lock) {
        if (liveSlide != DRAWING) liveClient = null
    }

    companion object {
        /** The `Presenting` name a drawing goes live under — and `LiveStateDto.liveSlide` reports. */
        const val DRAWING = "DRAWING"
    }
}
