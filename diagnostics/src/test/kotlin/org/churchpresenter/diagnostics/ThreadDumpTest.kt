package org.churchpresenter.diagnostics

import java.awt.EventQueue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadDumpTest {

    @Test
    fun `a dump has the header, the lock verdict and every thread by name`() {
        val text = ThreadDump.text("=== A HEADER ===")

        assertContains(text, "=== A HEADER ===")
        assertContains(text, "No monitor/synchronizer deadlock cycle found.")
        assertContains(text, "--- \"${Thread.currentThread().name}\"")
    }

    @Test
    fun `the stack of a named thread is its top frames, and an unknown name has none`() {
        val parked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thread = Thread({
            parked.countDown()
            release.await(10, TimeUnit.SECONDS)
        }, "dump-test-parked").apply { isDaemon = true; start() }
        try {
            assertTrue(parked.await(10, TimeUnit.SECONDS))
            val stack = assertNotNull(ThreadDump.stackOf("dump-test-parked", depth = 3))
            assertTrue(stack.size in 1..3)
            assertNull(ThreadDump.stackOf("no such thread"))
        } finally {
            release.countDown()
            thread.join(TimeUnit.SECONDS.toMillis(10))
        }
    }

    @Test
    fun `two threads that wait on each other's locks are reported as a deadlock cycle, with owners`() {
        val a = Any()
        val b = Any()
        val bothHold = CountDownLatch(2)
        val release = CountDownLatch(1)
        fun locker(name: String, first: Any, second: Any) = Thread({
            synchronized(first) {
                bothHold.countDown()
                bothHold.await(10, TimeUnit.SECONDS)
                synchronized(second) { release.await(10, TimeUnit.SECONDS) }
            }
        }, name).apply { isDaemon = true; start() }
        val one = locker("deadlock-one", a, b)
        val two = locker("deadlock-two", b, a)
        try {
            assertTrue(bothHold.await(10, TimeUnit.SECONDS))
            val text = awaitCycle()
            assertContains(text, "DEADLOCK CYCLE: 2 threads")
            assertContains(text, "held by \"deadlock-")
        } finally {
            // The cycle cannot be released; the daemons die with the JVM.
            one.interrupt()
            two.interrupt()
        }
    }

    /** The dump once the JVM sees the cycle -- it takes a moment after both threads are blocked. */
    private fun awaitCycle(): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val text = ThreadDump.text("probe")
            if ("DEADLOCK CYCLE" in text) return text
            Thread.yield()
        }
        return ThreadDump.text("probe")
    }

    @Test
    fun `starting the watchdog twice runs one thread, and stopping it ends it`() {
        UiWatchdog.start()
        UiWatchdog.start(budgetMs = 1_000)

        assertEquals(1, Thread.getAllStackTraces().keys.count { it.name == "ui-watchdog" && it.isAlive })

        UiWatchdog.stop()
    }

    @Test
    fun `a blocked event thread is reported while it is blocked, and again when it answers`() {
        val captured = ByteArrayOutputStream()
        val stderr = System.err
        val release = CountDownLatch(1)
        System.setErr(PrintStream(captured, true))
        try {
            EventQueue.invokeLater { release.await(10, TimeUnit.SECONDS) }
            UiWatchdog.start(budgetMs = 50)
            assertContains(awaitText(captured, "has not answered"), "[UiWatchdog]")
            release.countDown()
            assertContains(awaitText(captured, "UI thread answered after"), "(budget 50ms)")
        } finally {
            release.countDown()
            UiWatchdog.stop()
            System.setErr(stderr)
        }
    }

    /** What was written to [out] once it holds [wanted], read on the condition and not after a pause. */
    private fun awaitText(out: ByteArrayOutputStream, wanted: String): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline && wanted !in out.toString()) Thread.yield()
        return out.toString()
    }
}
