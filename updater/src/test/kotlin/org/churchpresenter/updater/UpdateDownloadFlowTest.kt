@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One download, start to finish, in one open dialog: the progress line and the action button follow
 * the [DownloadState] as it moves on, rather than each state being drawn fresh as
 * `UpdateAvailableContentTest` draws them. A button left over from the previous state -- an Install
 * that still says Downloading, a Download offered again mid-flight -- is what this would catch.
 */
class UpdateDownloadFlowTest {

    private object Label {
        const val DOWNLOAD = "Download & Install"
        const val DOWNLOADING = "Downloading..."
        const val INSTALL = "Install Now"
        const val OPEN_PAGE = "Open Download Page"
        const val LATER = "Later"
    }

    private val info = UpdateInfo(
        latestVersion = "2.6.0",
        releaseUrl = "https://example.invalid/releases/2.6.0",
        releaseNotes = "",
        downloadUrl = "https://example.invalid/ChurchPresenter-2.6.0.dmg",
        isPrerelease = false,
    )

    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `the progress and the button follow a download from start to install`() = runComposeUiTest {
        var state by mutableStateOf<DownloadState>(DownloadState.Idle)
        var downloads = 0
        var installed: File? = null
        var dismissed = 0
        setContent {
            MaterialTheme {
                Column {
                    DownloadProgress(state)
                    UpdateActions(
                        updateInfo = info,
                        downloadState = state,
                        onDismiss = { dismissed++ },
                        onInstall = { installed = it },
                        onOpenReleasePage = {},
                        onDownload = { downloads++ },
                        copyText = {},
                    )
                }
            }
        }

        onNodeWithText(Label.DOWNLOAD).performClick()
        waitForIdle()
        assertEquals(1, downloads)

        state = DownloadState.Downloading(-1f)
        waitForIdle()
        // The size is not known yet: the progress line and the button both say so.
        assertEquals(2, onAllNodes(hasText(Label.DOWNLOADING)).fetchSemanticsNodes().size)
        assertFalse(shows(Label.DOWNLOAD), "a second download is not offered while one runs")

        state = DownloadState.Downloading(0.25f)
        waitForIdle()
        assertTrue(shows("25%"), "the size arrived, so the line turns into a percentage")

        state = DownloadState.Downloading(0.75f)
        waitForIdle()
        assertTrue(shows("75%"))
        assertFalse(shows("25%"))

        val fetched = Files.createTempFile("cp-update", ".dmg").toFile().apply { deleteOnExit() }
        state = DownloadState.Done(fetched)
        waitForIdle()
        assertFalse(shows("75%"), "a finished download shows no progress")
        onNodeWithText(Label.INSTALL).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(fetched, installed)

        onNodeWithText(Label.LATER).performClick()
        waitForIdle()
        assertEquals(1, dismissed)
        fetched.delete()
    }

    @Test
    fun `a download that fails part way swaps its progress for the reason and the release page`() =
        runComposeUiTest {
            var state by mutableStateOf<DownloadState>(DownloadState.Downloading(0.5f))
            var opened: String? = null
            var copied: String? = null
            var dismissed = 0
            setContent {
                MaterialTheme {
                    Column {
                        DownloadProgress(state)
                        UpdateActions(
                            updateInfo = info,
                            downloadState = state,
                            onDismiss = { dismissed++ },
                            onInstall = {},
                            onOpenReleasePage = { opened = it },
                            onDownload = {},
                            copyText = { copied = it },
                        )
                    }
                }
            }
            onNodeWithText(Label.DOWNLOADING).assertIsNotEnabled()

            state = DownloadState.Error("Disk full")
            waitForIdle()

            assertTrue(shows("Disk full"))
            assertFalse(shows("50%"))
            onNodeWithContentDescription("Copy link").performClick()
            waitForIdle()
            assertEquals(info.releaseUrl, copied)
            onNodeWithText(Label.OPEN_PAGE).performClick()
            waitForIdle()
            assertEquals(info.releaseUrl, opened)
            assertEquals(1, dismissed, "opening the page closes the dialog behind it")

            state = DownloadState.Idle
            waitForIdle()
            assertFalse(shows("Disk full"), "a retry starts clean")
            assertTrue(shows(Label.DOWNLOAD))
        }
}
