@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Putting a media row from the schedule on screen.
 *
 * The tab blanks the output while a newly selected file loads, so the projector does not sit on the
 * last frame of whatever was playing. That blank has to be undone once the file is in, and it was
 * not: `setPresentingMode` is the only thing that resets the clear flag, and pressing play on a
 * schedule row sets the mode in the same handler that sets the item — so by the time this tab's
 * effect ran, the mode was already MEDIA, the effect read that as "something is already live",
 * blanked, and nothing ever turned it back on.
 *
 * The result was a black screen and no playback, while lyrics from the same schedule worked; it was
 * reported from Windows but nothing about it is platform-specific. Issue #602.
 */
class MediaTabScheduleLiveTest {

    private fun clip(id: String = "m1", url: String = "/media/clip.mp4") = ScheduleItem.MediaItem(
        id = id,
        mediaUrl = url,
        mediaTitle = "Clip",
        mediaType = Constants.MEDIA_TYPE_LOCAL,
    )

    @Test
    fun `presenting a media row leaves the output showing, not blanked`() {
        val presenter = PresenterManager()
        // What the schedule's play button does: go live, and hand the tab the row — in that order,
        // in one handler, which is the whole cause.
        presenter.setPresentingMode(Presenting.MEDIA)

        mediaTab(selectedMediaItem = clip(), presenterManager = presenter) { vm, _ ->
            waitUntil { vm.isLoaded }

            assertFalse(
                presenter.clearDisplayRequested.value,
                "the output was blanked to load the file and must be showing again afterwards",
            )
            assertEquals(Presenting.MEDIA, presenter.presentingMode.value)
        }
    }

    @Test
    fun `the row's own file is what gets loaded`() {
        val presenter = PresenterManager()
        presenter.setPresentingMode(Presenting.MEDIA)

        mediaTab(selectedMediaItem = clip(url = "/media/sermon.mp4"), presenterManager = presenter) { vm, _ ->
            waitUntil { vm.isLoaded }

            assertEquals("/media/sermon.mp4", vm.mediaUrl)
            assertEquals("Clip", vm.mediaTitle)
        }
    }

    @Test
    fun `a row handed to a tab that is not live does not switch the output on`() {
        // Selecting a row without going live is a different gesture, and it must stay that way:
        // the fix re-asserts the mode only where it blanked it.
        val presenter = PresenterManager()

        mediaTab(selectedMediaItem = clip(), presenterManager = presenter) { vm, _ ->
            waitUntil { vm.isLoaded }

            assertEquals(Presenting.NONE, presenter.presentingMode.value, "loading is not going live")
            assertFalse(presenter.clearDisplayRequested.value)
        }
    }
}
