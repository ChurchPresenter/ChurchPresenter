package org.churchpresenter.helper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.display.DisplayStep
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.display.assignPickedScreen
import org.churchpresenter.helper.display.identifyAgain
import org.churchpresenter.helper.display.screenLabel
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.resolve
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_back
import org.churchpresenter.strings.generated.resources.helper_cancel
import org.churchpresenter.strings.generated.resources.helper_confirm_assign
import org.churchpresenter.strings.generated.resources.helper_display_done
import org.churchpresenter.strings.generated.resources.helper_display_in_use
import org.churchpresenter.strings.generated.resources.helper_display_one_screen
import org.churchpresenter.strings.generated.resources.helper_display_pick
import org.churchpresenter.strings.generated.resources.helper_display_show_numbers
import org.churchpresenter.strings.generated.resources.helper_display_test
import org.churchpresenter.strings.generated.resources.helper_display_test_no
import org.churchpresenter.strings.generated.resources.helper_display_test_yes
import org.churchpresenter.strings.generated.resources.helper_do_it
import org.churchpresenter.strings.generated.resources.helper_ok
import org.churchpresenter.strings.generated.resources.helper_open_projection
import org.churchpresenter.strings.generated.resources.helper_display_title
import org.jetbrains.compose.resources.stringResource

/**
 * Display setup, step by step inside the bubble: find the screens, pick the one the congregation
 * sees, put it to work, and check the number shows up on it.
 */
@Composable
internal fun DisplaySetupPanel(state: HelperState, screens: List<HelperScreen>, executor: HelperActionExecutor) {
    LaunchedEffect(screens) { state.displayFlow = state.displayFlow.detected(screens) }
    val flow = state.displayFlow
    BubbleHeading(stringResource(Res.string.helper_display_title), Icons.Filled.Tv)
    when (flow.step) {
        DisplayStep.DETECT -> {
            Said(helperText(Res.string.helper_display_one_screen))
            Actions(
                Res.string.helper_cancel to state::reset,
                primary = Res.string.helper_open_projection to {
                    state.request(HelperAction.OpenSettings(SettingsPage.PROJECTION), executor)
                },
            )
        }
        DisplayStep.PICK -> {
            Said(helperText(Res.string.helper_display_pick))
            screens.filter { !it.isPrimary }.forEach { screen ->
                ScreenCard(screen, onClick = { state.displayFlow = flow.picking(screen) })
            }
            Actions(Res.string.helper_cancel to state::reset)
        }
        DisplayStep.CONFIRM -> {
            val picked = flow.picked ?: return
            Said(helperText(Res.string.helper_confirm_assign, picked.screenLabel()), Modifier.testTag("helper.confirm"))
            Actions(
                Res.string.helper_back to { state.displayFlow = flow.retry() },
                primary = Res.string.helper_do_it to { state.assignPickedScreen(executor) },
            )
        }
        DisplayStep.TEST -> {
            val picked = flow.picked ?: return
            Said(helperText(Res.string.helper_display_test, picked.screenLabel()))
            Actions(
                Res.string.helper_display_show_numbers to { state.identifyAgain(executor) },
                Res.string.helper_display_test_no to { state.displayFlow = flow.retry() },
                primary = Res.string.helper_display_test_yes to { state.displayFlow = flow.confirmed() },
            )
        }
        DisplayStep.DONE -> {
            Said(helperText(Res.string.helper_display_done))
            Actions(primary = Res.string.helper_ok to state::reset)
        }
    }
}

@Composable
private fun ScreenCard(screen: HelperScreen, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("helper.screen.${screen.index}"),
        border = if (screen.isAudience) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Tv, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(screen.screenLabel().resolve(), style = MaterialTheme.typography.bodyMedium)
                if (screen.isAudience) {
                    Text(
                        stringResource(Res.string.helper_display_in_use),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
