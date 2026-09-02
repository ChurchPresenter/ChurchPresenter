package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.mockk.every
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.AutoStartManager
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.math.abs
import kotlin.test.assertTrue
import java.nio.file.Path as NioPath

/**
 * The System settings tab: every button clicked, and everything it displays asserted.
 *
 * The tab itself is untouched — no test parameter, no widened member. Each test renders the real
 * composable, drives it the way an operator would, and checks both halves of the result: what
 * appears on screen, and what the `onSettingsChange` transform produced. Settings are held in test
 * state and fed straight back in, exactly as `OptionsDialog` does, so a click is followed all the
 * way to the text it changes.
 *
 * Two OS-level steps sit between a click and its outcome, and neither can run on a headless CI
 * runner. Both are stood in for from the test side rather than by changing the tab:
 * - the **native folder chooser** behind every "Browse…", via a real [FileChooser] subclass
 *   substituted for `FileChooser.platformInstance`. Only the property is stubbed; the chooser it
 *   returns is a plain fake and the assertions are on the path displayed and stored afterwards.
 * - the **Swing dialogs** the sample-copy and Convert buttons put up, via `mockkStatic(JOptionPane)`.
 *   The stubs record what the operator was asked and told; the assertions are on the files those
 *   actions actually wrote.
 * Both are undone in [tidy]; a leaked object mock is a cross-test flake, not a local failure.
 *
 * The auto-start switch is off under Gradle (`jpackage.app-path` is unset, so `AutoStartManager` is
 * unsupported), which leaves the analytics switch as the only toggle whose state comes from the
 * passed-in [AppSettings] — that is what lets these tests locate switches by state and by
 * declaration order without a test tag.
 *
 * Two buttons cannot be carried through to their end: **Import Settings** and **Reset All Settings**
 * both finish with `ProcessBuilder(…).start()` and `Runtime.exit(0)`, which would restart the app and
 * take the test JVM down with it. For those, the confirmation they must ask first is asserted and the
 * answer is always no — a regression that skips the question would kill the test worker, which reads
 * as a crashed executor rather than a neat failure, but it does not pass silently.
 *
 * The picker's status dot carries no semantics of its own, so it cannot be found by a matcher. It is
 * hovered by position instead — derived from the measured bounds of the "Browse…" button beside it —
 * which brings up the real `TooltipArea` and lets each state's wording be asserted. Nothing here uses
 * reflection.
 */
