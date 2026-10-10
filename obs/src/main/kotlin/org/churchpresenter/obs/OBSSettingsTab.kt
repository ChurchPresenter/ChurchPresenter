package org.churchpresenter.obs

import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SettingsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.obs_connect
import org.churchpresenter.strings.generated.resources.obs_default_scene
import org.churchpresenter.strings.generated.resources.obs_default_scene_hint
import org.churchpresenter.strings.generated.resources.obs_description
import org.churchpresenter.strings.generated.resources.obs_disconnect
import org.churchpresenter.strings.generated.resources.obs_enable
import org.churchpresenter.strings.generated.resources.obs_host
import org.churchpresenter.strings.generated.resources.obs_host_hint
import org.churchpresenter.strings.generated.resources.obs_mode_announcements
import org.churchpresenter.strings.generated.resources.obs_mode_bible
import org.churchpresenter.strings.generated.resources.obs_mode_canvas
import org.churchpresenter.strings.generated.resources.obs_mode_lower_third
import org.churchpresenter.strings.generated.resources.obs_mode_media
import org.churchpresenter.strings.generated.resources.obs_mode_none
import org.churchpresenter.strings.generated.resources.obs_mode_pictures
import org.churchpresenter.strings.generated.resources.presentation
import org.churchpresenter.strings.generated.resources.obs_mode_qa
import org.churchpresenter.strings.generated.resources.obs_mode_songs
import org.churchpresenter.strings.generated.resources.obs_mode_stt
import org.churchpresenter.strings.generated.resources.obs_mode_website
import org.churchpresenter.strings.generated.resources.obs_password
import org.churchpresenter.strings.generated.resources.obs_password_hint
import org.churchpresenter.strings.generated.resources.obs_scene_hint
import org.churchpresenter.strings.generated.resources.obs_scene_mappings
import org.churchpresenter.strings.generated.resources.obs_scene_mappings_desc
import org.churchpresenter.strings.generated.resources.obs_section_connection
import org.churchpresenter.strings.generated.resources.obs_status_connected
import org.churchpresenter.strings.generated.resources.obs_status_connecting
import org.churchpresenter.strings.generated.resources.obs_status_disconnected
import org.churchpresenter.strings.generated.resources.obs_status_error
import org.churchpresenter.sharedui.composables.SettingRow
import org.churchpresenter.sharedui.composables.SettingSwitchRow
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/** OBS WebSocket's own default port, used when the field does not hold a number. */
private const val DEFAULT_OBS_PORT = 4455
private const val TRAILING_SPACER_WEIGHT = 3f

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

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = SettingsScrollbarGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Connection card ────────────────────────────────────────────────
            SettingsSection(
                title = stringResource(Res.string.obs_section_connection),
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)
            ) {
                Text(
                    text = stringResource(Res.string.obs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))

                SettingSwitchRow(
                    label = Res.string.obs_enable,
                    checked = obs.enabled,
                    onCheckedChange = { update { copy(enabled = it) } }
                )

                if (obs.enabled) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))

                    SettingRow(label = Res.string.obs_host) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.widthIn(max = 350.dp)
                        ) {
                            SettingsTextField(
                                value = hostText,
                                onValueChange = {
                                    hostText = it
                                    update { copy(host = it) }
                                },
                                placeholder = { Text(stringResource(Res.string.obs_host_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            SettingsTextField(
                                value = portText,
                                onValueChange = { v ->
                                    portText = v
                                    v.toIntOrNull()?.let { update { copy(port = it) } }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(68.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    SettingRow(label = Res.string.obs_password) {
                        SettingsTextField(
                            value = passwordText,
                            onValueChange = {
                                passwordText = it
                                update { copy(password = it) }
                            },
                            placeholder = { Text(stringResource(Res.string.obs_password_hint)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.widthIn(max = 350.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Connect/Disconnect + status
                    ObsConnectionControls(
                        status = status,
                        errorMessage = errorMessage,
                        onConnect = {
                            obsManager.connect(hostText, portText.toIntOrNull() ?: DEFAULT_OBS_PORT, passwordText)
                        },
                        onDisconnect = { obsManager.disconnect() },
                    )
                }
            }

            // ── Scene Mappings card ────────────────────────────────────────────
            if (obs.enabled) ObsSceneMappingsCard(obs, ::update)
        }
        SettingsScrollbar(scrollState)
    }
}

/** Connect or disconnect, and what state the connection is in. */
@Composable
private fun ObsConnectionControls(
    status: OBSWebSocketManager.ConnectionStatus,
    errorMessage: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (status == OBSWebSocketManager.ConnectionStatus.CONNECTED) {
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(Res.string.obs_disconnect))
            }
        } else {
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = onConnect,
                enabled = status != OBSWebSocketManager.ConnectionStatus.CONNECTING,
                modifier = Modifier.guideTarget(GuideTargets.OBS_CONNECT),
            ) {
                Text(stringResource(Res.string.obs_connect))
            }
        }

        val (statusText, statusColor) = when (status) {
            OBSWebSocketManager.ConnectionStatus.CONNECTED ->
                stringResource(Res.string.obs_status_connected) to MaterialTheme.semantic.success
            OBSWebSocketManager.ConnectionStatus.CONNECTING ->
                stringResource(Res.string.obs_status_connecting) to MaterialTheme.semantic.warning
            OBSWebSocketManager.ConnectionStatus.ERROR ->
                "${stringResource(Res.string.obs_status_error)}: $errorMessage" to
                    MaterialTheme.colorScheme.error
            OBSWebSocketManager.ConnectionStatus.DISCONNECTED ->
                stringResource(Res.string.obs_status_disconnected) to
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
    }
}

/** The default scene, then the scene each kind of content switches OBS to, two to a row. */
@Composable
private fun ObsSceneMappingsCard(obs: OBSSettings, update: (OBSSettings.() -> OBSSettings) -> Unit) {
    SettingsSection(
        title = stringResource(Res.string.obs_scene_mappings),
        modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)
    ) {
        Text(
            text = stringResource(Res.string.obs_scene_mappings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))

        // Default scene
        SettingRow(label = Res.string.obs_default_scene) {
            SettingsTextField(
                value = obs.defaultScene,
                onValueChange = { update { copy(defaultScene = it) } },
                placeholder = { Text(stringResource(Res.string.obs_default_scene_hint)) },
                singleLine = true,
                modifier = Modifier.widthIn(max = 350.dp).guideTarget(GuideTargets.OBS_DEFAULT_SCENE)
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))

        // Two scene mappings per row
        val modes = listOf(
            Presenting.BIBLE to stringResource(Res.string.obs_mode_bible),
            Presenting.LYRICS to stringResource(Res.string.obs_mode_songs),
            Presenting.PICTURES to stringResource(Res.string.obs_mode_pictures),
            Presenting.PRESENTATION to stringResource(Res.string.presentation),
            Presenting.MEDIA to stringResource(Res.string.obs_mode_media),
            Presenting.LOWER_THIRD to stringResource(Res.string.obs_mode_lower_third),
            Presenting.ANNOUNCEMENTS to stringResource(Res.string.obs_mode_announcements),
            Presenting.WEBSITE to stringResource(Res.string.obs_mode_website),
            Presenting.CANVAS to stringResource(Res.string.obs_mode_canvas),
            Presenting.QA to stringResource(Res.string.obs_mode_qa),
            Presenting.STT to stringResource(Res.string.obs_mode_stt),
            Presenting.NONE to stringResource(Res.string.obs_mode_none),
        )
        modes.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (mode0, label0) = pair[0]
                Text(
                    text = label0,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                SceneMappingField(obs, mode0, update, Modifier.weight(2f))
                if (pair.size == 2) {
                    val (mode1, label1) = pair[1]
                    Text(
                        text = label1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    SceneMappingField(obs, mode1, update, Modifier.weight(2f))
                } else {
                    Spacer(Modifier.weight(TRAILING_SPACER_WEIGHT))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

}

/** The OBS scene [mode] switches to; blank removes the mapping. */
@Composable
private fun SceneMappingField(
    obs: OBSSettings,
    mode: Presenting,
    update: (OBSSettings.() -> OBSSettings) -> Unit,
    modifier: Modifier,
) {
    SettingsTextField(
        value = obs.sceneMappings[mode.name] ?: "",
        onValueChange = { scene ->
            val updated = obs.sceneMappings.toMutableMap()
            if (scene.isBlank()) updated.remove(mode.name) else updated[mode.name] = scene
            update { copy(sceneMappings = updated) }
        },
        placeholder = { Text(stringResource(Res.string.obs_scene_hint)) },
        singleLine = true,
        modifier = modifier
    )
}
