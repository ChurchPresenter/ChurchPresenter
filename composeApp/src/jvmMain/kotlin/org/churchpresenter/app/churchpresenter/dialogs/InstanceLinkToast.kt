package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.instance_link_command_failed
import churchpresenter.composeapp.generated.resources.instance_link_command_no_ack
import churchpresenter.composeapp.generated.resources.remote_activity_dismiss
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkCommandFailure
import org.jetbrains.compose.resources.stringResource

private const val TOAST_AUTO_DISMISS_MS = 6_000L

/**
 * Bottom-of-screen toast for InstanceLink command failures — the visible counterpart to what
 * used to be a silent drop. Modeled on [RemoteActivityToastHost]; shows the most recent
 * failure and auto-dismisses.
 */
@Composable
fun InstanceLinkToastHost(
    failures: List<InstanceLinkCommandFailure>,
    onDismiss: (InstanceLinkCommandFailure) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val current = failures.lastOrNull()
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            if (current != null) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                        .widthIn(max = 560.dp),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (current.soft) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (current.soft) {
                                stringResource(Res.string.instance_link_command_no_ack)
                            } else {
                                stringResource(
                                    Res.string.instance_link_command_failed,
                                    current.commandType,
                                    current.reason ?: "unknown"
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(shape = RoundedCornerShape(6.dp), onClick = { onDismiss(current) }) {
                            Text(
                                stringResource(Res.string.remote_activity_dismiss),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                LaunchedEffect(current) {
                    delay(TOAST_AUTO_DISMISS_MS)
                    onDismiss(current)
                }
            }
        }
    }
}
