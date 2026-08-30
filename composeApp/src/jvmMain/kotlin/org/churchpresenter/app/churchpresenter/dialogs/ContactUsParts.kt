/*
 * The pieces of the Contact Us dialog that are not the form itself: what the send attempt says,
 * and the row of buttons under it. Their own file because the dialog is at its function cap.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.contact_open_browser
import churchpresenter.composeapp.generated.resources.contact_send
import churchpresenter.composeapp.generated.resources.contact_sending
import org.churchpresenter.app.churchpresenter.composables.CopyLinkIconButton
import org.churchpresenter.app.churchpresenter.utils.ContactReporter
import org.jetbrains.compose.resources.stringResource

/**
 * What the send attempt has to say, if anything.
 *
 * Outside the scrolling field area above so it is always visible: an error sentence can wrap to
 * two lines and must never clip.
 */
@Composable
internal fun ContactUsStatus(status: SendStatus, sentText: String) {
    when (status) {
        is SendStatus.Sending -> StatusLine(
            stringResource(Res.string.contact_sending),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is SendStatus.Sent -> StatusLine(sentText, MaterialTheme.colorScheme.primary)
        is SendStatus.Error -> StatusLine(status.message, MaterialTheme.colorScheme.error)
        SendStatus.Idle -> {}
    }
}

/** The row along the bottom: the browser fall-back on the left, Cancel and Send on the right. */
@Composable
internal fun ContactUsActions(
    canSend: Boolean,
    onOpenInBrowser: () -> Unit,
    onCopyLink: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Always available: fall back to the full web contact form in a browser.
        TextButton(shape = RoundedCornerShape(6.dp), onClick = onOpenInBrowser) {
            Text(stringResource(Res.string.contact_open_browser), style = MaterialTheme.typography.labelLarge)
        }
        // And a way out when that browser opens where the operator cannot reach it: the operating
        // system picks the display, which on a two-screen setup is regularly the live output.
        CopyLinkIconButton(url = ContactReporter.WEB_CONTACT_URL, onCopy = onCopyLink)

        Spacer(modifier = Modifier.weight(1f))

        TextButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
            Text(stringResource(Res.string.cancel), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            shape = RoundedCornerShape(6.dp),
            enabled = canSend,
            onClick = onSend,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(Res.string.contact_send), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
