package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.mergingProfileOf
import org.churchpresenter.settings.utils.Constants
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_omt_output
import org.churchpresenter.strings.generated.resources.apply
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ffmpeg_clear
import org.churchpresenter.strings.generated.resources.confirm_delete
import org.churchpresenter.strings.generated.resources.identify_screen
import org.churchpresenter.strings.generated.resources.ndi_confirm_remove_message
import org.churchpresenter.strings.generated.resources.ndi_enabled
import org.churchpresenter.strings.generated.resources.ndi_fps
import org.churchpresenter.strings.generated.resources.ndi_mode
import org.churchpresenter.strings.generated.resources.ndi_mode_alpha
import org.churchpresenter.strings.generated.resources.ndi_mode_fill
import org.churchpresenter.strings.generated.resources.ndi_no_receivers
import org.churchpresenter.strings.generated.resources.ndi_receivers
import org.churchpresenter.strings.generated.resources.ndi_resolution
import org.churchpresenter.strings.generated.resources.ndi_runtime_browse
import org.churchpresenter.strings.generated.resources.ndi_runtime_check_again
import org.churchpresenter.strings.generated.resources.omt_address
import org.churchpresenter.strings.generated.resources.omt_discovery_server
import org.churchpresenter.strings.generated.resources.omt_discovery_server_help
import org.churchpresenter.strings.generated.resources.omt_discovery_server_placeholder
import org.churchpresenter.strings.generated.resources.omt_discovery_service_missing
import org.churchpresenter.strings.generated.resources.omt_library_bundled
import org.churchpresenter.strings.generated.resources.omt_library_custom
import org.churchpresenter.strings.generated.resources.omt_library_load_failed
import org.churchpresenter.strings.generated.resources.omt_library_missing_help
import org.churchpresenter.strings.generated.resources.omt_library_missing_title
import org.churchpresenter.strings.generated.resources.omt_library_path
import org.churchpresenter.strings.generated.resources.omt_library_path_help
import org.churchpresenter.strings.generated.resources.omt_mode_alpha_help
import org.churchpresenter.strings.generated.resources.omt_mode_fill_help
import org.churchpresenter.strings.generated.resources.ndi_name_tooltip
import org.churchpresenter.strings.generated.resources.omt_output_numbered
import org.churchpresenter.strings.generated.resources.omt_outputs
import org.churchpresenter.strings.generated.resources.omt_outputs_help
import org.churchpresenter.strings.generated.resources.omt_quality
import org.churchpresenter.strings.generated.resources.omt_quality_default
import org.churchpresenter.strings.generated.resources.omt_quality_default_help
import org.churchpresenter.strings.generated.resources.omt_quality_fixed_help
import org.churchpresenter.strings.generated.resources.omt_quality_high
import org.churchpresenter.strings.generated.resources.omt_quality_low
import org.churchpresenter.strings.generated.resources.omt_quality_medium
import org.churchpresenter.strings.generated.resources.output_profile_picker_tooltip
import org.churchpresenter.strings.generated.resources.remove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.composables.LabeledSwitch
import org.churchpresenter.app.churchpresenter.composables.ResolutionPicker
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.presenter.OmtManager
import org.churchpresenter.app.churchpresenter.presenter.OmtVideoRenderer
import org.churchpresenter.omt.OmtOutputMode
import org.churchpresenter.omt.OmtQuality
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.addOmtOutput
import org.churchpresenter.settings.removeOmtOutput
import org.churchpresenter.settings.withOmtOutput
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private const val DISABLED_ALPHA = 0.5f
private val NAME_FIELD_WIDTH = 150.dp
private val PATH_FIELD_WIDTH = 320.dp
private val OMT_FRAME_RATES = listOf(24, 25, 30, 50, 60)

/** How often the card re-asks how many receivers an output has — a counter read, as NDI's is. */
private const val RECEIVER_POLL_MS = 1_000L

/**
 * The "OMT outputs" card of the Projection settings tab, beside the NDI one and in its shape.
 *
 * Where the NDI card is mostly about a runtime the operator has to go and install, this one is
 * mostly about the outputs: `libomt` ships with the app, so the library row is a status line and an
 * override rather than a download link. The discovery server is here too, because it governs being
 * found as an output as much as finding sources on the Canvas.
 */
