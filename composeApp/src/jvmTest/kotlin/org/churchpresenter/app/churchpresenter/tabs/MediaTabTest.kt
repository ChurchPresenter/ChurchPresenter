@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Media tab's source bar — choosing between a local file and a network URL, loading one, and
 * putting it in the schedule.
 *
 * This is everything an operator does before playback starts, and none of it needs VLC: the picker, the
 * URL field and the Load button are ordinary Compose. Only the player surface underneath is bound to
 * the runtime, which is why this tab sat at 0% with no test file while `MediaViewModel` behind it was
 * already at 97%.
 *
 * Loading is asserted through the view model rather than by reading the screen back — the tab shows the
 * title it was given whether or not anything was actually loaded, so the screen alone cannot tell a
 * working Load button from a decorative one.
 *
 * See `MediaTabTestSupport.kt` for the harness, and `MediaTabVlcUnavailableTest` for the other branch.
 */
class MediaTabTest {

    // ── The source picker ───────────────────────────────────────────────────────

    @Test
    fun `the tab offers both a local file and a network url source`() = mediaTab { _, _ ->
        onNodeWithText(MediaLabel.LOCAL_FILE).assertExists()
        onNodeWithText(MediaLabel.NETWORK_URL).assertExists()
    }

    @Test
    fun `it starts on local file, with the file picker rather than a url box`() = mediaTab { _, _ ->
        // Local is the common case — a video file dropped in a folder — so it is the default.
        onNodeWithText(MediaLabel.SELECT_FILE).assertExists()
        onNodeWithText(MediaLabel.URL_PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    fun `choosing network url swaps the file picker for a url field`() = mediaTab { _, _ ->
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()

        onNodeWithText(MediaLabel.URL_PLACEHOLDER).assertExists("the url entry must appear")
        onNodeWithText(MediaLabel.SELECT_FILE).assertDoesNotExist()
    }

    @Test
    fun `switching back to local file restores the picker`() = mediaTab { _, _ ->
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()
        onNodeWithText(MediaLabel.LOCAL_FILE).performClick()
        waitForIdle()

        onNodeWithText(MediaLabel.SELECT_FILE).assertExists()
        onNodeWithText(MediaLabel.URL_PLACEHOLDER).assertDoesNotExist()
    }

    // ── Loading a URL ───────────────────────────────────────────────────────────

    @Test
    fun `typing a url and loading it reaches the view model`() = mediaTab { vm, _ ->
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("rtsp://example.org/stream")
        waitForIdle()

        onNodeWithText("Load").performClick()
        waitForIdle()

        // Asserted on the view model: the tab would display the url either way.
        assertEquals("rtsp://example.org/stream", vm.mediaUrl)
        assertEquals(Constants.MEDIA_TYPE_URL, vm.mediaType)
    }

    @Test
    fun `an empty url does not load anything`() = mediaTab { vm, _ ->
        onNodeWithText(MediaLabel.NETWORK_URL).performClick()
        waitForIdle()

        onNodeWithText("Load").performClick()
        waitForIdle()

        // A blank Load would clear whatever is playing for nothing.
        assertEquals("", vm.mediaUrl)
    }

    // ── A schedule item arriving ────────────────────────────────────────────────

    @Test
    fun `a url item from the schedule selects the url source and loads it`() {
        val item = ScheduleItem.MediaItem(
            id = "1",
            mediaUrl = "https://example.org/clip.mp4",
            mediaTitle = "Clip",
            mediaType = Constants.MEDIA_TYPE_URL,
        )

        mediaTab(selectedMediaItem = item) { vm, _ ->
            waitForIdle()

            // Clicking a media item in the schedule has to land the operator on a tab that is already
            // showing that item, not on an empty picker they have to re-fill.
            assertEquals("https://example.org/clip.mp4", vm.mediaUrl)
            assertEquals("Clip", vm.mediaTitle)
            onNodeWithText(MediaLabel.URL_PLACEHOLDER).assertDoesNotExist()
        }
    }

    @Test
    fun `a local item from the schedule stays on the local source`() {
        val item = ScheduleItem.MediaItem(
            id = "1",
            mediaUrl = "/videos/sermon.mp4",
            mediaTitle = "Sermon",
            mediaType = Constants.MEDIA_TYPE_LOCAL,
        )

        mediaTab(selectedMediaItem = item) { vm, _ ->
            waitForIdle()

            assertEquals("/videos/sermon.mp4", vm.mediaUrl)
            assertEquals(Constants.MEDIA_TYPE_LOCAL, vm.mediaType)
            onNodeWithText(MediaLabel.SELECT_FILE).assertExists("the local picker stays")
        }
    }

    // ── What is shown before anything is loaded ─────────────────────────────────

    @Test
    fun `with nothing loaded the tab says so`() = mediaTab { vm, _ ->
        assertTrue(!vm.isLoaded, "nothing is loaded at first")
        // Said twice — once beside the file picker and once over the empty player area — so this
        // counts rather than asserting a single node.
        assertTrue(
            onAllNodesWithText("No media loaded").fetchSemanticsNodes(atLeastOneRootRequired = false).size >= 1,
            "an idle tab must say it has no source: ${renderedText()}",
        )
    }
}
