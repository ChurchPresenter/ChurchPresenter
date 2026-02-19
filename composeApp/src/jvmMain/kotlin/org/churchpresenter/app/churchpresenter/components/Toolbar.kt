package org.churchpresenter.app.churchpresenter.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_down_double
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_arrow_up_double
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.ic_settings
import churchpresenter.composeapp.generated.resources.tooltip_add_to_schedule
import churchpresenter.composeapp.generated.resources.tooltip_clear_schedule
import churchpresenter.composeapp.generated.resources.tooltip_move_down
import churchpresenter.composeapp.generated.resources.tooltip_move_to_bottom
import churchpresenter.composeapp.generated.resources.tooltip_move_to_top
import churchpresenter.composeapp.generated.resources.tooltip_move_up
import churchpresenter.composeapp.generated.resources.tooltip_new_schedule
import churchpresenter.composeapp.generated.resources.tooltip_open_schedule
import churchpresenter.composeapp.generated.resources.tooltip_remove_from_schedule
import churchpresenter.composeapp.generated.resources.tooltip_save_schedule
import churchpresenter.composeapp.generated.resources.tooltip_settings
import org.churchpresenter.app.churchpresenter.composables.ThemeSegmentedButton
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {},
    onNewSchedule: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onSaveSchedule: () -> Unit = {},
    onMoveToTop: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onMoveToBottom: () -> Unit = {},
    onAddToSchedule: () -> Unit = {},
    onRemoveFromSchedule: () -> Unit = {},
    onClearSchedule: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Schedule file operations
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_add),
                text = stringResource(Res.string.tooltip_new_schedule),
                onClick = onNewSchedule,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_folder),
                text = stringResource(Res.string.tooltip_open_schedule),
                onClick = onOpenSchedule,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_save),
                text = stringResource(Res.string.tooltip_save_schedule),
                onClick = onSaveSchedule,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(4.dp))
            ToolbarDivider()
            Spacer(modifier = Modifier.width(4.dp))

            // Move operations
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_arrow_up_double),
                text = stringResource(Res.string.tooltip_move_to_top),
                onClick = onMoveToTop,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_arrow_up),
                text = stringResource(Res.string.tooltip_move_up),
                onClick = onMoveUp,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_arrow_down),
                text = stringResource(Res.string.tooltip_move_down),
                onClick = onMoveDown,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_arrow_down_double),
                text = stringResource(Res.string.tooltip_move_to_bottom),
                onClick = onMoveToBottom,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(4.dp))
            ToolbarDivider()
            Spacer(modifier = Modifier.width(4.dp))

            // Add/Remove operations
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_playlist_add),
                text = stringResource(Res.string.tooltip_add_to_schedule),
                onClick = onAddToSchedule,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_close),
                text = stringResource(Res.string.tooltip_remove_from_schedule),
                onClick = onRemoveFromSchedule,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_delete),
                text = stringResource(Res.string.tooltip_clear_schedule),
                onClick = onClearSchedule,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))


            // Settings
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_settings),
                text = stringResource(Res.string.tooltip_settings),
                onClick = onOpenSettings,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Theme switcher
            ThemeSegmentedButton(
                selectedTheme = currentTheme,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}


