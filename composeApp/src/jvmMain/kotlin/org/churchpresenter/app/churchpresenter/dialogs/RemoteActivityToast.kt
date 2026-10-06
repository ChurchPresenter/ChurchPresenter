package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.server.RemoteActivityNotification
import org.churchpresenter.server.RemoteEventType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.remote_activity_calendar_enroll
import org.churchpresenter.strings.generated.resources.block_for_session
import org.churchpresenter.strings.generated.resources.remote_activity_added_to_schedule
import org.churchpresenter.strings.generated.resources.remote_activity_removed_from_schedule
import org.churchpresenter.strings.generated.resources.remote_activity_by
import org.churchpresenter.strings.generated.resources.remote_activity_more_count
import org.churchpresenter.strings.generated.resources.remote_activity_dismiss
import org.churchpresenter.strings.generated.resources.remote_activity_dismiss_all
import org.churchpresenter.strings.generated.resources.remote_activity_projected
import org.churchpresenter.strings.generated.resources.remote_activity_presented
import org.churchpresenter.strings.generated.resources.remote_activity_uploaded
import org.churchpresenter.strings.generated.resources.remote_activity_cleared
import org.churchpresenter.strings.generated.resources.remote_activity_qa_add
import org.churchpresenter.strings.generated.resources.remote_activity_qa_edit
import org.churchpresenter.strings.generated.resources.remote_activity_qa_delete
import org.churchpresenter.strings.generated.resources.remote_activity_qa_approve
import org.churchpresenter.strings.generated.resources.remote_activity_qa_deny
import org.churchpresenter.strings.generated.resources.remote_activity_qa_done
import org.churchpresenter.strings.generated.resources.remote_activity_qa_display
import org.churchpresenter.strings.generated.resources.remote_activity_qa_clear_display
import org.churchpresenter.strings.generated.resources.remote_activity_presentation_connect
import org.churchpresenter.strings.generated.resources.remote_activity_qa_admin_connect_detail
import org.churchpresenter.strings.generated.resources.remote_activity_qa_admin_connect
import org.churchpresenter.strings.generated.resources.remote_activity_musician_connect
import org.churchpresenter.strings.generated.resources.instance_link_follower_badge
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CancelPresentation
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size

internal const val TOAST_AUTO_DISMISS_MS = 10_000L

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
    /** Device ids currently connected as an Instance Link follower/controller — same set
     *  ServerSettingsTab's Remote Clients list uses for its badge (companionServer.connectedInstanceLinkFollowers). */
    connectedInstanceLinkFollowers: Set<String> = emptySet(),
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
                    isInstanceLinkFollower = current.clientId.isNotBlank() &&
                        current.clientId in connectedInstanceLinkFollowers,
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
    isInstanceLinkFollower: Boolean = false,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
    onBlockForSession: () -> Unit,
) {
    val actionLabel = remoteActionLabel(notification.type)
    val icon = remoteEventIcon(notification.type)

    val clientDisplay = when {
        notification.clientLabel.isNotBlank() -> notification.clientLabel
        notification.clientId.isNotBlank()    -> notification.clientId.take(12)
        else                                   -> ""
    }

    val bodyTitle = notification.title.ifBlank { connectDetail(notification.type) }

    Surface(
        modifier = Modifier
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 680.dp)
            .fillMaxWidth(),
        shape = AppShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        // Single compact row: icon | labels | spacer | buttons
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
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
                    if (isInstanceLinkFollower) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.instance_link_follower_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (remaining > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.remote_activity_more_count, remaining),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bodyTitle,
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

            ToastButtons(remaining, onDismiss, onDismissAll, onBlockForSession)
        }
    }
}

