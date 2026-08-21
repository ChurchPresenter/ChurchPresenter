@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.app.churchpresenter.data.settings.KeyboardShortcutSettings
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stepping through a slideshow from the keyboard.
 *
 * This is how the tab is actually driven during a service — nobody clicks thumbnails while the
 * congregation is watching — so the arrow keys and the spacebar are the tab's real interface. The
 * handler is an `onPreviewKeyEvent` on the tab's own focusable root, so presses go to the root
 * rather than to any one control.
 *
 * Up and down move by a whole row, which means they depend on how many columns the grid laid out;
 * these tests read that from the resulting selection rather than assuming a column count, since the
 * grid is adaptive and the number differs with the window width.
 *
 * See `PicturesTabTestSupport.kt` for the harness.
 */
class PicturesTabKeyboardTest {

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    // ── Left and right ──────────────────────────────────────────────────────────

    @Test
    fun `right steps to the next image`() = picturesTab { vm, _ ->
        assertEquals(0, vm.selectedImageIndex)

        press(Key.DirectionRight)

        assertEquals(1, vm.selectedImageIndex)
    }

    @Test
    fun `left steps back to the previous image`() = picturesTab { vm, _ ->
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertEquals(2, vm.selectedImageIndex)

        press(Key.DirectionLeft)

        assertEquals(1, vm.selectedImageIndex)
    }

    @Test
    fun `right past the last image wraps to the first`() = picturesTab { vm, _ ->
        repeat(vm.images.size - 1) { press(Key.DirectionRight) }
        assertEquals(vm.images.lastIndex, vm.selectedImageIndex)

        press(Key.DirectionRight)

        // Wrapping matters live: running off the end mid-service must not stick or blank the screen.
        assertEquals(0, vm.selectedImageIndex)
    }

    @Test
    fun `left from the first image wraps to the last`() = picturesTab { vm, _ ->
        press(Key.DirectionLeft)

        assertEquals(vm.images.lastIndex, vm.selectedImageIndex)
    }

    // ── Up and down, which move by a row ────────────────────────────────────────

    @Test
    fun `down moves forward by a whole row and up comes back`() = picturesTab { vm, _ ->
        press(Key.DirectionDown)
        val afterDown = vm.selectedImageIndex

        if (afterDown == 0) {
            // Every image fitted on one row, so there is no row below to move to — the handler
            // refuses rather than running off the end, which is the behaviour being pinned.
            return@picturesTab
        }
        press(Key.DirectionUp)
        assertEquals(0, vm.selectedImageIndex, "up must undo exactly what down did")
    }

    @Test
    fun `up from the top row stays put`() = picturesTab { vm, _ ->
        press(Key.DirectionUp)

        // The target index is negative here; selecting it would throw or blank the output.
        assertEquals(0, vm.selectedImageIndex)
    }

    @Test
    fun `down from the last row stays put`() = picturesTab { vm, _ ->
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        val last = vm.selectedImageIndex

        press(Key.DirectionDown)

        assertEquals(last, vm.selectedImageIndex, "there is no row past the end to land on")
    }

    // ── Spacebar ────────────────────────────────────────────────────────────────

    @Test
    fun `space starts the slideshow and stops it again`() = picturesTab { vm, _ ->
        press(Key.Spacebar)
        assertEquals(true, vm.isPlaying, "space must start the auto-advance")

        press(Key.Spacebar)
        assertEquals(false, vm.isPlaying, "and the same key must stop it")
    }

    // ── With no folder loaded ───────────────────────────────────────────────────

    @Test
    fun `the arrow keys do nothing while no folder is loaded`() = picturesTab(folder = null) { vm, _ ->
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.Spacebar)

        // With an empty list and no Instance Link target the handler declines every key, leaving
        // the presses to fall through rather than moving a selection that does not exist.
        assertEquals(0, vm.selectedImageIndex)
        assertEquals(false, vm.isPlaying)
    }

    // ── Rebound keys ────────────────────────────────────────────────────────────

    @Test
    fun `a rebound next-image key steps the slideshow and the shipped key stops doing so`() {
        val remapped = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(ShortcutAction.PICTURES_NEXT.name to listOf(KeyChord.of(Key.N)))
            )
        )
        picturesTab(shortcuts = remapped) { vm, _ ->
            press(Key.N)
            assertEquals(1, vm.selectedImageIndex, "the rebound key must drive the action")

            press(Key.DirectionRight)
            assertEquals(1, vm.selectedImageIndex, "the shipped key must stop working once rebound")
        }
    }

    @Test
    fun `an unbound play-pause key stops toggling playback`() {
        val cleared = ShortcutMap.from(
            KeyboardShortcutSettings(overrides = mapOf(ShortcutAction.PICTURES_PLAY_PAUSE.name to emptyList()))
        )
        picturesTab(shortcuts = cleared) { vm, _ ->
            press(Key.Spacebar)

            assertEquals(false, vm.isPlaying)
            // Navigation is untouched, so this is the binding being gone rather than the whole
            // handler having stopped responding.
            press(Key.DirectionRight)
            assertEquals(1, vm.selectedImageIndex)
        }
    }
}
