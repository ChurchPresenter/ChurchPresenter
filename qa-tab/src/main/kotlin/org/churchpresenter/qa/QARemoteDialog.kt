package org.churchpresenter.qa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.close
import org.churchpresenter.resources.generated.resources.qa_admin_panel
import org.churchpresenter.resources.generated.resources.qa_admin_uses_api_key
import org.churchpresenter.resources.generated.resources.qa_background_color
import org.churchpresenter.resources.generated.resources.qa_copy_url
import org.churchpresenter.resources.generated.resources.qa_cooldown_label
import org.churchpresenter.resources.generated.resources.qa_disable_public_access
import org.churchpresenter.resources.generated.resources.qa_display_styling
import org.churchpresenter.resources.generated.resources.qa_downloading_tunnel
import org.churchpresenter.resources.generated.resources.qa_enable_public_access
import org.churchpresenter.resources.generated.resources.qa_font
import org.churchpresenter.resources.generated.resources.qa_local
import org.churchpresenter.resources.generated.resources.qa_opacity
import org.churchpresenter.resources.generated.resources.qa_position
import org.churchpresenter.resources.generated.resources.qa_pos_bc
import org.churchpresenter.resources.generated.resources.qa_pos_bl
import org.churchpresenter.resources.generated.resources.qa_pos_br
import org.churchpresenter.resources.generated.resources.qa_pos_c
import org.churchpresenter.resources.generated.resources.qa_pos_cl
import org.churchpresenter.resources.generated.resources.qa_pos_cr
import org.churchpresenter.resources.generated.resources.qa_pos_tc
import org.churchpresenter.resources.generated.resources.qa_pos_tl
import org.churchpresenter.resources.generated.resources.qa_pos_tr
import org.churchpresenter.resources.generated.resources.qa_public
import org.churchpresenter.resources.generated.resources.qa_public_access
import org.churchpresenter.resources.generated.resources.qa_public_access_description
import org.churchpresenter.resources.generated.resources.qa_qr_bg_color
import org.churchpresenter.resources.generated.resources.qa_qr_code_shows
import org.churchpresenter.resources.generated.resources.qa_qr_fg_color
import org.churchpresenter.resources.generated.resources.qa_qr_message_default
import org.churchpresenter.resources.generated.resources.qa_qr_message_label
import org.churchpresenter.resources.generated.resources.qa_qr_message_reset
import org.churchpresenter.resources.generated.resources.qa_remote_dialog_description
import org.churchpresenter.resources.generated.resources.qa_remote_dialog_title
import org.churchpresenter.resources.generated.resources.qa_retry
import org.churchpresenter.resources.generated.resources.qa_server_hint
import org.churchpresenter.resources.generated.resources.qa_size
import org.churchpresenter.resources.generated.resources.qa_starting_tunnel
import org.churchpresenter.resources.generated.resources.qa_styling_qr_group
import org.churchpresenter.resources.generated.resources.qa_styling_text_group
import org.churchpresenter.resources.generated.resources.qa_submit_questions
import org.churchpresenter.resources.generated.resources.qa_text_color
import org.churchpresenter.resources.generated.resources.qa_transparent
import org.churchpresenter.ui.LocalMainWindowState
import org.churchpresenter.ui.centeredOnMainWindow
import org.churchpresenter.ui.ColorPickerField
import org.churchpresenter.ui.FontSettingsDropdown
import org.churchpresenter.ui.NumberSettingsTextField
import org.churchpresenter.ui.ShadowDetailRow
import org.churchpresenter.ui.SlimSlider
import org.churchpresenter.ui.TextStyleButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.ui.generateQRCodeBitmap
import org.churchpresenter.companionserver.TunnelStatus
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.ui.rememberSystemFonts
import org.jetbrains.compose.resources.stringResource

private const val POSITION_GRID_COLUMNS = 3

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
    val availableFonts = rememberSystemFonts()
    val mainWindowState = LocalMainWindowState.current
    val dialogWidth = 760.dp
    val dialogHeight = 700.dp
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
            availableFonts = availableFonts,
            scrollState = scrollState,
            copyText = { text ->
                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(text), null)
            },
            onDismiss = onDismiss
        )
    }
}

