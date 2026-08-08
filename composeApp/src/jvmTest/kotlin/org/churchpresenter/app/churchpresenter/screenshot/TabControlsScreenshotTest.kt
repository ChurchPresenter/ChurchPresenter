@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_refresh
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.FocusLostBanner
import org.churchpresenter.app.churchpresenter.composables.FocusLostRescueState
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
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
    fun `action icon enabled`() = captureComponent(SECTION, "action_icon_enabled") {
        ActionIconButton(onClick = {}, tooltipText = "Search", icon = Icons.Filled.Search)
    }

    @Test
    fun `action icon disabled`() = captureComponent(SECTION, "action_icon_disabled") {
        ActionIconButton(
            onClick = {},
            tooltipText = "Search",
            icon = Icons.Filled.Search,
            enabled = false,
        )
    }

    @Test
    fun `the action row as a tab draws it`() = captureComponent(SECTION, "action_row") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIconButton(
                onClick = {},
                tooltipText = "Search",
                icon = Icons.Filled.Search,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            AddToScheduleButton(onClick = {}, tooltipText = "Add to Schedule")
            GoLiveButton(onClick = {}, tooltipText = "Go Live")
        }
    }

    @Test
    fun `edit song button`() = captureComponent(SECTION, "edit_song_button") {
        ActionIconButton(
            onClick = {},
            tooltipText = "Edit Song",
            painter = painterResource(Res.drawable.ic_edit),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    }

    @Test
    fun `new song button`() = captureComponent(SECTION, "new_song_button") {
        ActionIconButton(
            onClick = {},
            tooltipText = "New Song",
            painter = painterResource(Res.drawable.ic_add),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    }

    @Test
    fun `the songs action row with a song selected`() = captureComponent(SECTION, "songs_action_row") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionIconButton(
                onClick = {},
                tooltipText = "Edit Song",
                painter = painterResource(Res.drawable.ic_edit),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
            ActionIconButton(
                onClick = {},
                tooltipText = "New Song",
                painter = painterResource(Res.drawable.ic_add),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
            AddToScheduleButton(onClick = {}, tooltipText = "Add to Schedule")
            GoLiveButton(onClick = {}, tooltipText = "Go Live")
        }
    }

    // ── TooltipIconButton ───────────────────────────────────────────────────────────────────────
    // The plain icon button behind a tooltip, used across the Web toolbar, the media transport and
    // the schedule row. Unlike ActionIconButton it carries no container of its own, so what there is
    // to see is the icon, its tint, and how the disabled state dims it.

    @Test
    fun `tooltip icon button`() = captureComponent(SECTION, "tooltip_icon") {
        TooltipIconButton(painter = painterResource(Res.drawable.ic_refresh), text = "Refresh", onClick = {})
    }

    @Test
    fun `tooltip icon button disabled`() = captureComponent(SECTION, "tooltip_icon_disabled") {
        TooltipIconButton(
            painter = painterResource(Res.drawable.ic_refresh),
            text = "Refresh",
            onClick = {},
            enabled = false,
        )
    }

    @Test
    fun `tooltip icon button tinted`() = captureComponent(SECTION, "tooltip_icon_tinted") {
        TooltipIconButton(
            painter = painterResource(Res.drawable.ic_refresh),
            text = "Refresh",
            onClick = {},
            iconTint = MaterialTheme.colorScheme.primary,
        )
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
