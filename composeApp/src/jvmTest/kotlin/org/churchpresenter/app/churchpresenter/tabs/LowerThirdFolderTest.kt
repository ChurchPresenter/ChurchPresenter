@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * What the Lower Third tab makes of the folder it is pointed at.
 *
 * The preset list is rebuilt from disk on every change to the configured folder, through a chain of
 * guards — the path is non-empty, it exists, it is a directory, and each file in it is a `.json`
 * that actually parses as a Lottie. [LowerThirdTabTest] covers the two ordinary outcomes (a folder
 * with presets, and an empty one); this covers the ways the setting can be wrong, which is the
 * common case in the field, since the path is typed or picked once and then the folder gets moved,
 * renamed or deleted.
 *
 * All of them have to land on the same "no JSON files found" panel rather than an empty list that
 * looks like a working folder, or an exception on a tab the operator opens mid-service. That message
 * is distinct from the one for a folder that was never configured at all — a path that is set but
 * wrong is a different problem from a path that is missing, and the tab now says which.
 */
class LowerThirdFolderTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temps.forEach { it.deleteRecursively() }
        temps.clear()
    }

    private fun tempDir(): File =
        Files.createTempDirectory("cp-lowerthird-folder").toFile().also { temps.add(it) }

    @Test
    fun `a folder that no longer exists offers no presets`() {
        // The operator picked a folder on a drive that is no longer mounted, or deleted it.
        val gone = tempDir().also { it.deleteRecursively() }

        lowerThirdTab(folder = gone) { _ ->
            onNodeWithText(LowerThirdLabel.NO_FILES).assertExists()
        }
    }

    @Test
    fun `a path pointing at a file rather than a folder offers no presets`() {
        // Picked with a file chooser that allowed files, or typed by hand.
        val dir = tempDir()
        val notADirectory = File(dir, "lower-thirds.json").apply { writeText(LOWER_THIRD_LOTTIE) }

        lowerThirdTab(folder = notADirectory) { _ ->
            onNodeWithText(LowerThirdLabel.NO_FILES).assertExists()
        }
    }

    @Test
    fun `a folder holding only non-JSON files offers no presets`() {
        val dir = tempDir().apply {
            File(this, "notes.txt").writeText("not a preset")
            File(this, "logo.png").writeText("not a preset either")
        }

        lowerThirdTab(folder = dir) { _ ->
            onNodeWithText(LowerThirdLabel.NO_FILES).assertExists()
        }
    }

    @Test
    fun `a JSON file that is not a Lottie is not offered as a preset`() {
        // The filter is by content, not extension: a stray settings or schedule file sharing the
        // folder must not appear as something the operator can put on screen.
        val dir = tempDir().apply {
            File(this, "settings.json").writeText("""{"theme":"dark"}""")
        }

        lowerThirdTab(folder = dir) { _ ->
            onNodeWithText(LowerThirdLabel.NO_FILES).assertExists()
        }
    }

    @Test
    fun `a JSON extension in capitals is still read as a preset`() {
        // File systems differ on case, and a file exported as .JSON is the same file.
        val dir = tempDir().apply {
            File(this, "Welcome.JSON").writeText(LOWER_THIRD_LOTTIE)
        }

        lowerThirdTab(folder = dir) { _ ->
            onNodeWithText("Welcome").assertExists()
        }
    }

    @Test
    fun `real Lottie files are listed alongside the rubbish that shares their folder`() {
        val dir = tempDir().apply {
            File(this, "Speaker.json").writeText(LOWER_THIRD_LOTTIE)
            File(this, "settings.json").writeText("""{"theme":"dark"}""")
            File(this, "notes.txt").writeText("not a preset")
        }

        lowerThirdTab(folder = dir) { _ ->
            onNodeWithText("Speaker").assertExists()
            onNodeWithText("settings").assertDoesNotExist()
            onNodeWithText("notes").assertDoesNotExist()
        }
    }
}
