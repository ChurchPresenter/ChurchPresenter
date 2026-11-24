@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.hosanna.composables.model.VolumeLabels
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeControlTest {

    private val labels = VolumeLabels("Volume", "Mute", "Unmute")

    @Test
    fun `mute goes to zero and unmute brings back the level`() = runComposeUiTest {
        var volume by mutableStateOf(65.0)
        setContent { HosannaSurface { VolumeControl(volume, { volume = it }, labels) } }
        onNodeWithText("65%").assertExists()
        onNodeWithContentDescription("Mute").performClick()
        waitForIdle()
        assertEquals(0.0, volume)
        onNodeWithText("0%").assertExists()
        onNodeWithContentDescription("Unmute").performClick()
        waitForIdle()
        assertEquals(65.0, volume)
    }

    @Test
    fun `unmuting a control that started silent goes to full`() = runComposeUiTest {
        var volume by mutableStateOf(0.0)
        setContent { HosannaSurface { VolumeControl(volume, { volume = it }, labels) } }
        onNodeWithContentDescription("Unmute").performClick()
        waitForIdle()
        assertEquals(100.0, volume)
    }

    @Test
    fun `a disabled control cannot be muted or moved`() = runComposeUiTest {
        var changes = 0
        setContent { HosannaSurface { VolumeControl(40.0, { changes++ }, labels, enabled = false) } }
        onNodeWithContentDescription("Mute").assertIsNotEnabled().performClick()
        onNodeWithContentDescription("Volume").assertIsNotEnabled()
        waitForIdle()
        assertEquals(0, changes)
    }
}
