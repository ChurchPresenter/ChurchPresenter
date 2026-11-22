package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StacklessCrashContextTest {
    private class LibraryThread : Thread()

    private fun stackless() = NullPointerException().apply { stackTrace = emptyArray() }

    @Test
    fun `an exception with frames gets no thread context`() {
        assertNull(CrashReporter.stacklessCrashContext(NullPointerException(), Thread(), emptyList()))
    }

    @Test
    fun `a stackless exception names the thread's class, kind and group`() {
        val thread = LibraryThread().apply { isDaemon = true }

        val context = assertNotNull(CrashReporter.stacklessCrashContext(stackless(), thread, emptyList()))

        assertEquals(LibraryThread::class.java.name, context["class"])
        assertEquals(true, context["daemon"])
        assertEquals(Thread.currentThread().threadGroup.name, context["group"])
    }

    @Test
    fun `the live threads are listed by name, in order`() {
        val live = listOf("ndi-send", "AWT-EventQueue-0", "Thread-19").map { Thread(null, {}, it) }

        val context = assertNotNull(CrashReporter.stacklessCrashContext(stackless(), Thread(), live))

        assertEquals("AWT-EventQueue-0, Thread-19, ndi-send", context["live_threads"])
    }

    @Test
    fun `a long thread list is cut short`() {
        val live = (1..500).map { Thread(null, {}, "worker-thread-$it") }

        val context = assertNotNull(CrashReporter.stacklessCrashContext(stackless(), Thread(), live))

        assertEquals(2_000, (context["live_threads"] as String).length)
    }

    @Test
    fun `a thread that has already ended has no group`() {
        val ended = Thread {}.apply {
            start()
            join()
        }

        val context = assertNotNull(CrashReporter.stacklessCrashContext(stackless(), ended, emptyList()))

        assertEquals("none", context["group"])
    }
}
