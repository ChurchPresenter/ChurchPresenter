@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaTabRecentClickTest {

    private lateinit var savedPaths: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        savedPaths = RecentMediaFiles.paths.toList()
        savedPinned = RecentMediaFiles.pinned.toList()
        RecentMediaFiles.paths.clear()
        RecentMediaFiles.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentMediaFiles.paths.clear()
        RecentMediaFiles.paths.addAll(savedPaths)
        RecentMediaFiles.pinned.clear()
        RecentMediaFiles.pinned.addAll(savedPinned)
    }

    private fun seed(vararg paths: String) {
        RecentMediaFiles.paths.addAll(paths)
    }

    @Test
    fun `clicking a recent video loads it as a local file`() {
        seed("/media/advent-promo.mp4")

        mediaTab { vm, _ ->
            onNodeWithText("advent-promo.mp4").performClick()
            waitForIdle()

            assertEquals("/media/advent-promo.mp4", vm.mediaUrl)
            assertEquals(Constants.MEDIA_TYPE_LOCAL, vm.mediaType)
            assertTrue(vm.isLoaded)
        }
    }

    @Test
    fun `clicking a recent https address loads it as a url`() {
        seed("https://example.org/stream.m3u8")

        mediaTab { vm, _ ->
            onNodeWithText("https://example.org/stream.m3u8").performClick()
            waitForIdle()

            assertEquals(Constants.MEDIA_TYPE_URL, vm.mediaType)
        }
    }

    @Test
    fun `clicking a recent rtsp stream loads it as a url`() {
        seed("rtsp://camera.local/live")

        mediaTab { vm, _ ->
            onNodeWithText("rtsp://camera.local/live").performClick()
            waitForIdle()

            assertEquals(Constants.MEDIA_TYPE_URL, vm.mediaType)
        }
    }

    @Test
    fun `an unfamiliar extension is treated as a local file`() {
        seed("/media/clip.mkv")

        mediaTab { vm, _ ->
            onNodeWithText("clip.mkv").performClick()
            waitForIdle()

            assertEquals(Constants.MEDIA_TYPE_LOCAL, vm.mediaType)
        }
    }

    @Test
    fun `loading a recent file while media is live clears the output first`() {
        seed("/media/advent-promo.mp4")
        val presenter = PresenterManager().apply { setPresentingMode(Presenting.MEDIA) }

        mediaTab(presenterManager = presenter) { vm, _ ->
            onNodeWithText("advent-promo.mp4").performClick()
            waitForIdle()

            assertTrue(
                presenter.clearDisplayRequested.value,
                "the previous clip must come off before the new one is loaded",
            )
            assertEquals("/media/advent-promo.mp4", vm.mediaUrl)
        }
    }

    @Test
    fun `loading a recent file while something else is live leaves the output alone`() {
        seed("/media/advent-promo.mp4")
        val presenter = PresenterManager().apply { setPresentingMode(Presenting.BIBLE) }

        mediaTab(presenterManager = presenter) { vm, _ ->
            onNodeWithText("advent-promo.mp4").performClick()
            waitForIdle()

            assertEquals(
                false,
                presenter.clearDisplayRequested.value,
                "a verse on screen is not this tab's to clear",
            )
            assertEquals("/media/advent-promo.mp4", vm.mediaUrl)
        }
    }

    @Test
    fun `clicking a second recent file replaces the first`() {
        seed("/media/first.mp4", "/media/second.mp4")

        mediaTab { vm, _ ->
            onNodeWithText("first.mp4").performClick()
            waitForIdle()
            onNodeWithText("second.mp4").performClick()
            waitForIdle()

            assertEquals("/media/second.mp4", vm.mediaUrl)
        }
    }
}
