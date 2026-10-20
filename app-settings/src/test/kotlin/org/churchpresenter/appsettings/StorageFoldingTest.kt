package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.churchpresenter.settings.AppSettings
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Storage card with a big library: old song files fold after three, and the actions sit under Browse. */
@OptIn(ExperimentalTestApi::class)
class StorageFoldingTest {
    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temps.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("cp-storage-fold").toFile().also { temps.add(it) }

    private fun songsIn(dir: File) =
        AppSettings().let { it.copy(songSettings = it.songSettings.copy(storageDirectory = dir.path)) }

    private fun biblesIn(dir: File) =
        AppSettings().let { it.copy(bibleSettings = it.bibleSettings.copy(storageDirectory = dir.path)) }

    @Test
    fun `a long list of unsupported song files shows three and folds the rest`() = runComposeUiTest {
        val songs = tempDir()
        (1..5).forEach { File(songs, "00$it Song.sps").writeText("x") }
        setContent { MaterialTheme { SystemSettingsTab(settings = songsIn(songs), onSettingsChange = {}) } }
        waitUntil { onAllNodesWithText("001 Song.sps").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("004 Song.sps").assertCountEquals(0)
        onAllNodesWithText("2 more").onFirst().performScrollTo().performClick()
        onAllNodesWithText("005 Song.sps").assertCountEquals(1)
        onAllNodesWithText("Show less").onFirst().performScrollTo().performClick()
        onAllNodesWithText("005 Song.sps").assertCountEquals(0)
    }

    @Test
    fun `Download Bibles starts where the Bible row's Browse button starts`() = runDesktopComposeUiTest(1400, 900) {
        val bibles = tempDir()
        File(bibles, "kjv.spb").writeText("x")
        setContent { MaterialTheme { SystemSettingsTab(settings = biblesIn(bibles), onSettingsChange = {}) } }
        waitUntil { onAllNodesWithText("Download Bibles…").fetchSemanticsNodes().isNotEmpty() }
        val browse = onAllNodesWithText("Browse...").onFirst().fetchSemanticsNode().boundsInRoot
        val download = onAllNodesWithText("Download Bibles…").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(abs(browse.left - download.left) < 1f, "Browse at ${browse.left}, Download at ${download.left}")
    }
}
