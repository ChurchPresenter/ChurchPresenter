@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.updater

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.utils.UpdateCheckInterval
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The update window while what it is given changes under it: the same open window must follow a
 * download's progress, install the file it was last told about, open the release page of the update
 * it now shows, and call the callbacks it was last handed — not the ones it opened with.
 *
 * `UpdateAvailableContentTest` draws each state fresh and `UpdateDownloadFlowTest` walks one download
 * through its stages; this changes each input in turn on a window that is already up.
 */
class UpdateContentChangesTest {

    private object Label {
        const val INSTALL = "Install Now"
        const val OPEN_PAGE = "Open Download Page"
        const val VIEW_ON_GITHUB = "View on GitHub"
        const val WEEKLY = "Weekly"
        const val NEVER = "Never"
    }

    private fun available(version: String) = UpdateCheckResult.Available(
        UpdateInfo(
            latestVersion = version,
            releaseUrl = "https://example.invalid/releases/$version",
            releaseNotes = "notes",
            downloadUrl = "https://example.invalid/ChurchPresenter-$version.dmg",
        ),
    )

    /** What the window was asked to do, tagged with which set of callbacks was asked. */
    private val calls = CopyOnWriteArrayList<String>()

    private inner class Inputs {
        var result by mutableStateOf<UpdateCheckResult>(available("2.5.0"))
        var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        var callbacks by mutableStateOf("first")
    }

    private fun window(isManualCheck: Boolean = false, block: ComposeUiTest.(Inputs) -> Unit) {
        val inputs = Inputs()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    val tag = inputs.callbacks
                    UpdateAvailableContent(
                        result = inputs.result,
                        isManualCheck = isManualCheck,
                        participateInPrereleases = false,
                        onParticipateInPrereleasesChange = {},
                        updateCheckInterval = UpdateCheckInterval.WEEKLY,
                        onUpdateCheckIntervalChange = { calls += "$tag interval $it" },
                        downloadState = inputs.downloadState,
                        onDownload = { calls += "$tag download" },
                        onInstall = { calls += "$tag install ${it.name}" },
                        onOpenReleasePage = { calls += "$tag open $it" },
                        onDismiss = { calls += "$tag dismiss" },
                    )
                }
            }
            block(inputs)
        }
    }

    private fun ComposeUiTest.shows(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `the progress line moves on in place as the download advances`() = window { inputs ->
        inputs.downloadState = DownloadState.Downloading(0.3f)
        waitUntil("the first reading must show", timeoutMillis = 5_000) { shows("30%") }

        inputs.downloadState = DownloadState.Downloading(0.6f)
        waitUntil("the later reading must replace it", timeoutMillis = 5_000) { shows("60%") }
        assertTrue(!shows("30%"), "the old reading must not linger")

        // Redrawn for another reason, the window keeps the reading it has.
        inputs.callbacks = "second"
        waitForIdle()
        assertTrue(shows("60%"))
    }

    @Test
    fun `install runs the file the window was last told about`() = window { inputs ->
        inputs.downloadState = DownloadState.Done(File("first-installer.dmg"))
        waitUntil("install must be offered", timeoutMillis = 5_000) { shows(Label.INSTALL) }

        // One change at a time: the callbacks, then the file.
        inputs.callbacks = "second"
        waitForIdle()
        inputs.downloadState = DownloadState.Done(File("second-installer.dmg"))
        waitForIdle()
        onNodeWithText(Label.INSTALL).performClick()
        waitForIdle()

        assertEquals(listOf("second install second-installer.dmg"), calls.toList())
    }

    @Test
    fun `after a failure the release page offered is the one for the update now shown`() = window { inputs ->
        inputs.downloadState = DownloadState.Error("Download failed")
        waitUntil("the release page must be offered", timeoutMillis = 5_000) { shows(Label.OPEN_PAGE) }

        // One change at a time: the callbacks, then the update shown.
        inputs.callbacks = "second"
        waitForIdle()
        inputs.result = available("2.6.0")
        waitForIdle()
        onNodeWithText(Label.OPEN_PAGE).performClick()
        waitForIdle()

        assertEquals(
            listOf("second open https://example.invalid/releases/2.6.0", "second dismiss"),
            calls.toList(),
        )
    }

    @Test
    fun `up to date, the GitHub button calls the callbacks the window was last handed`() = window { inputs ->
        inputs.result = UpdateCheckResult.UpToDate
        waitUntil("the up-to-date actions must show", timeoutMillis = 5_000) { shows(Label.VIEW_ON_GITHUB) }

        inputs.callbacks = "second"
        waitForIdle()
        onNodeWithText(Label.VIEW_ON_GITHUB).performClick()
        waitForIdle()

        assertEquals(listOf("second open ${UpdateChecker.RELEASES_URL}", "second dismiss"), calls.toList())
    }

    @Test
    fun `closing the interval menu without a choice keeps the interval`() = window(isManualCheck = true) { _ ->
        onNodeWithText(Label.WEEKLY).performClick()
        waitUntil("the menu must open", timeoutMillis = 5_000) { shows(Label.NEVER) }

        // A click on the window outside the menu, as the operator closes it.
        onAllNodes(isRoot())[0].performMouseInput { click(Offset(2f, 2f)) }
        waitUntil("the menu must close", timeoutMillis = 5_000) { !shows(Label.NEVER) }

        assertEquals(emptyList(), calls.toList(), "dismissing is not a choice")
    }
}
