package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.block_for_session
import churchpresenter.composeapp.generated.resources.remote_activity_added_to_schedule
import churchpresenter.composeapp.generated.resources.remote_activity_by
import churchpresenter.composeapp.generated.resources.remote_activity_dismiss
import churchpresenter.composeapp.generated.resources.remote_activity_dismiss_all
import churchpresenter.composeapp.generated.resources.remote_activity_projected
import churchpresenter.composeapp.generated.resources.remote_activity_presented
import churchpresenter.composeapp.generated.resources.remote_activity_uploaded
import churchpresenter.composeapp.generated.resources.remote_activity_cleared
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Describes an auto-approved remote action that should be surfaced as a toast
 * so the operator knows what was done on their behalf.
 */
data class RemoteActivityNotification(
    val type: RemoteEventType,
    val title: String,
    val detail: String = "",
    val clientId: String = "",
    val clientLabel: String = ""
)

private const val TOAST_AUTO_DISMISS_MS = 10_000L

/**
 * Overlay shown at the bottom of the screen whenever a session-allowed or
 * permanently-allowed client performs an action that was auto-approved.
 *
 * Shows for [TOAST_AUTO_DISMISS_MS] ms (10 s) then fades out automatically.
 * The operator can dismiss it immediately or block the client for the session.
 *
 * Multiple notifications stack — the topmost (most-recent) entry is shown first.
 */
@Composable
fun RemoteActivityToastHost(
    notifications: List<RemoteActivityNotification>,
    onDismiss: (RemoteActivityNotification) -> Unit,
    onDismissAll: () -> Unit,
    onBlockForSession: (RemoteActivityNotification) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val current = notifications.lastOrNull()
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            if (current != null) {
                RemoteActivityToast(
                    notification = current,
                    remaining = notifications.size - 1,
                    onDismiss = { onDismiss(current) },
                    onDismissAll = onDismissAll,
                    onBlockForSession = { onBlockForSession(current) }
                )

                // Auto-dismiss after timeout
                LaunchedEffect(current) {
                    delay(TOAST_AUTO_DISMISS_MS)
                    onDismiss(current)
                }
            }
        }
    }
}

@Composable
private fun RemoteActivityToast(
    notification: RemoteActivityNotification,
    remaining: Int,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
    onBlockForSession: () -> Unit,
) {
    val actionLabel = when (notification.type) {
        RemoteEventType.ADD_TO_SCHEDULE -> stringResource(Res.string.remote_activity_added_to_schedule)
        RemoteEventType.PROJECT         -> stringResource(Res.string.remote_activity_projected)
        RemoteEventType.PRESENT         -> stringResource(Res.string.remote_activity_presented)
        RemoteEventType.UPLOAD          -> stringResource(Res.string.remote_activity_uploaded)
        RemoteEventType.CLEAR           -> stringResource(Res.string.remote_activity_cleared)
    }
    val icon = when (notification.type) {
        RemoteEventType.ADD_TO_SCHEDULE -> "📋"
        RemoteEventType.PROJECT         -> "📡"
        RemoteEventType.PRESENT         -> "▶️"
        RemoteEventType.UPLOAD          -> "📤"
        RemoteEventType.CLEAR           -> "🔲"
    }

    val clientDisplay = when {
        notification.clientLabel.isNotBlank() -> notification.clientLabel
        notification.clientId.isNotBlank()    -> notification.clientId.take(12)
        else                                   -> ""
    }

    Surface(
        modifier = Modifier
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 680.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        // Single compact row: icon | labels | spacer | buttons
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(icon, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))

            // Action + title + client
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    if (clientDisplay.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.remote_activity_by, clientDisplay),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (remaining > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "+$remaining more",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (notification.detail.isNotBlank()) {
                        Text(
                            text = " · ${notification.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBlockForSession) {
                    Icon(
                        Icons.Filled.RemoveCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(Res.string.remote_activity_dismiss),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (remaining > 0) {
                    TextButton(onClick = onDismissAll) {
                        Text(
                            stringResource(Res.string.remote_activity_dismiss_all),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