@OptIn(ExperimentalTestApi::class)
class SystemSettingsTabTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun tidy() {
        unmockkAll()
        temps.forEach {
            it.setReadable(true)
            it.setWritable(true)
            it.deleteRecursively()
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("cp-sys-tab").toFile().also { temps.add(it) }

    // ── Standing in for the two steps that need an OS ─────────────────────────

    /** A folder chooser that "returns" [picked] without opening anything. */
    private class FakeChooser(private val picked: String?) : FileChooser() {
        override suspend fun chooseImpl(
            path: NioPath,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean
        ): List<NioPath>? = picked?.let { listOf(Path(it)) }

        override suspend fun saveImpl(
            location: NioPath,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String
        ): NioPath? = picked?.let { Path(it) }
    }

    /** Makes every "Browse…" in the tab resolve to [picked] (null = the operator cancelled). */
    private fun givenFolderChooserReturns(picked: String?) {
        mockkObject(FileChooser.Companion)
        every { FileChooser.platformInstance } returns FakeChooser(picked)
    }

    /** What the tab asked the operator, in order. */
    private val asked = mutableListOf<String>()

    /** What the tab told the operator, in order. */
    private val told = mutableListOf<String>()

    /**
     * Stands in for both Swing dialogs, answering every question with [confirmAnswer]. Both are
     * stubbed in one `mockkStatic` call: re-mocking the class a second time drops the first stub,
     * and an unstubbed `JOptionPane` call cannot run headless.
     */
    private fun stubSwingDialogs(confirmAnswer: Int = JOptionPane.YES_OPTION) {
        mockkStatic(JOptionPane::class)
        every { JOptionPane.showConfirmDialog(any(), any(), any(), any(), any()) } answers {
            asked += secondArg<Any?>().toString()
            confirmAnswer
        }
        every { JOptionPane.showMessageDialog(any(), any(), any(), any()) } answers {
            told += secondArg<Any?>().toString()
            Unit
        }
    }

    /**
     * Returns once everything already queued on the AWT event thread has run. `Reset All Settings`
     * and `Clear Remote Uploads` post their confirmation with `SwingUtilities.invokeLater`, so
     * queueing an empty task behind it and waiting for that is exact — no polling, no timeout.
     */
    private fun flushEventQueue() = SwingUtilities.invokeAndWait { }

    // ── Switches ──────────────────────────────────────────────────────────────

    @Test
    fun `toggling the analytics switch off flips the setting through the callback`() = runComposeUiTest {
        var applied: AppSettings? = null
        val initial = AppSettings(analyticsReportingEnabled = true)
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = initial,
                    onSettingsChange = { transform -> applied = transform(initial) },
                )
            }
        }

        onNode(isToggleable() and isOn()).performScrollTo().performClick()
        waitForIdle()

        assertEquals(false, applied?.analyticsReportingEnabled, "clicking the on analytics switch turns reporting off")
    }

    @Test
    fun `toggling the analytics switch on flips the setting through the callback`() = runComposeUiTest {
        var applied: AppSettings? = null
        val initial = AppSettings(analyticsReportingEnabled = false)
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = initial,
                    onSettingsChange = { transform -> applied = transform(initial) },
                )
            }
        }

        // Both switches are off here; analytics is the second one declared, after launch-at-login.
        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        waitForIdle()

        assertEquals(true, applied?.analyticsReportingEnabled, "clicking the off analytics switch turns reporting on")
    }

    @Test
    fun `the analytics switch shows off when reporting is disabled`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = false),
                )
            }
        }

        onAllNodes(isToggleable() and isOn()).assertCountEquals(0)
    }

    @Test
    fun `the analytics switch shows on when reporting is enabled`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = true),
                )
            }
        }

        onAllNodes(isToggleable() and isOn()).assertCountEquals(1)
    }

    @Test
    fun `the tab can be driven without a settings callback at all`() = runComposeUiTest {
        // onSettingsChange is defaulted; a caller may leave it out. Flipping a switch must then be
        // a no-op rather than a crash, and the switch must keep reporting the settings it was given.
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = true),
                )
            }
        }

        onNode(isToggleable() and isOn()).performScrollTo().performClick()
        waitForIdle()

        onAllNodes(isToggleable() and isOn()).assertCountEquals(1)
    }

    @Test
    fun `launch at login stays off when autostart is not supported`() = runComposeUiTest {
        assertFalse(
            AutoStartManager.isSupported,
            "a Gradle run has no jpackage launcher path, so autostart cannot be registered"
        )
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = false),
                )
            }
        }

        onAllNodes(isToggleable()).assertCountEquals(2)
        // Launch-at-login is declared first. The switch follows the OS registration, not the click:
        // it can only turn on if setEnabled() reported success, which cannot happen here — so this
        // cannot race the coroutine the click starts.
        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        waitForIdle()
        onAllNodes(isToggleable() and isOn()).assertCountEquals(0)
    }

    // ── Browse: one test per button ───────────────────────────────────────────

    /**
     * Clicks the [index]th "Browse…" with the chooser resolving to [picked], asserts the picker now
     * displays it, and hands back the settings the tab produced.
     */
    private fun ComposeUiTest.browsePicker(index: Int, picked: String): AppSettings {
        givenFolderChooserReturns(picked)
        var current by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = current,
                    onSettingsChange = { transform -> current = transform(current) },
                )
            }
        }

        onAllNodesWithText("No directory selected").assertCountEquals(6)
        onAllNodesWithText("Browse...")[index].performScrollTo().performClick()
        waitUntil { onAllNodesWithText(picked).fetchSemanticsNodes().isNotEmpty() }

        onAllNodesWithText(picked).assertCountEquals(1)
        onAllNodesWithText("No directory selected").assertCountEquals(5)
        return current
    }

    /** Every storage path in [settings], in the order the pickers are declared. */
    private fun storagePaths(settings: AppSettings) = listOf(
        settings.bibleSettings.storageDirectory,
        settings.songSettings.storageDirectory,
        settings.pictureSettings.storageDirectory,
        settings.streamingSettings.lowerThirdFolder,
        settings.presentationStorageDirectory,
        settings.mediaStorageDirectory,
    )

    /** Asserts the browsed path landed in picker [index]'s own setting and nowhere else. */
    private fun assertOnlyPickerChanged(index: Int, picked: String, settings: AppSettings) {
        storagePaths(settings).forEachIndexed { i, path ->
            if (i == index) assertEquals(picked, path, "picker $index must store the browsed path")
            else assertEquals("", path, "picker $i must be left alone")
        }
    }

    @Test
    fun `browsing the bible picker stores and displays the chosen folder`() = runComposeUiTest {
        val picked = tempDir().path
        assertOnlyPickerChanged(0, picked, browsePicker(0, picked))
    }

    @Test
    fun `browsing the songs picker stores and displays the chosen folder`() = runComposeUiTest {
        val picked = tempDir().path
        assertOnlyPickerChanged(1, picked, browsePicker(1, picked))
    }

    @Test
    fun `browsing the pictures picker stores and displays the chosen folder`() = runComposeUiTest {
        val picked = tempDir().path
        assertOnlyPickerChanged(2, picked, browsePicker(2, picked))
    }

    @Test
    fun `browsing the lower-third picker stores and displays the chosen folder`() = runComposeUiTest {
        val picked = tempDir().path
        assertOnlyPickerChanged(3, picked, browsePicker(3, picked))
    }

    @Test
    fun `browsing the presentation picker stores and displays the chosen folder`() = runComposeUiTest {
        val picked = tempDir().path
        assertOnlyPickerChanged(4, picked, browsePicker(4, picked))
    }

    @Test
    fun `browsing the media picker stores and displays the chosen folder`() = runComposeUiTest {
        val picked = tempDir().path
        assertOnlyPickerChanged(5, picked, browsePicker(5, picked))
    }

    @Test
    fun `cancelling the folder picker changes nothing`() = runComposeUiTest {
        givenFolderChooserReturns(null)
        var current by mutableStateOf(AppSettings())
        var changes = 0
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = current,
                    onSettingsChange = { transform -> changes++; current = transform(current) },
                )
            }
        }

        onAllNodesWithText("Browse...")[0].performScrollTo().performClick()
        waitForIdle()

        assertEquals(0, changes, "a cancelled picker must not report a settings change")
        onAllNodesWithText("No directory selected").assertCountEquals(6)
    }

    // ── Set All ───────────────────────────────────────────────────────────────

    @Test
    fun `Set All applies the picker's directory to every storage setting`() = runComposeUiTest {
        val dir = tempDir()
        var applied: AppSettings? = null
        val initial = AppSettings(bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = dir.path))
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = initial,
                    onSettingsChange = { transform -> applied = transform(initial) },
                )
            }
        }

        // Only the Bible picker has a path, so only its Set All is enabled; it is the first one.
        onAllNodesWithText("Set All")[0].performScrollTo().performClick()
        waitForIdle()

        val result = assertNotNull(applied, "Set All must report a settings change")
        assertEquals(List(6) { dir.path }, storagePaths(result), "every storage directory follows")
    }

    @Test
    fun `every picker's Set All applies its own directory, not another picker's`() = runComposeUiTest {
        // One distinct folder per picker, in the order the pickers are declared.
        val dirs = List(6) { tempDir() }
        var applied: AppSettings? = null
        val initial = AppSettings(
            bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = dirs[0].path),
            songSettings = AppSettings().songSettings.copy(storageDirectory = dirs[1].path),
            pictureSettings = AppSettings().pictureSettings.copy(storageDirectory = dirs[2].path),
            streamingSettings = AppSettings().streamingSettings.copy(lowerThirdFolder = dirs[3].path),
            presentationStorageDirectory = dirs[4].path,
            mediaStorageDirectory = dirs[5].path,
        )
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = initial,
                    onSettingsChange = { transform -> applied = transform(initial) },
                )
            }
        }

        dirs.forEachIndexed { index, dir ->
            applied = null
            onAllNodesWithText("Set All")[index].performScrollTo().performClick()
            waitForIdle()

            val result = assertNotNull(applied, "picker $index's Set All must report a settings change")
            assertEquals(List(6) { dir.path }, storagePaths(result), "picker $index spreads its own path")
        }
    }

    @Test
    fun `an unset picker shows the placeholder and cannot set all directories`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("No directory selected").assertCountEquals(6)
        repeat(6) { i -> onAllNodesWithText("Set All")[i].assertIsNotEnabled() }
    }

    // ── What the tab displays ─────────────────────────────────────────────────

    @Test
    fun `every storage section renders with a browse and a set-all button`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        listOf(
            "Storage",
            "Bible",
            "Songs",
            "Pictures",
            "Lower Third",
            "Presentation",
            "Media",
            "General",
            "Manage settings",
        ).forEach { title ->
            onAllNodesWithText(title).onFirst().assertExists("the $title heading must render")
        }
        onAllNodesWithText("Browse...").assertCountEquals(6)
        onAllNodesWithText("Set All").assertCountEquals(6)
    }

    @Test
    fun `each picker displays the directory it is configured with`() = runComposeUiTest {
        val dirs = List(6) { tempDir() }
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = dirs[0].path),
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dirs[1].path),
                        pictureSettings = AppSettings().pictureSettings.copy(storageDirectory = dirs[2].path),
                        streamingSettings = AppSettings().streamingSettings.copy(lowerThirdFolder = dirs[3].path),
                        presentationStorageDirectory = dirs[4].path,
                        mediaStorageDirectory = dirs[5].path,
                    ),
                )
            }
        }

        dirs.forEach { dir ->
            onAllNodesWithText(dir.path).onFirst().assertExists("${dir.path} must be shown in its picker")
        }
        onAllNodesWithText("No directory selected").assertCountEquals(0)
    }

    @Test
    fun `bible files found in the directory are listed as detected`() = runComposeUiTest {
        val dir = tempDir()
        File(dir, "kjv1769.spb").writeText("x")
        File(dir, "asv.spb").writeText("x")
        File(dir, "notes.txt").writeText("x")
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        waitUntil { onAllNodesWithText("asv.spb").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Detected:").onFirst().assertExists("the strip says what it is listing")
        onAllNodesWithText("kjv1769.spb").onFirst().assertExists("every bible file gets its own chip")
        onAllNodesWithText("notes.txt").assertCountEquals(0)
        // Sorted, which one chip per file no longer says on its own: asv is drawn left of kjv1769.
        val asv = onAllNodesWithText("asv.spb")[0].fetchSemanticsNode().boundsInRoot
        val kjv = onAllNodesWithText("kjv1769.spb")[0].fetchSemanticsNode().boundsInRoot
        assertTrue(asv.left < kjv.left, "the chips are in the order the scan sorted them")
    }

    @Test
    fun `an empty bible directory reports that no files were detected`() = runComposeUiTest {
        val dir = tempDir()
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        onAllNodesWithText("No files detected").onFirst()
            .assertExists("a directory with no bible files says so instead of listing nothing")
    }

    @Test
    fun `an sps song file is flagged unsupported and offers to convert it`() = runComposeUiTest {
        val dir = tempDir()
        File(dir, "old-songbook.sps").writeText("x")
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        waitUntil { onAllNodesWithText("Convert").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("old-songbook.sps").onFirst().assertExists("the legacy file is named")
        onAllNodesWithText("not supported").onFirst().assertExists("and flagged beside its name")
        onAllNodesWithText("Convert").assertCountEquals(1)
    }

    @Test
    fun `songbook folders are listed with their song counts`() = runComposeUiTest {
        val dir = tempDir()
        File(dir, "Hymns").mkdirs()
        File(dir, "Hymns/a.song").writeText("x")
        File(dir, "Hymns/b.song").writeText("x")
        File(dir, "Empty").mkdirs()
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        waitUntil { onAllNodesWithText("Hymns", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Hymns (2 songs)").onFirst()
            .assertExists("a songbook folder reports how many songs it holds")
        onAllNodesWithText("Empty", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an empty songs directory reports that no files were detected`() = runComposeUiTest {
        val dir = tempDir()
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        // The scan is async, so wait for the line rather than for the composition to settle — the
        // sibling test above waits the same way. Asserting straight away passed only when an
        // earlier test had already warmed the folder scan, which made this order-dependent.
        waitUntil { onAllNodesWithText("No files detected").fetchSemanticsNodes().isNotEmpty() }

        // The bible picker is unset, so its detected-files line is absent entirely: this is the
        // songs section speaking for itself.
        onAllNodesWithText("No files detected").assertCountEquals(1)
    }

    @Test
    fun `songs sitting in the storage folder itself are listed as the root songbook`() = runComposeUiTest {
        val dir = tempDir()
        File(dir, "a.song").writeText("x")
        File(dir, "b.song").writeText("x")
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        waitUntil { onAllNodesWithText("/ (2 songs)").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("/ (2 songs)").onFirst()
            .assertExists("loose songs count as a songbook at the root of the folder")
    }

    @Test
    fun `Send test event reports the outcome back to the operator`() = runComposeUiTest {
        stubSwingDialogs()
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = true),
                )
            }
        }

        onNode(hasText("Send test event") and hasClickAction()).performScrollTo().performClick()
        waitUntil { told.isNotEmpty() }

        // The test JVM runs with an empty `sentry.dsn`, so the SDK is never enabled and the send
        // cannot succeed — the button must say so rather than claim it worked.
        assertEquals(
            "Could not send test event. Crash reporting is disabled or no DSN is configured.",
            told.single()
        )
    }

    @Test
    fun `the test-event button and its dev-only note show while reporting is on`() = runComposeUiTest {
        assertFalse(BuildConfig.IS_RELEASE, "a Gradle run is not a release build, so the affordance is offered")
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = true),
                )
            }
        }

        onNode(hasText("Send test event") and hasClickAction())
            .assertExists("the test-event button must be offered, not just its label")
        onAllNodesWithText("Visible to developers only — hidden in released installer builds.").onFirst()
            .assertExists("the note explaining why the button is there must render with it")
    }

    @Test
    fun `the test-event button is hidden once reporting is off`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(analyticsReportingEnabled = false),
                )
            }
        }

        onAllNodesWithText("Send test event").assertCountEquals(0)
        onAllNodesWithText("Visible to developers only — hidden in released installer builds.")
            .assertCountEquals(0)
    }

    @Test
    fun `the settings-file and maintenance buttons all render`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        listOf("Export Settings", "Import Settings", "Reset All Settings", "Clear Remote Uploads")
            .forEach { label -> onAllNodesWithText(label).assertCountEquals(1) }
    }

    // ── Sample libraries ──────────────────────────────────────────────────────

    @Test
    fun `Add Song Samples writes the bundled songs and says how many`() = runComposeUiTest {
        val dir = tempDir()
        stubSwingDialogs()
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        onAllNodesWithText("Add Song Samples").onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        val written = File(dir, "Song Samples").listFiles { f -> f.extension == "song" }?.size ?: 0
        assertTrue(written > 0, "the bundled sample songs are written into a Song Samples folder")
        assertTrue(written.toString() in told.single(), "the report names how many were copied: ${told.single()}")
    }

    /**
     * The bundled-KJV button this replaced is gone: the download browser offers nine King James
     * editions among 264 translations, so copying one sample out of app resources had no purpose.
     *
     * Only the button's presence is asserted. Clicking it opens a real `DialogWindow` and would
     * reach the Zefania archive over the network — the browser's own behaviour is covered by
     * `ZefaniaRepositoryIndexTest`, `ZefaniaInstallerTest` and `BibleCatalogViewModelTest` instead.
     */
    @Test
    fun `the bible section offers the download browser`() = runComposeUiTest {
        val dir = tempDir()
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        onAllNodesWithText("Download Bibles…").onFirst().performScrollTo().assertExists(
            "the Bible folder section is where someone with no Bibles goes, so the downloader lives there"
        )
    }

    @Test
    fun `the download browser is not offered until a bible folder exists`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(),
                )
            }
        }

        // A download is written to disk the moment it finishes, so it must never be startable
        // while there is any doubt about which folder it would land in.
        onAllNodesWithText("Download Bibles…").assertCountEquals(0)
    }

    @Test
    fun `Add Song Samples asks before overwriting an existing folder and honours a no`() = runComposeUiTest {
        val dir = tempDir()
        val samples = File(dir, "Song Samples").apply { mkdirs() }
        val mine = File(samples, "0001 - My Song.song").apply { writeText("mine") }
        stubSwingDialogs(confirmAnswer = JOptionPane.NO_OPTION)
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        onAllNodesWithText("Add Song Samples").onFirst().performScrollTo().performClick()
        waitUntil { asked.isNotEmpty() }

        assertTrue("Song Samples" in asked.single(), "the question names the folder: ${asked.single()}")
        assertEquals(
            listOf(mine.name),
            samples.listFiles()?.map { it.name },
            "declining copies nothing into the existing folder"
        )
        assertTrue(told.isEmpty(), "and reports no copy that never happened")
    }

    // ── Convert: the songs library's legacy .sps import ───────────────────────

    /**
     * A legacy `.sps` songbook in [dir]: `##` header lines (the second names the songbook), then one
     * song per line with `#$#` between fields — number, title, category, key, author, composer,
     * lyrics. Same format as [org.churchpresenter.app.churchpresenter.data.SpsConverterTest] uses.
     */
    private fun writeSpsFile(dir: File, name: String, songbook: String, vararg titles: String): File =
        File(dir, name).also { file ->
            val rows = titles.mapIndexed { i, title ->
                listOf("${i + 1}", title, "1", "G", "Newton", "Excell", "Amazing grace").joinToString("#\$#")
            }
            file.writeText("##SongPresenter\n##$songbook\n" + rows.joinToString("\n"), Charsets.UTF_8)
        }

    private fun ComposeUiTest.clickConvert(dir: File) {
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }
        waitUntil { onAllNodesWithText("Convert").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Convert").onFirst().performScrollTo().performClick()
    }

    @Test
    fun `Convert imports the sps songbook and reports what it wrote`() = runComposeUiTest {
        val dir = tempDir()
        writeSpsFile(dir, "library.sps", "Hymnal", "Amazing Grace", "How Great Thou Art")
        stubSwingDialogs()

        clickConvert(dir)
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        val songFiles = File(
            dir,
            "Hymnal",
        ).listFiles { f -> f.extension == "song" }?.map { it.name }?.sorted().orEmpty()
        assertEquals(2, songFiles.size, "both songs are written as .song files: $songFiles")
        val message = told.single()
        assertTrue("2" in message && "Hymnal" in message, "the report names the song count and folder: $message")
    }

    @Test
    fun `Convert asks before overwriting an existing songbook folder and honours a no`() = runComposeUiTest {
        val dir = tempDir()
        writeSpsFile(dir, "library.sps", "Hymnal", "Amazing Grace")
        val existing = File(dir, "Hymnal").apply { mkdirs() }
        val keep = File(existing, "keep-me.song").apply { writeText("original") }
        stubSwingDialogs(confirmAnswer = JOptionPane.NO_OPTION)

        clickConvert(dir)
        // Declining is decided in the click itself — nothing is launched, so once the question has
        // been asked there is no later work that could still touch the folder.
        waitUntil { asked.isNotEmpty() }

        assertTrue("Hymnal" in asked.single(), "the question names the folder at risk: ${asked.single()}")
        assertEquals("original", keep.readText(), "declining leaves the existing songbook untouched")
    }

    @Test
    fun `Convert writes into an existing songbook folder once confirmed`() = runComposeUiTest {
        val dir = tempDir()
        writeSpsFile(dir, "library.sps", "Hymnal", "Amazing Grace")
        val existing = File(dir, "Hymnal").apply { mkdirs() }
        // The name the converter gives song 1: number padded to four digits, then the title.
        val sameSong = File(existing, "0001 - Amazing Grace.song").apply { writeText("stale") }
        val unrelated = File(existing, "notes.txt").apply { writeText("keep") }
        stubSwingDialogs(confirmAnswer = JOptionPane.YES_OPTION)

        clickConvert(dir)
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        assertTrue("Hymnal" in asked.single(), "the question named the folder at risk: ${asked.single()}")
        assertNotEquals("stale", sameSong.readText(), "the song with the same number is rewritten")
        assertEquals(
            "keep",
            unrelated.readText(),
            "confirming writes into the folder rather than clearing it — anything else in there survives"
        )
    }

    @Test
    fun `Convert reports the errors when the songbook holds no songs`() = runComposeUiTest {
        val dir = tempDir()
        // Header lines only: a file the old app would have written for an empty songbook.
        File(dir, "empty.sps").writeText("##SongPresenter\n##Hymnal\n", Charsets.UTF_8)
        stubSwingDialogs()

        clickConvert(dir)
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        assertTrue(
            "No songs found in file" in told.single(),
            "a conversion that produced nothing says why instead of claiming success: ${told.single()}"
        )
        assertEquals(
            emptyList(),
            dir.listFiles { f -> f.isDirectory }?.map { it.name },
            "and no songbook folder is left behind"
        )
    }

    // ── Export, import, reset, clear ──────────────────────────────────────────

    /** The settings file the tab's own buttons read and write, under the test home. */
    private fun settingsFile() =
        File(System.getProperty("user.home"), ".churchpresenter/settings.json")

    @Test
    fun `Export Settings writes the settings to the chosen file as json`() = runComposeUiTest {
        val target = File(tempDir(), "backup")   // no extension: the export must add one
        givenFolderChooserReturns(target.path)
        stubSwingDialogs()
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Export Settings").onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        val written = File(target.path + ".json")
        assertTrue(written.exists(), "the export lands on a .json file even when the chosen name had none")
        val json = written.readText()
        assertTrue("\n" in json, "the export is pretty-printed for humans")
        Json { ignoreUnknownKeys = true }.decodeFromString<AppSettings>(json)   // throws if malformed
        assertEquals("Settings exported successfully.", told.single())
    }

    @Test
    fun `Import Settings asks first and changes nothing when declined`() = runComposeUiTest {
        val chosen = File(tempDir(), "incoming.json")
        chosen.writeText("""{"analyticsReportingEnabled":false}""")
        val before = settingsFile().takeIf { it.exists() }?.readText()
        givenFolderChooserReturns(chosen.path)
        stubSwingDialogs(confirmAnswer = JOptionPane.NO_OPTION)
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Import Settings").onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { asked.isNotEmpty() }

        assertTrue("import settings" in asked.single(), "the question is asked before anything is read")
        assertEquals(before, settingsFile().takeIf { it.exists() }?.readText(), "declining writes nothing")
    }

    @Test
    fun `Reset All Settings asks before it resets anything`() = runComposeUiTest {
        val before = settingsFile().takeIf { it.exists() }?.readText()
        stubSwingDialogs(confirmAnswer = JOptionPane.NO_OPTION)
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Reset All Settings").onFirst().performScrollTo().performClick()
        // The confirm runs inside SwingUtilities.invokeLater; queueing behind it on the EDT is
        // deterministic, where waiting on the Compose clock would be a race.
        flushEventQueue()

        assertTrue("reset all settings" in asked.single(), "resetting is never silent: ${asked.single()}")
        assertEquals(
            before,
            settingsFile().takeIf { it.exists() }?.readText(),
            "declining leaves settings as they were",
        )
    }

    @Test
    fun `Clear Remote Uploads deletes the device uploads folder once confirmed`() = runComposeUiTest {
        val uploads = File(System.getProperty("user.home"), ".churchpresenter/device_uploads")
        uploads.mkdirs()
        File(uploads, "photo.jpg").writeText("data")
        stubSwingDialogs(confirmAnswer = JOptionPane.YES_OPTION)
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Clear Remote Uploads").onFirst().performScrollTo().performClick()
        flushEventQueue()

        assertFalse(uploads.exists(), "confirming removes the whole uploads folder")
        assertEquals("Remote uploads folder cleared successfully.", told.single())
    }

    @Test
    fun `Clear Remote Uploads keeps the uploads when declined`() = runComposeUiTest {
        val uploads = File(System.getProperty("user.home"), ".churchpresenter/device_uploads")
        uploads.mkdirs()
        val photo = File(uploads, "photo.jpg").apply { writeText("data") }
        stubSwingDialogs(confirmAnswer = JOptionPane.NO_OPTION)
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Clear Remote Uploads").onFirst().performScrollTo().performClick()
        flushEventQueue()

        assertTrue("delete all remotely uploaded files" in asked.single())
        assertEquals("data", photo.readText(), "declining keeps every uploaded file")
        assertTrue(told.isEmpty())
    }

    @Test
    fun `Export Settings reports a failure it cannot write`() = runComposeUiTest {
        // A folder that does not exist: the write throws, which is the only way into the error path
        // short of revoking permissions mid-test.
        val unreachable = File(File(tempDir(), "no-such-folder"), "backup.json")
        givenFolderChooserReturns(unreachable.path)
        stubSwingDialogs()
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Export Settings").onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        assertEquals("Failed to export settings.", told.single())
        assertFalse(unreachable.exists(), "and nothing is left half-written")
    }

    @Test
    fun `Import Settings reports a failure it cannot read`() = runComposeUiTest {
        // The chosen file is gone by the time it is read. Reading throws before anything is saved,
        // so the confirmed path stops at the error dialog instead of restarting the app.
        val missing = File(tempDir(), "gone.json")
        val before = settingsFile().takeIf { it.exists() }?.readText()
        givenFolderChooserReturns(missing.path)
        stubSwingDialogs(confirmAnswer = JOptionPane.YES_OPTION)
        setContent {
            MaterialTheme {
                SystemSettingsTab()
            }
        }

        onAllNodesWithText("Import Settings").onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }

        assertEquals("Failed to import settings. The file may be invalid.", told.single())
        assertEquals(before, settingsFile().takeIf { it.exists() }?.readText(), "a failed import changes nothing")
    }

    // ── The status dot behind each picker, and the hint it carries ────────────

    /** True if a file can actually be created in [dir] — the same question the dot's colour asks. */
    private fun canWriteInto(dir: File): Boolean = try {
        File.createTempFile(".probe", ".tmp", dir).delete()
        true
    } catch (_: Exception) {
        false
    }

    /** True if [dir] can be opened for listing — the dot's read-only-versus-broken distinction. */
    private fun canList(dir: File): Boolean = try {
        Files.newDirectoryStream(dir.toPath()).use { }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Hovers the status dot beside [path] and returns once its tooltip has had time to appear.
     *
     * The dot carries no semantics of its own, so it cannot be found by a matcher. It sits at the
     * left end of the path field, before the path itself in a row with `Arrangement.spacedBy(8.dp)`,
     * and is 7dp wide — so its centre is 11.5dp to the left of the path's own measured bounds rather
     * than at a fixed coordinate.
     */
    private fun ComposeUiTest.hoverStatusDotBeside(path: String) {
        waitForIdle()
        val pathText = onAllNodesWithText(path)[0].fetchSemanticsNode()
        val bounds = pathText.boundsInRoot
        val dotCentre = Offset(bounds.left - 11.5f * pathText.layoutInfo.density.density, bounds.center.y)

        onRoot().performMouseInput { moveTo(dotCentre) }
        mainClock.advanceTimeBy(1_000)   // TooltipArea holds the tooltip back for 500ms
        waitForIdle()
    }

    private fun ComposeUiTest.showBibleFolder(path: String) {
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = path)
                    ),
                )
            }
        }
    }

    @Test
    fun `the dot on a usable folder explains that it is writable`() = runComposeUiTest {
        val dir = tempDir()
        showBibleFolder(dir.path)

        hoverStatusDotBeside(dir.path)

        onAllNodesWithText("Directory is writable").onFirst()
            .assertExists("hovering a green dot must explain what it means")
    }

    @Test
    fun `the dot on a missing folder explains that it was not found`() = runComposeUiTest {
        val missing = File(tempDir(), "moved-away")
        showBibleFolder(missing.path)

        onAllNodesWithText(missing.path).onFirst()
            .assertExists("the configured path is still shown even though it is gone")
        hoverStatusDotBeside(missing.path)

        onAllNodesWithText("Directory not found. Create it or choose a different location.").onFirst()
            .assertExists("a red dot on a missing folder tells the operator how to fix it")
    }

    @Test
    fun `the dot on a read-only folder explains it cannot be written to`() = runComposeUiTest {
        val dir = tempDir()
        // Windows ignores the write flag on directories, and root bypasses it; skip where the
        // permission does not actually take rather than assert a state the OS refuses to produce.
        if (!dir.setWritable(false) || canWriteInto(dir)) return@runComposeUiTest
        showBibleFolder(dir.path)

        hoverStatusDotBeside(dir.path)

        onAllNodesWithText(
            "Cannot write to this directory. Choose a different location or change permissions."
        ).onFirst().assertExists("an amber dot names the permission problem")
    }

    @Test
    fun `the dot on a folder that cannot even be listed explains the same`() = runComposeUiTest {
        val dir = tempDir()
        if (!dir.setReadable(false) || !dir.setWritable(false) || canList(dir)) return@runComposeUiTest
        showBibleFolder(dir.path)

        // Unreadable is a distinct state internally (INVALID rather than READ_ONLY) but reads as the
        // same advice to the operator: this folder cannot be written to, choose another.
        hoverStatusDotBeside(dir.path)

        onAllNodesWithText(
            "Cannot write to this directory. Choose a different location or change permissions."
        ).onFirst().assertExists()
        onAllNodesWithText("No files detected").onFirst()
            .assertExists("and the section reports nothing found rather than failing")
    }

    // ── The storage list's own reporting ──────────────────────────────────────

    /** Renders the tab with all six folders pointed at real directories, one of them gone. */
    private fun ComposeUiTest.showAllFolders(bible: String, missing: String) {
        val other = tempDir().path
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        bibleSettings = AppSettings().bibleSettings.copy(storageDirectory = bible),
                        songSettings = AppSettings().songSettings.copy(storageDirectory = other),
                        pictureSettings = AppSettings().pictureSettings.copy(storageDirectory = other),
                        streamingSettings = AppSettings().streamingSettings.copy(lowerThirdFolder = other),
                        presentationStorageDirectory = missing,
                        mediaStorageDirectory = other,
                    ),
                )
            }
        }
    }

    @Test
    fun `the storage header counts what is linked and what needs attention`() = runComposeUiTest {
        val bible = tempDir()
        val missing = File(tempDir(), "moved-away")
        showAllFolders(bible.path, missing.path)

        waitUntil { onAllNodesWithText("5 linked").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("1 need attention").onFirst()
            .assertExists("the folder that is gone is counted as needing attention")
    }

    @Test
    fun `nothing needs attention when every folder is usable`() = runComposeUiTest {
        val dir = tempDir()
        showAllFolders(dir.path, tempDir().path)

        waitUntil { onAllNodesWithText("6 linked").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("need attention", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an unset folder says so under its name`() = runComposeUiTest {
        setContent { MaterialTheme { SystemSettingsTab() } }

        waitUntil { onAllNodesWithText("Not set").fetchSemanticsNodes().size == 6 }
        onAllNodesWithText("0 linked").onFirst().assertExists("nothing is linked before anything is chosen")
    }

    @Test
    fun `a folder holding bibles reports how many it found`() = runComposeUiTest {
        val dir = tempDir()
        File(dir, "kjv.spb").writeText("x")
        File(dir, "asv.spb").writeText("x")
        showBibleFolder(dir.path)

        waitUntil { onAllNodesWithText("2 files").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("2 files").onFirst().assertExists("the bible row counts what the scan found")
    }

    @Test
    fun `a folder with nothing to report just says it is linked`() = runComposeUiTest {
        val dir = tempDir()
        showAllFolders(dir.path, dir.path)

        waitUntil { onAllNodesWithText("Linked").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the songs row reports how many files still need converting`() = runComposeUiTest {
        val dir = tempDir()
        File(dir, "old-songbook.sps").writeText("x")
        setContent {
            MaterialTheme {
                SystemSettingsTab(
                    settings = AppSettings(
                        songSettings = AppSettings().songSettings.copy(storageDirectory = dir.path)
                    ),
                )
            }
        }

        waitUntil { onAllNodesWithText("1 need converting").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("1 need attention").onFirst()
            .assertExists("a folder full of files the app cannot read is counted in the header too")
    }

    // ── Where the cards sit ───────────────────────────────────────────────────

    /** The top-left corner of the heading naming a card. */
    private fun ComposeUiTest.cardCorner(title: String): Offset =
        onAllNodesWithText(title)[0].fetchSemanticsNode().boundsInRoot.topLeft

    @Test
    fun `the cards sit side by side in a wide dialog`() =
        runDesktopComposeUiTest(width = 1400, height = 900) {
            setContent { MaterialTheme { SystemSettingsTab() } }
            waitForIdle()

            val storage = cardCorner("Storage")
            val general = cardCorner("General")

            assertTrue(general.x > storage.x, "General must be drawn to the right of Storage")
            assertTrue(
                abs(general.y - storage.y) < 4f,
                "the two headings must line up, which is what makes them read as columns",
            )
        }

    @Test
    fun `the cards stack once the dialog is too narrow for two columns`() =
        runDesktopComposeUiTest(width = 1000, height = 800) {
            setContent { MaterialTheme { SystemSettingsTab() } }
            waitForIdle()

            val storage = cardCorner("Storage")
            val general = cardCorner("General")

            assertTrue(general.y > storage.y, "General must fall below Storage rather than beside it")
            assertTrue(
                abs(general.x - storage.x) < 4f,
                "and share its left edge, since both are full width",
            )
        }
}
