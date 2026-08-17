@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The setup wizard's VLC step: what its status card says for each detection outcome, and what its
 * Download / Recheck buttons do.
 *
 * `isVlcAvailable`/`isVlcArchMismatch`/`isVlcLoadFailed`/`recheckVlcAvailability` are real probes of
 * the machine VLC is (or isn't) installed on, so [VlcStep] takes its starting [VlcCheckResult], OS
 * name/arch, and both the recheck and the browse-to-download actions as parameters — this drives it
 * with fixed values instead of whatever happens to be true of the CI/dev machine.
 */
class VlcStepTest {

    private object Label {
        const val OK = "VLC is installed and ready"
        const val MISSING = "VLC was not found on this system"
        const val WRONG_ARCH = "Wrong VLC architecture installed"
        const val LOAD_FAILED = "VLC was found but failed to load"
        const val WRONG_ARCH_DETAIL =
            "Your CPU architecture differs from the installed VLC. Download the correct build below."
        const val LOAD_FAILED_DETAIL = "A VLC installation was detected, but it could not be loaded correctly."
        const val DOWNLOAD = "Download VLC"
        const val DOWNLOAD_SILICON = "Download VLC (Apple Silicon)"
        const val DOWNLOAD_INTEL = "Download VLC (Intel)"
        const val RECHECK = "Recheck"
        const val LINUX_TIP = "On Linux, install VLC via your package manager"
    }

    private fun ok() = VlcCheckResult(available = true, archMismatch = false, loadFailed = false)
    private fun missing() = VlcCheckResult(available = false, archMismatch = false, loadFailed = false)
    private fun wrongArch() = VlcCheckResult(available = false, archMismatch = true, loadFailed = false)
    private fun loadFailed() = VlcCheckResult(available = false, archMismatch = false, loadFailed = true)

    private fun vlcStep(
        initial: VlcCheckResult = missing(),
        osName: String = "mac os x",
        arch: String = "x86_64",
        onRecheck: suspend () -> VlcCheckResult = { initial },
        block: ComposeUiTest.(openedUrl: () -> String?) -> Unit,
    ) {
        var openedUrl: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    VlcStep(
                        initial = initial,
                        osName = osName,
                        arch = arch,
                        onRecheck = onRecheck,
                        onOpenDownloadPage = { openedUrl = it },
                    )
                }
            }
            waitForIdle()
            block({ openedUrl })
        }
    }

    // ── Status card ──────────────────────────────────────────────────────────────

    @Test
    fun `VLC available shows the ok banner and no action buttons`() = vlcStep(initial = ok()) { _ ->
        onNodeWithText(Label.OK).assertIsDisplayed()
        onNodeWithText(Label.DOWNLOAD, substring = true).assertDoesNotExist()
        onNodeWithText(Label.RECHECK).assertDoesNotExist()
    }

    @Test
    fun `VLC missing shows the missing banner with no detail line`() = vlcStep(initial = missing()) { _ ->
        onNodeWithText(Label.MISSING).assertIsDisplayed()
        onNodeWithText(Label.WRONG_ARCH_DETAIL, substring = true).assertDoesNotExist()
        onNodeWithText(Label.LOAD_FAILED_DETAIL, substring = true).assertDoesNotExist()
    }

    @Test
    fun `a wrong architecture shows its own banner and detail`() = vlcStep(initial = wrongArch()) { _ ->
        onNodeWithText(Label.WRONG_ARCH).assertIsDisplayed()
        onNodeWithText(Label.WRONG_ARCH_DETAIL, substring = true).assertIsDisplayed()
        onNodeWithText(Label.LOAD_FAILED_DETAIL, substring = true).assertDoesNotExist()
    }

    @Test
    fun `a load failure shows its own banner and detail`() = vlcStep(initial = loadFailed()) { _ ->
        onNodeWithText(Label.LOAD_FAILED).assertIsDisplayed()
        onNodeWithText(Label.LOAD_FAILED_DETAIL, substring = true).assertIsDisplayed()
        onNodeWithText(Label.WRONG_ARCH_DETAIL, substring = true).assertDoesNotExist()
    }

    // ── Download label depends on OS/architecture ───────────────────────────────

    @Test
    fun `Apple Silicon offers the silicon build`() =
        vlcStep(initial = missing(), osName = "mac os x", arch = "aarch64") { _ ->
            onNodeWithText(Label.DOWNLOAD_SILICON).assertIsDisplayed()
        }

    @Test
    fun `Intel Mac offers the intel build`() =
        vlcStep(initial = missing(), osName = "mac os x", arch = "x86_64") { _ ->
            onNodeWithText(Label.DOWNLOAD_INTEL).assertIsDisplayed()
        }

    @Test
    fun `Windows and Linux offer the plain download label`() {
        vlcStep(initial = missing(), osName = "windows 11", arch = "amd64") { _ ->
            onNodeWithText(Label.DOWNLOAD).assertIsDisplayed()
        }
        vlcStep(initial = missing(), osName = "linux", arch = "amd64") { _ ->
            onNodeWithText(Label.DOWNLOAD).assertIsDisplayed()
        }
    }

    @Test
    fun `only Linux shows the package-manager tip`() {
        vlcStep(initial = missing(), osName = "linux", arch = "amd64") { _ ->
            onNodeWithText(Label.LINUX_TIP, substring = true).assertIsDisplayed()
        }
        vlcStep(initial = missing(), osName = "mac os x", arch = "x86_64") { _ ->
            onNodeWithText(Label.LINUX_TIP, substring = true).assertDoesNotExist()
        }
        vlcStep(initial = missing(), osName = "windows 11", arch = "amd64") { _ ->
            onNodeWithText(Label.LINUX_TIP, substring = true).assertDoesNotExist()
        }
    }

    // ── Buttons ──────────────────────────────────────────────────────────────────

    @Test
    fun `Download opens the download page for the current platform`() =
        vlcStep(initial = missing(), osName = "windows 11", arch = "amd64") { openedUrl ->
            onNodeWithText(Label.DOWNLOAD).performClick()
            waitForIdle()
            assertEquals("https://www.videolan.org/vlc/download-windows.html", openedUrl())
        }

    @Test
    fun `Recheck applies the fresh result including it clearing to ok`() =
        vlcStep(initial = missing(), onRecheck = { ok() }) { _ ->
            onNodeWithText(Label.RECHECK).performClick()
            waitForIdle()

            onNodeWithText(Label.OK).assertIsDisplayed()
            onNodeWithText(Label.MISSING).assertDoesNotExist()
        }

    @Test
    fun `Recheck can report a different failure than the one it started with`() =
        vlcStep(initial = missing(), onRecheck = { wrongArch() }) { _ ->
            onNodeWithText(Label.RECHECK).performClick()
            waitForIdle()

            onNodeWithText(Label.WRONG_ARCH).assertIsDisplayed()
        }

    @Test
    fun `after a successful recheck VLC is available and Recheck disappears`() = runComposeUiTest {
        val gate = CompletableDeferred<VlcCheckResult>()
        var openedUrl: String? = null
        setContent {
            MaterialTheme {
                VlcStep(
                    initial = missing(),
                    osName = "mac os x",
                    arch = "x86_64",
                    onRecheck = { gate.await() },
                    onOpenDownloadPage = { openedUrl = it },
                )
            }
        }
        waitForIdle()
        onNodeWithText(Label.RECHECK).performClick()
        waitForIdle()
        onNodeWithText(Label.RECHECK).assertIsNotEnabled()

        gate.complete(ok())
        assertTextEventually(Label.OK)

        onNodeWithText(Label.RECHECK).assertDoesNotExist()
        assertNull(openedUrl, "recheck must never open a browser")
    }

    /**
     * Polls for [text] to appear, settling the Swing/Compose queues between checks — needed here
     * because [VlcStep]'s recheck hops to `Dispatchers.IO`, a real thread pool outside Compose's own
     * test clock, so a single `waitForIdle()` right after completing the gate can race it.
     */
    private fun ComposeUiTest.assertTextEventually(text: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            repeat(3) { SwingUtilities.invokeAndWait { } }
            waitForIdle()
            try {
                onNodeWithText(text).assertIsDisplayed()
                return
            } catch (e: Throwable) {
                lastError = e
            }
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for text: $text", lastError)
    }
}
