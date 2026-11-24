@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.FocusLostBanner
import org.churchpresenter.sharedui.composables.FocusLostRescueState
import org.churchpresenter.sharedui.composables.GoLiveButton
import kotlin.test.Test

class TabControlsScreenshotTest {

    @Test
    fun `go live enabled`() = captureComponent(SECTION, "go_live_enabled") {
        GoLiveButton(onClick = {}, tooltipText = "Go Live")
    }

    @Test
    fun `go live disabled`() = captureComponent(SECTION, "go_live_disabled") {
        GoLiveButton(onClick = {}, tooltipText = "Go Live", enabled = false)
    }

    @Test
    fun `go live dimmed`() = captureComponent(SECTION, "go_live_dimmed") {
        GoLiveButton(onClick = {}, tooltipText = "Go Live", dimmed = true)
    }

    @Test
    fun `add to schedule enabled`() = captureComponent(SECTION, "add_to_schedule_enabled") {
        AddToScheduleButton(onClick = {}, tooltipText = "Add to Schedule")
    }

    @Test
    fun `add to schedule disabled`() = captureComponent(SECTION, "add_to_schedule_disabled") {
        AddToScheduleButton(onClick = {}, tooltipText = "Add to Schedule", enabled = false)
    }

    @Test
    fun `the action row as a tab draws it`() = captureComponent(SECTION, "action_row") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddToScheduleButton(onClick = {}, tooltipText = "Add to Schedule")
            GoLiveButton(onClick = {}, tooltipText = "Go Live")
        }
    }

    @Test
    fun `focus lost banner`() = captureComponent(SECTION, "focus_lost_banner") {
        Box(Modifier.width(640.dp)) {
            FocusLostBanner(
                state = rescueState(),
                text = "Keyboard shortcuts paused — click here to restore",
            )
        }
    }

    @Test
    fun `focus lost banner wrapped in a narrow panel`() =
        captureComponent(SECTION, "focus_lost_banner_narrow") {
            Box(Modifier.width(260.dp)) {
                FocusLostBanner(
                    state = rescueState(),
                    text = "Keyboard shortcuts paused — click here to restore",
                )
            }
        }

    @Test
    fun `focus lost banner hidden while the tab has focus`() =
        captureComponent(SECTION, "focus_lost_banner_hidden") {
            Box(Modifier.width(640.dp).height(56.dp)) {
                FocusLostBanner(
                    state = rescueState().apply { onFocusChanged(true) },
                    text = "Keyboard shortcuts paused — click here to restore",
                )
            }
        }

    private fun rescueState() = FocusLostRescueState(
        null,
        FocusRequester(),
        CoroutineScope(Dispatchers.Unconfined),
    )

    private companion object {
        const val SECTION = "tabControls"
    }
}
