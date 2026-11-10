package org.churchpresenter.liveoutput.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.ScreenHold
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.lock_screen_to_tab
import org.churchpresenter.strings.generated.resources.screen_blank
import org.churchpresenter.strings.generated.resources.screen_hold
import org.churchpresenter.strings.generated.resources.screen_hold_all
import org.churchpresenter.strings.generated.resources.screen_hold_menu
import org.churchpresenter.strings.generated.resources.screen_release
import org.churchpresenter.strings.generated.resources.screen_release_all
import org.churchpresenter.strings.generated.resources.unlock_screen
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.stringResource

/** The hold menu for screen [screenIndex]. */
@Composable
internal fun screenHoldMenu(presenterManager: PresenterManager, screenIndex: Int): ScreenHoldMenu {
    val holds by presenterManager.screenHolds
    return ScreenHoldMenu(
        hold = holds[screenIndex],
        anyHeld = holds.isNotEmpty(),
        onHold = { presenterManager.setScreenHold(screenIndex, it) },
        onHoldAll = presenterManager::holdAllScreens,
    )
}

/**
 * What a screen tile's menu does about holding its output: how it is held now, and the calls that
 * hold or release it, or every screen at once. Only a screen's tile gets one.
 */
internal class ScreenHoldMenu(
    val hold: ScreenHold?,
    val anyHeld: Boolean,
    val onHold: (ScreenHold?) -> Unit,
    val onHoldAll: (ScreenHold?) -> Unit,
)

/**
 * The corner button of a screen tile and the menu it opens: hold, blank, lock to the tab, and the same
 * for every screen. It stands where the plain lock toggle stands on the other outputs.
 */
@Composable
internal fun ScreenHoldMenuButton(
    menu: ScreenHoldMenu,
    lockedMode: Presenting?,
    effectiveMode: Presenting,
    onToggleLock: (Presenting?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val active = menu.hold != null || lockedMode != null
    Box(modifier = modifier) {
        KeyIconButton(
            onClick = { open = true },
            modifier = Modifier.size(24.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (active) Color(LOCK_BADGE_COLOR) else Color.White.copy(alpha = 0.5f),
            ),
        ) {
            Icon(
                imageVector = when {
                    menu.hold == ScreenHold.HOLD -> Icons.Filled.Pause
                    menu.hold == ScreenHold.BLANK -> Icons.Filled.VisibilityOff
                    lockedMode != null -> Icons.Filled.Lock
                    else -> Icons.Filled.LockOpen
                },
                contentDescription = stringResource(Res.string.screen_hold_menu),
                modifier = Modifier.size(13.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: () -> Unit) {
                open = false
                action()
            }
            if (menu.hold == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.screen_hold)) },
                    onClick = { pick { menu.onHold(ScreenHold.HOLD) } },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.screen_blank)) },
                    onClick = { pick { menu.onHold(ScreenHold.BLANK) } },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.screen_release)) },
                    onClick = { pick { menu.onHold(null) } },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (lockedMode != null) Res.string.unlock_screen else Res.string.lock_screen_to_tab,
                        ),
                    )
                },
                onClick = { pick { onToggleLock(if (lockedMode != null) null else effectiveMode) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.screen_hold_all)) },
                onClick = { pick { menu.onHoldAll(ScreenHold.HOLD) } },
            )
            if (menu.anyHeld) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.screen_release_all)) },
                    onClick = { pick { menu.onHoldAll(null) } },
                )
            }
        }
    }
}
