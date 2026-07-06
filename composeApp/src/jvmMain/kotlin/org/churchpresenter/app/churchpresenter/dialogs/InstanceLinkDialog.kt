package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.api_key_hint
import churchpresenter.composeapp.generated.resources.api_key_label
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.connect
import churchpresenter.composeapp.generated.resources.instance_link_allow_push_to_schedule
import churchpresenter.composeapp.generated.resources.instance_link_autoconnect
import churchpresenter.composeapp.generated.resources.instance_link_description
import churchpresenter.composeapp.generated.resources.instance_link_host
import churchpresenter.composeapp.generated.resources.instance_link_host_hint
import churchpresenter.composeapp.generated.resources.instance_link_last_received
import churchpresenter.composeapp.generated.resources.instance_link_port
import churchpresenter.composeapp.generated.resources.instance_link_schedule_count
import churchpresenter.composeapp.generated.resources.instance_link_title
import churchpresenter.composeapp.generated.resources.menu_disconnect
import churchpresenter.composeapp.generated.resources.obs_mode_announcements
import churchpresenter.composeapp.generated.resources.obs_mode_bible
import churchpresenter.composeapp.generated.resources.obs_mode_canvas
import churchpresenter.composeapp.generated.resources.obs_mode_lower_third
import churchpresenter.composeapp.generated.resources.obs_mode_media
import churchpresenter.composeapp.generated.resources.obs_mode_none
import churchpresenter.composeapp.generated.resources.obs_mode_pictures
import churchpresenter.composeapp.generated.resources.obs_mode_presentation
import churchpresenter.composeapp.generated.resources.obs_mode_qa
import churchpresenter.composeapp.generated.resources.obs_mode_songs
import churchpresenter.composeapp.generated.resources.obs_mode_website
import churchpresenter.composeapp.generated.resources.tab_dictionary
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.ConnectionStatusRow
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkSettings
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.LiveStateDto
import org.jetbrains.compose.resources.stringResource

@Composable
fun InstanceLinkDialog(
    isVisible: Boolean,
    settings: InstanceLinkSettings,
    connectionStatus: InstanceLinkStatus,
    remoteLiveState: LiveStateDto?,
    remoteScheduleCount: Int,
    onConnect: (host: String, port: Int, apiKey: String, autoConnect: Boolean, allowPushToSchedule: Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var host by remember(isVisible) { mutableStateOf(settings.primaryHost) }
    var portText by remember(isVisible) { mutableStateOf(if (settings.primaryPort > 0) settings.primaryPort.toString() else "") }
    var apiKey by remember(isVisible) { mutableStateOf(settings.apiKey) }
    var autoConnect by remember(isVisible) { mutableStateOf(settings.autoConnect) }
    var allowPushToSchedule by remember(isVisible) { mutableStateOf(settings.allowPushToSchedule) }

    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, 460.dp, 620.dp),
        width = 460.dp,
        height = 620.dp
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.instance_link_title),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(Res.string.instance_link_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.instance_link_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ConnectionStatusRow(connectionStatus)

                    if (connectionStatus == InstanceLinkStatus.CONNECTED) {
                        Text(
                            text = stringResource(Res.string.instance_link_schedule_count, remoteScheduleCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        remoteLiveState?.let { state ->
                            Text(
                                text = stringResource(Res.string.instance_link_last_received, liveStateSummary(state)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingRow(label = stringResource(Res.string.instance_link_host)) {
                        SettingsTextField(
                            value = host,
                            onValueChange = { host = it },
                            placeholder = { Text(stringResource(Res.string.instance_link_host_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    SettingRow(label = stringResource(Res.string.instance_link_port)) {
                        SettingsTextField(
                            value = portText,
                            onValueChange = { new -> if (new.all(Char::isDigit)) portText = new },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    SettingRow(label = stringResource(Res.string.api_key_label)) {
                        SettingsTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = { Text(stringResource(Res.string.api_key_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                        Text(
                            stringResource(Res.string.instance_link_autoconnect),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(checked = allowPushToSchedule, onCheckedChange = { allowPushToSchedule = it })
                        Text(
                            stringResource(Res.string.instance_link_allow_push_to_schedule),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (connectionStatus != InstanceLinkStatus.DISCONNECTED) {
                        TextButton(shape = RoundedCornerShape(6.dp), onClick = onDisconnect) {
                            Text(
                                stringResource(Res.string.menu_disconnect),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                        Text(
                            stringResource(Res.string.cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            val port = portText.toIntOrNull() ?: return@Button
                            onConnect(host.trim(), port, apiKey.trim(), autoConnect, allowPushToSchedule)
                            onDismiss()
                        },
                        enabled = host.isNotBlank() && portText.toIntOrNull() != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            stringResource(Res.string.connect),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/** Short human-readable summary of a [LiveStateDto] for the "Last received" readout. */
@Composable
private fun liveStateSummary(state: LiveStateDto): String = when (state.contentType) {
    "BIBLE" -> state.bookName?.let { "$it ${state.chapter}:${state.verseNumber}" }
        ?: stringResource(Res.string.obs_mode_bible)
    "LYRICS" -> state.songTitle ?: stringResource(Res.string.obs_mode_songs)
    "PICTURES" -> stringResource(Res.string.obs_mode_pictures)
    "PRESENTATION" -> stringResource(Res.string.obs_mode_presentation)
    "MEDIA" -> state.mediaUrl?.substringAfterLast('/') ?: stringResource(Res.string.obs_mode_media)
    "ANNOUNCEMENTS" -> state.announcementText?.take(40) ?: stringResource(Res.string.obs_mode_announcements)
    "WEBSITE" -> state.websiteTitle ?: state.websiteUrl ?: stringResource(Res.string.obs_mode_website)
    "CANVAS" -> state.sceneName ?: stringResource(Res.string.obs_mode_canvas)
    "QA" -> state.questionText?.take(40) ?: stringResource(Res.string.obs_mode_qa)
    "DICTIONARY" -> state.dictionaryWord ?: stringResource(Res.string.tab_dictionary)
    "LOWER_THIRD" -> stringResource(Res.string.obs_mode_lower_third)
    "NONE" -> stringResource(Res.string.obs_mode_none)
    else -> state.contentType
}


