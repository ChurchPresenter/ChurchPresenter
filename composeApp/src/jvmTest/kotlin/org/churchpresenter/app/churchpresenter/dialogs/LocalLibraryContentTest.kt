@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REAL_BUNDLED_IMAGE = "mountains_34448034.jpg"

class LocalLibraryContentTest {

    private var realHome: String? = null
    private lateinit var tempHome: File

    @BeforeTest
    fun setUpHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-local-library-content-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun tearDownHome() {
        realHome?.let { System.setProperty("user.home", it) }
    }

    private class Result {
        var selected: String? = null
        var dismissed = 0
    }

    private fun dialog(
        mediaType: StockMediaClient.StockMediaType = StockMediaClient.StockMediaType.VIDEO,
        downloadedFiles: List<File> = emptyList(),
        bundledFileNames: List<String> = emptyList(),
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LocalLibraryDialogContent(
                        mediaType = mediaType,
                        downloadedFiles = downloadedFiles,
                        bundledFileNames = bundledFileNames,
                        onDismiss = { result.dismissed++ },
                        onMediaSelected = { result.selected = it },
                    )
                }
            }
            block(result)
        }
    }

    /**
     * Polls until [condition] is true, settling the Swing/Compose queues between checks.
     * `LibraryThumbnail`'s bitmap decode runs on `Dispatchers.IO` inside a `LaunchedEffect`; that
     * doesn't reliably synchronize with `ComposeUiTest.waitUntil`'s virtual clock, so this polls
     * real wall-clock time instead (mirrors `StockMediaBrowserContentTest.awaitUntil`).
     */
    private fun ComposeUiTest.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait { }
            waitForIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for condition")
    }

    @Test
    fun `an empty library shows the empty-state message`() = dialog {
        onNodeWithText("Nothing downloaded yet — use Search to add photos or videos.").assertExists()
    }

    @Test
    fun `downloaded videos are listed by name`() = dialog(downloadedFiles = listOf(File("worship-loop.mp4"))) {
        onNodeWithText("worship-loop.mp4").assertExists()
        onNodeWithText("Nothing downloaded yet — use Search to add photos or videos.").assertDoesNotExist()
    }

    @Test
    fun `typing in the search box filters the list`() = dialog(
        downloadedFiles = listOf(File("worship-loop.mp4"), File("countdown.mp4")),
    ) {
        onNodeWithText("Filter by file name…").performTextInput("worship")

        onNodeWithText("worship-loop.mp4").assertExists()
        onNodeWithText("countdown.mp4").assertDoesNotExist()
    }

    @Test
    fun `a search matching nothing shows the empty state`() =
        dialog(downloadedFiles = listOf(File("worship-loop.mp4"))) {
        onNodeWithText("Filter by file name…").performTextInput("no such file")

        onNodeWithText("Nothing downloaded yet — use Search to add photos or videos.").assertExists()
    }

    @Test
    fun `clicking a downloaded entry selects it and dismisses the dialog`() = dialog(
        downloadedFiles = listOf(File("worship-loop.mp4")),
    ) { result ->
        onNodeWithText("worship-loop.mp4").performClick()

        assertEquals(File("worship-loop.mp4").absolutePath, result.selected)
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `Cancel dismisses without selecting anything`() =
        dialog(downloadedFiles = listOf(File("worship-loop.mp4"))) { result ->
        onNodeWithText("Cancel").performClick()

        assertNull(result.selected)
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `the photo library title is used for photos`() = dialog(mediaType = StockMediaClient.StockMediaType.PHOTO) {
        onNodeWithText("Image Library").assertExists()
    }

    @Test
    fun `the video library title is used for videos`() = dialog(mediaType = StockMediaClient.StockMediaType.VIDEO) {
        onNodeWithText("Video Library").assertExists()
    }

    @Test
    fun `a downloaded photo renders its decoded thumbnail`() {
        val file = File(tempHome, "photo.jpg")
        file.writeBytes(runBlocking { Res.readBytes("files/backgrounds/$REAL_BUNDLED_IMAGE") })

        dialog(mediaType = StockMediaClient.StockMediaType.PHOTO, downloadedFiles = listOf(file)) {
            awaitUntil {
                onAllNodesWithTag(
                    LIBRARY_THUMBNAIL_IMAGE_TAG,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(LIBRARY_THUMBNAIL_IMAGE_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `a downloaded photo that fails to decode never shows a thumbnail`() {
        val file = File(tempHome, "not-a-real-image.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        dialog(mediaType = StockMediaClient.StockMediaType.PHOTO, downloadedFiles = listOf(file)) {
            SwingUtilities.invokeAndWait { }
            waitForIdle()
            onNodeWithTag(LIBRARY_THUMBNAIL_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `clicking a bundled entry materializes it, selects it and dismisses the dialog`() = dialog(
        bundledFileNames = listOf(REAL_BUNDLED_IMAGE),
    ) { result ->
        onNodeWithText(REAL_BUNDLED_IMAGE).performClick()

        waitUntil("bundled entry materialized") { result.dismissed == 1 }

        val expected = File(tempHome, ".churchpresenter/stock-backgrounds/$REAL_BUNDLED_IMAGE")
        assertEquals(expected.absolutePath, result.selected)
        assertTrue(expected.exists())
    }

    @Test
    fun `a bundled entry shadowed by a downloaded file of the same name selects the downloaded copy directly`() {
        val downloaded = File(tempHome, REAL_BUNDLED_IMAGE).apply { writeBytes(byteArrayOf(9)) }
        val materialized = File(tempHome, ".churchpresenter/stock-backgrounds/$REAL_BUNDLED_IMAGE")

        dialog(
            downloadedFiles = listOf(downloaded),
            bundledFileNames = listOf(REAL_BUNDLED_IMAGE),
        ) { result ->
            onNodeWithText(REAL_BUNDLED_IMAGE).performClick()

            assertEquals(downloaded.absolutePath, result.selected)
            assertEquals(1, result.dismissed)
            assertTrue(!materialized.exists(), "the downloaded copy should win — nothing should be materialized")
        }
    }
}
