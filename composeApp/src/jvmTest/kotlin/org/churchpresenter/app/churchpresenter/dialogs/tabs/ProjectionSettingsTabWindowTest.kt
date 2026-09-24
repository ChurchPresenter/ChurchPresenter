@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.composables.VlcAudioDevice
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Projection tab's last two cards: where a presenter window sits, and what it plays audio on.
 *
 * The four inset fields are one control repeated, so what is worth pinning is which of the four
 * each writes. A pair crossed over here moves the window the wrong way by exactly the amount asked
 * for, which reads as the setting being ignored rather than as its being wired backwards.
 *
 * The audio card is driven through `vlcProbe`/`audioDeviceProbe` rather than against whatever the
 * machine happens to have installed. An earlier pair of tests here read the host instead, passed on
 * a developer's machine and failed on CI, and had to be deleted; those two parameters are what
 * replaced that.
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

    // ── The audio card ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a machine without VLC is told so instead of being offered a dropdown`() {
        projectionTab(insets(), vlcInstalled = false) { _ ->
            onNodeWithText("VLC media player is required for media playback").assertExists()
            onAllNodesWithText("Output device").assertCountEquals(0)
        }
    }

    @Test
    fun `VLC's devices are listed alongside the system default`() {
        projectionTab(
            insets(),
            audioDevices = listOf(
                VlcAudioDevice("alsa:hw:0,0", "Built-in Output"),
                VlcAudioDevice("alsa:hw:2,0", "Focusrite Scarlett"),
            ),
        ) { _ ->
            openDeviceMenu()

            onNodeWithText("Built-in Output").assertExists()
            onNodeWithText("Focusrite Scarlett").assertExists()
            onAllNodesWithText("System Default").assertCountEquals(2) // the closed button, and the menu item
        }
    }

    @Test
    fun `picking a device stores its id, not its name`() {
        // The name is VLC's and changes between versions and machines; the id is what the player is
        // actually opened with.
        projectionTab(
            insets(),
            audioDevices = listOf(VlcAudioDevice("alsa:hw:2,0", "Focusrite Scarlett")),
        ) { get ->
            openDeviceMenu()
            onNodeWithText("Focusrite Scarlett").performClick()
            waitForIdle()

            assertEquals("alsa:hw:2,0", get().projectionSettings.audioOutputDeviceId)
        }
    }

    @Test
    fun `picking the system default clears a stored device id`() {
        // The branch that matters most: an id left behind by a machine that no longer has that
        // device silences the output entirely, and this is the only way back.
        val withDevice = insets().let {
            it.copy(projectionSettings = it.projectionSettings.copy(audioOutputDeviceId = "alsa:hw:2,0"))
        }
        projectionTab(withDevice, audioDevices = listOf(VlcAudioDevice("alsa:hw:2,0", "Focusrite Scarlett"))) { get ->
            openDeviceMenu()
            // The closed dropdown reads the device's name, so "System Default" here is unambiguous.
            onNodeWithText("System Default").performClick()
            waitForIdle()

            assertEquals("", get().projectionSettings.audioOutputDeviceId)
        }
    }

    /** Opens the Output Device dropdown, which is the button beside that label. */
    private fun ComposeUiTest.openDeviceMenu() {
        onNode(hasTestTag(AUDIO_DEVICE_BUTTON_TAG)).performScrollTo().performClick()
        waitForIdle()
    }
}