/**
 * Everything the Q&A remote window contains: the two share panels with their QR codes, the tunnel
 * controls, the display settings, and the addresses it derives for each.
 *
 * Held apart from [QARemoteDialog] because that function's remaining work is the `DialogWindow` it
 * opens and the grow-to-fit effect sizing it, neither of which can run on a headless machine.
 *
 * Three things are taken as parameters rather than reached for here, because each is a piece of the
 * machine rather than of the dialog: [availableFonts] comes from the machine's installed fonts,
 * [copyText] writes the system clipboard, and [scrollState] is shared with the sizing effect above.
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
    availableFonts: List<String>,
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
                        Button(
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
                                Button(
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
                                    Button(
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
                                    Button(
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
                                Button(
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
                                Button(
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
                        Button(
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

                Text(stringResource(Res.string.qa_display_styling), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Left: QR Code styling ────────────────────────
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.qa_styling_qr_group), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))

                        Text(stringResource(Res.string.qa_qr_message_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
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
                            FilledIconButton(
                                onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrCodeMessage = "")) } },
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(5.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.qa_qr_message_reset), modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorPickerField(label = stringResource(Res.string.qa_qr_fg_color), color = qaSettings.qrForegroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrForegroundColor = it)) } }, modifier = Modifier.weight(1f))
                            ColorPickerField(label = stringResource(Res.string.qa_qr_bg_color), color = qaSettings.qrBackgroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrBackgroundColor = it)) } }, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.qa_opacity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            SlimSlider(
                                value = qaSettings.qrBackgroundOpacity / 100f,
                                onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(qrBackgroundOpacity = (it * 100).toInt())) } },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                trailingLabel = "${qaSettings.qrBackgroundOpacity}%"
                            )
                        }
                    }

                    // ── Center: Position ──────────────────────────────
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.qa_position), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        val positions = listOf(
                            Constants.TOP_LEFT to stringResource(Res.string.qa_pos_tl),
                            Constants.TOP_CENTER to stringResource(Res.string.qa_pos_tc),
                            Constants.TOP_RIGHT to stringResource(Res.string.qa_pos_tr),
                            Constants.CENTER_LEFT to stringResource(Res.string.qa_pos_cl),
                            Constants.CENTER to stringResource(Res.string.qa_pos_c),
                            Constants.CENTER_RIGHT to stringResource(Res.string.qa_pos_cr),
                            Constants.BOTTOM_LEFT to stringResource(Res.string.qa_pos_bl),
                            Constants.BOTTOM_CENTER to stringResource(Res.string.qa_pos_bc),
                            Constants.BOTTOM_RIGHT to stringResource(Res.string.qa_pos_br),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                            positions.chunked(POSITION_GRID_COLUMNS).forEach { rowItems ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    rowItems.forEach { (posConst, posLabel) ->
                                        val isSelected = qaSettings.position == posConst
                                        Box(
                                            modifier = Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(3.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(position = posConst)) } },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(posLabel, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Right: Text & Background ─────────────────────
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.qa_styling_text_group), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))

                        ColorPickerField(label = stringResource(Res.string.qa_text_color), color = qaSettings.textColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(textColor = it)) } }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        TextStyleButtons(
                                bold = qaSettings.bold, italic = qaSettings.italic, underline = qaSettings.underline, shadow = qaSettings.shadow,
                                onBoldChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(bold = it)) } },
                                onItalicChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(italic = it)) } },
                                onUnderlineChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(underline = it)) } },
                                onShadowChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadow = it)) } }
                        )

                        AnimatedVisibility(visible = qaSettings.shadow) {
                            ShadowDetailRow(
                                shadowColor = qaSettings.shadowColor, shadowSize = qaSettings.shadowSize, shadowOpacity = qaSettings.shadowOpacity,
                                onColorChange = { c -> onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadowColor = c)) } },
                                onSizeChange = { v -> onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadowSize = v)) } },
                                onOpacityChange = { v -> onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(shadowOpacity = v)) } },
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FontSettingsDropdown(label = stringResource(Res.string.qa_font), value = qaSettings.fontType, fonts = availableFonts, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontType = it)) } }, modifier = Modifier.weight(1f))
                            NumberSettingsTextField(label = stringResource(Res.string.qa_size), initialText = qaSettings.fontSize, range = 8..200, onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(fontSize = it)) } })
                        }

                        Spacer(Modifier.height(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            val bgIsTransparent = qaSettings.backgroundColor.equals("transparent", ignoreCase = true)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                                if (bgIsTransparent) {
                                    OutlinedButton(
                                        onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = "#1E1E2E")) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                    ) { Text(stringResource(Res.string.qa_background_color) + " · " + stringResource(Res.string.qa_transparent), style = MaterialTheme.typography.labelSmall) }
                                } else {
                                    ColorPickerField(label = stringResource(Res.string.qa_background_color), color = qaSettings.backgroundColor, onColorChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = it)) } }, modifier = Modifier.weight(1f))
                                    OutlinedButton(
                                        onClick = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundColor = "transparent")) } },
                                        shape = RoundedCornerShape(6.dp)
                                    ) { Text(stringResource(Res.string.qa_transparent), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.qa_opacity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(4.dp))
                                SlimSlider(
                                    value = qaSettings.backgroundOpacity / 100f,
                                    onValueChange = { onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(backgroundOpacity = (it * 100).toInt())) } },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f),
                                    trailingLabel = "${qaSettings.backgroundOpacity}%"
                                )
                            }
                        }
                    }
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
