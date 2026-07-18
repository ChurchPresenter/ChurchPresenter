package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.allow
import churchpresenter.composeapp.generated.resources.allow_for_session
import churchpresenter.composeapp.generated.resources.allow_permanently
import churchpresenter.composeapp.generated.resources.block_for_session
import churchpresenter.composeapp.generated.resources.block_permanently
import churchpresenter.composeapp.generated.resources.deny
import churchpresenter.composeapp.generated.resources.remote_api_add_to_schedule
import churchpresenter.composeapp.generated.resources.remote_api_presentation_connect
import churchpresenter.composeapp.generated.resources.remote_api_presentation_connect_detail
import churchpresenter.composeapp.generated.resources.remote_api_project
import churchpresenter.composeapp.generated.resources.remote_api_remove_from_schedule
import churchpresenter.composeapp.generated.resources.remote_api_qa_add
import churchpresenter.composeapp.generated.resources.remote_api_qa_edit
import churchpresenter.composeapp.generated.resources.remote_api_qa_delete
import churchpresenter.composeapp.generated.resources.remote_api_qa_approve
import churchpresenter.composeapp.generated.resources.remote_api_qa_deny
import churchpresenter.composeapp.generated.resources.remote_api_qa_done
import churchpresenter.composeapp.generated.resources.remote_api_qa_display
import churchpresenter.composeapp.generated.resources.remote_api_qa_clear_display
import churchpresenter.composeapp.generated.resources.remote_api_qa_admin_connect
import churchpresenter.composeapp.generated.resources.remote_api_qa_admin_connect_detail
import churchpresenter.composeapp.generated.resources.remote_api_request_title
import churchpresenter.composeapp.generated.resources.remote_api_request_title_queued
import churchpresenter.composeapp.generated.resources.remote_client_allowed_badge
import churchpresenter.composeapp.generated.resources.remote_client_blocked_badge
import churchpresenter.composeapp.generated.resources.instance_link_follower_badge
import churchpresenter.composeapp.generated.resources.remote_client_label
import churchpresenter.composeapp.generated.resources.remote_queue_waiting_many
import churchpresenter.composeapp.generated.resources.remote_queue_waiting_one
import org.jetbrains.compose.resources.stringResource

/**
 * Describes a pending remote API event waiting for user approval.
 */
data class RemoteEvent(
    val type: RemoteEventType,
    val title: String,
    val detail: String = "",
    /** The value of the X-Device-Id header sent by the remote client. Empty if none provided. */
    val clientId: String = "",
    /** Human-readable label saved for this device. Empty if none has been set. */
    val clientLabel: String = ""
)

enum class RemoteEventType {
    ADD_TO_SCHEDULE,
    REMOVE_FROM_SCHEDULE,
    PROJECT,
    PRESENTATION_CONNECT,
    PRESENT,    // instant: select_song_section / select_picture / select_slide / select_bible_verse
    UPLOAD,     // instant: presentation or picture upload
    CLEAR,      // instant: POST /api/clear
    QA_ADD,
    QA_EDIT,
    QA_DELETE,
    QA_APPROVE,
    QA_DENY,
    QA_DONE,
    QA_DISPLAY,
    QA_CLEAR_DISPLAY,
    QA_ADMIN_CONNECT,
}

