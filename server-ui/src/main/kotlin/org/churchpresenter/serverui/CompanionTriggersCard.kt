package org.churchpresenter.serverui

import androidx.compose.foundation.background
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.lowerthird.render.isLottieFile
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.composables.HintTooltip
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.companion_atem_clip_key
import org.churchpresenter.strings.generated.resources.companion_atem_clip_key_note
import org.churchpresenter.strings.generated.resources.companion_atem_clip_only
import org.churchpresenter.strings.generated.resources.companion_atem_key_desc
import org.churchpresenter.strings.generated.resources.companion_atem_key_off
import org.churchpresenter.strings.generated.resources.companion_atem_key_on
import org.churchpresenter.strings.generated.resources.companion_atem_key_section
import org.churchpresenter.strings.generated.resources.companion_atem_still_key
import org.churchpresenter.strings.generated.resources.companion_atem_still_only
import org.churchpresenter.strings.generated.resources.companion_atem_upload_pill
import org.churchpresenter.strings.generated.resources.companion_lt_copy_hide
import org.churchpresenter.strings.generated.resources.companion_lt_copy_key
import org.churchpresenter.strings.generated.resources.companion_lt_copy_nokey
import org.churchpresenter.strings.generated.resources.companion_lt_filter
import org.churchpresenter.strings.generated.resources.companion_lt_no_match
import org.churchpresenter.strings.generated.resources.companion_lt_none
import org.churchpresenter.strings.generated.resources.companion_lt_plus_key
import org.churchpresenter.strings.generated.resources.companion_lt_server_off
import org.churchpresenter.strings.generated.resources.companion_lt_takedown_desc
import org.churchpresenter.strings.generated.resources.companion_lt_triggers
import org.churchpresenter.strings.generated.resources.companion_lt_triggers_desc
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import java.io.File

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
    val lowerThirds by produceState(emptyList<File>(), lowerThirdFolder, isRunning) {
        value = withContext(Dispatchers.IO) {
            File(lowerThirdFolder)
                .takeIf { lowerThirdFolder.isNotEmpty() && it.isDirectory }
                ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                ?.sortedBy { it.nameWithoutExtension.lowercase() }
                ?.toList()
                ?: emptyList()
        }
    }
    val atemConfigured = settings.atemSettings.host.isNotBlank()
    val serving = isRunning && serverUrl.isNotBlank()

    // Default key target (1-based) for the "+ key" URLs, matching the configured key
    // type. DSK ignores M/E and uses the DSK number; both carry an explicit keytype so
    // the copied URL behaves as shown regardless of later setting changes.
    val keyTypeParam = atemKeyTypeParam(settings.atemSettings)
    val apiKeyOrBlank = effectiveApiKey(settings.serverSettings)
    val urls = TriggerUrls(serverUrl, atemKeyTarget(settings.atemSettings), apiKeyOrBlank)

    ServerSettingsCard(
        icon = Icons.Outlined.Subtitles,
        tint = MaterialTheme.semantic.contentSongs,
        title = stringResource(Res.string.companion_lt_triggers),
        description = stringResource(Res.string.companion_lt_triggers_desc),
        titleExtra = {
            if (serving && lowerThirds.isNotEmpty()) {
                Text(
                    lowerThirds.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = { if (serving) TakedownButtons(serverUrl, apiKeyOrBlank, copyText) },
    ) {
        CardBlock {
            when {
                !serving -> EmptyNote(stringResource(Res.string.companion_lt_server_off))
                lowerThirds.isEmpty() -> EmptyNote(stringResource(Res.string.companion_lt_none))
                else -> TriggerList(lowerThirds.map { it.nameWithoutExtension }, urls, atemConfigured, copyText)
            }
            if (serving && atemConfigured) {
                AtemKeyRow(
                    serverUrl = serverUrl,
                    keyTypeParam = keyTypeParam,
                    apiKey = apiKeyOrBlank,
                    copyText = copyText,
                )
                DimText(stringResource(Res.string.companion_atem_clip_key_note), small = true)
            }
        }
    }
}

/** The filter, the upload note, and one compact row per lower third that matches, in a list of its own. */
@Composable
private fun TriggerList(names: List<String>, urls: TriggerUrls, atemConfigured: Boolean, copyText: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterField(filter, { filter = it }, Modifier.weight(1f))
        Pill(stringResource(Res.string.companion_atem_upload_pill), MaterialTheme.semantic.warning)
    }
    val shown = names.filter { it.contains(filter.trim(), ignoreCase = true) }
    if (shown.isEmpty()) {
        EmptyNote(stringResource(Res.string.companion_lt_no_match))
    } else {
        Column(
            modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            shown.forEach { name -> TriggerRow(name, urls, atemConfigured, copyText) }
        }
    }
}

/** A slim search box: the glass, then what is typed. */
@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = AppShape(8.dp)
    val placeholder = stringResource(Res.string.companion_lt_filter)
    val textStyle = MaterialTheme.typography.bodySmall
    Row(
        modifier = modifier
            .height(32.dp)
            .sunken(shape, elevationPalette())
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

@Composable
private fun TriggerRow(name: String, urls: TriggerUrls, atemConfigured: Boolean, copyText: (String) -> Unit) {
    val (title, variant) = remember(name) { splitVariant(name) }
    val plusKey = stringResource(Res.string.companion_lt_plus_key)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .hoverable(interaction)
            .background(
                if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent,
                AppShape(9.dp),
            )
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The name takes every bit of width the buttons leave, so they line up down the right edge.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (variant.isNotEmpty()) VariantTag(variant)
        }
        if (atemConfigured) {
            SplitKey(
                first = stringResource(Res.string.companion_atem_still_only),
                onFirst = { copyText(urls.still(name, withKey = false)) },
                second = plusKey,
                secondName = stringResource(Res.string.companion_atem_still_key),
                onSecond = { copyText(urls.still(name, withKey = true)) },
            )
            SplitKey(
                first = stringResource(Res.string.companion_atem_clip_only),
                onFirst = { copyText(urls.clip(name, withKey = false)) },
                second = plusKey,
                secondName = stringResource(Res.string.companion_atem_clip_key),
                onSecond = { copyText(urls.clip(name, withKey = true)) },
            )
        }
        GoLiveSplit(
            onLive = { copyText(urls.trigger(name, withKey = false)) },
            plusKey = plusKey,
            keyName = stringResource(Res.string.companion_lt_copy_key),
            onLiveKey = { copyText(urls.trigger(name, withKey = true)) },
        )
    }
}

