@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Song tab's transition-duration slider — the one control in that section with no field to type
 * into. It snaps to 50ms steps over a 100..2000 track.
 */
class SongSettingsTabTransitionSliderTest {

    private fun withDuration(ms: Float) =
        AppSettings(songSettings = SongSettings(transitionDuration = ms))

    private val caption = "Transition Duration"

    @Test
    fun `the slider shows the stored duration`() = songTab(withDuration(500f)) { _ ->
        onNodeWithText("500ms").assertExists("the readout is the only place the value is written")
    }

    @Test
    fun `dropping the handle at the far end stores nearly the longest transition`() =
        songTab(withDuration(500f)) { get ->
            tapSliderTrack(caption, "500ms", fraction = 1f)
            // Not exactly 2000: the track's right edge cannot be tapped, so the last reachable stop
            // is one pixel short of it. What matters is that the handle went all the way over.
            assertTrue(
                get().songSettings.transitionDuration >= 1900f,
                "the far end of the track must store a near-maximum: ${get().songSettings.transitionDuration}",
            )
        }

    @Test
    fun `dropping the handle at the near end stores the shortest transition`() = songTab(withDuration(1500f)) { get ->
        tapSliderTrack(caption, "1500ms", fraction = 0f)
        assertEquals(100f, get().songSettings.transitionDuration)
    }

    @Test
    fun `the readout follows the handle`() = songTab(withDuration(500f)) { _ ->
        tapSliderTrack(caption, "500ms", fraction = 0.5f)
        onNodeWithText("1050ms").assertExists("the readout must show what was stored")
    }

    @Test
    fun `halfway along the track is halfway through the range, snapped to a stop`() =
        songTab(withDuration(500f)) { get ->
            tapSliderTrack(caption, "500ms", fraction = 0.5f)
            val stored = get().songSettings.transitionDuration
            assertEquals(1050f, stored, "halfway along a 100..2000 track, on the 50ms grid")
            assertEquals(0f, stored % 50f, "every stop is a multiple of 50ms")
        }

    @Test
    fun `the slider writes nothing but the duration`() = songTab(withDuration(500f)) { get ->
        val before = get().songSettings
        tapSliderTrack(caption, "500ms", fraction = 0.5f)
        assertEquals(
            before.copy(transitionDuration = 1050f),
            get().songSettings,
            "no other song setting may move with it",
        )
    }
}
