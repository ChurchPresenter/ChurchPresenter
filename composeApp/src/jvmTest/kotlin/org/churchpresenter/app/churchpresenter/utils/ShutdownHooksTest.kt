package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shutdown-hook guard.
 *
 * A hook that throws reaches the default uncaught handler, which is the crash reporter — so a
 * clean exit gets filed as a fatal crash. The hook itself cannot be run here without ending the
 * JVM, so what is tested is the body wrapper: it must run the work, and it must not let anything
 * out. `Thread.getUncaughtExceptionHandler` is what would have seen the escape, so the test drives
 * the same shape directly.
 */
class ShutdownHooksTest {

    /** Runs the real guarded body on its own thread and returns whatever escaped it. */
    private fun escapedFrom(body: () -> Unit): Throwable? {
        var escaped: Throwable? = null
        val thread = Thread(guardedShutdownBody(body), "shutdown-test")
        thread.setUncaughtExceptionHandler { _, t -> escaped = t }
        thread.start()
        thread.join()
        return escaped
    }

    @Test
    fun `the guarded body still does its work`() {
        val done = mutableListOf<String>()
        assertEquals(null, escapedFrom { done += "released" })
        assertEquals(listOf("released"), done)
    }

    @Test
    fun `an Error during shutdown does not escape`() {
        // The reported one: NoClassDefFoundError, raised because the hook was the first thing to
        // ask for a class that could no longer be loaded.
        assertEquals(null, escapedFrom { throw NoClassDefFoundError("some/Class") })
    }

    @Test
    fun `an exception during shutdown does not escape`() {
        assertEquals(null, escapedFrom { error("could not release") })
    }

    @Test
    fun `the guarded body runs its work exactly once`() {
        var calls = 0
        guardedShutdownBody { calls++ }.run()
        assertEquals(1, calls)
    }

    @Test
    fun `a failure part way through does not undo what already ran`() {
        val done = mutableListOf<String>()
        assertEquals(null, escapedFrom {
            done += "stopped tunnel"
            error("could not release the rest")
        })
        assertTrue(done.contains("stopped tunnel"))
    }
}
