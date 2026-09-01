package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_browser_source_output
import churchpresenter.composeapp.generated.resources.browser_source_confirm_remove_message
import churchpresenter.composeapp.generated.resources.browser_source_enabled
import churchpresenter.composeapp.generated.resources.browser_source_fps
import churchpresenter.composeapp.generated.resources.browser_source_name_tooltip
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.browser_source_outputs
import churchpresenter.composeapp.generated.resources.browser_source_outputs_help
import churchpresenter.composeapp.generated.resources.browser_source_require_api_key
import churchpresenter.composeapp.generated.resources.browser_source_resolution
import churchpresenter.composeapp.generated.resources.browser_source_uses_server_api_key
import churchpresenter.composeapp.generated.resources.browser_source_website_snapshot_tooltip
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_outputs
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_short
import churchpresenter.composeapp.generated.resources.content_outputs_for
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.copy_url_black_bg
import churchpresenter.composeapp.generated.resources.copy_url_transparent
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.projection_web_decklink_tooltip
import churchpresenter.composeapp.generated.resources.remove
import org.churchpresenter.app.churchpresenter.composables.LabeledSwitch
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.addBrowserSourceOutput
import org.churchpresenter.settings.removeBrowserSourceOutput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withBrowserSourceOutput
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard

private const val DISABLED_ALPHA = 0.5f
private val NAME_FIELD_WIDTH = 150.dp
private val BROWSER_SOURCE_RESOLUTIONS = listOf(1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160)
private val BROWSER_SOURCE_FRAME_RATES = listOf(10, 15, 24, 30, 60)

/**
 * The "Browser Source outputs" card of the Projection settings tab — the OBS/vMix overlay outputs,
 * their resolution/fps and per-output content toggles.
 *
 * Split out of ProjectionSettingsTab.kt's single 1,390-line composable. The derived label and size
 * values it needs are recomputed here rather than threaded through as parameters; only real state
 * and the column groups are passed.
 */
