@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.media.FakeMediaOutput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Go Live key on the Media tab: Enter on the tab root puts the loaded clip on the output and
 * starts it, as the Go Live button does; Enter in the address field is the field's.
 *
 * The tab root takes the keyboard when the tab opens and whenever a clip finishes loading, by any
 * route -- a schedule row, the file picker or the Load button.
 */
class MediaGoLiveKeyTest {

    private val clip = ScheduleItem.MediaItem(
        id = "m1",
        mediaUrl = "/media/clip.mp4",
        mediaTitle = "Clip",
        mediaType = Constants.MEDIA_TYPE_LOCAL,
    )

    private fun ComposeUiTest.pressEnter() {
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root puts the loaded clip live and plays it`() {
        val presenter = FakeMediaOutput()
        mediaTab(selectedMediaItem = clip, presenterManager = presenter) { vm, _ ->
            waitUntil("the clip to load") { vm.isLoaded }
            waitForIdle()

            pressEnter()

            assertEquals(Presenting.MEDIA, presenter.onAir.value)
            assertTrue(presenter.showPresenterWindow.value)
            assertEquals(clip.mediaUrl to clip.mediaType, presenter.currentMedia)
            assertTrue(vm.isPlaying, "going live starts the clip")
        }
    }

    @Test
    fun `enter does nothing with no presenter to go live on`() {
        mediaTab(selectedMediaItem = clip) { vm, _ ->
            waitUntil("the clip to load") { vm.isLoaded }
            waitForIdle()

            pressEnter()

            assertFalse(vm.isPlaying, "the key did not reach Go Live")
        }
    }

    @Test
    fun `enter typed in the address field does not go live`() {
        val presenter = FakeMediaOutput()
        mediaTab(presenterManager = presenter) { vm, _ ->
            onNodeWithText(MediaLabel.NETWORK_URL).performClick()
            waitForIdle()
            val field = onAllNodes(hasSetTextAction())[0]
            field.performTextReplacement("https://example.org/clip.mp4")
            onNodeWithText("Load").performClick()
            waitUntil("the clip to load") { vm.isLoaded }
            field.requestFocus()

            field.performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            assertEquals(Presenting.NONE, presenter.onAir.value)
            assertNull(presenter.currentMedia)
        }
    }

    @Test
    fun `after loading an address with the load button, enter goes live rather than loading again`() {
        val presenter = FakeMediaOutput()
        mediaTab(presenterManager = presenter) { vm, _ ->
            onNodeWithText(MediaLabel.NETWORK_URL).performClick()
            waitForIdle()
            onAllNodes(hasSetTextAction())[0].performTextReplacement("https://example.org/clip.mp4")
            onNodeWithText("Load").performClick()
            waitUntil("the clip to load") { vm.isLoaded }
            waitForIdle()

            pressEnter()

            assertEquals(Presenting.MEDIA, presenter.onAir.value)
            assertEquals("https://example.org/clip.mp4", presenter.currentMedia?.first)
        }
    }
}
