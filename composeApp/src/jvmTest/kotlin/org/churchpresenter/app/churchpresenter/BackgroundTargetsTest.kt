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
import kotlin.test.assertFalse
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.OutputProfile
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

    @Test
    fun `every kind of output that draws is named, and the ones that draw nothing are not`() = runComposeUiTest {
        val proj = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(activeProfileId = "main", screenName = "Sanctuary"),
                ScreenAssignment(activeProfileId = "main", targetDisplay = Constants.KEY_TARGET_NONE),
            ),
            browserSourceOutputs = listOf(ScreenAssignment(activeProfileId = "stream")),
            ndiOutputs = listOf(
                ScreenAssignment(activeProfileId = "stream", ndiName = "Livestream"),
                ScreenAssignment(activeProfileId = "stream", ndiEnabled = false),
            ),
            omtOutputs = listOf(
                ScreenAssignment(activeProfileId = "main"),
                ScreenAssignment(activeProfileId = "main", omtEnabled = false),
            ),
            outputProfiles = listOf(OutputProfile(id = "main"), OutputProfile(id = "stream")),
        )
        var targets: List<BackgroundTarget> = emptyList()
        setContent { targets = backgroundTargets(AppSettings(projectionSettings = proj)) }
        waitForIdle()
        assertEquals(
            listOf(
                BackgroundTarget("main", "Sanctuary + OMT Output 1"),
                BackgroundTarget("stream", "Browser Source 1 + Livestream"),
            ),
            targets,
        )
    }

    private fun state() = MainDesktopState(mutableStateOf(0), showCrashFeedbackInitially = false)

    @Test
    fun `asking to set a background opens the question, naming the menu's target`() {
        val state = state()
        val wiring = SetAsBackgroundWiring(listOf(BackgroundTarget("main", "Screen 1")), AppSettings(), state)

        wiring.request(File("sunrise.jpg"), "main", BackgroundContent.BIBLE)

        val pending = state.pendingBackground
        assertEquals("sunrise.jpg", pending?.file?.name)
        assertEquals("Screen 1", pending?.target?.label)
        assertEquals(BackgroundContent.BIBLE, pending?.content)
    }

    @Test
    fun `the wiring knows which background a file already is`() {
        val file = File("/bg/sunrise.jpg")
        val settings = AppSettings().withFileAsBackground(file, null, BackgroundContent.SONG)
        val wiring = SetAsBackgroundWiring(emptyList(), settings, state())
        assertTrue(wiring.isBackground(file, null, BackgroundContent.SONG))
        assertFalse(wiring.isBackground(file, null, BackgroundContent.DEFAULT))
    }

    @Test
    fun `Set saves the background and closes the question, Cancel only closes it`() {
        val state = state()
        var settings = AppSettings()
        val confirmation = SetAsBackgroundConfirmation(state) { transform -> settings = transform(settings) }
        val pending = PendingBackground(File("/bg/waves.mp4"), BackgroundContent.DEFAULT, target = null)

        state.pendingBackground = pending
        confirmation.dismiss()
        assertNull(state.pendingBackground)
        assertEquals(Constants.BACKGROUND_COLOR, settings.backgroundSettings.defaultBackgroundType)

        state.pendingBackground = pending
        confirmation.confirm(pending)
        assertNull(state.pendingBackground)
        assertEquals(Constants.BACKGROUND_VIDEO, settings.backgroundSettings.defaultBackgroundType)
    }
}
