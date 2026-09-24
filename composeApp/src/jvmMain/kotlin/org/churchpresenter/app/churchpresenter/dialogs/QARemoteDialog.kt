package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import org.churchpresenter.theme.components.RaisedIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.qa_admin_panel
import churchpresenter.composeapp.generated.resources.qa_admin_uses_api_key
import churchpresenter.composeapp.generated.resources.qa_copy_url
import churchpresenter.composeapp.generated.resources.qa_cooldown_label
import churchpresenter.composeapp.generated.resources.qa_disable_public_access
import churchpresenter.composeapp.generated.resources.qa_downloading_tunnel
import churchpresenter.composeapp.generated.resources.qa_enable_public_access
import churchpresenter.composeapp.generated.resources.qa_local
import churchpresenter.composeapp.generated.resources.qa_public
import churchpresenter.composeapp.generated.resources.qa_public_access
import churchpresenter.composeapp.generated.resources.qa_public_access_description
import churchpresenter.composeapp.generated.resources.qa_qr_code_shows
import churchpresenter.composeapp.generated.resources.qa_qr_message_default
import churchpresenter.composeapp.generated.resources.qa_qr_message_label
import churchpresenter.composeapp.generated.resources.qa_qr_message_reset
import churchpresenter.composeapp.generated.resources.qa_remote_dialog_description
import churchpresenter.composeapp.generated.resources.qa_remote_dialog_title
import churchpresenter.composeapp.generated.resources.qa_retry
import churchpresenter.composeapp.generated.resources.qa_server_hint
import churchpresenter.composeapp.generated.resources.qa_starting_tunnel
import churchpresenter.composeapp.generated.resources.qa_submit_questions
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.app.churchpresenter.presenter.generateQRCodeBitmap
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.theme.ProvideUiFontScale
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken


