package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.obs_connect
import churchpresenter.composeapp.generated.resources.obs_default_scene
import churchpresenter.composeapp.generated.resources.obs_default_scene_hint
import churchpresenter.composeapp.generated.resources.obs_description
import churchpresenter.composeapp.generated.resources.obs_disconnect
import churchpresenter.composeapp.generated.resources.obs_enable
import churchpresenter.composeapp.generated.resources.obs_host
import churchpresenter.composeapp.generated.resources.obs_host_hint
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
import churchpresenter.composeapp.generated.resources.obs_mode_stt
import churchpresenter.composeapp.generated.resources.obs_mode_website
import churchpresenter.composeapp.generated.resources.obs_password
import churchpresenter.composeapp.generated.resources.obs_password_hint
import churchpresenter.composeapp.generated.resources.obs_port
import churchpresenter.composeapp.generated.resources.obs_scene_hint
import churchpresenter.composeapp.generated.resources.obs_scene_mappings
import churchpresenter.composeapp.generated.resources.obs_scene_mappings_desc
import churchpresenter.composeapp.generated.resources.obs_status_connected
import churchpresenter.composeapp.generated.resources.obs_status_connecting
import churchpresenter.composeapp.generated.resources.obs_status_disconnected
import churchpresenter.composeapp.generated.resources.obs_status_error
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.OBSSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.jetbrains.compose.resources.stringResource

@Composable
fun OBSSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    obsManager: OBSWebSocketManager
) {
    val obs = settings.obsSettings
    fun update(block: OBSSettings.() -> OBSSettings) {
        onSettingsChange { s -> s.copy(obsSettings = s.obsSettings.block()) }
    }

    var hostText by remember(obs.host) { mutableStateOf(obs.host) }
    var portText by remember(obs.port) { mutableStateOf(obs.port.toString()) }
    var passwordText by remember(obs.password) { mutableStateOf(obs.password) }

    val status by obsManager.status
    val errorMessage by obsManager.errorMessage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val cardModifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)

            // ── Connection card ────────────────────────────────────────────────
            Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ObsSectionHeader("OBS WebSocket")
                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.obs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))

                // Enable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.obs_enable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = obs.enabled,
                        onCheckedChange = { update { copy(enabled = it) } }
                    )
                }

                if (obs.enabled) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))

                    // Host + Port row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hostText,
                            onValueChange = {
                                hostText = it
                                update { copy(host = it) }
                            },
                            label = { Text(stringResource(Res.string.obs_host)) },
                            placeholder = { Text(stringResource(Res.string.obs_host_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { v ->
                                portText = v
                                v.toIntOrNull()?.let { update { copy(port = it) } }
                            },
                            label = { Text(stringResource(Res.string.obs_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            update { copy(password = it) }
                        },
                        label = { Text(stringResource(Res.string.obs_password)) },
                        placeholder = { Text(stringResource(Res.string.obs_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // Connect/Disconnect + status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (status == OBSWebSocketManager.ConnectionStatus.CONNECTED) {
                            Button(
                                onClick = { obsManager.disconnect() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(Res.string.obs_disconnect))
                            }
                        } else {
                            Button(
                                onClick = {
                                    val port = portText.toIntOrNull() ?: 4455
                                    obsManager.connect(hostText, port, passwordText)
                                },
                                enabled = status != OBSWebSocketManager.ConnectionStatus.CONNECTING
                            ) {
                                Text(stringResource(Res.string.obs_connect))
                            }
                        }

                        val (statusText, statusColor) = when (status) {
                            OBSWebSocketManager.ConnectionStatus.CONNECTED ->
                                stringResource(Res.string.obs_status_connected) to Color(0xFF4CAF50)
                            OBSWebSocketManager.ConnectionStatus.CONNECTING ->
                                stringResource(Res.string.obs_status_connecting) to Color(0xFFFFC107)
                            OBSWebSocketManager.ConnectionStatus.ERROR ->
                                "${stringResource(Res.string.obs_status_error)}: $errorMessage" to MaterialTheme.colorScheme.error
                            OBSWebSocketManager.ConnectionStatus.DISCONNECTED ->
                                stringResource(Res.string.obs_status_disconnected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        }
                        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    }
                }
            }

            // ── Scene Mappings card ────────────────────────────────────────────
            if (obs.enabled) {
                Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    ObsSectionHeader(stringResource(Res.string.obs_scene_mappings))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.obs_scene_mappings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))

                    // Default scene
                    OutlinedTextField(
                        value = obs.defaultScene,
                        onValueChange = { update { copy(defaultScene = it) } },
                        label = { Text(stringResource(Res.string.obs_default_scene)) },
                        placeholder = { Text(stringResource(Res.string.obs_default_scene_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))

                    // One row per Presenting mode
                    val modes = listOf(
                        Presenting.BIBLE to stringResource(Res.string.obs_mode_bible),
                        Presenting.LYRICS to stringResource(Res.string.obs_mode_songs),
                        Presenting.PICTURES to stringResource(Res.string.obs_mode_pictures),
                        Presenting.PRESENTATION to stringResource(Res.string.obs_mode_presentation),
                        Presenting.MEDIA to stringResource(Res.string.obs_mode_media),
                        Presenting.LOWER_THIRD to stringResource(Res.string.obs_mode_lower_third),
                        Presenting.ANNOUNCEMENTS to stringResource(Res.string.obs_mode_announcements),
                        Presenting.WEBSITE to stringResource(Res.string.obs_mode_website),
                        Presenting.CANVAS to stringResource(Res.string.obs_mode_canvas),
                        Presenting.QA to stringResource(Res.string.obs_mode_qa),
                        Presenting.STT to stringResource(Res.string.obs_mode_stt),
                        Presenting.NONE to stringResource(Res.string.obs_mode_none),
                    )
                    modes.forEach { (mode, label) ->
                        SceneMappingRow(
                            label = label,
                            value = obs.sceneMappings[mode.name] ?: "",
                            onValueChange = { scene ->
                                val updated = obs.sceneMappings.toMutableMap()
                                if (scene.isBlank()) updated.remove(mode.name) else updated[mode.name] = scene
                                update { copy(sceneMappings = updated) }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsSectionHeader(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SceneMappingRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(130.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(Res.string.obs_scene_hint), style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}
