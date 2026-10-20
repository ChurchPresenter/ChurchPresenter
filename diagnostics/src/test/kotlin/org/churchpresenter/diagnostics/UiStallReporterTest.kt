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
        assertEquals(
            mapOf(
                "stall.ms" to "1200",
                "stall.budget.ms" to "100",
                "stall.samples" to "1",
                "stall.samples.top" to "1x org.churchpresenter.liveoutput.PresenterScreen.draw",
            ),
            event.extras,
        )
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

    private fun frame(className: String, method: String) = StackTraceElement(className, method, null, -1)

    @Test
    fun `a stall linking a lambda or a call site is a cold load`() {
        val link = frame("java.lang.invoke.MethodHandleNatives", "linkCallSite")
        val metafactory = frame("java.lang.invoke.LambdaMetafactory", "metafactory")

        listOf(link, metafactory).forEach { loader ->
            val event = stallEvent(Stall(600, 100, listOf(loader, appFrame)), live = false)
            assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), event.fingerprint, loader.className)
        }
    }

    @Test
    fun `a string accessor created for the first time is a cold load, and a lazy elsewhere is not`() {
        val lazy = frame("kotlin.SynchronizedLazyImpl", "getValue")
        val accessor = frame("org.churchpresenter.strings.generated.resources.String5_mainKt", "getBible_tab")
        val ownLazy = frame("org.churchpresenter.bible.Books", "getAll")

        val resource = stallEvent(Stall(600, 100, listOf(jdkFrame, lazy, accessor, appFrame)), live = false)
        val other = stallEvent(Stall(600, 100, listOf(jdkFrame, lazy, ownLazy, appFrame)), live = false)

        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), resource.fingerprint)
        assertEquals(listOf("ui-stall", "org.churchpresenter.bible.Books.getAll"), other.fingerprint)
    }

    @Test
    fun `a generated resource accessor is not the app's own code`() {
        val accessor = frame("org.churchpresenter.strings.generated.resources.String5_mainKt", "getBible_tab")

        val event = stallEvent(Stall(600, 100, listOf(jdkFrame, accessor, appFrame)), live = false)

        assertEquals(listOf("ui-stall", "${appFrame.className}.${appFrame.methodName}"), event.fingerprint)
    }

    @Test
    fun `a sampled stall groups on the app frame most samples were in, and shows that sample's stack`() {
        val measure = frame("org.churchpresenter.songs.SongList", "measure")
        val drawn = listOf(jdkFrame, appFrame)
        val measured = listOf(measure, appFrame)
        val outside = listOf(jdkFrame)
        val stall = Stall(900, 100, frames = drawn, samples = listOf(drawn, measured, outside, measured, measured))

        val event = stallEvent(stall, live = false)

        assertEquals(listOf("ui-stall", "org.churchpresenter.songs.SongList.measure"), event.fingerprint)
        assertEquals(measured, event.frames)
        assertEquals("5", event.extras["stall.samples"])
        assertEquals(
            "3x org.churchpresenter.songs.SongList.measure; 1x ${appFrame.className}.draw; 1x -",
            event.extras["stall.samples.top"],
        )
    }

    @Test
    fun `a sample taken as the thread answered is left out of the count`() {
        val answering = listOf(frame(StallDetector::class.java.name, "answered"), appFrame)
        val measure = listOf(frame("org.churchpresenter.songs.SongList", "measure"), appFrame)
        val stall = Stall(900, 100, frames = measure, samples = listOf(measure, answering, answering))

        val event = stallEvent(stall, live = false)

        assertEquals(listOf("ui-stall", "org.churchpresenter.songs.SongList.measure"), event.fingerprint)
        assertEquals("1", event.extras["stall.samples"])
    }

    @Test
    fun `a stall is a cold load only when most of its samples are`() {
        val classLoad = listOf(frame("java.lang.ClassLoader", "defineClass1"), appFrame)
        val drawing = listOf(jdkFrame, appFrame)

        val mostlyLoading = stallEvent(Stall(900, 100, classLoad, listOf(classLoad, classLoad, drawing)), false)
        val mostlyDrawing = stallEvent(Stall(900, 100, classLoad, listOf(classLoad, drawing, drawing)), false)

        assertEquals(STALL_CAUSE_COLD_LOAD, mostlyLoading.tags["stall.cause"])
        assertEquals(listOf("ui-stall", "${appFrame.className}.draw"), mostlyDrawing.fingerprint)
    }

    @Test
    fun `the screen a stall happened on is tagged and grouped on, ahead of the frame`() {
        val stack = listOf(
            frame("org.churchpresenter.songs.SongList", "measure"),
            frame("org.churchpresenter.profiles.ProfileSongsPageKt", "ProfileSongsPage\$lambda\$3"),
            frame("org.churchpresenter.profiles.ProfilesSettingsTabKt\$ProfilesSettingsTab\$1", "invoke"),
            frame("org.churchpresenter.app.churchpresenter.dialogs.SettingsTabContentKt", "SettingsTabContent"),
            frame("org.churchpresenter.app.churchpresenter.dialogs.OptionsDialogKt", "OptionsDialog"),
        )

        val event = stallEvent(Stall(900, 100, stack), live = false)

        assertEquals("ProfileSongsPage", event.tags["stall.screen"])
        assertEquals(
            listOf("ui-stall", "ProfileSongsPage", "org.churchpresenter.songs.SongList.measure"),
            event.fingerprint,
        )
    }

    @Test
    fun `the screen is the outermost of the finest kind on the stack`() {
        fun screen(vararg names: Pair<String, String>) = screenOf(names.map { (c, m) -> frame(c, m) })

        assertEquals(
            "SystemSettingsTab",
            screen(
                "org.churchpresenter.x.LabeledTabKt" to "LabeledTab",
                "org.churchpresenter.x.SystemSettingsTabKt" to "SystemSettingsTab",
                "org.churchpresenter.x.SettingsTabContentKt" to "SettingsTabContent",
                "org.churchpresenter.x.OptionsDialogKt" to "OptionsDialog",
            ),
        )
        assertEquals(
            "SettingsTabContent",
            screen(
                "org.churchpresenter.x.SettingsTabContentKt" to "SettingsTabContent",
                "org.churchpresenter.x.OptionsDialogKt\$OptionsDialog\$1" to "invoke",
            ),
        )
        assertEquals("OptionsDialog", screen("org.churchpresenter.x.OptionsDialogKt\$OptionsDialog\$1" to "invoke"))
        assertEquals(
            null,
            screen(
                "androidx.compose.material3.TabKt" to "Tab",
                "org.churchpresenter.x.ComposableSingletons\$BibleTabKt\$lambda-1\$1" to "invoke",
                "org.churchpresenter.x.Tab" to "draw",
            ),
        )
    }
}
