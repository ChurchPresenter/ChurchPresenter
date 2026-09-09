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
 * The image-file picker row: a click opens a native chooser and, when the user picks a file,
 * reports its path back through [FileImagePicker.onImagePathChange].
 *
 * The real chooser opens an OS dialog that would block this test suite waiting for a human, so
 * [FileChooser] — already an `abstract class` built around exactly this seam, not something added
 * for this test — is stood in for with [FakeFileChooser], which returns a canned answer instead of
 * showing anything.
 */
@OptIn(ExperimentalTestApi::class)
class FileImagePickerTest {

    /** Records the call it received and returns [answer] without ever touching a real dialog. */
    private class FakeFileChooser(private val answer: Path?) : FileChooser() {
        var lastPath: Path? = null
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
            lastPath = path
            lastFilters = filters
            return answer?.let { listOf(it) }
        }

        override suspend fun saveImpl(
            location: Path,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): Path? = error("not used by FileImagePicker")
    }

    @Test
    fun `with no path chosen, the placeholder text shows`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FileImagePicker(imagePath = "", onImagePathChange = {})
            }
        }
        onNodeWithText("No image selected").assertExists()
    }

    @Test
    fun `with a path set, only the file name shows, not the full path`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FileImagePicker(imagePath = "/home/user/pictures/logo.png", onImagePathChange = {})
            }
        }
        onNodeWithText("logo.png").assertExists()
        onNodeWithText("/home/user/pictures/logo.png").assertDoesNotExist()
    }

    @Test
    fun `choosing a file reports its path back`() {
        // The chosen path is built from a real temp directory rather than written as "/chosen/...".
        // A POSIX literal is not an absolute path on Windows, so the round-trip gained a drive
        // letter or lost its root and the assertion failed there for reasons unrelated to the
        // picker (see the root AGENT.md). Derived this way, both sides agree on every platform.
        val dir = Files.createTempDirectory("cp-picker-test")
        try {
            val chosen = dir.resolve("new-logo.png")
            runComposeUiTest {
                val chooser = FakeFileChooser(answer = chosen)
                var reported: String? = null
                setContent {
                    MaterialTheme {
                        FileImagePicker(imagePath = "", onImagePathChange = { reported = it }, fileChooser = chooser)
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
                FileImagePicker(imagePath = "", onImagePathChange = { reported = it }, fileChooser = chooser)
            }
        }
        onNode(hasClickAction()).performClick()
        waitForIdle()

        assertNull(reported, "canceling must not invoke onImagePathChange at all")
    }

    @Test
    fun `the filter offered is for image file extensions`() = runComposeUiTest {
        val chooser = FakeFileChooser(answer = null)
        setContent {
            MaterialTheme {
                FileImagePicker(imagePath = "", onImagePathChange = {}, fileChooser = chooser)
            }
        }
        onNode(hasClickAction()).performClick()
        waitForIdle()

        val extensions = chooser.lastFilters?.single()?.extensions?.toList()
        assertEquals(listOf("jpg", "jpeg", "png", "gif", "bmp"), extensions)
    }

    @Test
    fun `with an existing image path, the chooser is opened at that exact path`() = runComposeUiTest {
        val existing = java.nio.file.Files.createTempFile("cp-file-image-picker-test", ".png")
        try {
            val chooser = FakeFileChooser(answer = null)
            setContent {
                MaterialTheme {
                    FileImagePicker(imagePath = existing.toString(), onImagePathChange = {}, fileChooser = chooser)
                }
            }
            onNode(hasClickAction()).performClick()
            waitForIdle()

            assertEquals(existing, chooser.lastPath)
        } finally {
            existing.toFile().delete()
        }
    }

    @Test
    fun `with a nonexistent image path, the chooser falls back to the user's home directory`() = runComposeUiTest {
        val chooser = FakeFileChooser(answer = null)
        setContent {
            MaterialTheme {
                FileImagePicker(imagePath = "/nonexistent/dir/logo.png", onImagePathChange = {}, fileChooser = chooser)
            }
        }
        onNode(hasClickAction()).performClick()
        waitForIdle()

        assertEquals(Path.of(System.getProperty("user.home")), chooser.lastPath)
    }
}
