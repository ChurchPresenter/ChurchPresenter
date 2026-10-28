package org.churchpresenter.signage.tab

import org.churchpresenter.theme.components.KeyButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_update_checking
import org.churchpresenter.strings.generated.resources.signage_update_downloading
import org.churchpresenter.strings.generated.resources.signage_update_failed
import org.churchpresenter.strings.generated.resources.signage_update_installing
import org.churchpresenter.strings.generated.resources.signage_update_restarting
import org.churchpresenter.strings.generated.resources.signage_update_rolled_back
import org.churchpresenter.strings.generated.resources.signage_update_system
import org.churchpresenter.strings.generated.resources.signage_update_to
import org.churchpresenter.strings.generated.resources.signage_updating
import org.churchpresenter.strings.generated.resources.signage_updating_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How an update of this screen is going, or why the last one failed or was rolled back; nothing when
 * none is under way. A screen that finished updating reconnects, and the line goes. The button that
 * starts one is [UpdateButton].
 */
@Composable
internal fun UpdateLine(row: ScreenRowState) {
    val progress = row.link?.update
    val problem = problemOf(progress)
    if (progress != null && problem != null) {
        Text(
            stringResource(problem, progress.version, progress.message.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (progress != null && problem == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            val text = if (progress.message == SYSTEM_STEP) {
                stringResource(Res.string.signage_updating_system)
            } else {
                val step = listOfNotNull(
                    stringResource(stepName(progress.state)),
                    progress.message?.takeIf { it.isNotBlank() },
                )
                stringResource(Res.string.signage_updating, progress.version, step.joinToString(" "))
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The button that updates the screen while no update is under way: Update to a newer player when one
 * is offered (the machine is updated first), or Update for the machine alone (Chromium, mpv and
 * every package) on a screen that can. Offered again after a failure or a rollback.
 */
@Composable
internal fun UpdateButton(row: ScreenRowState, onUpdate: () -> Unit) {
    val progress = row.link?.update
    if (progress != null && problemOf(progress) == null) return
    val offer = row.offer
    when {
        offer != null -> KeyButton(onClick = onUpdate) {
            Text(stringResource(Res.string.signage_update_to, offer.release.version), maxLines = 1, softWrap = false)
        }
        row.link?.features?.contains(Features.SYSTEM_UPDATE) == true -> KeyButton(onClick = onUpdate) {
            Text(stringResource(Res.string.signage_update_system), maxLines = 1, softWrap = false)
        }
    }
}

/** What went wrong with [progress], if it failed or was rolled back. */
private fun problemOf(progress: PlayerMessage.UpdateProgress?): StringResource? = when (progress?.state) {
    UpdateState.FAILED -> Res.string.signage_update_failed
    UpdateState.ROLLED_BACK -> Res.string.signage_update_rolled_back
    else -> null
}

/** What a player says as its step while its machine updates (the player's `Updater.SYSTEM_STEP`). */
private const val SYSTEM_STEP = "system"

private fun stepName(state: UpdateState): StringResource = when (state) {
    UpdateState.DOWNLOADING -> Res.string.signage_update_downloading
    UpdateState.CHECKING -> Res.string.signage_update_checking
    UpdateState.INSTALLING -> Res.string.signage_update_installing
    UpdateState.RESTARTING, UpdateState.FAILED, UpdateState.ROLLED_BACK -> Res.string.signage_update_restarting
}
