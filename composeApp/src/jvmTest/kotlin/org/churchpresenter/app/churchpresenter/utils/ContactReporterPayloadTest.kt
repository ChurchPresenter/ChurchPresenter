package org.churchpresenter.app.churchpresenter.utils

import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The feedback message an operator sends from inside the app.
 *
 * It is posted as JSON to a public endpoint with no credentials in the app at all, which puts the
 * weight on two things this can check: the shape of the payload — including a honeypot field the
 * server rejects if a bot fills it in — and the diagnostics quietly appended to every message.
 *
 * Those diagnostics are the sensitive part. They ride along with a message the user is writing to a
 * stranger, so what goes in them is a privacy decision, not a debugging convenience.
 *
 * The honeypot default, the escalation URL and the outcome types are already covered by
 * `ContactReporterTest` in `MiscUtilsTest.kt`; this adds the payload on the wire and the
 * diagnostics.
 *
 * NOT covered anywhere: the HTTP status mapping in `submit` (200 → Success, 429 → RateLimited, 4xx →
 * Invalid, else Failure). The endpoint is a private constant and the client a private lazy, so a
 * test cannot point it anywhere; an injectable endpoint would make that mapping reachable, and it
 * is the part that decides whether the user is asked to retry or sent to the web form.
 */
class ContactReporterPayloadTest {

    /** Configured as ContactReporter configures its own encoder. */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun encode(request: ContactReporter.ContactRequest) =
        json.encodeToString(ContactReporter.ContactRequest.serializer(), request)

    private fun request(
        type: String = "feedback",
        name: String = "Sam",
        message: String = "The lower thirds are great",
        email: String = "sam@example.org",
        context: String = "",
        company: String = "",
    ) = ContactReporter.ContactRequest(
        type = type, name = name, message = message, email = email, context = context, company = company,
    )

    // ── What gets posted ────────────────────────────────────────────────────────

    @Test
    fun `a message carries the fields the server expects`() {
        val encoded = encode(request())

        listOf("type", "name", "message", "email", "context", "company").forEach {
            assertTrue(""""$it":""" in encoded, "the server reads $it: $encoded")
        }
    }

    @Test
    fun `every field is sent even when left empty`() {
        // The encoder keeps defaults, so an optional field arrives as "" rather than being absent —
        // which is what lets the server treat "absent" as a malformed request.
        val encoded = encode(ContactReporter.ContactRequest(type = "bug", name = "Sam", message = "Broken"))

        assertTrue(""""email":""""" in encoded, encoded)
        assertTrue(""""context":""""" in encoded, encoded)
        assertTrue(""""company":""""" in encoded, encoded)
    }


    @Test
    fun `a message keeps exactly what was typed`() {
        val typed = """Line one
Line two — "quoted" & 100% of the time"""

        val restored = json.decodeFromString(
            ContactReporter.ContactRequest.serializer(),
            encode(request(message = typed)),
        )

        assertEquals(typed, restored.message, "newlines and quotes survive or the report is unreadable")
    }

    @Test
    fun `a message round-trips whole`() {
        val original = request(type = "bug", context = "some context")

        assertEquals(
            original,
            json.decodeFromString(ContactReporter.ContactRequest.serializer(), encode(original)),
        )
    }

    @Test
    fun `a non-ascii name and message survive`() {
        val original = request(name = "Андрей", message = "Спасибо за программу")

        val restored = json.decodeFromString(ContactReporter.ContactRequest.serializer(), encode(original))

        assertEquals("Андрей", restored.name)
        assertEquals("Спасибо за программу", restored.message)
    }

    // ── The diagnostics appended to it ──────────────────────────────────────────

    @Test
    fun `the context names the build so a report can be placed`() {
        val context = ContactReporter.defaultContext()

        assertTrue(context.startsWith("Church Presenter "), context)
        assertTrue(
            BuildConfig.VERSION_DISPLAY in context,
            "a bug report without a version cannot be triaged: $context",
        )
    }

    @Test
    fun `the context names the operating system`() {
        val context = ContactReporter.defaultContext()

        assertTrue(System.getProperty("os.name") in context, context)
        assertTrue(System.getProperty("os.version") in context, context)
    }

    @Test
    fun `the context carries nothing personal`() {
        // This text is appended to a message the user is sending to a stranger, so the absence of
        // identifying detail is the requirement — not the presence of debugging detail.
        val context = ContactReporter.defaultContext()

        listOf(
            System.getProperty("user.name"),
            System.getProperty("user.home"),
            System.getProperty("user.dir"),
        ).filter { !it.isNullOrBlank() }.forEach {
            assertFalse(it in context, "the diagnostics leaked \"$it\": $context")
        }
    }

    @Test
    fun `the context is one short line`() {
        val context = ContactReporter.defaultContext()

        assertFalse("\n" in context, "it is appended to the message body, not attached as a file: $context")
        assertTrue(context.length < 200, "got ${context.length} characters: $context")
    }

    @Test
    fun `the context is the same every time`() {
        assertEquals(
            ContactReporter.defaultContext(),
            ContactReporter.defaultContext(),
            "nothing in it may vary between two reports from the same machine",
        )
    }
}
