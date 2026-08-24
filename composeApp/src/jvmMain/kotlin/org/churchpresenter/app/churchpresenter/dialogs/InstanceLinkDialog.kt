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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.api_key_hint
import org.churchpresenter.resources.generated.resources.api_key_label
import org.churchpresenter.resources.generated.resources.cancel
import org.churchpresenter.resources.generated.resources.connect
import org.churchpresenter.resources.generated.resources.instance_link_allow_push_to_schedule
import org.churchpresenter.resources.generated.resources.instance_link_autoconnect
import org.churchpresenter.resources.generated.resources.instance_link_description
import org.churchpresenter.resources.generated.resources.instance_link_host
import org.churchpresenter.resources.generated.resources.instance_link_host_hint
import org.churchpresenter.resources.generated.resources.instance_link_bible_sync_full_replica
import org.churchpresenter.resources.generated.resources.instance_link_bible_sync_mode
import org.churchpresenter.resources.generated.resources.instance_link_bible_sync_reference_only
import org.churchpresenter.resources.generated.resources.instance_link_last_received
import org.churchpresenter.resources.generated.resources.instance_link_last_update_age
import org.churchpresenter.resources.generated.resources.instance_link_mirror_backgrounds
import org.churchpresenter.resources.generated.resources.instance_link_port
import org.churchpresenter.resources.generated.resources.instance_link_reconnect_delay
import org.churchpresenter.resources.generated.resources.instance_link_reconnect_delay_hint
import org.churchpresenter.resources.generated.resources.instance_link_role
import org.churchpresenter.resources.generated.resources.instance_link_role_controlled
import org.churchpresenter.resources.generated.resources.instance_link_role_controller
import org.churchpresenter.resources.generated.resources.instance_link_schedule_count
import org.churchpresenter.resources.generated.resources.instance_link_title
import org.churchpresenter.resources.generated.resources.menu_disconnect
import org.churchpresenter.resources.generated.resources.obs_mode_announcements
import org.churchpresenter.resources.generated.resources.obs_mode_bible
import org.churchpresenter.resources.generated.resources.obs_mode_canvas
import org.churchpresenter.resources.generated.resources.obs_mode_lower_third
import org.churchpresenter.resources.generated.resources.obs_mode_media
import org.churchpresenter.resources.generated.resources.obs_mode_none
import org.churchpresenter.resources.generated.resources.obs_mode_pictures
import org.churchpresenter.resources.generated.resources.obs_mode_presentation
import org.churchpresenter.resources.generated.resources.obs_mode_qa
import org.churchpresenter.resources.generated.resources.obs_mode_songs
import org.churchpresenter.resources.generated.resources.obs_mode_website
import org.churchpresenter.resources.generated.resources.save
import org.churchpresenter.resources.generated.resources.tab_dictionary
import org.churchpresenter.resources.generated.resources.unit_ms
import org.churchpresenter.ui.LocalMainWindowState
import org.churchpresenter.ui.centeredOnMainWindow
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.composables.ConnectionStatusRow
import org.churchpresenter.ui.SettingRow
import org.churchpresenter.ui.SettingsTextField
import org.churchpresenter.settings.BibleSyncMode
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.companionserver.InstanceLinkStatus
import org.churchpresenter.companionserver.LiveStateDto
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.ui.LabeledSwitch
import org.churchpresenter.ui.LabeledRadioButton

private const val STATUS_POLL_MS = 1000L
private const val SECONDS_PER_MINUTE = 60
private const val SUMMARY_PREVIEW_CHARS = 40

