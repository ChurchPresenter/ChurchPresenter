package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AtemSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The picture row's two "upload to the ATEM's background slot" buttons: offered only with a switcher
 * configured and a picture chosen, and a picture that cannot be read says so on the button it was
 * sent from -- before anything is sent to the switcher.
 */
@OptIn(ExperimentalTestApi::class)
class ImagePickerRowAtemTest {

    private val dir: File = Files.createTempDirectory("cp-atem-picker").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val atem = AtemSettings(host = "192.0.2.10")

    @Test
    fun `without a switcher or a picture there is nothing to upload`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ImagePickerRow("", {}, "", {}, "", {}, atemSettings = atem)
                ImagePickerRow(File(dir, "a.png").absolutePath, {}, "", {}, "", {}, atemSettings = AtemSettings())
            }
        }
        assertTrue(onAllNodesWithContentDescription(SLOT_1).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a picture that is not an image fails on its own button, and the other slot is untouched`() {
        val notAnImage = File(dir, "notes.png").apply { writeText("not a picture") }
        runComposeUiTest {
            setContent {
                MaterialTheme { ImagePickerRow(notAnImage.absolutePath, {}, "", {}, "", {}, atemSettings = atem) }
            }
            onNodeWithContentDescription(SLOT_1).performClick()
            waitUntil(timeoutMillis = FAIL_TIMEOUT_MS) {
                onAllNodesWithContentDescription(UNREADABLE).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription(SLOT_2).assertExists()
        }
    }

    private companion object {
        const val SLOT_1 = "Upload to Background Slot 1"
        const val SLOT_2 = "Upload to Background Slot 2"
        const val UNREADABLE = "Could not read image file"
        const val FAIL_TIMEOUT_MS = 5_000L
    }
}
