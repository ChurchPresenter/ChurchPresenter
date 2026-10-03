package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.mergingProfileOf
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
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.RaisedCheckbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
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
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_browser_source_output
import org.churchpresenter.strings.generated.resources.browser_source_confirm_remove_message
import org.churchpresenter.strings.generated.resources.browser_source_enabled
import org.churchpresenter.strings.generated.resources.browser_source_fps
import org.churchpresenter.strings.generated.resources.browser_source_name_tooltip
import org.churchpresenter.strings.generated.resources.browser_source_output_label
import org.churchpresenter.strings.generated.resources.browser_source_outputs
import org.churchpresenter.strings.generated.resources.browser_source_outputs_help
import org.churchpresenter.strings.generated.resources.browser_source_require_api_key
import org.churchpresenter.strings.generated.resources.browser_source_resolution
import org.churchpresenter.strings.generated.resources.browser_source_uses_server_api_key
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.confirm_delete
import org.churchpresenter.strings.generated.resources.copy_url_black_bg
import org.churchpresenter.strings.generated.resources.copy_url_transparent
import org.churchpresenter.strings.generated.resources.identify_screen
import org.churchpresenter.strings.generated.resources.output_profile_picker_tooltip
import org.churchpresenter.strings.generated.resources.remove
import org.churchpresenter.sharedui.composables.LabeledSwitch
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.composables.ResolutionPicker
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.addBrowserSourceOutput
import org.churchpresenter.settings.removeBrowserSourceOutput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withBrowserSourceOutput
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.utils.SystemClipboard

