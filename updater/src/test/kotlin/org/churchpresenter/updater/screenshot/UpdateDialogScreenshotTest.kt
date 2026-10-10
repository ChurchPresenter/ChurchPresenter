@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.updater.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.utils.UpdateCheckInterval
import org.churchpresenter.updater.DownloadState
import org.churchpresenter.updater.UpdateAvailableContent
import org.churchpresenter.updater.UpdateCheckResult
import org.churchpresenter.updater.UpdateInfo
import org.churchpresenter.updater.updateDialogHeight
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import java.time.Instant
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes

/**
 * The update dialog in every state it can be in, at the size its window opens at.
 *
 * Most shots are from a manual check (Help → Check for Updates…), the only route that shows the
 * interval dropdown; one is the check at launch, which leaves it out and opens shorter. Each takes
 * its height from [updateDialogHeight], so a window resized in the dialog is resized here too.
 */
class UpdateDialogScreenshotTest {

    private fun shoot(
        name: String,
        result: UpdateCheckResult,
        downloadState: DownloadState = DownloadState.Idle,
        isManualCheck: Boolean = true,
        participateInPrereleases: Boolean = false,
    ) {
        val height = updateDialogHeight(result is UpdateCheckResult.Available, isManualCheck).value
        stackedThemes(SECTION, name) { mode, file ->
            runSkikoComposeUiTest(size = Size(WIDTH, height), density = Density(1f)) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                UpdateAvailableContent(
                                    result = result,
                                    isManualCheck = isManualCheck,
                                    participateInPrereleases = participateInPrereleases,
                                    onParticipateInPrereleasesChange = {},
                                    updateCheckInterval = UpdateCheckInterval.EVERY_LAUNCH,
                                    onUpdateCheckIntervalChange = {},
                                    downloadState = downloadState,
                                    onDownload = {},
                                    onInstall = {},
                                    onOpenReleasePage = {},
                                    onDismiss = {},
                                    copyText = {},
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                captureTo(file)
            }
        }
    }

    /** An update to the real release, with notes, a date and a size, so every part of the window shows. */
    private val info = UpdateInfo(
        latestVersion = "26.12.171",
        releaseUrl = "https://example.invalid/releases/26.12.171",
        releaseNotes = NOTES,
        downloadUrl = "https://example.invalid/ChurchPresenter-26.12.171.dmg",
        isPrerelease = false,
        currentVersion = "26.11.164",
        publishedAt = Instant.parse("2026-10-08T09:01:10Z"),
        downloadSize = 601_405_516L,
    )
    private val available = UpdateCheckResult.Available(info)

    @Test
    fun `up to date`() = shoot("up_to_date", UpdateCheckResult.UpToDate)

    @Test
    fun `an update is available`() = shoot("available", available)

    /** The check at launch: no interval row, a shorter window, the notes taking the room. */
    @Test
    fun `an update offered by the launch check`() = shoot("available_launch_check", available, isManualCheck = false)

    /** A beta offered with beta updates switched on: the amber pill, and the switch on. */
    @Test
    fun `a pre-release is offered`() = shoot(
        "prerelease",
        UpdateCheckResult.Available(info.copy(latestVersion = "26.12.172", isPrerelease = true)),
        participateInPrereleases = true,
    )

    /** A release with no notes: no heading over an empty panel, the footer drops to the bottom. */
    @Test
    fun `an update with no release notes`() =
        shoot("available_no_notes", UpdateCheckResult.Available(info.copy(releaseNotes = "")))

    /** The footer mid-download: megabytes so far, the percentage, the bar and Cancel. */
    @Test
    fun `an update downloading`() = shoot("downloading", available, DownloadState.Downloading(0.42f))

    /** A server that sent no length: the bar runs without a percentage or megabyte count. */
    @Test
    fun `an update downloading with no known size`() =
        shoot("downloading_unknown_size", available, DownloadState.Downloading(-1f))

    /** The footer once the installer is in: ready to install, Later, and the green Install Now. */
    @Test
    fun `an update ready to install`() =
        shoot("ready_to_install", available, DownloadState.Done(File("ChurchPresenter-update.dmg")))

    /** A download that failed: the reason, and the release page with its address to copy. */
    @Test
    fun `a download that failed`() =
        shoot("download_failed", available, DownloadState.Error("Connection reset by the server"))

    /** No installer for this machine: no Download, only the release page and its address. */
    @Test
    fun `an update with no installer for this machine`() =
        shoot("available_no_installer", UpdateCheckResult.Available(info.copy(downloadUrl = null)))

    private companion object {
        const val SECTION = "updateDialog"

        /** The `DialogWindow` width in `UpdateAvailableDialog`. */
        const val WIDTH = 520f

        /** Notes written the way the releases are: groups of bullets, each naming its pull request. */
        val NOTES = """
            **Songs**
            - Section label is now a song element, with four places to put song elements (#693)
            - Profiles follow-ups: free element moves, a song All layer, and Checker (#692)

            **Canvas**
            - Canvas size and portrait scenes (#690)
            - Edit scenes in place without leaving Live (#688)

            **Fixes**
            - Keyboard focus is restored after closing a dialog (#686)
        """.trimIndent()
    }
}
