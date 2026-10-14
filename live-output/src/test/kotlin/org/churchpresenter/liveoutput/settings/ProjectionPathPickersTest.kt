@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.composables.vlcCustomPath
import org.churchpresenter.settings.AppSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val VLC_REQUIRED = "VLC media player is required for media playback"
private const val VLC_PATH_INVALID = "Directory does not contain VLC libraries"
private const val AUDIO_DEVICE_LABEL = "Output device"

/**
 * The tab's folder pickers, answered through [LocalPathChooser] instead of a native dialog, and the
 * tab on its own defaults.
 *
 * The NDI folder is only ever answered with nothing, which must change nothing: choosing one starts
 * the NDI runtime, and that may not leak into the suites after this one. A VLC folder is answered
 * with a path, because what VLC makes of it is the tab's `vlcRecheck` and pinned here; the folder
 * still repoints the process-wide [vlcCustomPath], which each such test puts back.
 */
class ProjectionPathPickersTest {

    @Test
    fun `the OMT folder chosen is written back, and a dismissed NDI chooser writes nothing`() {
        val folder = Files.createTempDirectory("omt-library").toFile()
        val asked = mutableListOf<String>()
        try {
            projectionTab(chooser = PathChooser { _, title, directory ->
                asked += title
                folder.toPath().takeIf { directory && title == "OMT library folder" }
            }) { read ->
                onAllNodesWithText("Choose Folder").fetchSemanticsNodes().indices.forEach { i ->
                    onAllNodesWithText("Choose Folder")[i].performScrollTo().performClick()
                }
                waitUntil { read().projectionSettings.omtLibraryPath.isNotEmpty() }
                assertEquals(folder.absolutePath, read().projectionSettings.omtLibraryPath)
                assertEquals("", read().projectionSettings.ndiRuntimePath, "the NDI chooser was dismissed")
                assertEquals(setOf("NDI Runtime folder", "OMT library folder"), asked.toSet())
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a dismissed VLC chooser leaves the VLC path as it was`() {
        val asked = mutableListOf<Boolean>()
        projectionTab(vlcInstalled = false, chooser = PathChooser { _, _, directory ->
            asked += directory
            null
        }) { read ->
            onNodeWithText("Browse").performScrollTo().performClick()
            waitUntil { asked.isNotEmpty() }
            waitForIdle()
            assertEquals(listOf(true), asked, "VLC is chosen by its folder")
            assertEquals("", read().projectionSettings.vlcPath)
        }
    }

    @Test
    fun `a VLC folder with no VLC in it is saved, and the field says so`() {
        chooseVlcFolder(rechecked = false) { read, folder ->
            assertEquals(folder.absolutePath, read().projectionSettings.vlcPath)
            assertEquals(folder.absolutePath, vlcCustomPath, "the player searches the folder chosen")
            onNodeWithText(VLC_PATH_INVALID).assertExists()
            onNodeWithText(VLC_REQUIRED).assertExists()
        }
    }

    @Test
    fun `a VLC folder that has VLC opens the device list in place of the message`() {
        chooseVlcFolder(rechecked = true) { read, folder ->
            assertEquals(folder.absolutePath, read().projectionSettings.vlcPath)
            waitUntil { onAllNodesWithText(AUDIO_DEVICE_LABEL).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodesWithText(VLC_REQUIRED).fetchSemanticsNodes().isEmpty())
            assertTrue(onAllNodesWithText(VLC_PATH_INVALID).fetchSemanticsNodes().isEmpty())
        }
    }

    /**
     * Opens the tab with VLC missing, browses to a fresh folder that VLC is then said to be in or
     * not, and runs [block] once the choice is written back — restoring [vlcCustomPath] after.
     */
    private fun chooseVlcFolder(
        rechecked: Boolean,
        block: ComposeUiTest.(read: () -> AppSettings, folder: File) -> Unit,
    ) {
        val folder = Files.createTempDirectory("vlc-folder").toFile()
        val searched = vlcCustomPath
        try {
            projectionTab(
                vlcInstalled = false,
                vlcRechecked = rechecked,
                chooser = PathChooser { _, _, directory -> folder.toPath().takeIf { directory } },
            ) { read ->
                onNodeWithText(VLC_REQUIRED).assertExists()
                onNodeWithText("Browse").performScrollTo().performClick()
                waitUntil { read().projectionSettings.vlcPath.isNotEmpty() }
                waitForIdle()
                block(read, folder)
            }
        } finally {
            vlcCustomPath = searched
            folder.deleteRecursively()
        }
    }

    @Test
    fun `left to its own probes, the tab still draws its cards`() = runComposeUiTest {
        // Every probe at its default but the two that cannot run here: the screens (headless AWT
        // has none) and VLC (a native search too slow for a unit test).
        setContent {
            MaterialTheme {
                var settings by remember { mutableStateOf(AppSettings()) }
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = { settings = it(settings) },
                    serverUrl = "",
                    detectScreens = { emptyList() },
                    vlcProbe = { false },
                )
            }
        }
        waitForIdle()
        onNodeWithText("Camera Capture").performScrollTo().assertExists()
    }
}
