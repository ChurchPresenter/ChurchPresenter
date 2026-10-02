package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.companion_lt_copy_key
import org.churchpresenter.strings.generated.resources.companion_lt_copy_hide
import org.churchpresenter.strings.generated.resources.companion_lt_takedown_desc
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import org.churchpresenter.strings.generated.resources.companion_lt_copy_nokey
import org.churchpresenter.strings.generated.resources.companion_lt_none
import org.churchpresenter.strings.generated.resources.companion_lt_server_off
import org.churchpresenter.strings.generated.resources.companion_atem_clip_key
import org.churchpresenter.strings.generated.resources.companion_atem_clip_key_note
import org.churchpresenter.strings.generated.resources.companion_atem_clip_only
import org.churchpresenter.strings.generated.resources.companion_atem_key_desc
import org.churchpresenter.strings.generated.resources.companion_atem_key_off
import org.churchpresenter.strings.generated.resources.companion_atem_key_on
import org.churchpresenter.strings.generated.resources.companion_atem_key_section
import org.churchpresenter.strings.generated.resources.companion_atem_still_key
import org.churchpresenter.strings.generated.resources.companion_atem_still_only
import org.churchpresenter.strings.generated.resources.companion_atem_upload_note
import org.churchpresenter.strings.generated.resources.companion_lt_triggers
import org.churchpresenter.strings.generated.resources.companion_lt_triggers_desc
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.lowerthird.render.isLottieFile
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CompanionTriggersCard(
    settings: AppSettings,
    isRunning: Boolean,
    serverUrl: String,
    copyText: (String) -> Unit,
) {
    val lowerThirdFolder = settings.streamingSettings.lowerThirdFolder
    // `isLottieFile` reads each JSON in full, so this is the folder's whole weight in
    // bytes — off the composition thread. The card is a list of trigger URLs, so a
    // frame of it empty says nothing misleading.
    val lowerThirds by produceState(emptyList<java.io.File>(), lowerThirdFolder, isRunning) {
        value = withContext(Dispatchers.IO) {
            java.io.File(lowerThirdFolder)
                .takeIf { lowerThirdFolder.isNotEmpty() && it.isDirectory }
                ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                ?.sortedBy { it.nameWithoutExtension.lowercase() }
                ?.toList()
                ?: emptyList()
        }
    }
    val atemConfigured = settings.atemSettings.host.isNotBlank()

    // Default key target (1-based) for the "+ key" URLs, matching the configured key
    // type. DSK ignores M/E and uses the DSK number; both carry an explicit keytype so
    // the copied URL behaves as shown regardless of later setting changes.
    val keyTypeParam = atemKeyTypeParam(settings.atemSettings)
    val apiKeyOrBlank = effectiveApiKey(settings.serverSettings)
    val urls = TriggerUrls(serverUrl, atemKeyTarget(settings.atemSettings), apiKeyOrBlank)

    SettingsSection(title = stringResource(Res.string.companion_lt_triggers)) {
        Text(
            text = stringResource(Res.string.companion_lt_triggers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.companion_atem_upload_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        )
        if (atemConfigured) {
            Text(
                text = stringResource(Res.string.companion_atem_clip_key_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            !isRunning || serverUrl.isBlank() -> Text(
                text = stringResource(Res.string.companion_lt_server_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            lowerThirds.isEmpty() -> Text(
                text = stringResource(Res.string.companion_lt_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            else -> lowerThirds.forEach { file ->
                LowerThirdTriggerRow(
                    name = file.nameWithoutExtension,
                    urls = urls,
                    atemConfigured = atemConfigured,
                    copyText = copyText,
                )
            }
        }

        if (isRunning && serverUrl.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(4.dp))

            // Key controls — only when ATEM is configured
            if (atemConfigured) {
                AtemKeySection(
                    serverUrl = serverUrl,
                    keyTypeParam = keyTypeParam,
                    apiKey = apiKeyOrBlank,
                    copyText = copyText,
                )
            }

            TakedownSection(serverUrl = serverUrl, apiKey = apiKeyOrBlank, copyText = copyText)
        }
    }
}

@Composable
private fun LowerThirdTriggerRow(
    name: String,
    urls: TriggerUrls,
    atemConfigured: Boolean,
    copyText: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CopyUrlButton(
                text = stringResource(Res.string.companion_lt_copy_key),
                tone = ButtonTone.PRIMARY,
                horizontalPadding = 10.dp,
                onClick = { copyText(urls.trigger(name, withKey = true)) },
            )
            CopyUrlButton(
                text = stringResource(Res.string.companion_lt_copy_nokey),
                tone = ButtonTone.SECONDARY,
                horizontalPadding = 10.dp,
                onClick = { copyText(urls.trigger(name, withKey = false)) },
            )
            if (atemConfigured) {
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_still_key),
                    tone = ButtonTone.PRIMARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.still(name, withKey = true)) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_still_only),
                    tone = ButtonTone.SECONDARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.still(name, withKey = false)) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_clip_key),
                    tone = ButtonTone.PRIMARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.clip(name, withKey = true)) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_clip_only),
                    tone = ButtonTone.SECONDARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.clip(name, withKey = false)) },
                )
            }
        }
    }
}

@Composable
private fun AtemKeySection(serverUrl: String, keyTypeParam: String, apiKey: String, copyText: (String) -> Unit) {
    Text(
        text = stringResource(Res.string.companion_atem_key_section),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(Res.string.companion_atem_key_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyUrlButton(
            text = stringResource(Res.string.companion_atem_key_on),
            tone = ButtonTone.PRIMARY,
            onClick = { copyText(atemKeyUrl(serverUrl, on = true, keyTypeParam = keyTypeParam, apiKey = apiKey)) },
        )
        CopyUrlButton(
            text = stringResource(Res.string.companion_atem_key_off),
            tone = ButtonTone.SECONDARY,
            onClick = { copyText(atemKeyUrl(serverUrl, on = false, keyTypeParam = keyTypeParam, apiKey = apiKey)) },
        )
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * Take-down actions — available whenever the server is running. "Hide Lower Third" clears only a
 * lower third; "Clear Display" clears any output (Bible, song, lower third, …) via POST /api/clear.
 */
@Composable
private fun TakedownSection(serverUrl: String, apiKey: String, copyText: (String) -> Unit) {
    Text(
        text = stringResource(Res.string.companion_lt_takedown_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyUrlButton(
            text = stringResource(Res.string.companion_lt_copy_hide),
            tone = ButtonTone.ERROR_CONTAINER,
            onClick = { copyText(lowerThirdHideUrl(serverUrl, apiKey)) },
        )
        CopyUrlButton(
            text = stringResource(Res.string.tooltip_clear_display),
            tone = ButtonTone.ERROR,
            onClick = { copyText(clearDisplayUrl(serverUrl, apiKey)) },
        )
    }
}

/** The URLs one lower third can be copied as: the trigger, and the ATEM still and clip uploads. */
private class TriggerUrls(
    private val serverUrl: String,
    private val keyTarget: String,
    private val apiKey: String,
) {
    fun trigger(name: String, withKey: Boolean): String = lowerThirdTriggerUrl(serverUrl, name, withKey, apiKey)
    fun still(name: String, withKey: Boolean): String =
        atemMediaUrl(serverUrl, "still", name, if (withKey) keyTarget else "", apiKey)
    fun clip(name: String, withKey: Boolean): String =
        atemMediaUrl(serverUrl, "clip", name, if (withKey) keyTarget else "", apiKey)
}
