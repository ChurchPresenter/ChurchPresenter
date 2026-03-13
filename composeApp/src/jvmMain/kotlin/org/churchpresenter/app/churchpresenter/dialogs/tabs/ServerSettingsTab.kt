package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.api_key_hint
import churchpresenter.composeapp.generated.resources.api_key_label
import churchpresenter.composeapp.generated.resources.api_key_protection
import churchpresenter.composeapp.generated.resources.companion_server
import churchpresenter.composeapp.generated.resources.copy_api_key
import churchpresenter.composeapp.generated.resources.copy_url
import churchpresenter.composeapp.generated.resources.enable_server
import churchpresenter.composeapp.generated.resources.endpoint_desc_add_to_schedule
import churchpresenter.composeapp.generated.resources.endpoint_desc_bible_catalog
import churchpresenter.composeapp.generated.resources.endpoint_desc_bible_chapter
import churchpresenter.composeapp.generated.resources.endpoint_desc_filter_book
import churchpresenter.composeapp.generated.resources.endpoint_desc_filter_chapter
import churchpresenter.composeapp.generated.resources.endpoint_desc_filter_songbook
import churchpresenter.composeapp.generated.resources.endpoint_desc_picture_catalog
import churchpresenter.composeapp.generated.resources.endpoint_desc_picture_image
import churchpresenter.composeapp.generated.resources.endpoint_desc_presentation_catalog
import churchpresenter.composeapp.generated.resources.endpoint_desc_project
import churchpresenter.composeapp.generated.resources.endpoint_desc_schedule
import churchpresenter.composeapp.generated.resources.endpoint_desc_select_picture
import churchpresenter.composeapp.generated.resources.endpoint_desc_server_info
import churchpresenter.composeapp.generated.resources.endpoint_desc_slide_image
import churchpresenter.composeapp.generated.resources.endpoint_desc_song_catalog
import churchpresenter.composeapp.generated.resources.endpoint_desc_song_detail
import churchpresenter.composeapp.generated.resources.endpoint_desc_ws_add_to_schedule
import churchpresenter.composeapp.generated.resources.endpoint_desc_ws_project
import churchpresenter.composeapp.generated.resources.endpoint_desc_ws_realtime
import churchpresenter.composeapp.generated.resources.endpoint_desc_ws_select_picture
import churchpresenter.composeapp.generated.resources.generate_api_key
import churchpresenter.composeapp.generated.resources.server_description
import churchpresenter.composeapp.generated.resources.server_endpoints
import churchpresenter.composeapp.generated.resources.server_endpoints_actions
import churchpresenter.composeapp.generated.resources.server_endpoints_read
import churchpresenter.composeapp.generated.resources.server_endpoints_websocket
import churchpresenter.composeapp.generated.resources.server_payload_reference
import churchpresenter.composeapp.generated.resources.server_port
import churchpresenter.composeapp.generated.resources.server_port_hint
import churchpresenter.composeapp.generated.resources.server_port_note
import churchpresenter.composeapp.generated.resources.server_restart
import churchpresenter.composeapp.generated.resources.server_running
import churchpresenter.composeapp.generated.resources.server_stopped
import churchpresenter.composeapp.generated.resources.server_url_label
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

