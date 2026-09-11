@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The three fade boxes, on the Song tab and on the Customize dialog's strip. Fade Out was the one
 * neither suite ever clicked, which is why its handler had never run in either place.
 */
class FadeTogglesTest {

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(transitionDuration = 555f),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.stored(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    // ── The Song tab ──────────────────────────────────────────────────────────

    @Test
    fun `Fade In toggles only its own flag`() = songTab { get ->
        val before = get().songSettings
        onNodeWithText("Fade In").performClick()
        waitForIdle()
        assertEquals(before.copy(fadeIn = !before.fadeIn), get().songSettings)
    }

    @Test
    fun `Fade Out toggles only its own flag`() = songTab { get ->
        val before = get().songSettings
        onNodeWithText("Fade Out").performClick()
        waitForIdle()
        assertEquals(before.copy(fadeOut = !before.fadeOut), get().songSettings)
    }

    @Test
    fun `the three fade boxes are independent of each other`() = songTab { get ->
        val before = get().songSettings
        for (label in listOf("Fade In", "Fade Out", "Crossfade")) {
            onNodeWithText(label).performClick()
            waitForIdle()
        }
        val after = get().songSettings
        assertEquals(
            before.copy(fadeIn = !before.fadeIn, fadeOut = !before.fadeOut, crossfade = !before.crossfade),
            after,
            "each box must have moved its own flag and no other",
        )
    }

    @Test
    fun `a fade turned off goes back on`() = songTab { get ->
        onNodeWithText("Fade Out").performClick()
        waitForIdle()
        val once = get().songSettings.fadeOut
        onNodeWithText("Fade Out").performClick()
        waitForIdle()
        assertEquals(!once, get().songSettings.fadeOut)
    }

    @Test
    fun `toggling a fade leaves the transition duration alone`() = songTab { get ->
        val before = get().songSettings.transitionDuration
        onNodeWithText("Fade Out").performClick()
        waitForIdle()
        assertEquals(before, get().songSettings.transitionDuration)
    }

    // ── The Customize strip ───────────────────────────────────────────────────

    @Test
    fun `the strip's Fade Out writes the output's own flag`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            toggleCheckbox("Fade Out", scroll = false)

            val stored = get().stored()
            assertFalse(stored.fadeOut, "it ships on and must have gone off")
            assertTrue(stored.fadeIn, "the box above it must not move")
            assertFalse(stored.crossfade, "nor the one below")
        }
    }

    @Test
    fun `the strip's Fade Out writes a band's flag too`() {
        projectionTab(output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            toggleCheckbox("Fade Out", scroll = false)

            assertFalse(get().stored().fadeOut)
        }
    }

    @Test
    fun `the strip's three fades are independent`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            for (label in listOf("Fade In", "Fade Out", "Crossfade")) {
                toggleCheckbox(label, scroll = false)
            }

            val stored = get().stored()
            assertFalse(stored.fadeIn, "both fades shipped on and must have gone off")
            assertFalse(stored.fadeOut)
            assertTrue(stored.crossfade, "and the crossfade shipped off and must have come on")
        }
    }

    @Test
    fun `the strip's fades leave the duration alone`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            toggleCheckbox("Fade Out", scroll = false)

            assertEquals(555f, get().stored().transitionDuration)
        }
    }

    @Test
    fun `the Bible strip carries the same three fades`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithText("Fade In").assertExists()
            onNodeWithText("Fade Out").assertExists()
            onNodeWithText("Crossfade").assertExists()
        }
    }
}
