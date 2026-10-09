package org.churchpresenter.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ContactReporter]'s network path can't be exercised offline, but its request shape carries an
 * anti-abuse contract worth pinning down.
 */
class ContactReporterTest {

    @Test
    fun `the honeypot field defaults to empty`() {
        // The server rejects a non-empty `company`; a real client must never populate it, so the
        // default has to stay empty even as other fields are added.
        val request = ContactReporter.ContactRequest(type = "bug", name = "Sam", message = "hello")
        assertEquals("", request.company)
        assertEquals("", request.email, "email is optional")
        assertEquals("", request.context)
    }

    @Test
    fun `the rate-limit escalation target is a public https url`() {
        assertTrue(ContactReporter.WEB_CONTACT_URL.startsWith("https://"), "must not fall back to http")
        assertTrue(ContactReporter.WEB_CONTACT_URL.contains("churchpresenter.org"))
    }

    @Test
    fun `outcomes are distinguishable from one another`() {
        // These drive different UI branches (retry vs. open the browser vs. fix your input), so
        // they must not collapse onto one another via data-class equality.
        val outcomes = listOf(
            ContactReporter.Outcome.Success,
            ContactReporter.Outcome.RateLimited,
            ContactReporter.Outcome.NetworkError,
            ContactReporter.Outcome.Failure,
            ContactReporter.Outcome.Invalid(null),
        )
        assertEquals(outcomes.size, outcomes.toSet().size)
        assertTrue(ContactReporter.Outcome.Invalid("bad email") != ContactReporter.Outcome.Invalid(null))
    }

    // ── The status mapping in submit ────────────────────────────────────────────
    // This is the part of submit that decides the user's next step; the socket around it can't be
    // exercised offline, but the decision itself is pure once the status and body are in hand.

    @Test
    fun `a 200 succeeds and a 429 is a rate limit`() {
        assertEquals(ContactReporter.Outcome.Success, ContactReporter.classifyStatus(200))
        // 429 falls inside the 4xx range but must be caught first — it escalates to the web form,
        // it is not a validation error to be explained from the body.
        assertEquals(ContactReporter.Outcome.RateLimited, ContactReporter.classifyStatus(429))
    }

    @Test
    fun `any other 4xx defers to the body for its reason`() {
        // null is the signal to submit that the outcome isn't terminal yet — read the body to learn
        // why the request was rejected. Boundaries of the range plus one inside.
        assertNull(ContactReporter.classifyStatus(400))
        assertNull(ContactReporter.classifyStatus(422))
        assertNull(ContactReporter.classifyStatus(499))
    }

    @Test
    fun `a 5xx or unexpected status is a retryable failure`() {
        assertEquals(ContactReporter.Outcome.Failure, ContactReporter.classifyStatus(500))
        assertEquals(ContactReporter.Outcome.Failure, ContactReporter.classifyStatus(503))
        // Anything outside the mapped cases is treated as a transient failure, not a success.
        assertEquals(ContactReporter.Outcome.Failure, ContactReporter.classifyStatus(302))
    }

    @Test
    fun `the validation reason is lifted out of the server's error body`() {
        assertEquals(
            "email is invalid",
            ContactReporter.parseErrorMessage("""{"error":"email is invalid"}"""),
        )
    }

    @Test
    fun `a body with no usable reason yields null rather than throwing`() {
        // Each of these reaches Outcome.Invalid(null) in submit — a rejection the UI states plainly
        // without a server message. None may surface as a thrown exception.
        assertNull(ContactReporter.parseErrorMessage("{}"), "field absent")
        assertNull(ContactReporter.parseErrorMessage("not json at all"), "unparseable body")
        assertNull(ContactReporter.parseErrorMessage(""), "empty body")
    }
}
