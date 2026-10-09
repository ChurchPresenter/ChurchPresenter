@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tab's folder pickers, answered through [LocalPathChooser] instead of a native dialog, and the
 * tab on its own defaults.
 *
 * Only the OMT folder is answered with a path: choosing an NDI folder starts the NDI runtime and
 * choosing a VLC folder repoints the process-wide VLC search, and neither may leak into the suites
 * after this one. Those two are asked and answered with nothing, which must change nothing.
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
    fun `left to its own probes, the tab still draws its cards`() = runComposeUiTest {
        // Every probe at its default but the two that cannot run here: the screens (headless AWT
        // has none) and VLC (a native search too slow for a unit test).
        setContent {
            MaterialTheme {
                var settings by remember { mutableStateOf(AppSettings()) }
                ProjectionSettingsTab(
                    settings = settings,
                    onSettingsChange = { settings = it(settings) },
                    companionServer = CompanionServer(),
                    detectScreens = { emptyList() },
                    vlcProbe = { false },
                )
            }
        }
        waitForIdle()
        onNodeWithText("Camera Capture").performScrollTo().assertExists()
    }
}
