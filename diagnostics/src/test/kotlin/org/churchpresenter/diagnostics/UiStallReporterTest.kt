package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiStallReporterTest {

    private var now = 0L
    private val sent = mutableListOf<StallEvent>()
    private val reporter = UiStallReporter(idleBudgetMs = 500, clock = { now }, send = { sent += it })

    private val appFrame =
        StackTraceElement("org.churchpresenter.liveoutput.PresenterScreen", "draw", "PresenterScreen.kt", 10)
    private val jdkFrame = StackTraceElement("java.io.FileInputStream", "readBytes", "FileInputStream.java", 1)

    private fun stall(lateMs: Long, frames: List<StackTraceElement> = listOf(jdkFrame, appFrame)) =
        Stall(lateMs = lateMs, budgetMs = reporter.budgetMs(), frames = frames)

    /** The main window is up, and the settle window is well behind it. */
    private fun settled() {
        reporter.firstFrame()
        now += UiStallReporter.SETTLE_MS + 10_000
    }

    @Test
    fun `nothing counts before the first frame or while the app settles`() {
        now = 1_000
        reporter.stalled(stall(900))
        reporter.firstFrame()
        now += UiStallReporter.SETTLE_MS
        reporter.stalled(stall(1))
        assertTrue(sent.isEmpty(), "a stall that began inside the settle window is startup")

        now += 2_000
        reporter.stalled(stall(700))
        assertEquals(1, sent.size)
    }

    @Test
    fun `a second first frame does not move the start`() {
        reporter.firstFrame()
        now = 20_000
        reporter.firstFrame()
        now = UiStallReporter.SETTLE_MS + 400

        reporter.stalled(stall(300))

        assertEquals(1, sent.size)
    }

    @Test
    fun `the budget is six frames while an output is live, and the idle budget otherwise`() {
        assertEquals(500, reporter.budgetMs())
        reporter.outputLive(true)
        assertEquals(UiStallReporter.LIVE_BUDGET_MS, reporter.budgetMs())
        reporter.outputLive(false)
        assertEquals(500, reporter.budgetMs())
    }

    @Test
    fun `a live stall past half a second counts against the gate, an idle one or a shorter one does not`() {
        settled()
        reporter.stalled(stall(800))
        reporter.outputLive(true)
        now += 1_000
        reporter.stalled(stall(800))
        now += 1_000
        reporter.stalled(stall(UiStallReporter.GATE_MS))

        assertEquals(1, reporter.liveStallsPastGate())
        assertEquals(listOf("false", "true", "true"), sent.map { it.tags["stall.live"] })
    }

    @Test
    fun `a stall that began before the output went live is not a live stall`() {
        settled()
        reporter.outputLive(true)
        now += 300

        reporter.stalled(stall(900))

        assertEquals(0, reporter.liveStallsPastGate())
        assertEquals("false", sent.single().tags["stall.live"])
    }

    @Test
    fun `a run sends at most ten events, and keeps counting after`() {
        settled()
        reporter.outputLive(true)
        repeat(12) {
            now += 1_000
            reporter.stalled(stall(600))
        }

        assertEquals(UiStallReporter.MAX_EVENTS, sent.size)
        assertEquals(12, reporter.liveStallsPastGate())
    }

    @Test
    fun `a stall shorter than the send minimum is counted but not sent`() {
        settled()
        reporter.outputLive(true)
        now += 1_000

        reporter.stalled(stall(UiStallReporter.SEND_MIN_MS - 1))
        assertTrue(sent.isEmpty(), "a frame a little over budget is ordinary drawing")

        now += 1_000
        reporter.stalled(stall(UiStallReporter.SEND_MIN_MS))
        assertEquals(1, sent.size)
    }

    @Test
    fun `every stall on a window drawn in software groups as one issue`() {
        settled()
        reporter.softwareRendering(true)
        now += 1_000

        reporter.stalled(stall(600))

        val event = sent.single()
        assertEquals(listOf("ui-stall", STALL_CAUSE_SOFTWARE), event.fingerprint)
        assertEquals(STALL_CAUSE_SOFTWARE, event.tags["stall.cause"])
    }

    @Test
    fun `a stall loading code or a string for the first time groups as one issue`() {
        val classLoad = StackTraceElement("java.lang.ClassLoader", "defineClass1", null, -2)
        val resource = StackTraceElement(
            "org.jetbrains.compose.resources.ResourceState_blockingKt", "rememberResourceState", null, 15,
        )

        val loaded = stallEvent(Stall(600, 100, listOf(classLoad, appFrame)), live = false)
        val string = stallEvent(Stall(600, 100, listOf(resource, appFrame)), live = false)

        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), loaded.fingerprint)
        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), string.fingerprint)
        assertEquals(STALL_CAUSE_COLD_LOAD, loaded.tags["stall.cause"])
    }

    @Test
    fun `a class loaded below the app's own frame is not a cold load`() {
        // The app frame is where the thread was stuck; a loader further out is how it got there.
        val classLoad = StackTraceElement("jdk.internal.loader.BuiltinClassLoader", "loadClass", null, -1)

        val event = stallEvent(Stall(600, 100, listOf(jdkFrame, appFrame, classLoad)), live = false)

        assertEquals(listOf("ui-stall", "${appFrame.className}.${appFrame.methodName}"), event.fingerprint)
        assertTrue("stall.cause" !in event.tags)
    }

    @Test
    fun `a stall sampled as the thread answered the ping is counted but not sent`() {
        // The stack a real one carried: the event thread already inside the watchdog's own ping.
        val answering = listOf(
            StackTraceElement(StallDetector::class.java.name, "answered", "UiWatchdog.kt", 49),
            StackTraceElement(UiWatchdog::class.java.name, "watch\$lambda\$0", "UiWatchdog.kt", 127),
            StackTraceElement("java.awt.event.InvocationEvent", "dispatch", "InvocationEvent.java", 318),
        )
        settled()
        reporter.outputLive(true)
        now += 1_000

        reporter.stalled(stall(700, answering))

        assertTrue(sent.isEmpty(), "a stack of the watchdog itself says nothing about the stall")
        assertEquals(1, reporter.liveStallsPastGate(), "but the stall still happened")

        now += 1_000
        reporter.stalled(stall(700))
        assertEquals(1, sent.size, "and the next stall with a real stack is sent")
    }

    @Test
    fun `a run with nothing ever live has no live minutes`() {
        now = 3_600_000

        assertEquals(0, reporter.liveMinutes())
    }

    @Test
    fun `live minutes add up every interval, the one still open included`() {
        reporter.outputLive(true)
        now += 90_000
        reporter.outputLive(false)
        now += 600_000
        reporter.outputLive(false)
        reporter.outputLive(true)
        now += 30_000
        reporter.outputLive(true)
        now += 15_000

        assertEquals(2, reporter.liveMinutes(), "90 s, then 45 s still running: 135 s")
    }

    @Test
    fun `a stall groups on the first frame of the app's own code`() {
        val stall = Stall(lateMs = 1_200, budgetMs = 100, frames = listOf(jdkFrame, appFrame))
        val event = stallEvent(stall, live = true)

        assertEquals(listOf("ui-stall", "org.churchpresenter.liveoutput.PresenterScreen.draw"), event.fingerprint)
        assertEquals(mapOf("stall.live" to "true", "stall.length" to "1-2s"), event.tags)
        assertEquals(mapOf("stall.ms" to "1200", "stall.budget.ms" to "100"), event.extras)
        assertEquals(listOf(jdkFrame, appFrame), event.frames)
    }

    @Test
    fun `a stall wholly outside the app groups on its top frame, and one with no stack still groups`() {
        val library = stallEvent(Stall(300, 100, listOf(jdkFrame)), live = false)
        assertEquals(listOf("ui-stall", "java.io.FileInputStream.readBytes"), library.fingerprint)

        val noStack = stallEvent(Stall(300, 100, emptyList()), live = false)
        assertEquals(listOf("ui-stall", "no-frames"), noStack.fingerprint)
    }

    @Test
    fun `stall lengths fall into ranges, never raw numbers`() {
        assertEquals(
            listOf("under 250ms", "250-500ms", "500ms-1s", "1-2s", "2s or more"),
            listOf(249L, 250L, 999L, 1_000L, 2_000L).map(::lengthBucket),
        )
    }

    @Test
    fun `a stall's stack travels as the frames of a throwable`() {
        val carried = UiStall(listOf(appFrame, jdkFrame))

        assertEquals(listOf(appFrame, jdkFrame), carried.stackTrace.toList())
        assertEquals(STALL_CONTEXT, carried.message)
    }
}
