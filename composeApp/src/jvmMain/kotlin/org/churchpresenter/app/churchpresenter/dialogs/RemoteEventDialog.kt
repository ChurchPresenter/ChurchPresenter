package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

/**
 * Describes a pending remote API event waiting for user approval.
 */
data class RemoteEvent(
    val type: RemoteEventType,
    val title: String,
    val detail: String = ""
)

enum class RemoteEventType {
    ADD_TO_SCHEDULE,
    PROJECT
}

/**
 * Persistent dialog shown when a remote API event arrives.
 * Shows the front-of-queue item and a badge with how many more are waiting.
 *
 *  - **Allow**         — execute the action, move to next in queue
 *  - **Deny**          — reject this item, move to next in queue
 *  - **Block Session** — deny all queued items and drop all future events
 */
@Composable
fun RemoteEventDialog(
    event: RemoteEvent?,
    queueSize: Int = 1,
    onAllow: () -> Unit,
    onBlockSession: () -> Unit,
    onDeny: () -> Unit
) {
    if (event == null) return

    val actionLabel = when (event.type) {
        RemoteEventType.ADD_TO_SCHEDULE -> "Add to Schedule"
        RemoteEventType.PROJECT         -> "Project (Go Live)"
    }
    val icon = when (event.type) {
        RemoteEventType.ADD_TO_SCHEDULE -> "📋"
        RemoteEventType.PROJECT         -> "📡"
    }
    val remaining = queueSize - 1  // items behind this one

    val dialogHeight = if (remaining > 0) 290.dp else 260.dp

    DialogWindow(
        onCloseRequest = onDeny,
        state = rememberDialogState(width = 420.dp, height = dialogHeight),
        title = "Remote API Request${if (remaining > 0) " ($queueSize pending)" else ""}",
        resizable = false,
        alwaysOnTop = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header row with icon, label, and queue badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(icon, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    // Badge showing how many are queued behind this one
                    if (remaining > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        ) {
                            Text(
                                text = "+$remaining",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Item details
                Column {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (event.detail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = event.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // "More waiting" hint
                if (remaining > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$remaining more request${if (remaining > 1) "s" else ""} waiting in queue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBlockSession,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Block Session")
                    }

                    OutlinedButton(onClick = onDeny) {
                        Text("Deny")
                    }

                    Button(
                        onClick = onAllow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Allow")
                    }
                }
            }
        }
    }
}
