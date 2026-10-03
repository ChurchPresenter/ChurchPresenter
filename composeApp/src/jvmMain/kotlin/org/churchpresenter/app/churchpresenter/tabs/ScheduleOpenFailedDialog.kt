package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleOpenFailure
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.schedule_open_failed_title
import org.churchpresenter.strings.generated.resources.schedule_open_not_a_schedule
import org.churchpresenter.strings.generated.resources.schedule_open_unreadable
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.stringResource

/**
 * Tells the operator the file they opened was not used, and why: it could not be read, or it is
 * not a schedule at all -- a web page saved under a schedule's name being the case seen in the
 * field. Nothing in the Schedule changed, which is the other thing worth saying.
 */
@Composable
internal fun ScheduleOpenFailedDialog(failure: ScheduleOpenFailure, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.schedule_open_failed_title)) },
        text = {
            val reason = if (failure.unreadable) {
                Res.string.schedule_open_unreadable
            } else {
                Res.string.schedule_open_not_a_schedule
            }
            Text(stringResource(reason, failure.fileName))
        },
        confirmButton = {
            RaisedButton(shape = AppShape(6.dp), onClick = onDismiss) { Text(stringResource(Res.string.ok)) }
        },
    )
}
