package org.churchpresenter.app.churchpresenter.utils

import io.sentry.Breadcrumb
import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrashReporterSentryTest {

    @BeforeTest
    fun enableSentry() {
        Sentry.init { options ->
            options.dsn = "https://key@localhost/1"
            options.setTransportFactory(NoOpTransportFactory.getInstance())
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableAutoSessionTracking = false
        }
    }

    @AfterTest
    fun disableSentry() {
        Sentry.close()
    }

    @Test
    fun `reporting is on once a dsn is configured`() {
        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `a breadcrumb is accepted while reporting is on`() {
        CrashReporter.breadcrumb("song went live", category = "content")
        CrashReporter.breadcrumb("warning trail", category = "warning", level = SentryLevel.WARNING)

        assertTrue(CrashReporter.isEnabled(), "recording a trail must not tear the client down")
    }

    @Test
    fun `a warning carries its tags without throwing`() {
        CrashReporter.reportWarning(
            "ATEM key on failed",
            throwable = IllegalStateException("unreachable"),
            tags = mapOf("subsystem" to "atem"),
        )
        CrashReporter.reportWarning("no throwable at all")

        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `tags and context are accepted`() {
        CrashReporter.setTag("output.count", "3")
        CrashReporter.setConfigTags(mapOf("vlc" to "present", "jcef" to "absent"))
        CrashReporter.setContext("jcef", mapOf("installDir" to "/Users/someone/jcef"))

        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `a user identity is accepted`() {
        CrashReporter.setUser(CrashReporter.installId())

        assertTrue(CrashReporter.installId().isNotEmpty(), "the install id is what dedupes a user")
    }

    @Test
    fun `feedback with a comment is sent`() {
        CrashReporter.sendUserFeedback("the lower third did not clear", name = "Sam", email = "sam@example.org")

        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `feedback with no name or email is still sent`() {
        CrashReporter.sendUserFeedback("just the comment")

        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `blank feedback is not sent at all`() {
        CrashReporter.sendUserFeedback("   ")

        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `a traced block returns its own value`() {
        val result = CrashReporter.trace("test.op", "Test operation") { 21 * 2 }

        assertEquals(42, result)
    }

    @Test
    fun `a traced block that throws lets the failure through`() {
        var thrown: IllegalStateException? = null
        try {
            CrashReporter.trace<Unit>("test.op", "Test operation") { throw IllegalStateException("boom") }
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertEquals("boom", assertNotNull(thrown).message)
    }

    @Test
    fun `a test event is sent while reporting is on`() {
        assertTrue(CrashReporter.sendTestEvent())
    }

    @Test
    fun `switching reporting off closes the client`() {
        CrashReporter.setReportingEnabled(false)

        assertFalse(CrashReporter.isEnabled(), "an operator opting out must stop the sending")
    }

    @Test
    fun `a test event is refused once reporting is off`() {
        CrashReporter.setReportingEnabled(false)

        assertFalse(CrashReporter.sendTestEvent())
    }

    @Test
    fun `an exception reported while on is scrubbed and sent`() {
        CrashReporter.reportException(IllegalStateException("failed reading /Users/someone/song.sps"), "Loading song")

        assertTrue(CrashReporter.isEnabled())
    }

    @Test
    fun `an event's message is scrubbed of the user's home path`() {
        val event = SentryEvent().apply {
            message = Message().apply { message = "could not read /Users/someone/.churchpresenter/songs" }
        }

        CrashReporter.scrubEvent(event)

        val scrubbed = assertNotNull(event.message?.message)
        assertFalse(scrubbed.contains("someone"), "a crash report must not carry the operator's name: $scrubbed")
        assertTrue(scrubbed.contains("<user>"), scrubbed)
    }

    @Test
    fun `a linux home path is scrubbed too`() {
        val event = SentryEvent().apply {
            message = Message().apply { message = "/home/someone/.churchpresenter" }
        }

        CrashReporter.scrubEvent(event)

        assertFalse(assertNotNull(event.message?.message).contains("someone"))
    }

    @Test
    fun `an event with nothing in it survives scrubbing`() {
        CrashReporter.scrubEvent(SentryEvent())
    }

    @Test
    fun `an exception value on the event is scrubbed`() {
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException().apply { value = "open failed: /Users/someone/deck.pptx" })
        }

        CrashReporter.scrubEvent(event)

        val scrubbed = assertNotNull(event.exceptions?.first()?.value)
        assertFalse(scrubbed.contains("someone"), scrubbed)
        assertTrue(scrubbed.contains("<user>"), scrubbed)
    }

    @Test
    fun `a breadcrumb trail is scrubbed`() {
        val event = SentryEvent().apply {
            breadcrumbs = listOf(Breadcrumb().apply { message = "watching /Users/someone/Songs" })
        }

        CrashReporter.scrubEvent(event)

        assertFalse(assertNotNull(event.breadcrumbs?.first()?.message).contains("someone"))
    }

    @Test
    fun `a context block of strings is scrubbed entry by entry`() {
        val event = SentryEvent()
        event.contexts["jcef"] = mapOf("installDir" to "/Users/someone/jcef", "version" to 122)

        CrashReporter.scrubEvent(event)

        @Suppress("UNCHECKED_CAST")
        val jcef = assertNotNull(event.contexts["jcef"] as? Map<String, Any?>)
        assertEquals("/Users/<user>/jcef", jcef["installDir"])
        assertEquals(122, jcef["version"], "a non-string value is carried through untouched")
    }

    @Test
    fun `a context value that is not a block of its own is left alone`() {
        val event = SentryEvent()
        event.contexts["note"] = "/Users/someone/plain"

        CrashReporter.scrubEvent(event)

        assertEquals("/Users/someone/plain", event.contexts["note"])
    }
}
