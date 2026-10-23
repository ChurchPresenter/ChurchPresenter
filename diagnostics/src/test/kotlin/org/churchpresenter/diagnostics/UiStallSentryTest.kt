package org.churchpresenter.diagnostics

import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A stall as Sentry receives it, seen by a `beforeSend` hook ahead of the (no-op) transport. */
class UiStallSentryTest {

    private val received = CopyOnWriteArrayList<Pair<SentryEvent, String>>()
    private val arrived = CountDownLatch(1)
    private val frame =
        StackTraceElement("org.churchpresenter.liveoutput.PresenterScreen", "draw", "PresenterScreen.kt", 10)

    @BeforeTest
    fun enableSentry() {
        Sentry.init { options ->
            options.dsn = "https://key@localhost/1"
            options.setTransportFactory(NoOpTransportFactory.getInstance())
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableAutoSessionTracking = false
            options.setBeforeSend { event, _ ->
                received += event to Thread.currentThread().name
                arrived.countDown()
                null
            }
        }
    }

    @AfterTest
    fun disableSentry() {
        Sentry.close()
    }

    @Test
    fun `a warning with a fingerprint groups on it`() {
        CrashReporter.reportWarning("grouped", fingerprint = listOf("ui-stall", "a.b"))
        CrashReporter.reportWarning("ungrouped")

        val (grouped, ungrouped) = received.map { it.first }
        assertEquals(listOf("ui-stall", "a.b"), grouped.fingerprints)
        assertTrue(ungrouped.fingerprints.isNullOrEmpty(), "Sentry's own grouping when none is given")
    }

    @Test
    fun `a stall reaches Sentry as a warning carrying its stack, from a thread of its own`() {
        val event = assertNotNull(stallEvent(Stall(lateMs = 700, budgetMs = 100, frames = listOf(frame)), live = true))

        UiStallReporter.sendToSentry(event)

        assertTrue(arrived.await(10, TimeUnit.SECONDS), "the stall never reached Sentry")
        val (sent, thread) = received.single()
        assertEquals("ui-stall-sender", thread, "never the event thread that answered the ping")
        assertEquals(SentryLevel.WARNING, sent.level, "a warning carries no unrelated crash log")
        assertEquals(STALL_CONTEXT, sent.message?.message)
        assertEquals(event.fingerprint, sent.fingerprints)
        assertEquals("true", sent.getTag("stall.live"))
        assertEquals("draw", sent.exceptions?.single()?.stacktrace?.frames?.single()?.function)
    }
}
