package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.churchpresenter.server.PendingDrawingRequest
import org.churchpresenter.server.RemoteActivityNotification
import org.churchpresenter.server.RemoteApproval
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What becomes of a tablet's request to put its drawing up, for each answer the device's access gives. */
class SettleDrawingRequestTest {

    // Unconfined: the prompt's clean-up runs the moment the request is answered.
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val prompts = mutableListOf<Triple<RemoteEvent, () -> Unit, () -> Unit>>()
    private val notices = mutableListOf<RemoteActivityNotification>()
    private var allowed = 0
    private val request = PendingDrawingRequest("ipad", "Sketch")

    private val event = RemoteEvent(RemoteEventType.PROJECT, "Sketch", "Drawing", "ipad", "Front iPad")
    private val notice = RemoteActivityNotification(RemoteEventType.PROJECT, "Sketch", "Drawing", "ipad", "Front iPad")

    private fun settle(outcome: RemoteApproval) =
        request.settle(outcome, { allowed++ }, { notices += it }, prompts, scope)

    @AfterTest
    fun stop() = scope.cancel()

    @Test
    fun `a blocked device is refused without a prompt`() {
        settle(RemoteApproval.Reject)

        assertTrue(request.decision.isCompleted)
        assertFalse(request.decision.getCompleted())
        assertEquals(0, allowed)
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun `a trusted device's drawing goes up at once and the operator is told`() {
        settle(RemoteApproval.Approve(notice))

        assertEquals(1, allowed)
        assertEquals(listOf(notice), notices)
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun `an unknown device is put to the operator`() {
        settle(RemoteApproval.Ask(event))

        assertEquals(listOf(event), prompts.map { it.first })
        assertEquals(0, allowed, "nothing goes up before the operator answers")
    }

    @Test
    fun `allow on the prompt puts the drawing up`() {
        settle(RemoteApproval.Ask(event))

        prompts.single().second()

        assertEquals(1, allowed)
    }

    @Test
    fun `deny on the prompt answers no and the prompt goes`() {
        settle(RemoteApproval.Ask(event))

        prompts.single().third()

        assertFalse(request.decision.getCompleted())
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun `a device that ends its drawing before the answer takes the prompt back`() {
        settle(RemoteApproval.Ask(event))

        request.decision.complete(false)

        assertTrue(prompts.isEmpty())
    }
}