@Composable
internal fun OmtOutputsCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onIdentifyOmt: (Int) -> Unit = {},
    /** What the app found when it loaded the library. A parameter so a screenshot can pin it. */
    status: OmtRuntimeStatus = OmtManager.status.collectAsState().value,
    /** How many receivers are watching output N. A parameter for the same reason as [status]. */
    receiverCount: (Int) -> Int = OmtManager::receiverCount,
    /** The name output N is advertised under. A parameter for the same reason as [status]. */
    addressOf: (Int) -> String = OmtManager::addressOf,
    /**
     * Loads the library again, from a folder and with a discovery server. A parameter so a test's
     * click never binds a real library; the card calls it off the UI thread.
     */
    recheck: (path: String, discoveryServer: String) -> Unit = { path, server ->
        OmtManager.ensureStarted(path, server)
    },
) {
    val proj = settings.projectionSettings

    SettingsSection(title = stringResource(Res.string.omt_outputs)) {
        Text(
            text = stringResource(Res.string.omt_outputs_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OmtLibraryRow(status = status, settings = settings, onSettingsChange = onSettingsChange, recheck = recheck)
        OmtDiscoveryServerRow(settings = settings, onSettingsChange = onSettingsChange)
        Spacer(modifier = Modifier.height(8.dp))

        if (status.isReady) {
            proj.omtOutputs.forEachIndexed { i, output ->
                OmtOutputRow(
                    index = i,
                    output = output,
                    receiverCount = receiverCount,
                    addressOf = addressOf,
                    onIdentifyOmt = onIdentifyOmt,
                    onSettingsChange = onSettingsChange,
                    outputProfiles = proj.outputProfiles,
                )
            }
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = {
                    onSettingsChange { s -> s.copy(projectionSettings = s.projectionSettings.addOmtOutput()) }
                },
            ) {
                Text(stringResource(Res.string.add_omt_output), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Which `libomt` is in use, and the override.
 *
 * The ordinary state is "included with the app"; the path is for an operator who wants a newer
 * build than the one bundled, or a platform the bundle does not cover.
 */
@Composable
private fun OmtLibraryRow(
    status: OmtRuntimeStatus,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    recheck: (path: String, discoveryServer: String) -> Unit,
) {
    val proj = settings.projectionSettings
    val path = proj.omtLibraryPath
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OmtLibraryMessage(status)

        val scope = rememberCoroutineScope()
        val folderTitle = stringResource(Res.string.omt_library_path)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Read-only plus a Browse button, as the NDI and VLC folders on this tab are.
            SettingsTextField(
                value = path,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = {
                    Text(
                        text = folderTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.width(PATH_FIELD_WIDTH),
            )
            KeyButton(
                shape = AppShape(6.dp),
                onClick = {
                    scope.launch {
                        val chosen = FileChooser.platformInstance.chooseSingle(
                            path = path.takeIf { it.isNotBlank() }?.let(::Path),
                            title = folderTitle,
                            selectDirectory = true,
                            filters = emptyList(),
                        ) ?: return@launch
                        val selected = chosen.absolutePathString()
                        // Only written back: main.kt's effect is keyed on this path and loads it.
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.copy(omtLibraryPath = selected))
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.ndi_runtime_browse), style = MaterialTheme.typography.labelSmall)
            }
            if (path.isNotBlank()) {
                KeyButton(
                    shape = AppShape(6.dp),
                    onClick = {
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.copy(omtLibraryPath = ""))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.ffmpeg_clear),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (!status.isReady) {
                KeyButton(
                    shape = AppShape(6.dp),
                    // Off the UI thread: this is a `Native.load` of an 18 MB library.
                    onClick = {
                        scope.launch(Dispatchers.IO) { recheck(path, proj.omtDiscoveryServer) }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.ndi_runtime_check_again),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.omt_library_path_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What [status] means, in the operator's words. */
@Composable
private fun OmtLibraryMessage(status: OmtRuntimeStatus) {
    when (status) {
        is OmtRuntimeStatus.Ready -> Text(
            text = if (status.bundled) {
                stringResource(Res.string.omt_library_bundled)
            } else {
                stringResource(Res.string.omt_library_custom, status.path)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        is OmtRuntimeStatus.LoadFailed -> Text(
            text = stringResource(Res.string.omt_library_load_failed, status.path),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OmtRuntimeStatus.DiscoveryServiceMissing -> Text(
            text = stringResource(Res.string.omt_discovery_service_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OmtRuntimeStatus.NotInstalled -> {
            Text(
                text = stringResource(Res.string.omt_library_missing_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.omt_library_missing_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The discovery server, committed by Apply rather than per keystroke. The library reads it once, as
 * its discovery starts, so a change is saved for the next launch — which the help line says.
 */
@Composable
private fun OmtDiscoveryServerRow(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val committed = settings.projectionSettings.omtDiscoveryServer
    var draft by remember(committed) { mutableStateOf(committed) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.omt_discovery_server),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.omt_discovery_server_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
                modifier = Modifier.width(PATH_FIELD_WIDTH),
            )
            RaisedButton(
                shape = AppShape(6.dp),
                enabled = draft.trim() != committed,
                onClick = {
                    onSettingsChange { s ->
                        s.copy(projectionSettings = s.projectionSettings.copy(omtDiscoveryServer = draft.trim()))
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.apply), style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = stringResource(Res.string.omt_discovery_server_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One configured OMT output: its name, mode, quality, size, rate and profile. */
@Composable
@Suppress("LongMethod", "LongParameterList")  // One card row, in the shape the NDI card established.
private fun OmtOutputRow(
    index: Int,
    output: ScreenAssignment,
    receiverCount: (Int) -> Int,
    addressOf: (Int) -> String,
    onIdentifyOmt: (Int) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    outputProfiles: List<OutputProfile>,
) {
    val defaultLabel = stringResource(Res.string.omt_output_numbered, index + 1)
    val outputLabel = output.omtLabelOr(defaultLabel)
    val cellWidth = 95.dp
    val labelHeight = 32.dp
    var showRemoveConfirm by remember { mutableStateOf(false) }

    // Committed by Apply, never per keystroke: OMT cannot rename a source in place either, so each
    // commit takes the source off the network and puts it back under the new name.
    var draftName by remember(output.omtName) { mutableStateOf(output.omtName) }
    val nameChanged = draftName != output.omtName

    fun update(updated: ScreenAssignment) {
        onSettingsChange { s -> s.copy(projectionSettings = s.projectionSettings.withOmtOutput(index, updated)) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledSwitch(
                    checked = output.omtEnabled,
                    onCheckedChange = { update(output.copy(omtEnabled = it)) },
                    label = stringResource(Res.string.ndi_enabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    spacing = 4.dp,
                )
                @OptIn(ExperimentalMaterial3Api::class)
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(Res.string.ndi_name_tooltip)) } },
                    state = rememberTooltipState(),
                ) {
                    SettingsTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = {
                            Text(
                                text = defaultLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.width(NAME_FIELD_WIDTH),
                    )
                }
                RaisedButton(
                    shape = AppShape(6.dp),
                    enabled = nameChanged,
                    onClick = { update(output.copy(omtName = draftName)) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(Res.string.apply), style = MaterialTheme.typography.labelSmall)
                }
                // Polled, for the reason the NDI card gives: the count lives in the library, not in
                // Compose state. Bounded by the dialog being open.
                val receivers by produceState(receiverCount(index), index) {
                    while (true) {
                        value = receiverCount(index)
                        delay(RECEIVER_POLL_MS)
                    }
                }
                Text(
                    text = if (receivers > 0) {
                        stringResource(Res.string.ndi_receivers, receivers)
                    } else {
                        stringResource(Res.string.ndi_no_receivers)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = { onIdentifyOmt(index) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.identify_screen), style = MaterialTheme.typography.labelSmall)
            }
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = { showRemoveConfirm = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(Res.string.remove), style = MaterialTheme.typography.labelSmall)
            }
        }

        // The name receivers actually list this output under. Worth showing because it is not the
        // name typed above: the library prefixes the machine's, and that prefix is what an operator
        // looks for in OBS.
        //
        // Polled like the receiver count, not read once: a rename replaces the sender, and the new
        // one registers after this card has already recomposed with the old name — read once, the
        // caption stayed on the old name until the dialog was reopened (found testing on Windows).
        val address by produceState(addressOf(index), index) {
            while (true) {
                value = addressOf(index)
                delay(RECEIVER_POLL_MS)
            }
        }
        if (address.isNotBlank()) {
            Text(
                text = stringResource(Res.string.omt_address, address),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                title = { Text(stringResource(Res.string.confirm_delete)) },
                text = { Text(stringResource(Res.string.ndi_confirm_remove_message, outputLabel)) },
                confirmButton = {
                    GhostButton(
                        shape = AppShape(6.dp),
                        onClick = {
                            showRemoveConfirm = false
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.removeOmtOutput(index))
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    GhostButton(shape = AppShape(6.dp), onClick = { showRemoveConfirm = false }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }

        // Dimmed rather than disabled while the output is off, as the NDI card does.
        Column(modifier = Modifier.alpha(if (output.omtEnabled) 1f else DISABLED_ALPHA)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val mode = OmtVideoRenderer.modeOf(output)
                LabeledDropdownCell(
                    label = stringResource(Res.string.ndi_mode),
                    value = omtModeLabel(mode),
                    options = OmtOutputMode.entries.map { omtModeLabel(it) to OmtVideoRenderer.storedModeOf(it) },
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                    help = omtModeHelp(mode),
                ) { update(output.copy(omtMode = it)) }
                Column(modifier = Modifier.width(cellWidth)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(labelHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            text = stringResource(Res.string.output_profile_picker_tooltip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutputProfilePicker(
                        profiles = outputProfiles,
                        activeProfileId = output.activeProfileId,
                        mergedBy = outputProfiles.mergingProfileOf(
                            Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, index),
                            output.activeProfileId,
                        ),
                        onPick = { pickedId -> update(output.copy(activeProfileId = pickedId)) },
                    )
                }
                ResolutionPicker(
                    label = stringResource(Res.string.ndi_resolution),
                    width = output.omtWidth,
                    height = output.omtHeight,
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                    onChange = { w, h -> update(output.copy(omtWidth = w, omtHeight = h)) },
                )
                LabeledDropdownCell(
                    label = stringResource(Res.string.ndi_fps),
                    value = output.omtFps.toString(),
                    options = OMT_FRAME_RATES.map { it.toString() to it.toString() },
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                ) { update(output.copy(omtFps = it.toInt())) }
                val quality = OmtVideoRenderer.qualityOf(output)
                LabeledDropdownCell(
                    label = stringResource(Res.string.omt_quality),
                    value = omtQualityLabel(quality),
                    options = OmtQuality.entries.map { omtQualityLabel(it) to OmtVideoRenderer.storedQualityOf(it) },
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                    help = if (quality == OmtQuality.DEFAULT) {
                        stringResource(Res.string.omt_quality_default_help)
                    } else {
                        stringResource(Res.string.omt_quality_fixed_help)
                    },
                ) { update(output.copy(omtQuality = it)) }
            }
        }
    }
}

@Composable
private fun omtModeLabel(mode: OmtOutputMode): String = when (mode) {
    OmtOutputMode.ALPHA -> stringResource(Res.string.ndi_mode_alpha)
    OmtOutputMode.FILL -> stringResource(Res.string.ndi_mode_fill)
}

@Composable
private fun omtModeHelp(mode: OmtOutputMode): String = when (mode) {
    OmtOutputMode.ALPHA -> stringResource(Res.string.omt_mode_alpha_help)
    OmtOutputMode.FILL -> stringResource(Res.string.omt_mode_fill_help)
}

@Composable
private fun omtQualityLabel(quality: OmtQuality): String = when (quality) {
    OmtQuality.DEFAULT -> stringResource(Res.string.omt_quality_default)
    OmtQuality.LOW -> stringResource(Res.string.omt_quality_low)
    OmtQuality.MEDIUM -> stringResource(Res.string.omt_quality_medium)
    OmtQuality.HIGH -> stringResource(Res.string.omt_quality_high)
}
