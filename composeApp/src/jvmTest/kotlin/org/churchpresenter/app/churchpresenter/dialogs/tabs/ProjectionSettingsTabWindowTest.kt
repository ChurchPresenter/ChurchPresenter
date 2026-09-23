@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Projection tab's last two cards: where a presenter window sits, and what it plays audio on.
 *
 * The four inset fields are one control repeated, so the thing worth pinning is which of the four
 * each writes -- they are laid out as a picture of a screen with a field on each side, and a pair
 * crossed over there moves the window the wrong way by exactly the amount asked for, which looks
 * like the setting being ignored rather than like it being wired backwards.
 *
 * The audio dropdown is asserted only as far as it goes headless: VLC supplies the device list, so
 * without it the menu holds nothing but the system default. Picking that is the branch that clears
 * a stored device id, which is worth having either way.
 */
class ProjectionSettingsTabWindowTest {

    private fun insets() = AppSettings(
        projectionSettings = ProjectionSettings(
            // Four distinct values, none of them the 32 they all default to: each field is found by
            // the number it shows, so a shared value would make them indistinguishable.
            windowTop = 11,
            windowLeft = 22,
            windowRight = 33,
            windowBottom = 44,
            outputProfiles = withProfiles().projectionSettings.outputProfiles,
        ),
    )

    @Test
    fun `the four inset fields each write their own edge`() {
        projectionTab(insets()) { get ->
            retypeNumberField(11, 100)
            assertEquals(100, get().projectionSettings.windowTop)

            retypeNumberField(22, 200)
            assertEquals(200, get().projectionSettings.windowLeft)

            retypeNumberField(33, 300)
            assertEquals(300, get().projectionSettings.windowRight)

            retypeNumberField(44, 400)
            assertEquals(400, get().projectionSettings.windowBottom)

            // And all four together, to catch a later field having overwritten an earlier one.
            val proj = get().projectionSettings
            assertEquals(
                listOf(100, 200, 300, 400),
                listOf(proj.windowTop, proj.windowLeft, proj.windowRight, proj.windowBottom),
            )
        }
    }

    @Test
    fun `an inset the operator has not touched keeps its stored value`() {
        projectionTab(insets()) { get ->
            retypeNumberField(11, 99)

            val proj = get().projectionSettings
            assertEquals(22, proj.windowLeft)
            assertEquals(33, proj.windowRight)
            assertEquals(44, proj.windowBottom)
        }
    }

    @Test
    fun `the audio card offers the system default`() {
        projectionTab(insets()) { _ ->
            assertTrue(
                onAllNodesWithText("System Default").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
                "with no VLC device list the default is the whole menu, and it must still be offered",
            )
        }
    }

    @Test
    fun `picking the system default clears a stored device id`() {
        // The one audio branch reachable without VLC, and the one that matters most: a device id
        // left behind by a machine that no longer has that device silences the output entirely.
        val withDevice = insets().let {
            it.copy(projectionSettings = it.projectionSettings.copy(audioOutputDeviceId = "alsa:hw:2,0"))
        }
        projectionTab(withDevice) { get ->
            onAllNodesWithText("System Default")[0].performClick()
            waitForIdle()
            // The closed dropdown already reads "System Default" -- a stored id that matches no
            // device falls back to the default label -- so the open menu's item is the one of the
            // two that is not the button.
            onNode(
                hasClickAction() and hasTextExactly("System Default") and
                    !SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).performClick()
            waitForIdle()

            assertEquals("", get().projectionSettings.audioOutputDeviceId)
        }
    }
}