private const val DISABLED_ALPHA = 0.5f
private val NAME_FIELD_WIDTH = 150.dp
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
) {
    val proj = settings.projectionSettings

SettingsSection(title = stringResource(Res.string.browser_source_outputs)) {
    Text(
        text = stringResource(Res.string.browser_source_outputs_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    val serverUrl by companionServer.serverUrl.collectAsState()

    proj.browserSourceOutputs.forEachIndexed { i, output ->
        val update: (ScreenAssignment) -> Unit = { updated ->
            onSettingsChange { s ->
                s.copy(projectionSettings = s.projectionSettings.withBrowserSourceOutput(i, updated))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), AppShape(4.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var showRemoveConfirm by remember { mutableStateOf(false) }
            val defaultLabel = stringResource(Res.string.browser_source_output_label, i + 1)
            val overlayUrl = if (serverUrl.isNotBlank()) {
                "$serverUrl${Constants.ENDPOINT_BROWSER_SOURCE}/${i + 1}"
            } else {
                null
            }
            val apiKeyParam = if (output.browserSourceApiKeyRequired && settings.serverSettings.apiKey.isNotBlank())
                "apiKey=${settings.serverSettings.apiKey}" else null

            BrowserSourceHeaderRow(
                output = output,
                defaultLabel = defaultLabel,
                overlayUrl = overlayUrl,
                urlWithBg = { bg -> (overlayUrl ?: "") + "?" + listOfNotNull(apiKeyParam, "bg=$bg").joinToString("&") },
                update = update,
                onIdentify = { onIdentifyBrowserSource(i) },
                onRemove = { showRemoveConfirm = true },
            )

            if (showRemoveConfirm) {
                RemoveBrowserSourceDialog(
                    outputLabel = output.browserSourceLabelOr(defaultLabel),
                    onRemove = {
                        showRemoveConfirm = false
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.removeBrowserSourceOutput(i))
                        }
                    },
                    onDismiss = { showRemoveConfirm = false },
                )
            }

            // Dim (not disable) the rest of this card's controls when the output is off, so
            // it's obvious at a glance which outputs are inactive — the controls underneath
            // still work normally if the output is re-enabled.
            Column(modifier = Modifier.alpha(if (output.browserSourceEnabled) 1f else DISABLED_ALPHA)) {
                BrowserSourceOptions(proj, i, output, update)
            }
        }
    }

    RaisedButton(
        shape = AppShape(6.dp),
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

/** The column width of the profile and API-key cells, and the height of every cell's label. */
private val OPTION_CELL_WIDTH = 95.dp
private val FPS_CELL_WIDTH = 82.dp
private val OPTION_LABEL_HEIGHT = 32.dp

/** An output's on switch, its name, its address, and the copy, identify and remove buttons. */
@Composable
private fun BrowserSourceHeaderRow(
    output: ScreenAssignment,
    defaultLabel: String,
    overlayUrl: String?,
    urlWithBg: (String) -> String,
    update: (ScreenAssignment) -> Unit,
    onIdentify: () -> Unit,
    onRemove: () -> Unit,
) {
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
                onCheckedChange = { checked -> update(output.copy(browserSourceEnabled = checked)) },
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
                    update(output.copy(browserSourceName = name))
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
        BrowserSourceButtons(overlayUrl, urlWithBg, onIdentify, onRemove)
    }
}

/** Copy the address with a transparent or a black background, identify the output, and remove it. */
@Composable
private fun BrowserSourceButtons(
    overlayUrl: String?,
    urlWithBg: (String) -> String,
    onIdentify: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (overlayUrl != null) {
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = { SystemClipboard.copy(urlWithBg("transparent")) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(Res.string.copy_url_transparent),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = { SystemClipboard.copy(urlWithBg("black")) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(Res.string.copy_url_black_bg),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        RaisedButton(
            shape = AppShape(6.dp),
            onClick = onIdentify,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(stringResource(Res.string.identify_screen), style = MaterialTheme.typography.labelSmall)
        }
        RaisedButton(
            shape = AppShape(6.dp),
            onClick = onRemove,
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

/** Asks before an output is removed, naming it. */
@Composable
private fun RemoveBrowserSourceDialog(outputLabel: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.confirm_delete)) },
        text = {
            Text(stringResource(Res.string.browser_source_confirm_remove_message, outputLabel))
        },
        confirmButton = {
            GhostButton(
                shape = AppShape(6.dp),
                onClick = onRemove
            ) {
                Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            GhostButton(shape = AppShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

/** The profile an output shows, its resolution and frame rate, and whether it asks for the API key. */
@Composable
private fun BrowserSourceOptions(
    proj: ProjectionSettings,
    i: Int,
    output: ScreenAssignment,
    update: (ScreenAssignment) -> Unit,
) {
    val langDropdownWidth = OPTION_CELL_WIDTH
    val contentLabelHeight = OPTION_LABEL_HEIGHT
    Row(verticalAlignment = Alignment.Top) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @OptIn(ExperimentalMaterial3Api::class)
            Column(
                modifier = Modifier.width(langDropdownWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(contentLabelHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = stringResource(Res.string.output_profile_picker_tooltip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutputProfilePicker(
                    profiles = proj.outputProfiles,
                    activeProfileId = output.activeProfileId,
                    mergedBy = proj.outputProfiles.mergingProfileOf(
                        Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, i),
                        output.activeProfileId,
                    ),
                    onPick = { pickedId -> update(output.copy(activeProfileId = pickedId)) },
                )
            }
            ResolutionPicker(
                label = stringResource(Res.string.browser_source_resolution),
                width = output.browserSourceWidth,
                height = output.browserSourceHeight,
                cellWidth = langDropdownWidth,
                labelHeight = contentLabelHeight,
                onChange = { w, h ->
                    update(output.copy(browserSourceWidth = w, browserSourceHeight = h))
                },
            )
            FpsCell(output, update)
            ApiKeyRequiredCell(output, update)
        }
    }
}

/** The output's frame rate, picked from a short list. */
@Composable
private fun FpsCell(output: ScreenAssignment, update: (ScreenAssignment) -> Unit) {
    val cellWidth = FPS_CELL_WIDTH
    val contentLabelHeight = OPTION_LABEL_HEIGHT
    Column(modifier = Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().height(contentLabelHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(Res.string.browser_source_fps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        var fpsExpanded by remember { mutableStateOf(false) }
        KeyButton(
            shape = AppShape(6.dp),
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
                        update(output.copy(browserSourceFps = fps))
                    }
                )
            }
        }
    }
}

/** Whether the output's page asks for the server's API key. */
@Composable
private fun ApiKeyRequiredCell(output: ScreenAssignment, update: (ScreenAssignment) -> Unit) {
    val langDropdownWidth = OPTION_CELL_WIDTH
    val contentLabelHeight = OPTION_LABEL_HEIGHT
    @OptIn(ExperimentalMaterial3Api::class)
    Column(
        modifier = Modifier.width(langDropdownWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(contentLabelHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
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
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above
            ),
            tooltip = {
                PlainTooltip { Text(stringResource(Res.string.browser_source_uses_server_api_key)) }
            },
            state = rememberTooltipState()
        ) {
            RaisedCheckbox(
                checked = output.browserSourceApiKeyRequired,
                onCheckedChange = { checked ->
                    update(output.copy(browserSourceApiKeyRequired = checked))
                }
            )
        }
    }
}
