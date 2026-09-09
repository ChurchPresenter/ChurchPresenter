package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The video-file picker row — the same shape as [FileImagePickerTest] but for video filters. See
 * that class's doc comment for why [FakeFileChooser] stands in for the real chooser.
 */
@OptIn(ExperimentalTestApi::class)
class FileVideoPickerTest {

    private class FakeFileChooser(private val answer: Path?) : FileChooser() {
        var lastFilters: List<FileNameExtensionFilter>? = null
        var callCount = 0

        override suspend fun chooseImpl(
            path: Path,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<Path>? {
            callCount++
            lastFilters = filters
            return answer?.let { listOf(it) }
        }

        override suspend fun saveImpl(
            location: Path,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): Path? = error("not used by FileVideoPicker")
    }

    @Test
    fun `with no path chosen, the placeholder text shows`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FileVideoPicker(videoPath = "", onVideoPathChange = {})
            }
        }
        onNodeWithText("No video selected").assertExists()
    }

    @Test
    fun `with a path set, only the file name shows, not the full path`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FileVideoPicker(videoPath = "/home/user/videos/intro.mp4", onVideoPathChange = {})
            }
        }
        onNodeWithText("intro.mp4").assertExists()
        onNodeWithText("/home/user/videos/intro.mp4").assertDoesNotExist()
    }

    @Test
    fun `choosing a file reports its path back`() {
        // The chosen path is built from a real temp directory rather than written as "/chosen/...".
        // A POSIX literal is not an absolute path on Windows, so the round-trip gained a drive
        // letter or lost its root and the assertion failed there for reasons unrelated to the
        // picker (see the root AGENT.md). Derived this way, both sides agree on every platform.
        val dir = Files.createTempDirectory("cp-picker-test")
        try {
            val chosen = dir.resolve("new-intro.mp4")
            runComposeUiTest {
                val chooser = FakeFileChooser(answer = chosen)
                var reported: String? = null
                setContent {
                    MaterialTheme {
                        FileVideoPicker(videoPath = "", onVideoPathChange = { reported = it }, fileChooser = chooser)
                    }
                }
                onNode(hasClickAction()).performClick()
                waitForIdle()

                assertEquals(chosen.toString(), reported)
                assertEquals(1, chooser.callCount)
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `canceling the dialog reports nothing`() = runComposeUiTest {
        val chooser = FakeFileChooser(answer = null)
        var reported: String? = null
        setContent {
            MaterialTheme {
                FileVideoPicker(videoPath = "", onVideoPathChange = { reported = it }, fileChooser = chooser)
            }
        }
        onNode(hasClickAction()).performClick()
        waitForIdle()

        assertNull(reported, "canceling must not invoke onVideoPathChange at all")
    }

    @Test
    fun `the filter offered is for video file extensions`() = runComposeUiTest {
        val chooser = FakeFileChooser(answer = null)
        setContent {
            MaterialTheme {
                FileVideoPicker(videoPath = "", onVideoPathChange = {}, fileChooser = chooser)
            }
        }
        onNode(hasClickAction()).performClick()
        waitForIdle()

        val extensions = chooser.lastFilters?.single()?.extensions?.toList()
        assertEquals(listOf("mp4", "mov", "avi", "mkv", "webm"), extensions)
    }
}
