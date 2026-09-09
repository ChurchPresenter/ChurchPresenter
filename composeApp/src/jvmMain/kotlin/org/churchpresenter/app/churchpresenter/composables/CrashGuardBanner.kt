package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.crash_video_bg_banner
import churchpresenter.composeapp.generated.resources.crash_video_bg_dismiss
import churchpresenter.composeapp.generated.resources.crash_video_bg_reenable
import org.churchpresenter.diagnostics.CrashReporter
import org.jetbrains.compose.resources.stringResource

private val BANNER_HORIZONTAL_PADDING = 16.dp
private val BANNER_VERTICAL_PADDING = 8.dp
private val BANNER_SPACING = 8.dp
private val BANNER_ELEVATION = 3.dp

/**
 * The strip that says video backgrounds were turned off by the crash-loop guard, and offers to turn
 * them back on again.
 *
 * The guard trips on its own after repeated crashes, so without this the only route back is editing
 * a file in `~/.churchpresenter`. [CrashReporter.reEnableVideoBackgrounds] existed for exactly this
 * and had no caller at all: the previous version of this banner spelled `[Re-enable]` and
 * `[Dismiss]` as literal characters inside a `Text`, with no `Button` and no `onClick` behind
 * either, and hid itself on a timer after fifteen seconds.
 *
 * The state arrives as parameters, defaulted to the reporter's own, so a test can pin it — the
 * banner is otherwise only reachable by crashing the app twice.
 *
 * Draw it **last** among its siblings. It is a top-aligned strip rather than the full-screen cover
 * it used to be, but it still has to be emitted after the content it sits over.
 */
@Composable
fun CrashGuardBanner(
    modifier: Modifier = Modifier,
    crashedLastRun: Boolean = CrashReporter.didCrashLastRun,
    videoBackgroundsDisabled: Boolean = CrashReporter.videoBackgroundsDisabled,
    consecutiveCrashes: Int = CrashReporter.consecutiveCrashes,
    onReEnable: () -> Unit = { CrashReporter.reEnableVideoBackgrounds() },
) {
    if (!crashedLastRun || !videoBackgroundsDisabled) return

    var showBanner by remember { mutableStateOf(true) }
    if (!showBanner) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = BANNER_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = BANNER_HORIZONTAL_PADDING,
                vertical = BANNER_VERTICAL_PADDING,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BANNER_SPACING),
        ) {
            Text(
                text = stringResource(Res.string.crash_video_bg_banner, consecutiveCrashes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onReEnable()
                    showBanner = false
                },
            ) {
                Text(stringResource(Res.string.crash_video_bg_reenable))
            }
            TextButton(onClick = { showBanner = false }) {
                Text(stringResource(Res.string.crash_video_bg_dismiss))
            }
        }
    }
}
