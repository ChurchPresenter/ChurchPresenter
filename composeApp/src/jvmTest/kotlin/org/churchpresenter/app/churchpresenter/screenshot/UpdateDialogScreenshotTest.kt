@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.dialogs.DownloadState
import org.churchpresenter.app.churchpresenter.dialogs.UpdateAvailableContent
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.utils.UpdateInfo
import org.churchpresenter.settings.utils.UpdateCheckInterval
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The update dialog (Help → Check for Updates…), at the size its window opens at.
 *
 * Shot from a manual check, which is the only route that shows the interval dropdown and the
 * copy-link button beside the buttons -- the row this suite exists to pin.
 */
class UpdateDialogScreenshotTest {

    private fun shoot(name: String, result: UpdateCheckResult, height: Float) =
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
                                    downloadState = DownloadState.Idle,
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
    fun `an update is available`() = shoot(
        "available",
        UpdateCheckResult.Available(
            UpdateInfo(
                latestVersion = "2.5.0",
                releaseUrl = "https://example.invalid/releases/2.5.0",
                releaseNotes = "Tab labels can now be icons.\nThe update dialog's buttons line up.",
                downloadUrl = "https://example.invalid/ChurchPresenter-2.5.0.dmg",
                isPrerelease = false,
            ),
        ),
        AVAILABLE_HEIGHT,
    )

    private companion object {
        const val SECTION = "updateDialog"

        /** The `DialogWindow` sizes in `UpdateAvailableDialog`, for a manual check. */
        const val WIDTH = 440f
        const val UP_TO_DATE_HEIGHT = 468f
        const val AVAILABLE_HEIGHT = 548f
    }
}
