@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.updater

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.utils.UpdateCheckInterval
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateAvailableContentTest {

    private object Label {
        const val DOWNLOAD = "Download & Install"
        const val DOWNLOADING = "Downloading..."
        const val INSTALL = "Install Now"
        const val OPEN_PAGE = "Open Download Page"
        const val LATER = "Later"
        const val UP_TO_DATE = "You are running the latest version."
        const val RELEASE_NOTES = "WHAT'S NEW"
        const val PRERELEASE_TOGGLE = "Include beta updates"
        const val CANCEL = "Cancel"
        const val SKIP = "Skip this version"
        const val READY = "Ready to install"
        const val FULL_NOTES = "Full release notes"
        const val CHECK_INTERVAL = "Check for updates"
    }

    /** Two groups of one bullet each, written the way the releases are. */
    private val groupedNotes = "**Songs**\n- Song compare (#667)\n\n**Fixes**\n- Focus comes back (#686)"

    private class Actions {
        var downloads = 0
        var cancels = 0
        var skipped: String? = null
        var installed: File? = null
        var openedPage: String? = null
        var copiedLink: String? = null
        var dismissed = 0
        var prereleases: Boolean? = null
        var interval: UpdateCheckInterval? = null
    }

    private fun available(
        version: String = "2.5.0",
        notes: String = "Fixed the drip feed",
        downloadUrl: String? = "https://example.invalid/ChurchPresenter-2.5.0.dmg",
        prerelease: Boolean = false,
        current: String = "",
        publishedAt: Instant? = null,
        size: Long? = null,
    ) = UpdateCheckResult.Available(
        UpdateInfo(
            latestVersion = version,
            releaseUrl = "https://example.invalid/releases/$version",
            releaseNotes = notes,
            downloadUrl = downloadUrl,
            isPrerelease = prerelease,
            currentVersion = current,
            publishedAt = publishedAt,
            downloadSize = size,
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    private fun updateDialog(
        result: UpdateCheckResult = available(),
        downloadState: DownloadState = DownloadState.Idle,
        isManualCheck: Boolean = false,
        participateInPrereleases: Boolean = false,
        block: ComposeUiTest.(Actions) -> Unit,
    ) {
        val actions = Actions()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    UpdateAvailableContent(
                        result = result,
                        isManualCheck = isManualCheck,
                        participateInPrereleases = participateInPrereleases,
                        onParticipateInPrereleasesChange = { actions.prereleases = it },
                        updateCheckInterval = UpdateCheckInterval.WEEKLY,
                        onUpdateCheckIntervalChange = { actions.interval = it },
                        downloadState = downloadState,
                        onDownload = { actions.downloads++ },
                        onCancelDownload = { actions.cancels++ },
                        onSkipVersion = { actions.skipped = it },
                        onInstall = { actions.installed = it },
                        onOpenReleasePage = { actions.openedPage = it },
                        onDismiss = { actions.dismissed++ },
                        copyText = { actions.copiedLink = it },
                    )
                }
            }
            block(actions)
        }
    }

    private fun ComposeUiTest.shows(text: String, substring: Boolean = false): Boolean =
        onAllNodes(hasText(text, substring = substring))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    // ── An update is available ──────────────────────────────────────────────────

    @Test
    fun `the new version and its release notes are shown`() =
        updateDialog(available(version = "2.5.0", notes = "Fixed the drip feed")) { _ ->
            assertTrue(shows("A new version is ready"))
            assertTrue(shows("2.5.0"))
            onNodeWithText(Label.RELEASE_NOTES).assertIsDisplayed()
            assertTrue(shows("Fixed the drip feed"), "the notes tell the operator whether to bother")
        }

    @Test
    fun `the jump from the running version to the new one is shown`() =
        updateDialog(available(version = "26.12.171", current = "26.11.164")) { _ ->
            assertTrue(shows("26.11.164"), "the version being replaced")
            assertTrue(shows("26.12.171"))
        }

    @Test
    fun `the release's date and installer size sit under the versions`() =
        updateDialog(available(publishedAt = Instant.parse("2026-10-08T09:01:10Z"), size = 601_405_516L)) { _ ->
            val facts = onAllNodes(hasText("601 MB", substring = true)).fetchSemanticsNodes()
            assertEquals(1, facts.size, "one line carries both facts")
            assertTrue(shows("Released", substring = true))
        }

    @Test
    fun `a release that gave no date or size shows no facts line`() = updateDialog { _ ->
        assertTrue(!shows("Released", substring = true))
        assertTrue(!shows(" MB", substring = true))
    }

    @Test
    fun `the notes are drawn as their groups and bullets, each pull request as its own chip`() =
        updateDialog(available(notes = groupedNotes)) { _ ->
            assertTrue(shows("Songs"))
            assertTrue(shows("Fixes"))
            assertTrue(shows("Song compare", substring = true))
            assertTrue(shows("#667", substring = true), "the pull request is still named, as a chip")
            assertTrue(!shows("**", substring = true), "no raw markdown reaches the window")
            assertTrue(!shows("(#667)", substring = true))
        }

    @Test
    fun `Full release notes opens the release page and leaves the dialog up`() = updateDialog { actions ->
        onNodeWithText(Label.FULL_NOTES).performClick()
        waitForIdle()
        assertEquals("https://example.invalid/releases/2.5.0", actions.openedPage)
        assertEquals(0, actions.dismissed)
    }

    @Test
    fun `a pre-release says so rather than looking like a stable one`() =
        updateDialog(available(prerelease = true)) { _ ->
            assertTrue(shows("Pre-release"), "an operator must not install a beta thinking it is stable")
        }

    @Test
    fun `a stable release is labelled stable`() = updateDialog(available(prerelease = false)) { _ ->
        assertTrue(shows("Stable release"))
    }

    @Test
    fun `a release with no notes shows no release-notes section`() =
        updateDialog(available(notes = "")) { _ ->
            assertTrue(!shows(Label.RELEASE_NOTES), "there is nothing to read")
        }

    // ── The button, at each stage of the download ───────────────────────────────

    @Test
    fun `before anything is downloaded the button offers to download`() = updateDialog { actions ->
        onNodeWithText(Label.DOWNLOAD).performClick()
        waitForIdle()
        assertEquals(1, actions.downloads)
        assertNull(actions.installed, "nothing has been fetched, so nothing can be installed")
    }

    @Test
    fun `while downloading Cancel takes the download button's place`() =
        updateDialog(downloadState = DownloadState.Downloading(0.4f)) { actions ->
            assertTrue(!shows(Label.DOWNLOAD), "a second download must not be startable mid-flight")
            onNodeWithText(Label.CANCEL).performClick()
            waitForIdle()
            assertEquals(1, actions.cancels)
            assertEquals(0, actions.downloads)
        }

    @Test
    fun `a download of known size counts its megabytes`() =
        updateDialog(available(size = 601_405_516L), downloadState = DownloadState.Downloading(0.42f)) { _ ->
            assertTrue(shows("Downloading 252 of 601 MB"))
        }

    @Test
    fun `a finished download is ready to install, with Later still on offer`() {
        val fetched = File("ChurchPresenter-update-ready.dmg")
        updateDialog(downloadState = DownloadState.Done(fetched)) { actions ->
            assertTrue(shows(Label.READY))
            onNodeWithText(Label.LATER).performClick()
            waitForIdle()
            assertEquals(1, actions.dismissed)
            assertNull(actions.installed, "Later installs nothing")
        }
    }

    @Test
    fun `skipping the version reports which one and closes the dialog`() =
        updateDialog(available(version = "26.12.171")) { actions ->
            onNodeWithText(Label.SKIP).performClick()
            waitForIdle()
            assertEquals("26.12.171", actions.skipped)
            assertEquals(1, actions.dismissed)
            assertEquals(0, actions.downloads)
        }

    @Test
    fun `skip is only offered before anything is downloaded`() =
        updateDialog(downloadState = DownloadState.Downloading(0.3f)) { _ ->
            assertTrue(!shows(Label.SKIP), "a download under way is not the moment to skip it")
        }

    @Test
    fun `a download in progress reports its percentage`() =
        updateDialog(downloadState = DownloadState.Downloading(0.42f)) { _ ->
            assertTrue(shows("42%"), "the operator needs to know it is moving")
        }

    @Test
    fun `a download of unknown size still shows it is running`() =
        updateDialog(downloadState = DownloadState.Downloading(-1f)) { _ ->
            // -1f means the server sent no content length; there is no percentage to show.
            assertTrue(shows(Label.DOWNLOADING))
            assertTrue(!shows("-100%"), "an unknown size must not be rendered as a negative percentage")
        }

    @Test
    fun `a finished download offers to install the file that was fetched`() {
        val fetched = File("/tmp/ChurchPresenter-update-test.dmg")
        updateDialog(downloadState = DownloadState.Done(fetched)) { actions ->
            onNodeWithText(Label.INSTALL).performClick()
            waitForIdle()
            assertEquals(
                fetched,
                actions.installed,
                "the installer launched must be the file the download produced",
            )
        }
    }

    // ── When the download cannot be the answer ──────────────────────────────────

    @Test
    fun `a failed download says why and falls back to the release page`() =
        updateDialog(downloadState = DownloadState.Error("Connection reset")) { actions ->
            assertTrue(shows("Connection reset"), "the operator is told what went wrong")

            onNodeWithText(Label.OPEN_PAGE).performClick()
            waitForIdle()
            assertEquals(
                "https://example.invalid/releases/2.5.0",
                actions.openedPage,
                "a failed download must still leave a way to get the update",
            )
            assertEquals(1, actions.dismissed, "opening the page closes the dialog behind it")
        }

    @Test
    fun `a release with no installer for this platform offers the page instead of a download`() =
        updateDialog(available(downloadUrl = null)) { actions ->
            assertTrue(!shows(Label.DOWNLOAD), "there is nothing to download, so it must not be offered")
            onNodeWithText(Label.OPEN_PAGE).performClick()
            waitForIdle()
            assertEquals("https://example.invalid/releases/2.5.0", actions.openedPage)
        }

    // ── Already up to date ──────────────────────────────────────────────────────

    @Test
    fun `being up to date says so and offers nothing to download`() =
        updateDialog(UpdateCheckResult.UpToDate) { _ ->
            onNodeWithText(Label.UP_TO_DATE).assertIsDisplayed()
            assertTrue(!shows(Label.DOWNLOAD), "there is no update to fetch")
            assertTrue(!shows(Label.RELEASE_NOTES), "and no notes to read")
        }

    @Test
    fun `up to date View on GitHub browses to the releases page and dismisses`() {
        updateDialog(UpdateCheckResult.UpToDate) { actions ->
            onNodeWithText("View on GitHub").performClick()
            waitForIdle()

            assertEquals(UpdateChecker.RELEASES_URL, actions.openedPage)
            assertEquals(1, actions.dismissed)
        }
    }

    /**
     * The browser opens on whichever display the operating system picks — on a two-screen setup,
     * regularly the projection output. The copy button is how the address is reached instead, and
     * unlike the button beside it, it leaves the dialog open.
     */
    @Test
    fun `up to date Copy link copies the releases page without browsing or dismissing`() {
        updateDialog(UpdateCheckResult.UpToDate) { actions ->
            onNodeWithContentDescription("Copy link").performClick()
            waitForIdle()

            assertEquals(UpdateChecker.RELEASES_URL, actions.copiedLink)
            assertNull(actions.openedPage, "copying must not also launch a browser")
            assertEquals(0, actions.dismissed, "the dialog stays up so the address can be used")
        }
    }

    @Test
    fun `a failed download offers the release address to copy as well as to open`() =
        updateDialog(downloadState = DownloadState.Error("Connection reset")) { actions ->
            onNodeWithContentDescription("Copy link").performClick()
            waitForIdle()

            assertEquals("https://example.invalid/releases/2.5.0", actions.copiedLink)
            assertNull(actions.openedPage)
        }

    @Test
    fun `up to date OK just dismisses without browsing anywhere`() {
        updateDialog(UpdateCheckResult.UpToDate) { actions ->
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(1, actions.dismissed)
            assertNull(actions.openedPage, "OK must not open a browser")
        }
    }

    @Test
    fun `being up to date offers nothing to skip`() = updateDialog(UpdateCheckResult.UpToDate) { _ ->
        assertTrue(!shows(Label.SKIP))
    }

    // ── The settings carried on the dialog ──────────────────────────────────────

    @Test
    fun `the pre-release toggle reports the value it moved to`() = updateDialog { actions ->
        onNodeWithText(Label.PRERELEASE_TOGGLE).assertIsDisplayed()
        // The caption is a plain Text beside the Switch, so the switch itself is what takes a press.
        onNode(isToggleable()).performClick()
        waitForIdle()
        assertEquals(true, actions.prereleases, "switching it on must report on")
    }

    @Test
    fun `turning the pre-release toggle back off reports off`() =
        updateDialog(participateInPrereleases = true) { actions ->
            onNode(isToggleable()).performClick()
            waitForIdle()
            assertEquals(false, actions.prereleases)
        }

    @Test
    fun `only a manual check offers the update-frequency setting`() {
        updateDialog(isManualCheck = true) { _ ->
            assertTrue(shows(Label.CHECK_INTERVAL), "a deliberate visit to Check for Updates offers it")
        }
        updateDialog(isManualCheck = false) { _ ->
            // A popup that appeared on its own at launch is not the place to change settings.
            assertTrue(!shows(Label.CHECK_INTERVAL))
        }
    }

    @Test
    fun `opening the check-interval dropdown lists every interval by its own label`() =
        updateDialog(isManualCheck = true) { _ ->
            onNodeWithText("Weekly").performClick()
            waitForIdle()

            assertTrue(shows("Every launch"))
            assertTrue(shows("Weekly"))
            assertTrue(shows("Monthly"))
            assertTrue(shows("Every 2 Months"))
            assertTrue(shows("Every 3 Months"))
            assertTrue(shows("Every 6 Months"))
            assertTrue(shows("Never"))
        }

    @Test
    fun `choosing a different interval from the dropdown reports it and closes the menu`() =
        updateDialog(isManualCheck = true) { actions ->
            onNodeWithText("Weekly").performClick()
            waitForIdle()

            onNodeWithText("Every 3 Months").performClick()
            waitForIdle()

            assertEquals(UpdateCheckInterval.EVERY_3_MONTHS, actions.interval)
            assertTrue(!shows("Never"), "the menu must close after a choice")
        }

    // ── Pure decisions the installer launch makes ───────────────────────────────

    @Test
    fun `msiexec installs on Windows`() {
        assertEquals(
            listOf("msiexec", "/i", "C:\\temp\\update.msi"),
            installerLaunchCommand("Windows 11", "C:\\temp\\update.msi"),
        )
    }

    @Test
    fun `open mounts the installer in Finder on macOS`() {
        assertEquals(listOf("open", "/tmp/update.dmg"), installerLaunchCommand("Mac OS X", "/tmp/update.dmg"))
    }

    @Test
    fun `xdg-open is the fallback everywhere else`() {
        assertEquals(listOf("xdg-open", "/tmp/update.deb"), installerLaunchCommand("Linux", "/tmp/update.deb"))
    }

    @Test
    fun `the os name match is case-insensitive`() {
        assertEquals(listOf("msiexec", "/i", "path"), installerLaunchCommand("WINDOWS", "path"))
        assertEquals(listOf("open", "path"), installerLaunchCommand("MACOS", "path"))
    }

    // ── Leaving ─────────────────────────────────────────────────────────────────

    @Test
    fun `Later closes without downloading anything`() = updateDialog { actions ->
        onNodeWithText(Label.LATER).performClick()
        waitForIdle()
        assertEquals(1, actions.dismissed)
        assertEquals(0, actions.downloads)
    }

    // ── Pure decisions the download coroutine makes ─────────────────────────────

    @Test
    fun `the installer suffix matches the release asset's extension`() {
        assertEquals(".msi", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0.msi"))
        assertEquals(".dmg", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0.dmg"))
        assertEquals(".deb", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0.deb"))
    }

    @Test
    fun `the installer suffix match is case-insensitive`() {
        assertEquals(".msi", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0.MSI"))
        assertEquals(".dmg", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0.DMG"))
    }

    @Test
    fun `an unrecognized asset extension falls back to a generic binary suffix`() {
        assertEquals(".bin", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0.zip"))
        assertEquals(".bin", installerSuffixFor("https://example.invalid/ChurchPresenter-2.5.0"))
    }

    @Test
    fun `download progress is the fraction of bytes received so far`() {
        assertEquals(0f, downloadProgressFraction(bytesRead = 0L, contentLength = 1_000L))
        assertEquals(0.5f, downloadProgressFraction(bytesRead = 500L, contentLength = 1_000L))
        assertEquals(1f, downloadProgressFraction(bytesRead = 1_000L, contentLength = 1_000L))
    }

    @Test
    fun `download progress is coerced into range even if more bytes arrive than expected`() {
        assertEquals(
            1f,
            downloadProgressFraction(bytesRead = 1_200L, contentLength = 1_000L),
            "a content-length off by a little must not report over 100%",
        )
    }

    @Test
    fun `download progress is indeterminate when the server reports no content length`() {
        assertEquals(
            -1f,
            downloadProgressFraction(bytesRead = 500L, contentLength = 0L),
            "no content length means no percentage can be computed",
        )
        assertEquals(-1f, downloadProgressFraction(bytesRead = 500L, contentLength = -1L))
    }
}