/** Go Live in green joined to + Key in the accent: the row's main pair, as the design draws it. */
@Composable
private fun GoLiveSplit(onLive: () -> Unit, plusKey: String, keyName: String, onLiveKey: () -> Unit) {
    val shape = AppShape(8.dp)
    Row(modifier = Modifier.height(28.dp).clip(shape)) {
        RaisedButton(
            onClick = onLive,
            shape = AppShape(0.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.semantic.success,
                contentColor = MaterialTheme.semantic.onSuccess,
            ),
        ) {
            Text(
                stringResource(Res.string.companion_lt_copy_nokey),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(alpha = SEAM_ALPHA)))
        RaisedButton(
            onClick = onLiveKey,
            shape = AppShape(0.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.fillMaxHeight().semantics { contentDescription = keyName },
        ) { Text(plusKey, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
    }
}

/** The trailing "01" of "Speaker - Title - 01", as a small tag instead of part of the name. */
@Composable
private fun VariantTag(variant: String) {
    Text(
        variant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** A lower third's name without its trailing two-digit variant, and that variant; blank when there is none. */
internal fun splitVariant(name: String): Pair<String, String> {
    val match = VARIANT.matchEntire(name) ?: return name to ""
    return match.groupValues[1] to match.groupValues[2]
}

private val VARIANT = Regex("""^(.*?)\s*-\s*(\d{2})$""")

@Composable
private fun AtemKeyRow(serverUrl: String, keyTypeParam: String, apiKey: String, copyText: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.companion_atem_key_section),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DimText(stringResource(Res.string.companion_atem_key_desc))
        }
        CompactKey(stringResource(Res.string.companion_atem_key_on)) {
            copyText(atemKeyUrl(serverUrl, on = true, keyTypeParam = keyTypeParam, apiKey = apiKey))
        }
        CompactKey(stringResource(Res.string.companion_atem_key_off)) {
            copyText(atemKeyUrl(serverUrl, on = false, keyTypeParam = keyTypeParam, apiKey = apiKey))
        }
    }
}

/**
 * Take-down URLs, in the header -- available whenever the server is running. "Hide Lower Third"
 * clears only a lower third; "Clear Display" clears any output (Bible, song, lower third, …) via
 * POST /api/clear. The difference is in their tooltip.
 */
@Composable
private fun TakedownButtons(serverUrl: String, apiKey: String, copyText: (String) -> Unit) {
    val hint = stringResource(Res.string.companion_lt_takedown_desc)
    HintTooltip(hint) {
        CompactKey(stringResource(Res.string.companion_lt_copy_hide)) { copyText(lowerThirdHideUrl(serverUrl, apiKey)) }
    }
    HintTooltip(hint) {
        // Outlined in red, the same height as its neighbour: a quiet button with a rim, not a raised one.
        val error = MaterialTheme.colorScheme.error
        GhostButton(
            onClick = { copyText(clearDisplayUrl(serverUrl, apiKey)) },
            shape = AppShape(8.dp),
            contentPadding = COMPACT_PADDING,
            border = BorderStroke(1.dp, error.copy(alpha = OUTLINE_ALPHA)),
            modifier = Modifier.height(COMPACT_ROW),
            colors = ButtonDefaults.textButtonColors(contentColor = error),
        ) { Text(stringResource(Res.string.tooltip_clear_display), style = MaterialTheme.typography.labelMedium) }
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

private val LIST_MAX_HEIGHT = 440.dp
private val COMPACT_ROW = 32.dp
private const val HOVER_ALPHA = 0.06f
private const val SEAM_ALPHA = 0.22f
private const val OUTLINE_ALPHA = 0.45f
