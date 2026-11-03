package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.BackgroundContent
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.BackgroundTarget
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where Set as Background can put a picture, the question it asks first, and what it saves. */
@OptIn(ExperimentalTestApi::class)
class BackgroundTargetsTest {

    @Test
    fun `outputs sharing a profile are one target, named by all of them`() {
        val targets = groupedBackgroundTargets(
            profileOrder = listOf("main", "stage", "spare"),
            outputs = listOf("Screen 1" to "main", "Stage" to "stage", "NDI Output 1" to "main"),
        )
        assertEquals(
            listOf(BackgroundTarget("main", "Screen 1 + NDI Output 1"), BackgroundTarget("stage", "Stage")),
            targets,
            "in the profiles' own order; a profile no output uses is left out",
        )
    }

    @Test
    fun `an output that draws nothing is no target`() {
        val targets = groupedBackgroundTargets(
            profileOrder = listOf("main"),
            outputs = listOf("Screen 2" to BLANK_OUTPUT_PROFILE_ID, "Screen 3" to null, "Screen 1" to "main"),
        )
        assertEquals(listOf(BackgroundTarget("main", "Screen 1")), targets)
    }

    @Test
    fun `a clip is saved as a video and a picture as an image`() {
        val clip = AppSettings().withFileAsBackground(File("/bg/waves.MP4"), null, BackgroundContent.DEFAULT)
        assertEquals(Constants.BACKGROUND_VIDEO, clip.backgroundSettings.defaultBackgroundType)
        assertEquals(File("/bg/waves.MP4").absolutePath, clip.backgroundSettings.defaultBackgroundVideo)

        val picture = AppSettings().withFileAsBackground(File("/bg/sunrise.jpg"), null, BackgroundContent.DEFAULT)
        assertEquals(Constants.BACKGROUND_IMAGE, picture.backgroundSettings.defaultBackgroundType)
    }

    @Test
    fun `the question names the target the menu named`() {
        val targets = listOf(BackgroundTarget("main", "Screen 1 + NDI Output 1"))
        val file = File("sunrise.jpg")
        val oneProfile = targets.pendingBackground(file, "main", BackgroundContent.SONG)
        assertEquals("Screen 1 + NDI Output 1", oneProfile.target?.label)
        assertNull(targets.pendingBackground(file, null, BackgroundContent.SONG).target, "every output")
    }

    @Test
    fun `nothing is saved until Set is pressed`() = runComposeUiTest {
        var pending by mutableStateOf<PendingBackground?>(
            PendingBackground(File("sunrise.jpg"), BackgroundContent.SONG, BackgroundTarget("main", "Screen 1")),
        )
        val confirmed = mutableListOf<PendingBackground>()
        setContent {
            MaterialTheme {
                SetBackgroundConfirmDialog(
                    pending = pending,
                    onConfirm = { confirmed += it; pending = null },
                    onDismiss = { pending = null },
                )
            }
        }
        onNodeWithText("Set the Songs background on Screen 1 to “sunrise.jpg”?").assertExists()
        assertTrue(confirmed.isEmpty())
        onNodeWithText("Set").performClick()
        waitForIdle()
        assertEquals("sunrise.jpg", confirmed.single().file.name)
        assertEquals(0, onAllNodesWithText("Set as Background").fetchSemanticsNodes().size, "the question closes")
    }

    @Test
    fun `Cancel saves nothing, and every output is named as such`() = runComposeUiTest {
        var pending by mutableStateOf<PendingBackground?>(
            PendingBackground(File("waves.mp4"), BackgroundContent.DEFAULT, target = null),
        )
        val confirmed = mutableListOf<PendingBackground>()
        setContent {
            MaterialTheme {
                SetBackgroundConfirmDialog(pending, onConfirm = { confirmed += it }, onDismiss = { pending = null })
            }
        }
        onNodeWithText("Set the Default background on All outputs to “waves.mp4”?").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(confirmed.isEmpty())
        assertNull(pending)
    }
}