/** The icon at the start of a remote-activity toast, one per kind of thing a remote did. */
internal fun remoteEventIcon(type: RemoteEventType): ImageVector = when (type) {
    RemoteEventType.ADD_TO_SCHEDULE -> Icons.AutoMirrored.Filled.PlaylistAdd
    RemoteEventType.REMOVE_FROM_SCHEDULE -> Icons.Filled.Delete
    RemoteEventType.PROJECT -> Icons.Filled.Cast
    RemoteEventType.PRESENTATION_CONNECT,
    RemoteEventType.CALENDAR_ENROLL,
    RemoteEventType.QA_ADMIN_CONNECT,
    RemoteEventType.MUSICIAN_CONNECT -> Icons.Filled.PhoneAndroid
    RemoteEventType.PRESENT -> Icons.Filled.PlayArrow
    RemoteEventType.UPLOAD -> Icons.Filled.Upload
    RemoteEventType.CLEAR -> Icons.Filled.CancelPresentation
    RemoteEventType.QA_ADD,
    RemoteEventType.QA_EDIT,
    RemoteEventType.QA_DELETE,
    RemoteEventType.QA_APPROVE,
    RemoteEventType.QA_DENY,
    RemoteEventType.QA_DONE,
    RemoteEventType.QA_DISPLAY,
    RemoteEventType.QA_CLEAR_DISPLAY -> Icons.Filled.QuestionAnswer
}

/** What the remote did, as the toast's first line names it. */
@Composable
private fun remoteActionLabel(type: RemoteEventType): String = when (type) {
    RemoteEventType.ADD_TO_SCHEDULE -> stringResource(Res.string.remote_activity_added_to_schedule)
    RemoteEventType.REMOVE_FROM_SCHEDULE -> stringResource(Res.string.remote_activity_removed_from_schedule)
    RemoteEventType.PROJECT         -> stringResource(Res.string.remote_activity_projected)
    RemoteEventType.PRESENT         -> stringResource(Res.string.remote_activity_presented)
    RemoteEventType.UPLOAD          -> stringResource(Res.string.remote_activity_uploaded)
    RemoteEventType.CLEAR           -> stringResource(Res.string.remote_activity_cleared)
    RemoteEventType.QA_ADD          -> stringResource(Res.string.remote_activity_qa_add)
    RemoteEventType.QA_EDIT         -> stringResource(Res.string.remote_activity_qa_edit)
    RemoteEventType.QA_DELETE       -> stringResource(Res.string.remote_activity_qa_delete)
    RemoteEventType.QA_APPROVE      -> stringResource(Res.string.remote_activity_qa_approve)
    RemoteEventType.QA_DENY         -> stringResource(Res.string.remote_activity_qa_deny)
    RemoteEventType.QA_DONE         -> stringResource(Res.string.remote_activity_qa_done)
    RemoteEventType.QA_DISPLAY      -> stringResource(Res.string.remote_activity_qa_display)
    RemoteEventType.QA_CLEAR_DISPLAY -> stringResource(Res.string.remote_activity_qa_clear_display)
    RemoteEventType.PRESENTATION_CONNECT -> stringResource(Res.string.remote_activity_presentation_connect)
    RemoteEventType.CALENDAR_ENROLL -> stringResource(Res.string.remote_activity_calendar_enroll)
    RemoteEventType.QA_ADMIN_CONNECT -> stringResource(Res.string.remote_activity_qa_admin_connect)
    RemoteEventType.MUSICIAN_CONNECT -> stringResource(Res.string.remote_activity_musician_connect)
}

/** Block the client for the session, dismiss this toast, and dismiss all of them when more wait. */
@Composable
private fun ToastButtons(
    remaining: Int,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
    onBlockForSession: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(shape = AppShape(6.dp), onClick = onBlockForSession) {
            Icon(
                Icons.Filled.RemoveCircle,
                contentDescription = stringResource(Res.string.block_for_session),
                tint = MaterialTheme.colorScheme.error
            )
        }
        GhostButton(shape = AppShape(6.dp), onClick = onDismiss) {
            Text(
                stringResource(Res.string.remote_activity_dismiss),
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (remaining > 0) {
            GhostButton(shape = AppShape(6.dp), onClick = onDismissAll) {
                Text(
                    stringResource(Res.string.remote_activity_dismiss_all),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** What a connect toast says when the event carried no title of its own; nothing for other kinds. */
@Composable
private fun connectDetail(type: RemoteEventType): String = when (type) {
    RemoteEventType.PRESENTATION_CONNECT,
    RemoteEventType.QA_ADMIN_CONNECT,
    RemoteEventType.MUSICIAN_CONNECT -> stringResource(Res.string.remote_activity_qa_admin_connect_detail)
    else -> ""
}
