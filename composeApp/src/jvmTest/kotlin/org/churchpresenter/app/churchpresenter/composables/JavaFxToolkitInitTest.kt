package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.TestSingletons
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaFxToolkitInitTest {

    private var starts = 0
    private var started = 0

    @BeforeTest
    fun latchHome() {
        TestSingletons.latchToTestHome()
    }

    private fun init(start: () -> Unit = {}) = JfxToolkitInit(startToolkit = { starts++; start() }) { started++ }

    @Test
    fun `the toolkit is started once however often it is asked for`() {
        val init = init()
        init.ensureInit()
        init.ensureInit()
        assertEquals(1, starts)
        assertEquals(1, started)
        assertTrue(init.available)
    }

    @Test
    fun `a toolkit that refuses to start is marked unavailable and never retried`() {
        val init = init { throw IllegalStateException("No toolkit found") }
        init.ensureInit()
        init.ensureInit()
        assertFalse(init.available)
        assertEquals(1, starts)
        assertEquals(0, started, "nothing is installed on a toolkit that never started")
    }

    @Test
    fun `a native load failure is an error, and is absorbed the same way`() {
        val init = init { throw UnsatisfiedLinkError("no prism_es2") }
        init.ensureInit()
        assertFalse(init.available)
    }

    @Test
    fun `a JVM out of headroom is not absorbed`() {
        val init = init { throw OutOfMemoryError("test") }
        assertFailsWith<OutOfMemoryError> { init.ensureInit() }
        assertEquals(0, started)
    }

    @Test
    fun `the process-wide toolkit reports available before anything tried it`() {
        assertTrue(isJavaFxAvailable())
    }

    private fun raceNpe() = NullPointerException().apply {
        stackTrace = arrayOf(StackTraceElement("com.sun.glass.ui.Screen", "notifySettingsChanged", "Screen.java", 1))
    }

    @Test
    fun `the screen race is downgraded instead of reaching the default handler`() {
        val reached = mutableListOf<Throwable>()
        javaFxScreenRaceHandler { _, t -> reached += t }.uncaughtException(Thread.currentThread(), raceNpe())
        assertEquals(emptyList(), reached)
    }

    @Test
    fun `anything else is handed to the default handler`() {
        val reached = mutableListOf<Throwable>()
        val other = IllegalStateException("boom")
        javaFxScreenRaceHandler { _, t -> reached += t }.uncaughtException(Thread.currentThread(), other)
        assertEquals(listOf<Throwable>(other), reached)
    }

    @Test
    fun `with no default handler anything else is dropped quietly`() {
        javaFxScreenRaceHandler(null).uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
    }
}
