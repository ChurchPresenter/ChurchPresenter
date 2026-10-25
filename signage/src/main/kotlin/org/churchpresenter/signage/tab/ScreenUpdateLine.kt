package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_update_checking
import org.churchpresenter.strings.generated.resources.signage_update_downloading
import org.churchpresenter.strings.generated.resources.signage_update_failed
import org.churchpresenter.strings.generated.resources.signage_update_installing
import org.churchpresenter.strings.generated.resources.signage_update_restarting
import org.churchpresenter.strings.generated.resources.signage_update_rolled_back
import org.churchpresenter.strings.generated.resources.signage_update_to
import org.churchpresenter.strings.generated.resources.signage_updating
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How an update of this screen is going, or the button that starts one when a newer player is
 * offered; a failure or a rollback is said above the button. A screen that finished updating
 * reconnects as the new version, and the line goes.
 */
@Composable
internal fun UpdateLine(row: ScreenRowState, onUpdate: () -> Unit) {
    val progress = row.link?.update
    val offer = row.offer
    val problem = when (progress?.state) {
        UpdateState.FAILED -> Res.string.signage_update_failed
        UpdateState.ROLLED_BACK -> Res.string.signage_update_rolled_back
        else -> null
    }
    if (progress != null && problem != null) {
        Text(
            stringResource(problem, progress.version, progress.message.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    when {
        progress != null && problem == null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            val step = listOfNotNull(
                stringResource(stepName(progress.state)),
                progress.message?.takeIf { it.isNotBlank() },
            )
            Text(
                stringResource(Res.string.signage_updating, progress.version, step.joinToString(" ")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Offered again after a failure or a rollback, so it can be tried once more.
        offer != null -> OutlinedButton(onClick = onUpdate) {
            Text(stringResource(Res.string.signage_update_to, offer.release.version))
        }
    }
}

private fun stepName(state: UpdateState): StringResource = when (state) {
    UpdateState.DOWNLOADING -> Res.string.signage_update_downloading
    UpdateState.CHECKING -> Res.string.signage_update_checking
    UpdateState.INSTALLING -> Res.string.signage_update_installing
    UpdateState.RESTARTING, UpdateState.FAILED, UpdateState.ROLLED_BACK -> Res.string.signage_update_restarting
}
