package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiStallReporterTest {

    private var now = 0L
    private val sent = mutableListOf<StallEvent>()
    private val reporter = UiStallReporter(idleBudgetMs = 500, clock = { now }, send = { sent += it })

    private val appFrame =
        StackTraceElement("org.churchpresenter.liveoutput.PresenterScreen", "draw", "PresenterScreen.kt", 10)
    private val jdkFrame = StackTraceElement("java.io.FileInputStream", "readBytes", "FileInputStream.java", 1)

    /** The event for [stall] on a GPU-drawn Mac, which must be sent. */
    private fun eventOf(stall: Stall, live: Boolean) =
        assertNotNull(stallEvent(stall, live, renderApi = "METAL", osFamily = "macos"))

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
        reporter.renderApi("SOFTWARE_FAST")
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

        val loaded = eventOf(Stall(600, 100, listOf(classLoad, appFrame)), live = false)
        val string = eventOf(Stall(600, 100, listOf(resource, appFrame)), live = false)

        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), loaded.fingerprint)
        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), string.fingerprint)
        assertEquals(STALL_CAUSE_COLD_LOAD, loaded.tags["stall.cause"])
    }

    @Test
    fun `a class loaded below the app's own frame is not a cold load`() {
        // The app frame is where the thread was stuck; a loader further out is how it got there.
        val classLoad = StackTraceElement("jdk.internal.loader.BuiltinClassLoader", "loadClass", null, -1)

        val event = eventOf(Stall(600, 100, listOf(jdkFrame, appFrame, classLoad)), live = false)

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
        val event = eventOf(stall, live = true)

        assertEquals(listOf("ui-stall", "org.churchpresenter.liveoutput.PresenterScreen.draw"), event.fingerprint)
        assertEquals(mapOf("stall.live" to "true", "stall.length" to "1-2s"), event.tags)
        assertEquals(
            mapOf(
                "stall.ms" to "1200",
                "stall.budget.ms" to "100",
                "stall.first.sample.ms" to "100",
                "stall.samples" to "1",
                "stall.samples.top" to "1x org.churchpresenter.liveoutput.PresenterScreen.draw",
            ),
            event.extras,
        )
        assertEquals(listOf(jdkFrame, appFrame), event.frames)
    }

    @Test
    fun `a stall wholly outside the app groups on its OS and render API, and names its top frame`() {
        val library = eventOf(Stall(300, 100, listOf(jdkFrame)), live = false)
        assertEquals(listOf("ui-stall", STALL_CAUSE_NO_APP_FRAME, "macos", "METAL"), library.fingerprint)
        assertEquals(STALL_CAUSE_NO_APP_FRAME, library.tags["stall.cause"])
        assertEquals("java.io.FileInputStream.readBytes", library.tags["stall.top"])

        val noStack = eventOf(Stall(300, 100, emptyList()), live = false)
        assertEquals(listOf("ui-stall", STALL_CAUSE_NO_APP_FRAME, "macos", "METAL"), noStack.fingerprint)
        assertEquals("no-frames", noStack.tags["stall.top"])
    }

    @Test
    fun `a stall with no app frame groups apart per OS and per render API`() {
        val stall = Stall(300, 100, listOf(jdkFrame))

        val windows = stallEvent(stall, live = false, renderApi = "OPENGL", osFamily = "windows")
        val direct3d = stallEvent(stall, live = false, renderApi = "DIRECT3D", osFamily = "windows")

        assertEquals(listOf("ui-stall", STALL_CAUSE_NO_APP_FRAME, "windows", "OPENGL"), windows?.fingerprint)
        assertEquals(listOf("ui-stall", STALL_CAUSE_NO_APP_FRAME, "windows", "DIRECT3D"), direct3d?.fingerprint)
    }

    @Test
    fun `before the main window says how it draws, a stall reports the render API as unknown`() {
        settled()
        now += 1_000

        reporter.stalled(stall(600, listOf(jdkFrame)))

        assertEquals(RENDER_API_UNKNOWN, sent.single().fingerprint.last())
    }

    @Test
    fun `a render API the main window drew with on the GPU is not software`() {
        settled()
        reporter.renderApi("METAL")
        now += 1_000

        reporter.stalled(stall(600))

        assertTrue("stall.cause" !in sent.single().tags)
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
            val event = eventOf(Stall(600, 100, listOf(loader, appFrame)), live = false)
            assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), event.fingerprint, loader.className)
        }
    }

    @Test
    fun `a string accessor created for the first time is a cold load, and a lazy elsewhere is not`() {
        val lazy = frame("kotlin.SynchronizedLazyImpl", "getValue")
        val accessor = frame("org.churchpresenter.strings.generated.resources.String5_mainKt", "getBible_tab")
        val ownLazy = frame("org.churchpresenter.bible.Books", "getAll")

        val resource = eventOf(Stall(600, 100, listOf(jdkFrame, lazy, accessor, appFrame)), live = false)
        val other = eventOf(Stall(600, 100, listOf(jdkFrame, lazy, ownLazy, appFrame)), live = false)

        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), resource.fingerprint)
        assertEquals(listOf("ui-stall", "org.churchpresenter.bible.Books.getAll"), other.fingerprint)
    }

    @Test
    fun `a generated resource accessor is not the app's own code`() {
        val accessor = frame("org.churchpresenter.strings.generated.resources.String5_mainKt", "getBible_tab")

        val event = eventOf(Stall(600, 100, listOf(jdkFrame, accessor, appFrame)), live = false)

        assertEquals(listOf("ui-stall", "${appFrame.className}.${appFrame.methodName}"), event.fingerprint)
    }

    @Test
    fun `a sampled stall groups on the app frame most samples were in, and shows that sample's stack`() {
        val measure = frame("org.churchpresenter.songs.SongList", "measure")
        val drawn = listOf(jdkFrame, appFrame)
        val measured = listOf(measure, appFrame)
        val outside = listOf(jdkFrame)
        val stall = Stall(900, 100, frames = drawn, samples = listOf(drawn, measured, outside, measured, measured))

        val event = eventOf(stall, live = false)

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

        val event = eventOf(stall, live = false)

        assertEquals(listOf("ui-stall", "org.churchpresenter.songs.SongList.measure"), event.fingerprint)
        assertEquals("1", event.extras["stall.samples"])
    }

    @Test
    fun `a stall is a cold load only when most of its samples are`() {
        val classLoad = listOf(frame("java.lang.ClassLoader", "defineClass1"), appFrame)
        val drawing = listOf(jdkFrame, appFrame)

        val mostlyLoading = eventOf(Stall(900, 100, classLoad, listOf(classLoad, classLoad, drawing)), false)
        val mostlyDrawing = eventOf(Stall(900, 100, classLoad, listOf(classLoad, drawing, drawing)), false)

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

        val event = eventOf(Stall(900, 100, stack), live = false)

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

    private val waiting = listOf(
        frame("jdk.internal.misc.Unsafe", "park"),
        frame("java.awt.EventQueue", "getNextEvent"),
        frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
        frame("java.awt.EventDispatchThread", "run"),
    )

    @Test
    fun `a sample of the thread waiting for its next event is idle, one inside an event is not`() {
        val nestedWait = waiting.take(3) + listOf(
            frame("java.awt.Dialog", "show"),
            appFrame,
            frame("java.awt.EventQueue", "dispatchEvent"),
        ) + waiting.drop(2)
        val loadingAQueuedClass = listOf(
            frame("java.awt.EventQueue", "dispatchEvent"),
            frame("java.awt.EventQueue", "getNextEvent"),
        )

        assertTrue(isIdle(waiting))
        assertTrue(isIdle(nestedWait), "a modal dialog's own loop waiting is the thread free")
        assertTrue(!isIdle(listOf(jdkFrame, appFrame)))
        assertTrue(!isIdle(loadingAQueuedClass), "an event being dispatched is not idle")
    }

    @Test
    fun `a stall whose every sample found the thread idle is counted but not sent`() {
        settled()
        reporter.outputLive(true)
        now += 1_000

        reporter.stalled(Stall(lateMs = 600, budgetMs = reporter.budgetMs(), frames = waiting))

        assertTrue(sent.isEmpty(), "the thread had moved on: there is nothing to group it on")
        assertEquals(1, reporter.liveStallsPastGate())
        assertNull(stallEvent(Stall(600, 100, waiting), live = false))
    }

    @Test
    fun `idle samples are left out, and the rest still group`() {
        val drawing = listOf(jdkFrame, appFrame)

        val event = eventOf(Stall(900, 100, waiting, listOf(waiting, drawing, waiting)), live = false)

        assertEquals(listOf("ui-stall", "${appFrame.className}.draw"), event.fingerprint)
        assertEquals(drawing, event.frames)
        assertEquals("1", event.extras["stall.samples"])
    }

    @Test
    fun `a stall under a modal dialog groups on what the dialog's loop runs, not on what opened it`() {
        val opener = frame("org.churchpresenter.songs.SongsTabKt", "SongsTab")
        val stack = listOf(
            frame("org.jetbrains.skiko.redrawer.LinuxOpenGLRedrawer", "makeCurrent"),
            frame("java.awt.EventQueue", "dispatchEvent"),
            frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
            frame("java.awt.WaitDispatchSupport", "enter"),
            frame("javax.swing.JOptionPane", "showOptionDialog"),
            frame("org.churchpresenter.songs.SongImport", "confirmOverwrite"),
            opener,
            frame("java.awt.EventQueue", "dispatchEvent"),
            frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
        )

        val event = eventOf(Stall(900, 100, stack), live = false)

        assertEquals(listOf("ui-stall", STALL_CAUSE_NO_APP_FRAME, "macos", "METAL"), event.fingerprint)
        assertEquals("org.churchpresenter.songs.SongImport.confirmOverwrite", event.tags["stall.modal"])
        assertNull(event.tags["stall.screen"], "the tab below the dialog is not where the thread was")
        assertEquals(stack, event.frames, "Sentry still shows the whole stack")
    }

    @Test
    fun `a stall outside any nested loop carries no modal tag`() {
        val stack = listOf(jdkFrame, appFrame) + waiting.drop(2)

        val event = eventOf(Stall(900, 100, stack), live = false)

        assertNull(event.tags["stall.modal"])
        assertEquals(listOf("ui-stall", "${appFrame.className}.draw"), event.fingerprint)
    }

    @Test
    fun `a first sample far past the budget is the whole process paused`() {
        val budget = 500L
        val onTime = Stall(645, budget, listOf(jdkFrame, appFrame), firstSampleMs = pausedPastMs(budget))
        val late = onTime.copy(firstSampleMs = pausedPastMs(budget) + 1)

        assertEquals(listOf("ui-stall", "${appFrame.className}.draw"), eventOf(onTime, live = false).fingerprint)
        val paused = eventOf(late, live = false)
        assertEquals(listOf("ui-stall", STALL_CAUSE_PAUSED), paused.fingerprint)
        assertEquals(STALL_CAUSE_PAUSED, paused.tags["stall.cause"])
        assertEquals("${pausedPastMs(budget) + 1}", paused.extras["stall.first.sample.ms"])
    }

    @Test
    fun `a paused process is sent even when its only sample found the thread idle`() {
        val event = eventOf(Stall(322, 100, waiting, firstSampleMs = 300), live = false)

        assertEquals(listOf("ui-stall", STALL_CAUSE_PAUSED), event.fingerprint)
        assertEquals("0", event.extras["stall.samples"])
    }

    @Test
    fun `the paused threshold allows a poll past the budget and two sample intervals`() {
        assertEquals(500L + 100 + 100, pausedPastMs(500))
        assertEquals(100L + 20 + 100, pausedPastMs(100))
    }

    @Test
    fun `software rendering comes before a cold load, and a cold load before a paused process`() {
        val classLoad = listOf(frame("java.lang.ClassLoader", "defineClass1"), appFrame)
        val stall = Stall(900, 100, classLoad, firstSampleMs = 5_000)

        assertEquals(
            listOf("ui-stall", STALL_CAUSE_SOFTWARE),
            stallEvent(stall, live = false, renderApi = "SOFTWARE_COMPAT")?.fingerprint,
        )
        assertEquals(listOf("ui-stall", STALL_CAUSE_COLD_LOAD), eventOf(stall, live = false).fingerprint)
    }
}