/**
 * Persistent dialog shown when a remote API event arrives.
 * Shows the front-of-queue item and a badge with how many more are waiting.
 *
 *  - **Allow**               — execute this action and move to next in queue
 *  - **Allow for Session**   — execute this action and auto-approve all future requests from the same client this session
 *  - **Allow Permanently**   — execute and permanently remember this client as allowed (only shown when the client is not in any permanent list)
 *  - **Deny**                — reject this item, move to next in queue
 *  - **Block for Session**   — deny all queued items from this client for the rest of the session
 *  - **Block Permanently**   — deny and permanently remember this client as blocked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteEventDialog(
    event: RemoteEvent?,
    queueSize: Int = 1,
    /** True when the client is already in the permanent allow list. */
    isClientKnownAllowed: Boolean = false,
    /** True when the client is already in the permanent block list. */
    isClientKnownBlocked: Boolean = false,
    /** True when this client is currently connected as an Instance Link follower/controller, rather
     *  than a regular mobile/browser companion — same badge already shown in ServerSettingsTab's
     *  Remote Clients list. */
    isInstanceLinkFollower: Boolean = false,
    onAllow: () -> Unit,
    onAllowForSession: () -> Unit,
    onAllowPermanently: () -> Unit,
    onBlockForSession: () -> Unit,
    onBlockPermanently: () -> Unit,
    onDeny: () -> Unit
) {
    if (event == null) return

    val actionLabel = when (event.type) {
        RemoteEventType.ADD_TO_SCHEDULE -> stringResource(Res.string.remote_api_add_to_schedule)
        RemoteEventType.REMOVE_FROM_SCHEDULE -> stringResource(Res.string.remote_api_remove_from_schedule)
        RemoteEventType.PROJECT         -> stringResource(Res.string.remote_api_project)
        RemoteEventType.PRESENTATION_CONNECT -> stringResource(Res.string.remote_api_presentation_connect)
        RemoteEventType.QA_ADD          -> stringResource(Res.string.remote_api_qa_add)
        RemoteEventType.QA_EDIT         -> stringResource(Res.string.remote_api_qa_edit)
        RemoteEventType.QA_DELETE       -> stringResource(Res.string.remote_api_qa_delete)
        RemoteEventType.QA_APPROVE      -> stringResource(Res.string.remote_api_qa_approve)
        RemoteEventType.QA_DENY         -> stringResource(Res.string.remote_api_qa_deny)
        RemoteEventType.QA_DONE         -> stringResource(Res.string.remote_api_qa_done)
        RemoteEventType.QA_DISPLAY      -> stringResource(Res.string.remote_api_qa_display)
        RemoteEventType.QA_CLEAR_DISPLAY -> stringResource(Res.string.remote_api_qa_clear_display)
        RemoteEventType.QA_ADMIN_CONNECT -> stringResource(Res.string.remote_api_qa_admin_connect)
        else                            -> stringResource(Res.string.remote_api_request_title)
    }
    val typeIcon: ImageVector = when (event.type) {
        RemoteEventType.ADD_TO_SCHEDULE -> Icons.Filled.CalendarMonth
        RemoteEventType.REMOVE_FROM_SCHEDULE -> Icons.Filled.EventBusy
        RemoteEventType.PROJECT         -> Icons.Filled.Cast
        RemoteEventType.PRESENTATION_CONNECT,
        RemoteEventType.QA_ADMIN_CONNECT -> Icons.Filled.Smartphone
        RemoteEventType.QA_ADD,
        RemoteEventType.QA_EDIT,
        RemoteEventType.QA_DELETE,
        RemoteEventType.QA_APPROVE,
        RemoteEventType.QA_DENY,
        RemoteEventType.QA_DONE,
        RemoteEventType.QA_DISPLAY,
        RemoteEventType.QA_CLEAR_DISPLAY -> Icons.Filled.QuestionAnswer
        else                            -> Icons.Filled.Notifications
    }
    // Go-live/project actions read as blue; schedule edits read as amber, matching the design.
    val typeAccent: Color = when (event.type) {
        RemoteEventType.ADD_TO_SCHEDULE,
        RemoteEventType.REMOVE_FROM_SCHEDULE -> REMOTE_SCHEDULE_AMBER
        else -> MaterialTheme.colorScheme.primary
    }
    val bodyTitle = event.title.ifBlank {
        when (event.type) {
            RemoteEventType.PRESENTATION_CONNECT -> stringResource(Res.string.remote_api_presentation_connect_detail)
            RemoteEventType.QA_ADMIN_CONNECT -> stringResource(Res.string.remote_api_qa_admin_connect_detail)
            else -> ""
        }
    }
    val remaining = queueSize - 1  // items behind this one

    // "Allow Permanently" is only shown when the client is not yet in any permanent list
    val showAllowPermanently = !isClientKnownAllowed && !isClientKnownBlocked

    val dialogTitle = if (remaining > 0)
        stringResource(Res.string.remote_api_request_title_queued, queueSize)
    else
        stringResource(Res.string.remote_api_request_title)

    val dialogHeight = if (remaining > 0) 330.dp else 290.dp
    val dialogWidth = 500.dp
    val mainWindowState = LocalMainWindowState.current

    DialogWindow(
        onCloseRequest = onDeny,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, dialogWidth, dialogHeight),
            width = dialogWidth,
            height = dialogHeight
        ),
        title = dialogTitle,
        resizable = false,
        alwaysOnTop = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(typeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, contentDescription = null, tint = typeAccent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = typeAccent,
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
                        Spacer(Modifier.width(8.dp))
                    }
                    // Prominent one-tap allow (mirrors the ✓ button below)
                    Button(
                        onClick = onAllow,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = REMOTE_ALLOW_GREEN,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(Res.string.allow), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── Item details ──────────────────────────────────────────────
                Column {
                    Text(bodyTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (event.detail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(event.detail, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    // Show client identity if present
                    if (event.clientId.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.remote_client_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(Modifier.width(4.dp))
                            Column {
                                // Label (if set) shown as primary identifier
                                if (event.clientLabel.isNotBlank()) {
                                    Text(
                                        text = event.clientLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isClientKnownAllowed -> MaterialTheme.colorScheme.primary
                                            isClientKnownBlocked -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                // Raw device ID always shown (smaller when label present)
                                Text(
                                    text = event.clientId,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        isClientKnownAllowed -> MaterialTheme.colorScheme.primary
                                        isClientKnownBlocked -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    },
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isClientKnownAllowed) {
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(Res.string.remote_client_allowed_badge),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else if (isClientKnownBlocked) {
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(Res.string.remote_client_blocked_badge),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (isInstanceLinkFollower) {
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(Res.string.instance_link_follower_badge),
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // "More waiting" hint
                if (remaining > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (remaining == 1) stringResource(Res.string.remote_queue_waiting_one)
                               else stringResource(Res.string.remote_queue_waiting_many, remaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.weight(1f))

                // ── Button rows ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: block actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionIconButton(
                            tooltip = stringResource(Res.string.block_permanently),
                            onClick = onBlockPermanently,
                            icon = Icons.Filled.Block,
                            style = ActionIconStyle.ErrorOutlined
                        )
                        ActionIconButton(
                            tooltip = stringResource(Res.string.block_for_session),
                            onClick = onBlockForSession,
                            icon = Icons.Filled.RemoveCircle,
                            style = ActionIconStyle.ErrorFilled
                        )
                    }

                    // Right: deny + allow actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionIconButton(
                            tooltip = stringResource(Res.string.deny),
                            onClick = onDeny,
                            icon = Icons.Filled.Close,
                            style = ActionIconStyle.Outlined
                        )
                        ActionIconButton(
                            tooltip = stringResource(Res.string.allow_for_session),
                            onClick = onAllowForSession,
                            icon = Icons.Filled.Schedule,
                            style = ActionIconStyle.PrimaryOutlined
                        )
                        if (showAllowPermanently) {
                            ActionIconButton(
                                tooltip = stringResource(Res.string.allow_permanently),
                                onClick = onAllowPermanently,
                                icon = Icons.Filled.Star,
                                style = ActionIconStyle.SuccessFilled
                            )
                        }
                        ActionIconButton(
                            tooltip = stringResource(Res.string.allow),
                            onClick = onAllow,
                            icon = Icons.Filled.Check,
                            style = ActionIconStyle.PrimaryFilled
                        )
                    }
                }
            }
        }
    }
}

private val REMOTE_SCHEDULE_AMBER = Color(0xFFF5B301)
private val REMOTE_ALLOW_GREEN = Color(0xFF43A047)

private enum class ActionIconStyle {
    ErrorOutlined,
    ErrorFilled,
    Outlined,
    PrimaryOutlined,
    SuccessFilled,
    PrimaryFilled
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIconButton(
    tooltip: String,
    onClick: () -> Unit,
    icon: ImageVector,
    style: ActionIconStyle
) {
    val errorColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        when (style) {
            ActionIconStyle.ErrorOutlined -> OutlinedIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = errorColor),
                border = BorderStroke(1.dp, errorColor.copy(alpha = 0.5f))
            ) { Icon(icon, contentDescription = tooltip) }

            ActionIconStyle.ErrorFilled -> FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = errorColor,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Icon(icon, contentDescription = tooltip) }

            ActionIconStyle.Outlined -> OutlinedIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.outlinedIconButtonColors()
            ) { Icon(icon, contentDescription = tooltip) }

            ActionIconStyle.PrimaryOutlined -> OutlinedIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = primaryColor),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
            ) { Icon(icon, contentDescription = tooltip) }

            ActionIconStyle.SuccessFilled -> FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = REMOTE_ALLOW_GREEN,
                    contentColor = Color.White
                )
            ) { Icon(icon, contentDescription = tooltip) }

            ActionIconStyle.PrimaryFilled -> FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Icon(icon, contentDescription = tooltip) }
        }
    }
}