@Composable
internal fun BrowserSourceOutputsCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    onIdentifyBrowserSource: (Int) -> Unit,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    displayModes: List<Pair<String, String>>,
    songLangModes: List<Pair<String, String>>,
    translationDisplays: List<BibleTranslationDisplay>,
    translationNames: List<String>,
) {
    val proj = settings.projectionSettings
    val bibleLabel = stringResource(Res.string.content_bible)
    val songsLabel = stringResource(Res.string.content_songs)
    val fullScreenLabel = stringResource(Res.string.display_fullscreen)
    val langDropdownWidth = 95.dp
    val cellWidth = 82.dp
    val contentLabelHeight = 32.dp

SettingsSection(title = stringResource(Res.string.browser_source_outputs)) {
    Text(
        text = stringResource(Res.string.browser_source_outputs_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    val serverUrl by companionServer.serverUrl.collectAsState()
    val copyText: (String) -> Unit = { text ->
        SystemClipboard.copy(text)
    }

    proj.browserSourceOutputs.forEachIndexed { i, output ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var showRemoveConfirm by remember { mutableStateOf(false) }
            val defaultLabel = stringResource(Res.string.browser_source_output_label, i + 1)
            val outputLabel = output.browserSourceLabelOr(defaultLabel)
            val overlayUrl = if (serverUrl.isNotBlank()) "$serverUrl${Constants.ENDPOINT_BROWSER_SOURCE}/${i + 1}" else null
            val apiKeyParam = if (output.browserSourceApiKeyRequired && settings.serverSettings.apiKey.isNotBlank())
                "apiKey=${settings.serverSettings.apiKey}" else null
            fun urlWithBg(bg: String): String =
                (overlayUrl ?: "") + "?" + listOfNotNull(apiKeyParam, "bg=$bg").joinToString("&")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabeledSwitch(
                        checked = output.browserSourceEnabled,
                        onCheckedChange = { checked ->
                            val updated = output.copy(browserSourceEnabled = checked)
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                            }
                        },
                        label = stringResource(Res.string.browser_source_enabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        spacing = 4.dp,
                    )
                    @OptIn(ExperimentalMaterial3Api::class)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(Res.string.browser_source_name_tooltip)) } },
                        state = rememberTooltipState()
                    ) {
                        SettingsTextField(
                            value = output.browserSourceName,
                            onValueChange = { name ->
                                val updated = output.copy(browserSourceName = name)
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                }
                            },
                            placeholder = {
                                Text(
                                    text = defaultLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.width(NAME_FIELD_WIDTH)
                        )
                    }
                    if (overlayUrl != null) {
                        Text(
                            text = overlayUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (overlayUrl != null) {
                        Button(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { copyText(urlWithBg("transparent")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(Res.string.copy_url_transparent), style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { copyText(urlWithBg("black")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(Res.string.copy_url_black_bg), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = { onIdentifyBrowserSource(i) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(Res.string.identify_screen), style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        shape = RoundedCornerShape(6.dp),
                        onClick = { showRemoveConfirm = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(Res.string.remove), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showRemoveConfirm) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirm = false },
                    title = { Text(stringResource(Res.string.confirm_delete)) },
                    text = {
                        Text(stringResource(Res.string.browser_source_confirm_remove_message, outputLabel))
                    },
                    confirmButton = {
                        TextButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = {
                                showRemoveConfirm = false
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.removeBrowserSourceOutput(i))
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(shape = RoundedCornerShape(6.dp), onClick = { showRemoveConfirm = false }) {
                            Text(stringResource(Res.string.cancel))
                        }
                    }
                )
            }

            // Dim (not disable) the rest of this card's controls when the output is off, so
            // it's obvious at a glance which outputs are inactive — the controls underneath
            // still work normally if the output is re-enabled.
            Column(modifier = Modifier.alpha(if (output.browserSourceEnabled) 1f else DISABLED_ALPHA)) {
            Row(verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                text = stringResource(Res.string.display_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        var displayModeExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { displayModeExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayModes.find { it.second == shownDisplayMode(output.displayMode) }?.first
                                ?: fullScreenLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = displayModeExpanded,
                            onDismissRequest = { displayModeExpanded = false }
                        ) {
                            displayModes.forEach { (label, modeValue) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        displayModeExpanded = false
                                        val updated = output.copy(
                                    displayMode = pickedDisplayMode(modeValue, output.displayMode),
                                )
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                text = stringResource(Res.string.browser_source_resolution),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        var resolutionExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { resolutionExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${output.browserSourceWidth}\u00d7${output.browserSourceHeight}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = resolutionExpanded,
                            onDismissRequest = { resolutionExpanded = false }
                        ) {
                            BROWSER_SOURCE_RESOLUTIONS.forEach { (w, h) ->
                                DropdownMenuItem(
                                    text = { Text("$w\u00d7$h", style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        resolutionExpanded = false
                                        val updated = output.copy(browserSourceWidth = w, browserSourceHeight = h)
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                text = stringResource(Res.string.browser_source_fps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        var fpsExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { fpsExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = output.browserSourceFps.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = fpsExpanded,
                            onDismissRequest = { fpsExpanded = false }
                        ) {
                            BROWSER_SOURCE_FRAME_RATES.forEach { fps ->
                                DropdownMenuItem(
                                    text = { Text(fps.toString(), style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        fpsExpanded = false
                                        val updated = output.copy(browserSourceFps = fps)
                                        onSettingsChange { s ->
                                            s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    @OptIn(ExperimentalMaterial3Api::class)
                    Column(modifier = Modifier.width(langDropdownWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                text = stringResource(Res.string.browser_source_require_api_key),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(stringResource(Res.string.browser_source_uses_server_api_key)) } },
                            state = rememberTooltipState()
                        ) {
                            Checkbox(
                                checked = output.browserSourceApiKeyRequired,
                                onCheckedChange = { checked ->
                                    val updated = output.copy(browserSourceApiKeyRequired = checked)
                                    onSettingsChange { s ->
                                        s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                    }
                                }
                            )
                        }
                    }
                    // Content Outputs — opens a modal listing every content type + background.
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth().height(contentLabelHeight), contentAlignment = Alignment.BottomStart) {
                            Text(
                                text = stringResource(Res.string.content_outputs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        var showContentDialog by remember { mutableStateOf(false) }
                        val enabledCount = contentOutputsEnabledCount(output, contentGroup, backgroundGroup)
                        val totalCount = contentOutputsTotalCount(output, contentGroup, backgroundGroup)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { showContentDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        Res.string.content_outputs_enabled_short, enabledCount, totalCount,
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            CustomizeOutputCell(
                                assignment = output,
                                screenLabel = outputLabel,
                                settings = settings,
                                onApply = { updated ->
                                    onSettingsChange { s ->
                                        s.copy(
                                            projectionSettings =
                                                s.projectionSettings.withBrowserSourceOutput(i, updated),
                                        )
                                    }
                                },
                            )
                        }
                        if (showContentDialog) {
                            ContentOutputsDialog(
                                title = stringResource(Res.string.content_outputs_for, outputLabel),
                                screenLabel = outputLabel,
                                assignment = output,
                                contentGroup = contentGroup,
                                backgroundGroup = backgroundGroup,
                                bibleLabel = bibleLabel,
                                songsLabel = songsLabel,
                                translationNames = translationNames,
                                translationDisplays = translationDisplays,
                                songLangModes = songLangModes,
                                webDeckLinkTooltip = stringResource(Res.string.projection_web_decklink_tooltip),
                                webSnapshotTooltip = stringResource(Res.string.browser_source_website_snapshot_tooltip),
                                isBrowserSource = true,
                                onApply = { updated ->
                                    onSettingsChange { s ->
                                        s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
                                    }
                                },
                                onDismiss = { showContentDialog = false }
                            )
                        }
                    }
                }
            }
            } // end alpha-dimmed Column
        }
    }

    Button(
        shape = RoundedCornerShape(6.dp),
        onClick = {
            onSettingsChange { s ->
                s.copy(projectionSettings = s.projectionSettings.addBrowserSourceOutput())
            }
        }
    ) {
        Text(stringResource(Res.string.add_browser_source_output), style = MaterialTheme.typography.labelSmall)
    }
}
}