@Composable
fun ServerSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer
) {
    val isRunning by companionServer.isRunning.collectAsState()
    val serverUrl by companionServer.serverUrl.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var portText by remember(settings.serverSettings.port) {
        mutableStateOf(settings.serverSettings.port.toString())
    }
    var apiKeyText by remember(settings.serverSettings.apiKey) {
        mutableStateOf(settings.serverSettings.apiKey)
    }

    LaunchedEffect(settings.serverSettings.apiKeyEnabled, settings.serverSettings.apiKey) {
        companionServer.updateApiKey(
            enabled = settings.serverSettings.apiKeyEnabled,
            key = settings.serverSettings.apiKey
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── LEFT COLUMN: settings ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Text(
                    text = stringResource(Res.string.companion_server),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.server_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // ── Enable toggle + status in one row ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.enable_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { enable ->
                            val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                            if (enable) {
                                companionServer.start(port)
                                onSettingsChange { s ->
                                    s.copy(serverSettings = s.serverSettings.copy(enabled = true, port = port))
                                }
                            } else {
                                companionServer.stop()
                                onSettingsChange { s ->
                                    s.copy(serverSettings = s.serverSettings.copy(enabled = false))
                                }
                            }
                        }
                    )
                    Text(
                        text = if (isRunning) stringResource(Res.string.server_running)
                               else stringResource(Res.string.server_stopped),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // ── Port + note/Restart in one row ────────────────────────────
                SettingRow(label = stringResource(Res.string.server_port)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { v ->
                                if (v.length <= 5 && v.all(Char::isDigit)) {
                                    portText = v
                                    v.toIntOrNull()?.let { port ->
                                        onSettingsChange { s ->
                                            s.copy(serverSettings = s.serverSettings.copy(port = port))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            enabled = !isRunning,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text(stringResource(Res.string.server_port_hint)) }
                        )
                        if (isRunning) {
                            Button(
                                onClick = {
                                    val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                                    companionServer.stop()
                                    companionServer.start(port)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text(stringResource(Res.string.server_restart), style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(
                                text = stringResource(Res.string.server_port_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Server URL + Copy in one row (shown when running) ─────────
                if (isRunning && serverUrl.isNotBlank()) {
                    SettingRow(label = stringResource(Res.string.server_url_label)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.copy_url), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── API Key protection toggle ─────────────────────────────────
                SettingRow(label = stringResource(Res.string.api_key_protection)) {
                    Switch(
                        checked = settings.serverSettings.apiKeyEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(apiKeyEnabled = enabled))
                            }
                        }
                    )
                }

                // ── API Key field + Generate + Copy all in one row ────────────
                if (settings.serverSettings.apiKeyEnabled) {
                    SettingRow(label = stringResource(Res.string.api_key_label)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { v ->
                                    apiKeyText = v
                                    onSettingsChange { s ->
                                        s.copy(serverSettings = s.serverSettings.copy(apiKey = v))
                                    }
                                },
                                modifier = Modifier.width(350.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.api_key_hint),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                            Button(
                                onClick = {
                                    val newKey = UUID.randomUUID().toString().replace("-", "")
                                    apiKeyText = newKey
                                    onSettingsChange { s ->
                                        s.copy(serverSettings = s.serverSettings.copy(apiKey = newKey))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.generate_api_key), style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(apiKeyText)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.copy_api_key), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── RIGHT COLUMN: available endpoints ─────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(Res.string.server_endpoints),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(2.dp))

                // ── Read endpoints ────────────────────────────────────────────
                Text(
                    text = stringResource(Res.string.server_endpoints_read),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "GET ${Constants.ENDPOINT_INFO}" to stringResource(Res.string.endpoint_desc_server_info),
                    "GET ${Constants.ENDPOINT_SONGS}" to stringResource(Res.string.endpoint_desc_song_catalog),
                    "GET ${Constants.ENDPOINT_SONGS}?songbook=Name" to stringResource(Res.string.endpoint_desc_filter_songbook),
                    "GET ${Constants.ENDPOINT_SONG_DETAIL}" to stringResource(Res.string.endpoint_desc_song_detail),
                    "GET ${Constants.ENDPOINT_SONG_DETAIL}?songbook=Name" to stringResource(Res.string.endpoint_desc_song_detail),
                    "GET ${Constants.ENDPOINT_BIBLE}" to stringResource(Res.string.endpoint_desc_bible_catalog),
                    "GET ${Constants.ENDPOINT_BIBLE}?book=Genesis" to stringResource(Res.string.endpoint_desc_filter_book),
                    "GET ${Constants.ENDPOINT_BIBLE}?book=Genesis&chapter=1" to stringResource(Res.string.endpoint_desc_filter_chapter),
                    "GET ${Constants.ENDPOINT_BIBLE}?book=1&chapter=1" to stringResource(Res.string.endpoint_desc_bible_chapter),
                    "GET ${Constants.ENDPOINT_SCHEDULE}" to stringResource(Res.string.endpoint_desc_schedule),
                    "GET ${Constants.ENDPOINT_PRESENTATIONS}" to stringResource(Res.string.endpoint_desc_presentation_catalog),
                    "GET ${Constants.ENDPOINT_PRESENTATIONS}/{id}/slides/{index}" to stringResource(Res.string.endpoint_desc_slide_image),
                    "GET ${Constants.ENDPOINT_PICTURES}" to stringResource(Res.string.endpoint_desc_picture_catalog),
                    "GET ${Constants.ENDPOINT_PICTURES}/{id}/images/{index}" to stringResource(Res.string.endpoint_desc_picture_image)
                ).forEach { (endpoint, description) ->
                    EndpointCard(endpoint = endpoint, description = description)
                }

                HorizontalDivider()

                // ── Action endpoints ──────────────────────────────────────────
                Text(
                    text = stringResource(Res.string.server_endpoints_actions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "POST ${Constants.ENDPOINT_SCHEDULE_ADD}" to stringResource(Res.string.endpoint_desc_add_to_schedule),
                    "POST ${Constants.ENDPOINT_PROJECT}" to stringResource(Res.string.endpoint_desc_project),
                    "POST ${Constants.ENDPOINT_PICTURES}/select" to stringResource(Res.string.endpoint_desc_select_picture)
                ).forEach { (endpoint, description) ->
                    EndpointCard(endpoint = endpoint, description = description, isAction = true)
                }

                HorizontalDivider()

                // ── WebSocket ─────────────────────────────────────────────────
                Text(
                    text = stringResource(Res.string.server_endpoints_websocket),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "WS  ${Constants.ENDPOINT_WS}" to stringResource(Res.string.endpoint_desc_ws_realtime),
                    "CMD ${Constants.WS_CMD_ADD_TO_SCHEDULE}" to stringResource(Res.string.endpoint_desc_ws_add_to_schedule),
                    "CMD ${Constants.WS_CMD_PROJECT}" to stringResource(Res.string.endpoint_desc_ws_project),
                    "CMD ${Constants.WS_CMD_SELECT_PICTURE}" to stringResource(Res.string.endpoint_desc_ws_select_picture)
                ).forEach { (endpoint, description) ->
                    EndpointCard(endpoint = endpoint, description = description)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Payload reference — mirrors ScheduleItem subclass fields ─
                Text(
                    text = stringResource(Res.string.server_payload_reference),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PayloadCard(
                    subclassName = ScheduleItem.SongItem::class.simpleName!!,
                    fields = listOf(
                        ScheduleItem.SongItem::songNumber.name,
                        ScheduleItem.SongItem::title.name,
                        ScheduleItem.SongItem::songbook.name
                    )
                )
                PayloadCard(
                    subclassName = ScheduleItem.BibleVerseItem::class.simpleName!!,
                    fields = listOf(
                        ScheduleItem.BibleVerseItem::bookName.name,
                        ScheduleItem.BibleVerseItem::chapter.name,
                        ScheduleItem.BibleVerseItem::verseNumber.name,
                        ScheduleItem.BibleVerseItem::verseText.name
                    )
                )
                PayloadCard(
                    subclassName = ScheduleItem.PresentationItem::class.simpleName!!,
                    fields = listOf(
                        ScheduleItem.PresentationItem::filePath.name,
                        ScheduleItem.PresentationItem::fileName.name,
                        ScheduleItem.PresentationItem::slideCount.name,
                        ScheduleItem.PresentationItem::fileType.name
                    )
                )
                PayloadCard(
                    subclassName = ScheduleItem.PictureItem::class.simpleName!!,
                    fields = listOf(
                        ScheduleItem.PictureItem::folderPath.name,
                        ScheduleItem.PictureItem::folderName.name,
                        ScheduleItem.PictureItem::imageCount.name
                    )
                )
                PayloadCard(
                    subclassName = ScheduleItem.MediaItem::class.simpleName!!,
                    fields = listOf(
                        ScheduleItem.MediaItem::mediaUrl.name,
                        ScheduleItem.MediaItem::mediaTitle.name,
                        ScheduleItem.MediaItem::mediaType.name
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun EndpointCard(endpoint: String, description: String, isAction: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isAction) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = endpoint,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (isAction) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Displays the field names of a [ScheduleItem] subclass as the expected
 * JSON payload shape for the remote API actions.
 */
@Composable
private fun PayloadCard(subclassName: String, fields: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = "{ \"item\": $subclassName }",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary
        )
        fields.forEach { field ->
            Text(
                text = "  \"$field\": …",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
