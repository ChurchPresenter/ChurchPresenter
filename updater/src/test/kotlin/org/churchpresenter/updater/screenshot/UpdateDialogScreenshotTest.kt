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
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import java.time.Instant
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes

/**
 * The update dialog (Help → Check for Updates…), at the size its window opens at.
 *
 * Shot from a manual check, which is the only route that shows the interval dropdown and the
 * copy-link button beside the buttons -- the row this suite exists to pin.
 */
class UpdateDialogScreenshotTest {

    private fun shoot(
        name: String,
        result: UpdateCheckResult,
        height: Float,
        downloadState: DownloadState = DownloadState.Idle,
    ) =
        stackedThemes(SECTION, name) { mode, file ->
            runSkikoComposeUiTest(size = Size(WIDTH, height), density = Density(1f)) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                UpdateAvailableContent(
                                    result = result,
                                    isManualCheck = true,
                                    participateInPrereleases = false,
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

    @Test
    fun `up to date`() = shoot("up_to_date", UpdateCheckResult.UpToDate, UP_TO_DATE_HEIGHT)

    @Test
    fun `an update is available`() = shoot("available", available, AVAILABLE_HEIGHT)

    /** The footer mid-download: megabytes so far, the percentage, the bar and Cancel. */
    @Test
    fun `an update downloading`() =
        shoot("downloading", available, AVAILABLE_HEIGHT, DownloadState.Downloading(0.42f))

    /** The footer once the installer is in: ready to install, Later, and the green Install Now. */
    @Test
    fun `an update ready to install`() =
        shoot("ready_to_install", available, AVAILABLE_HEIGHT, DownloadState.Done(File("ChurchPresenter-update.dmg")))

    /** An update to the real release, with notes, a date and a size, so every part of the window shows. */
    private val available = UpdateCheckResult.Available(
        UpdateInfo(
            latestVersion = "26.12.171",
            releaseUrl = "https://example.invalid/releases/26.12.171",
            releaseNotes = NOTES,
            downloadUrl = "https://example.invalid/ChurchPresenter-26.12.171.dmg",
            isPrerelease = false,
            currentVersion = "26.11.164",
            publishedAt = Instant.parse("2026-10-08T09:01:10Z"),
            downloadSize = 601_405_516L,
        ),
    )

    private companion object {
        const val SECTION = "updateDialog"

        /** The `DialogWindow` sizes in `UpdateAvailableDialog`, for a manual check. */
        const val WIDTH = 520f
        const val UP_TO_DATE_HEIGHT = 330f
        const val AVAILABLE_HEIGHT = 620f

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