@Composable
fun InstanceLinkDialog(
    isVisible: Boolean,
    settings: InstanceLinkSettings,
    connectionStatus: InstanceLinkStatus,
    remoteLiveState: LiveStateDto?,
    remoteScheduleCount: Int,
    /** Wall-clock ms of the last WS message from the primary, null while not connected. */
    lastMessageAtMs: Long? = null,
    onConnect: (InstanceLinkSettings) -> Unit,
    /** Persists the edited settings without touching the connection — see [InstanceLinkDialogContent]. */
    onSave: (InstanceLinkSettings) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, 760.dp, 620.dp),
        width = 760.dp,
        height = 620.dp
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.instance_link_title),
        resizable = false
    ) {
        InstanceLinkDialogContent(
            isVisible = isVisible,
            settings = settings,
            connectionStatus = connectionStatus,
            remoteLiveState = remoteLiveState,
            remoteScheduleCount = remoteScheduleCount,
            lastMessageAtMs = lastMessageAtMs,
            onConnect = onConnect,
            onSave = onSave,
            onDisconnect = onDisconnect,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Lower bound on the reconnect delay: the setting is the *floor* of the client's exponential
 * backoff, so a zero (or a stray "1") would have a dropped link retry in a tight loop against a
 * primary that is probably still restarting. The upper bound keeps a mistyped extra digit from
 * silently turning a link into one that looks dead for the rest of a service.
 */
internal const val INSTANCE_LINK_MIN_RECONNECT_DELAY_MS = 250
internal const val INSTANCE_LINK_MAX_RECONNECT_DELAY_MS = 60_000

/**
 * Reads the reconnect-delay field, falling back to [fallback] for anything unusable — an empty
 * field, or a number too long to be an Int — and clamping the rest into the supported range.
 * Never throws: this runs on the operator's keystrokes during a service.
 */
internal fun parseReconnectDelayMs(text: String, fallback: Int): Int =
    text.trim().toIntOrNull()?.coerceIn(INSTANCE_LINK_MIN_RECONNECT_DELAY_MS, INSTANCE_LINK_MAX_RECONNECT_DELAY_MS)
        ?: fallback

@Composable
internal fun InstanceLinkDialogContent(
    isVisible: Boolean,
    settings: InstanceLinkSettings,
    connectionStatus: InstanceLinkStatus,
    remoteLiveState: LiveStateDto?,
    remoteScheduleCount: Int,
    lastMessageAtMs: Long? = null,
    onConnect: (InstanceLinkSettings) -> Unit,
    /**
     * Persists the edited settings and leaves the connection alone. Connect used to be the only way
     * to save, so changing a setting that is consumed reactively — the role, Bible sync mode,
     * background mirroring — forced a reconnect that nothing about the change required.
     */
    onSave: (InstanceLinkSettings) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember(isVisible) { mutableStateOf(settings.primaryHost) }
    var portText by remember(isVisible) { mutableStateOf(if (settings.primaryPort > 0) settings.primaryPort.toString() else "") }
    var apiKey by remember(isVisible) { mutableStateOf(settings.apiKey) }
    var autoConnect by remember(isVisible) { mutableStateOf(settings.autoConnect) }
    var reconnectDelayText by remember(isVisible) { mutableStateOf(settings.reconnectDelayMs.toString()) }
    var allowPushToSchedule by remember(isVisible) { mutableStateOf(settings.allowPushToSchedule) }
    var bibleSyncMode by remember(isVisible) { mutableStateOf(settings.bibleSyncMode) }
    var mirrorBackgrounds by remember(isVisible) { mutableStateOf(settings.mirrorBackgrounds) }
    var role by remember(isVisible) { mutableStateOf(settings.role) }

    // Everything the operator has edited, folded back onto the settings this dialog was opened with
    // so the fields it does not show (deviceId, enabled) are carried through untouched.
    fun edited(): InstanceLinkSettings = settings.copy(
        primaryHost = host.trim(),
        primaryPort = portText.toIntOrNull() ?: settings.primaryPort,
        apiKey = apiKey.trim(),
        autoConnect = autoConnect,
        reconnectDelayMs = parseReconnectDelayMs(reconnectDelayText, settings.reconnectDelayMs),
        allowPushToSchedule = allowPushToSchedule,
        bibleSyncMode = bibleSyncMode,
        mirrorBackgrounds = mirrorBackgrounds,
        role = role
    )

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

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                            if (lastMessageAtMs != null) {
                                // 1s ticker keeps the age readout current while the dialog is open
                                var ageNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                                LaunchedEffect(Unit) {
                                    while (true) {
                                        ageNowMs = System.currentTimeMillis()
                                        delay(STATUS_POLL_MS)
                                    }
                                }
                                val ageSeconds = ((ageNowMs - lastMessageAtMs) / 1000).coerceAtLeast(0)
                                val ageText = formatInstanceLinkAge(ageSeconds)
                                Text(
                                    text = stringResource(Res.string.instance_link_last_update_age, ageText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

                        LabeledSwitch(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it },
                            label = stringResource(Res.string.instance_link_autoconnect),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            spacing = 12.dp,
                        )

                        SettingRow(label = stringResource(Res.string.instance_link_reconnect_delay)) {
                            SettingsTextField(
                                value = reconnectDelayText,
                                onValueChange = { new -> if (new.all(Char::isDigit)) reconnectDelayText = new },
                                placeholder = { Text(stringResource(Res.string.unit_ms)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        Text(
                            text = stringResource(Res.string.instance_link_reconnect_delay_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VerticalDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(Res.string.instance_link_role),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LabeledRadioButton(
                            selected = role == InstanceLinkRole.CONTROLLED,
                            onClick = { role = InstanceLinkRole.CONTROLLED },
                            label = stringResource(Res.string.instance_link_role_controlled),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            spacing = 12.dp,
                        )

                        LabeledRadioButton(
                            selected = role == InstanceLinkRole.CONTROLLER,
                            onClick = { role = InstanceLinkRole.CONTROLLER },
                            label = stringResource(Res.string.instance_link_role_controller),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            spacing = 12.dp,
                        )

                        // Pushing to the schedule, the Bible sync mode and background mirroring only
                        // matter in Controlled mode — a Controller keeps its own local content
                        // entirely, and its schedule is never the primary's, so a push has nothing
                        // to push to. Shown only where they do something.
                        if (role == InstanceLinkRole.CONTROLLED) {
                            LabeledSwitch(
                                checked = allowPushToSchedule,
                                onCheckedChange = { allowPushToSchedule = it },
                                label = stringResource(Res.string.instance_link_allow_push_to_schedule),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                spacing = 12.dp,
                            )

                            Text(
                                stringResource(Res.string.instance_link_bible_sync_mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LabeledRadioButton(
                                selected = bibleSyncMode == BibleSyncMode.FULL_REPLICA,
                                onClick = { bibleSyncMode = BibleSyncMode.FULL_REPLICA },
                                label = stringResource(Res.string.instance_link_bible_sync_full_replica),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                spacing = 12.dp,
                            )

                            LabeledRadioButton(
                                selected = bibleSyncMode == BibleSyncMode.REFERENCE_ONLY,
                                onClick = { bibleSyncMode = BibleSyncMode.REFERENCE_ONLY },
                                label = stringResource(Res.string.instance_link_bible_sync_reference_only),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                spacing = 12.dp,
                            )

                            LabeledSwitch(
                                checked = mirrorBackgrounds,
                                onCheckedChange = { mirrorBackgrounds = it },
                                label = stringResource(Res.string.instance_link_mirror_backgrounds),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                spacing = 12.dp,
                            )
                        }
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

                    // Always available, including with no host yet: turning autoConnect back off, or
                    // switching role, is a legitimate edit on its own and must not require a live
                    // connection to persist.
                    TextButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            onSave(edited())
                            onDismiss()
                        }
                    ) {
                        Text(
                            stringResource(Res.string.save),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            if (portText.toIntOrNull() == null) return@Button
                            onConnect(edited())
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

/** Formats an elapsed-seconds count for the "Last update N ago" readout. */
internal fun formatInstanceLinkAge(ageSeconds: Long): String =
    if (ageSeconds < SECONDS_PER_MINUTE) "${ageSeconds}s"
    else "${ageSeconds / SECONDS_PER_MINUTE}m ${ageSeconds % SECONDS_PER_MINUTE}s"

/** Short human-readable summary of a [LiveStateDto] for the "Last received" readout. */
@Composable
internal fun liveStateSummary(state: LiveStateDto): String = when (state.contentType) {
    "BIBLE" -> state.bookName?.let { "$it ${state.chapter}:${state.verseNumber}" }
        ?: stringResource(Res.string.obs_mode_bible)
    "LYRICS" -> state.songTitle ?: stringResource(Res.string.obs_mode_songs)
    "PICTURES" -> stringResource(Res.string.obs_mode_pictures)
    "PRESENTATION" -> stringResource(Res.string.obs_mode_presentation)
    "MEDIA" -> state.mediaUrl?.substringAfterLast('/') ?: stringResource(Res.string.obs_mode_media)
    "ANNOUNCEMENTS" -> state.announcementText?.take(SUMMARY_PREVIEW_CHARS) ?: stringResource(Res.string.obs_mode_announcements)
    "WEBSITE" -> state.websiteTitle ?: state.websiteUrl ?: stringResource(Res.string.obs_mode_website)
    "CANVAS" -> state.sceneName ?: stringResource(Res.string.obs_mode_canvas)
    "QA" -> state.questionText?.take(SUMMARY_PREVIEW_CHARS) ?: stringResource(Res.string.obs_mode_qa)
    "DICTIONARY" -> state.dictionaryWord ?: stringResource(Res.string.tab_dictionary)
    "LOWER_THIRD" -> stringResource(Res.string.obs_mode_lower_third)
    "NONE" -> stringResource(Res.string.obs_mode_none)
    else -> state.contentType
}


