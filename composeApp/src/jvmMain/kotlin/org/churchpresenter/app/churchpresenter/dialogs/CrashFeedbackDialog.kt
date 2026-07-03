package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.crash_feedback_comment_label
import churchpresenter.composeapp.generated.resources.crash_feedback_dismiss
import churchpresenter.composeapp.generated.resources.crash_feedback_email_label
import churchpresenter.composeapp.generated.resources.crash_feedback_message
import churchpresenter.composeapp.generated.resources.crash_feedback_send
import churchpresenter.composeapp.generated.resources.crash_feedback_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shown on the launch following an unexpected shutdown, inviting the user to describe
 * what they were doing. The text is sent to Sentry as user feedback via [onSend].
 */
@Composable
fun CrashFeedbackDialog(
    onDismiss: () -> Unit,
    onSend: (comment: String, email: String) -> Unit
) {
    var comment by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.crash_feedback_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.crash_feedback_message))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(Res.string.crash_feedback_comment_label)) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.crash_feedback_email_label)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(comment.trim(), email.trim()) },
                enabled = comment.isNotBlank()
            ) { Text(stringResource(Res.string.crash_feedback_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.crash_feedback_dismiss)) }
        }
    )
}
