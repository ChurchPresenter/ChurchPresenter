@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.theme.ThemeMode
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Path as NioPath

class AboutContentTest {

    @AfterTest
    fun tidy() {
        unmockkAll()
    }

    private fun dialog(
        // Both defaulted to a no-op, never to the real implementations: a click that reached
        // UrlOpener would launch the machine's browser, and one that reached SystemClipboard would
        // take the developer's own clipboard. Neither is stopped by the headless JVM.
        openUrl: (String) -> Unit = {},
        copyText: (String) -> Unit = {},
        block: ComposeUiTest.(dismissed: () -> Int) -> Unit,
    ) {
        var dismissed = 0
        runComposeUiTest {
            setContent {
                AboutDialogContent(
                    onDismiss = { dismissed++ },
                    appSettings = AppSettings(),
                    theme = ThemeMode.LIGHT,
                    openUrl = openUrl,
                    copyText = copyText,
                )
            }
            block { dismissed }
        }
    }

    @Test
    fun `the app name and copyright are shown`() = dialog {
        onNodeWithText("Church Presenter").assertExists()
        onNodeWithText("© 2026 Church Presenter").assertExists()
    }

    @Test
    fun `clicking OK dismisses the dialog`() = dialog { dismissed ->
        onNodeWithText("OK").performClick()
        assertEquals(1, dismissed())
    }

    @Test
    fun `the external-action buttons are present and enabled`() = dialog {
        onNodeWithText("Report a Bug").assertIsEnabled()
        onNodeWithText("Feature Request").assertIsEnabled()
        onNodeWithText("Open Crash Logs").assertIsEnabled()
        onNodeWithText("Save Diagnostic Info…").assertIsEnabled()
    }

    // ── Standing in for Desktop.getDesktop() ────────────────────────────────────

    private var openedFile: File? = null

    /**
     * Makes `Desktop.getDesktop()` resolve to a fake that records the folder it was asked to open.
     *
     * Only `open` is stubbed: the two link buttons take their action as a parameter now, so nothing
     * in this dialog reaches `browse` any more.
     */
    private fun stubDesktop() {
        openedFile = null
        val fakeDesktop = mockk<Desktop>()
        every { fakeDesktop.open(any()) } answers { openedFile = firstArg(); Unit }
        mockkStatic(Desktop::class)
        every { Desktop.getDesktop() } returns fakeDesktop
    }

    @Test
    fun `clicking Report a Bug opens the GitHub bug report template`() {
        var opened: String? = null
        dialog(openUrl = { opened = it }) {
            onNodeWithText("Report a Bug").performClick()
        }
        assertEquals(BUG_REPORT_URL, opened)
    }

    @Test
    fun `clicking Feature Request opens the GitHub feature request template`() {
        var opened: String? = null
        dialog(openUrl = { opened = it }) {
            onNodeWithText("Feature Request").performClick()
        }
        assertEquals(FEATURE_REQUEST_URL, opened)
    }

    /**
     * Each issue link carries a copy button because the browser opens on whichever display the
     * operating system picks — on a two-screen setup, regularly the projection output. There are
     * two of them, in the order the buttons appear.
     */
    @Test
    fun `each issue template can be copied instead of opened`() {
        val copied = mutableListOf<String>()
        var opened: String? = null
        dialog(openUrl = { opened = it }, copyText = { copied += it }) {
            onAllNodesWithContentDescription("Copy link")[0].performClick()
            onAllNodesWithContentDescription("Copy link")[1].performClick()
        }
        assertEquals(listOf(BUG_REPORT_URL, FEATURE_REQUEST_URL), copied)
        assertNull(opened, "copying must not also launch a browser")
    }

    @Test
    fun `clicking Open Crash Logs creates the folder and opens it`() {
        val fakeHome = Files.createTempDirectory("cp-about-home").toFile()
        val realHome = System.getProperty("user.home")
        System.setProperty("user.home", fakeHome.absolutePath)
        try {
            stubDesktop()
            dialog {
                onNodeWithText("Open Crash Logs").performClick()
            }
            val expectedDir = File(fakeHome, ".churchpresenter/crash-reports")
            assertTrue(expectedDir.exists(), "the crash-reports folder is created if it isn't there yet")
            assertEquals(expectedDir, openedFile)
        } finally {
            System.setProperty("user.home", realHome)
            fakeHome.deleteRecursively()
        }
    }

    // ── Standing in for the native save dialog ──────────────────────────────────

    /** A save dialog that "returns" [picked] without opening anything. */
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

    private fun givenSaveChooserReturns(picked: String?) {
        mockkObject(FileChooser.Companion)
        every { FileChooser.platformInstance } returns FakeChooser(picked)
    }

    /** What the dialog told the operator, in order. */
    private val told = mutableListOf<String>()

    private fun stubSwingDialogs() {
        mockkStatic(JOptionPane::class)
        every { JOptionPane.showMessageDialog(any(), any(), any(), any()) } answers {
            told += secondArg<Any?>().toString()
            Unit
        }
    }

    @Test
    fun `saving diagnostic info writes the report and appends the missing extension`() {
        val dir = Files.createTempDirectory("cp-about-save").toFile()
        try {
            val target = File(dir, "diagnostic") // no extension
            givenSaveChooserReturns(target.path)
            stubSwingDialogs()

            dialog {
                onNodeWithText("Save Diagnostic Info…").performClick()
                waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }
            }

            val written = File(dir, "diagnostic.txt")
            assertTrue(written.exists(), "the extension is appended when the chosen name had none")
            assertTrue(
                "=== ChurchPresenter Diagnostic Report ===" in written.readText(),
                "the report itself was written to the file",
            )
            assertEquals("Diagnostic info saved successfully.", told.single())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `AboutDialog renders nothing when not visible`() = runComposeUiTest {
        setContent {
            AboutDialog(isVisible = false, onDismiss = {}, appSettings = AppSettings())
        }
        onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun `saving diagnostic info reports the failure when the destination folder is gone`() {
        val dir = Files.createTempDirectory("cp-about-save-fail").toFile()
        try {
            val missing = File(dir, "gone/diagnostic.txt") // parent folder never created
            givenSaveChooserReturns(missing.path)
            stubSwingDialogs()

            dialog {
                onNodeWithText("Save Diagnostic Info…").performClick()
                waitUntil(timeoutMillis = 5_000) { told.isNotEmpty() }
            }

            assertEquals("Failed to save diagnostic info.", told.single())
        } finally {
            dir.deleteRecursively()
        }
    }
}
