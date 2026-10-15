package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterManager.LiveStateSnapshot
import org.churchpresenter.outputguard.GuardFiles
import org.churchpresenter.outputguard.GuardState
import org.churchpresenter.outputguard.OUTPUT_GUARD_ARGUMENT
import org.churchpresenter.outputguard.OutputGuardLink
import org.churchpresenter.outputguard.RELAUNCHED_ARGUMENT
import org.churchpresenter.sharedui.models.Presenting
import java.awt.Rectangle
import javax.swing.JPanel
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app's side of the output guard: how the guard and a relaunched app are started, the guard
 * running while it is switched on, what is live reaching it, and a relaunched app putting it back.
 * The guard here is never a real process: [rememberOutputGuard] is handed one that starts nothing.
 */
@OptIn(ExperimentalTestApi::class)
class OutputGuardWiringTest {

    private val files = GuardFiles.forUser()
    private val dir = File(System.getProperty("user.home"), "guard")
    private val verse = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved")
    private val scope = CoroutineScope(SupervisorJob())

    /** The app's root state, with no displays besides the main one. */
    private fun appRoot() = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        files.dir.deleteRecursively()
    }

    /** A process's command line as [ProcessHandle.Info] reports it, or does not. */
    private class Info(private val command: String?, private val arguments: List<String>?) : ProcessHandle.Info {
        override fun command(): Optional<String> = Optional.ofNullable(command)
        override fun commandLine(): Optional<String> = Optional.empty()
        override fun arguments(): Optional<Array<String>> = Optional.ofNullable(arguments?.toTypedArray())
        override fun startInstant(): Optional<Instant> = Optional.empty()
        override fun totalCpuDuration(): Optional<Duration> = Optional.empty()
        override fun user(): Optional<String> = Optional.empty()
    }

    @Test
    fun `a packaged app starts its guard, and itself again, through its own launcher`() {
        val none = Info(command = null, arguments = null)

        val guard = guardCommand(dir, appPath = "/Apps/CP", info = none)

        assertEquals(listOf("/Apps/CP", OUTPUT_GUARD_ARGUMENT, dir.path), guard)
        assertEquals(listOf("/Apps/CP"), relaunchCommand(appPath = "/Apps/CP", info = none))
    }

    @Test
    fun `run from the build, the guard is this JVM and its options, and a relaunch is this command once more`() {
        val info = Info("/jdk/bin/java", listOf("-Xmx2g", "-cp", "a.jar", MAIN_CLASS_NAME, RELAUNCHED_ARGUMENT))

        assertEquals(
            listOf("/jdk/bin/java", "-Xmx2g", "-cp", "a.jar", MAIN_CLASS_NAME, OUTPUT_GUARD_ARGUMENT, dir.path),
            guardCommand(dir, appPath = null, info = info),
        )
        assertEquals(
            listOf("/jdk/bin/java", "-Xmx2g", "-cp", "a.jar", MAIN_CLASS_NAME),
            relaunchCommand(appPath = null, info = info),
            "the guard adds the relaunch flag itself, so a second relaunch does not carry it twice",
        )
    }

    @Test
    fun `with no command line to copy, the guard is started from this JVM's home and class path`() {
        val none = Info(command = null, arguments = null)

        val command = guardCommand(dir, appPath = null, info = none)

        assertEquals(File(System.getProperty("java.home"), "bin/java").path, command.first())
        assertEquals(
            listOf("-cp", System.getProperty("java.class.path"), MAIN_CLASS_NAME, OUTPUT_GUARD_ARGUMENT, dir.path),
            command.drop(1),
        )
        assertEquals(emptyList(), relaunchCommand(appPath = null, info = none))

        val noMain = guardCommand(dir, appPath = null, info = Info("/jdk/bin/java", listOf("-Xmx2g")))
        assertEquals(listOf("/jdk/bin/java", "-cp"), noMain.take(2), "a command line without the main class")
    }

    @Test
    fun `switched on the guard is started for this app, and switched off it is told to leave`() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        var link: OutputGuardLink? = null
        var starts = 0
        setContent { link = rememberOutputGuard(enabled, startGuard = { starts++; null }) }

        waitUntil(timeoutMillis = 5_000) { link != null }
        val state = GuardState.open(files.state)
        assertEquals(ProcessHandle.current().pid(), state.appPid)
        assertEquals(1, starts)

        enabled = false
        waitUntil(timeoutMillis = 5_000) { link == null }
        assertTrue(state.closing)
    }

    @Test
    fun `what goes live is told to the guard, with the live state saved for a relaunch`() = runComposeUiTest {
        val link = OutputGuardLink(files, startGuard = { null }, relaunchCommand = emptyList())
        try {
            // Neither has a Skia surface to read a still from: one is no window, the other a plain panel.
            link.register("not a window", { true }, { Rectangle(0, 0, 10, 10) })
            link.register(JPanel(), { true }, { Rectangle(0, 0, 10, 10) })
            val pm = PresenterManager()
            setContent { OutputGuardStills(link, pm) }

            pm.setSelectedVerses(listOf(verse))
            pm.setPresentingMode(Presenting.BIBLE)

            waitUntil(timeoutMillis = 5_000) { link.live && readSavedLiveState(files)?.mode == Presenting.BIBLE }
            assertEquals(listOf(verse), readSavedLiveState(files)?.selectedVerses)
        } finally {
            link.stop()
        }
    }

    @Test
    fun `a relaunched app puts back what was on air`() = runComposeUiTest {
        val saved = PresenterManager().apply {
            setSelectedVerses(listOf(verse))
            setPresentingMode(Presenting.BIBLE)
        }.snapshotLiveState()
        files.writeLiveState(Json.encodeToString(LiveStateSnapshot.serializer(), saved))
        val pm = PresenterManager()

        setContent { RestoreAfterRelaunch(pm) }

        waitUntil(timeoutMillis = 5_000) { pm.slideContent.value == Presenting.BIBLE }
        assertEquals(listOf(verse), pm.selectedVerses.value)
    }

    @Test
    fun `the wiring runs the guard while dev mode and its switch say so, for the outputs to find`() = runComposeUiTest {
        val root = appRoot()
        val projection = root.appSettings.projectionSettings
        root.appSettings = root.appSettings.copy(
            projectionSettings = projection.copy(outputGuardEnabled = true, outputGuardOnHang = true),
        )
        var provided: OutputGuardLink? = null
        setContent {
            with(root) {
                OutputGuardWiring(relaunchedByGuard = false, startGuard = { null }) {
                    provided = LocalOutputGuard.current
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) { provided != null }
        assertTrue(provided!!.takeOverOnHang, "the hang switch reaches the guard")

        root.appSettings = root.appSettings.copy(
            projectionSettings = root.appSettings.projectionSettings.copy(outputGuardEnabled = false),
        )
        waitUntil(timeoutMillis = 5_000) { provided == null }
    }

    @Test
    fun `an app the guard relaunched puts back what was on air, through the wiring`() = runComposeUiTest {
        val saved = PresenterManager().apply {
            setSelectedVerses(listOf(verse))
            setPresentingMode(Presenting.BIBLE)
        }.snapshotLiveState()
        files.writeLiveState(Json.encodeToString(LiveStateSnapshot.serializer(), saved))
        val root = appRoot()

        setContent { with(root) { OutputGuardWiring(relaunchedByGuard = true, startGuard = { null }) { } } }

        waitUntil(timeoutMillis = 5_000) { root.presenterManager.slideContent.value == Presenting.BIBLE }
    }

    @Test
    fun `with no wiring above it an output window finds no guard`() = runComposeUiTest {
        var provided: OutputGuardLink? = OutputGuardLink(files, startGuard = { null }, relaunchCommand = emptyList())
        setContent { provided = LocalOutputGuard.current }

        waitForIdle()
        assertNull(provided)
    }

    @Test
    fun `an output window is followed while it is composed, and its surface read for a still`() = runComposeUiTest {
        val link = OutputGuardLink(files, startGuard = { null }, relaunchCommand = emptyList())
        val panel = JPanel()
        var shown by mutableStateOf(true)
        setContent { if (shown) GuardedOutputWindow(link, panel) }

        waitForIdle()
        assertEquals(listOf<Any>(panel), link.outputs().values.toList())
        assertNull(captureStill(panel), "a plain panel has no Skia surface to read")

        shown = false
        waitForIdle()
        assertTrue(link.outputs().isEmpty())
        link.stop()
    }

    @Test
    fun `by default the guard is started with this JVM's own command line`() {
        val command = guardCommand(dir)

        assertEquals(listOf(OUTPUT_GUARD_ARGUMENT, dir.path), command.takeLast(2))
    }

    @Test
    fun `a live state that is missing or unreadable restores nothing`() {
        assertNull(readSavedLiveState(files))

        files.writeLiveState("not json")

        assertNull(readSavedLiveState(files))
    }
}
