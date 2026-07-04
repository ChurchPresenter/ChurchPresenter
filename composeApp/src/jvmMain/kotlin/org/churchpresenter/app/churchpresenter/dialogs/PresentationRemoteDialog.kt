package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.presentation_remote_control
import churchpresenter.composeapp.generated.resources.presentation_remote_copy_url
import churchpresenter.composeapp.generated.resources.presentation_remote_description
import churchpresenter.composeapp.generated.resources.presentation_remote_enable
import churchpresenter.composeapp.generated.resources.presentation_server_not_running
import churchpresenter.composeapp.generated.resources.qa_disable_public_access
import churchpresenter.composeapp.generated.resources.qa_downloading_tunnel
import churchpresenter.composeapp.generated.resources.qa_enable_public_access
import churchpresenter.composeapp.generated.resources.qa_local
import churchpresenter.composeapp.generated.resources.qa_public
import churchpresenter.composeapp.generated.resources.qa_public_access
import churchpresenter.composeapp.generated.resources.qa_public_access_description
import churchpresenter.composeapp.generated.resources.presentation_remote_tunnel_temporary_note
import churchpresenter.composeapp.generated.resources.qa_qr_code_shows
import churchpresenter.composeapp.generated.resources.qa_retry
import churchpresenter.composeapp.generated.resources.qa_starting_tunnel
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.generateQRCodeBitmap
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun PresentationRemoteDialog(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    serverUrl: String,
    apiKeyEnabled: Boolean,
    apiKey: String,
    tunnelStatus: TunnelStatus,
    tunnelUrl: String,
    presentationDisplayUrl: String,
    onPresentationDisplayUrlChanged: (String) -> Unit,
    onStartTunnel: () -> Unit,
    onStopTunnel: () -> Unit,
    onDismiss: () -> Unit
) {
    val mainWindowState = LocalMainWindowState.current
    val dialogWidth = 400.dp
    val dialogHeight = 720.dp
    val copyText: (String) -> Unit = { text ->
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, dialogWidth, dialogHeight),
            width = dialogWidth,
            height = dialogHeight
        ),
        title = stringResource(Res.string.presentation_remote_control),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.presentation_remote_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.presentation_remote_enable),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.presentationRemoteSettings.remoteControlEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange { s ->
                                s.copy(presentationRemoteSettings = s.presentationRemoteSettings.copy(remoteControlEnabled = enabled))
                            }
                        }
                    )
                }

                val qrBaseUrl = presentationDisplayUrl.ifEmpty { serverUrl }
                if (qrBaseUrl.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    val qrDisplayUrl = "$qrBaseUrl/presentation-remote"
                    val qrUrl = if (apiKeyEnabled && apiKey.isNotBlank()) "$qrDisplayUrl?password=$apiKey" else qrDisplayUrl
                    val qrBitmap = remember(qrUrl) { generateQRCodeBitmap(qrUrl, 180) }
                    if (qrBitmap != null) {
                        Image(bitmap = qrBitmap, contentDescription = null, modifier = Modifier.size(180.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        stringResource(Res.string.qa_qr_code_shows),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            qrUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = { copyText(qrUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(stringResource(Res.string.presentation_remote_copy_url), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(Res.string.qa_public_access),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.presentation_remote_tunnel_temporary_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    when (tunnelStatus) {
                        is TunnelStatus.Idle -> {
                            Text(
                                stringResource(Res.string.qa_public_access_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = onStartTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text(stringResource(Res.string.qa_enable_public_access), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is TunnelStatus.Downloading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(stringResource(Res.string.qa_downloading_tunnel), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is TunnelStatus.Starting -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(stringResource(Res.string.qa_starting_tunnel), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is TunnelStatus.Connected -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    shape = RoundedCornerShape(6.dp),
                                    onClick = { onPresentationDisplayUrlChanged(serverUrl) },
                                    colors = if (presentationDisplayUrl.isEmpty() || presentationDisplayUrl == serverUrl)
                                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.primary)
                                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) { Text(stringResource(Res.string.qa_local), style = MaterialTheme.typography.labelSmall) }
                                Button(
                                    shape = RoundedCornerShape(6.dp),
                                    onClick = { onPresentationDisplayUrlChanged(tunnelUrl) },
                                    colors = if (presentationDisplayUrl == tunnelUrl)
                                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.primary)
                                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) { Text(stringResource(Res.string.qa_public), style = MaterialTheme.typography.labelSmall) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = onStopTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text(stringResource(Res.string.qa_disable_public_access), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is TunnelStatus.Error -> {
                            Text(
                                tunnelStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = onStartTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text(stringResource(Res.string.qa_retry), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(Res.string.presentation_server_not_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    shape = RoundedCornerShape(6.dp),
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(Res.string.close))
                }
            }
        }
    }
}