@Composable
fun QARemoteDialog(
    serverUrl: String,
    qaDisplayUrl: String,
    onQaDisplayUrlChanged: (String) -> Unit,
    apiKeyEnabled: Boolean,
    apiKey: String,
    tunnelStatus: TunnelStatus,
    tunnelUrl: String,
    onStartTunnel: () -> Unit,
    onStopTunnel: () -> Unit,
    qaSettings: QASettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val mainWindowState = LocalMainWindowState.current
    val dialogWidth = 760.dp
    // Sized for the links, public access and the QR message -- how questions look on screen is
    // styled per profile now -- and grown below if a tunnel state ever needs more.
    val dialogHeight = 560.dp
    val maxDialogHeight = 900.dp
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, dialogWidth, dialogHeight),
        width = dialogWidth,
        height = dialogHeight
    )

    // Grow the window (never shrink) when content overflows the current viewport, instead of
    // relying on a single guessed-at fixed height — the scroll stays as a fallback beyond the cap.
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val overflow = with(density) { scrollState.maxValue.toDp() }
            val grown = (dialogState.size.height + overflow).coerceAtMost(maxDialogHeight)
            if (grown > dialogState.size.height) {
                dialogState.size = DpSize(dialogState.size.width, grown)
            }
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.qa_remote_dialog_title),
        resizable = false
    ) {
        ProvideUiFontScale {
            QARemoteContent(
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                onQaDisplayUrlChanged = onQaDisplayUrlChanged,
                apiKeyEnabled = apiKeyEnabled,
                apiKey = apiKey,
                tunnelStatus = tunnelStatus,
                tunnelUrl = tunnelUrl,
                onStartTunnel = onStartTunnel,
                onStopTunnel = onStopTunnel,
                qaSettings = qaSettings,
                onSettingsChange = onSettingsChange,
                scrollState = scrollState,
                copyText = { text ->
                    SystemClipboard.copy(text)
                },
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Everything the Q&A remote window contains: the two share panels with their QR codes, the tunnel
 * controls, the display settings, and the addresses it derives for each.
 *
 * Held apart from [QARemoteDialog] because that function's remaining work is the `DialogWindow` it
 * opens and the grow-to-fit effect sizing it, neither of which can run on a headless machine.
 *
 * Two things are taken as parameters rather than reached for here, because each is a piece of the
 * machine rather than of the dialog: [copyText] writes the system clipboard, and [scrollState] is
 * shared with the sizing effect above.
 * Passing them in is what lets a test press Copy and see *which* address was handed over — the
 * point of the URL-building rules below.
 */
@Composable
internal fun QARemoteContent(
    serverUrl: String,
    qaDisplayUrl: String,
    onQaDisplayUrlChanged: (String) -> Unit,
    apiKeyEnabled: Boolean,
    apiKey: String,
    tunnelStatus: TunnelStatus,
    tunnelUrl: String,
    onStartTunnel: () -> Unit,
    onStopTunnel: () -> Unit,
    qaSettings: QASettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    copyText: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val strQrMessageDefault = stringResource(Res.string.qa_qr_message_default)

    val effectiveBaseUrl = qaDisplayUrl.ifEmpty { serverUrl }
    val submissionUrl = if (effectiveBaseUrl.isNotEmpty()) "$effectiveBaseUrl/qa" else ""
    val adminBaseUrl = if (tunnelUrl.isNotEmpty() && qaDisplayUrl == tunnelUrl) tunnelUrl else serverUrl
    val adminDisplayUrl = if (adminBaseUrl.isNotEmpty()) "$adminBaseUrl/qa/admin" else ""
    val adminQrUrl = if (adminBaseUrl.isNotEmpty()) {
        if (apiKeyEnabled && apiKey.isNotEmpty()) "$adminBaseUrl/qa/admin?password=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"
        else "$adminBaseUrl/qa/admin"
    } else ""

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.qa_remote_dialog_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (submissionUrl.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Left: Submission QR ──────────────────────────
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.qa_submit_questions),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        val submissionQR = remember(submissionUrl) { generateQRCodeBitmap(submissionUrl, 150) }
                        if (submissionQR != null) {
                            Image(bitmap = submissionQR, contentDescription = stringResource(Res.string.qa_submit_questions), modifier = Modifier.size(150.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                submissionUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        RaisedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { copyText(submissionUrl) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(stringResource(Res.string.qa_copy_url), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // ── Center: Public Access ─────────────────────────
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.qa_public_access),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.qa_public_access_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))

                        when (tunnelStatus) {
                            TunnelStatus.Idle -> {
                                RaisedButton(
                                    onClick = onStartTunnel,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(stringResource(Res.string.qa_enable_public_access), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            TunnelStatus.Downloading -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(stringResource(Res.string.qa_downloading_tunnel), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TunnelStatus.Starting -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(stringResource(Res.string.qa_starting_tunnel), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is TunnelStatus.Connected -> {
                                Text(stringResource(Res.string.qa_qr_code_shows), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    val isLocal = qaDisplayUrl.isEmpty() || qaDisplayUrl == serverUrl
                                    RaisedButton(
                                        onClick = { onQaDisplayUrlChanged(serverUrl) },
                                        modifier = Modifier.weight(1f),
                                        colors = if (isLocal) ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) else ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(stringResource(Res.string.qa_local), style = MaterialTheme.typography.labelSmall)
                                    }
                                    RaisedButton(
                                        onClick = { onQaDisplayUrlChanged(tunnelUrl) },
                                        modifier = Modifier.weight(1f),
                                        colors = if (!isLocal) ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) else ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(stringResource(Res.string.qa_public), style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                RaisedButton(
                                    onClick = {
                                        onStopTunnel()
                                        onQaDisplayUrlChanged(serverUrl)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(stringResource(Res.string.qa_disable_public_access), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            is TunnelStatus.Error -> {
                                Text(
                                    tunnelStatus.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                RaisedButton(
                                    onClick = onStartTunnel,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(stringResource(Res.string.qa_retry), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        NumberSettingsTextField(
                            label = stringResource(Res.string.qa_cooldown_label),
                            initialText = qaSettings.rateLimitCooldownSeconds,
                            range = 0..600,
                            onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(rateLimitCooldownSeconds = it)) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ── Right: Admin QR ──────────────────────────────
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.qa_admin_panel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.qa_admin_uses_api_key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))

                        val adminQR = remember(adminQrUrl) { generateQRCodeBitmap(adminQrUrl, 150) }
                        if (adminQR != null) {
                            Image(bitmap = adminQR, contentDescription = stringResource(Res.string.qa_admin_panel), modifier = Modifier.size(150.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                adminQrUrl.ifEmpty { adminDisplayUrl },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        RaisedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { copyText(adminQrUrl.ifEmpty { adminDisplayUrl }) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(stringResource(Res.string.qa_copy_url), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                // What the QR code's link page says -- one message for the install. How a question
                // and its QR code look on each output is styled per profile, on the Profiles tab.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.qa_qr_message_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .sunken(RoundedCornerShape(8.dp), elevationPalette())
                            .hoverTint(RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            BasicTextField(
                                value = qaSettings.qrCodeMessage,
                                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrCodeMessage = it)) } },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (qaSettings.qrCodeMessage.isEmpty()) {
                                        Text(strQrMessageDefault, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        RaisedIconButton(
                            onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrCodeMessage = "")) } },
                            modifier = Modifier.size(30.dp),
                            shape = RoundedCornerShape(5.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.qa_qr_message_reset), modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.qa_server_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
            RaisedButton(
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
