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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.app.churchpresenter.presenter.NdiVideoRenderer
import org.churchpresenter.ndi.NdiOutputMode
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.ndi.NdiSender
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_ndi_output
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_outputs
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_short
import churchpresenter.composeapp.generated.resources.content_outputs_for
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.ndi_confirm_remove_message
import churchpresenter.composeapp.generated.resources.ndi_enabled
import churchpresenter.composeapp.generated.resources.ndi_fps
import churchpresenter.composeapp.generated.resources.ndi_mode
import churchpresenter.composeapp.generated.resources.ndi_mode_alpha
import churchpresenter.composeapp.generated.resources.ndi_mode_alpha_help
import churchpresenter.composeapp.generated.resources.ndi_mode_fill
import churchpresenter.composeapp.generated.resources.ndi_mode_fill_help
import churchpresenter.composeapp.generated.resources.ndi_mode_fill_key
import churchpresenter.composeapp.generated.resources.ndi_mode_fill_key_help
import churchpresenter.composeapp.generated.resources.ndi_name_tooltip
import churchpresenter.composeapp.generated.resources.ndi_no_receivers
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.ndi_outputs
import churchpresenter.composeapp.generated.resources.ndi_outputs_help
import churchpresenter.composeapp.generated.resources.ndi_receivers
import churchpresenter.composeapp.generated.resources.ndi_resolution
import churchpresenter.composeapp.generated.resources.ndi_runtime_browse
import churchpresenter.composeapp.generated.resources.ndi_runtime_check_again
import churchpresenter.composeapp.generated.resources.ndi_runtime_install_link
import churchpresenter.composeapp.generated.resources.ndi_runtime_load_failed
import churchpresenter.composeapp.generated.resources.ndi_runtime_missing_help
import churchpresenter.composeapp.generated.resources.ndi_runtime_missing_title
import churchpresenter.composeapp.generated.resources.ndi_runtime_path
import churchpresenter.composeapp.generated.resources.ndi_runtime_path_help
import churchpresenter.composeapp.generated.resources.ndi_runtime_ready
import churchpresenter.composeapp.generated.resources.ndi_runtime_unsupported_cpu
import churchpresenter.composeapp.generated.resources.ndi_trademark
import churchpresenter.composeapp.generated.resources.projection_web_decklink_tooltip
import churchpresenter.composeapp.generated.resources.remove
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.composables.LabeledSwitch
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.jetbrains.compose.resources.stringResource
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private const val DISABLED_ALPHA = 0.5f
private val NAME_FIELD_WIDTH = 150.dp
private val PATH_FIELD_WIDTH = 320.dp
private val NDI_RESOLUTIONS = listOf(1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160)
private val NDI_FRAME_RATES = listOf(24, 25, 30, 50, 60)

/**
 * How often the card re-asks the runtime how many receivers an output has.
 *
 * A second is far below what an operator notices and far above what the call costs — it is a
 * non-blocking read of a counter the runtime already maintains (`NDIlib_send_get_no_connections`
 * with a zero timeout).
 */
private const val RECEIVER_POLL_MS = 1_000L

/** Where the runtime is downloaded from. Shown to the operator, and opened by the install button. */
internal const val NDI_RUNTIME_URL = "https://ndi.video/download-ndi-sdk/"

/**
 * The "NDI outputs" card of the Projection settings tab.
 *
 * A card here rather than a settings tab of its own, for the same reason the Browser Source outputs
 * are: `OptionsDialog`'s tab indices are hardcoded in three separate places plus
 * `companionSatelliteTabIndex` and the screenshot tests, and a new tab renumbers all of them.
 *
 * Two states, and only one of them is a fault. With no runtime installed the card is a paragraph
 * and a download link — the ordinary first-run state, since this app ships no NDI binaries and may
 * not. With one installed it is the output list.
 */
@Composable
internal fun NdiOutputsCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    displayModes: List<Pair<String, String>>,
    songLangModes: List<Pair<String, String>>,
    translationDisplays: List<BibleTranslationDisplay>,
    translationNames: List<String>,
    onIdentifyNdi: (Int) -> Unit = {},
    /**
     * What the app found when it looked for a runtime.
     *
     * A parameter so a screenshot can pin it. Left reading the live [NdiManager] the card draws
     * whatever the recording machine happens to have installed — the same nondeterminism that made
     * `canvasTab/source_camera` enumerate the host's real cameras.
     */
    status: NdiRuntimeStatus = NdiManager.status.collectAsState().value,
    /**
     * How many receivers are watching output N. A parameter for the same reason as [status].
     *
     * Read during composition, so it refreshes when something else on this card changes rather than
     * on a timer. A polling `produceState` would be more live and is deliberately not used: an
     * unbounded loop inside a composable is what the suite's known teardown hang is made of, and a
     * number that updates when the operator touches the card is enough to tell an unwatched source
     * from a broken one — which is the whole reason it is shown.
     */
    receiverCount: (Int) -> Int = NdiManager::connectionCount,
) {
    val proj = settings.projectionSettings

    SettingsSection(title = stringResource(Res.string.ndi_outputs)) {
        Text(
            text = stringResource(Res.string.ndi_outputs_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        NdiRuntimeRow(status = status, path = proj.ndiRuntimePath, onSettingsChange = onSettingsChange)
        Spacer(modifier = Modifier.height(8.dp))

        if (status.isReady) {
            proj.ndiOutputs.forEachIndexed { i, output ->
                NdiOutputRow(
                    index = i,
                    output = output,
                    receiverCount = receiverCount,
                    onIdentifyNdi = onIdentifyNdi,
                    onSettingsChange = onSettingsChange,
                    contentGroup = contentGroup,
                    backgroundGroup = backgroundGroup,
                    displayModes = displayModes,
                    songLangModes = songLangModes,
                    translationDisplays = translationDisplays,
                    translationNames = translationNames,
                )
            }
            Button(
                shape = RoundedCornerShape(6.dp),
                onClick = {
                    onSettingsChange { s -> s.copy(projectionSettings = s.projectionSettings.addNdiOutput()) }
                },
            ) {
                Text(stringResource(Res.string.add_ndi_output), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Required by the NDI licence terms wherever NDI is offered, alongside the About box line.
        Text(
            text = stringResource(Res.string.ndi_trademark),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the app found when it looked for the runtime, and the override path.
 *
 * Absent reads as an invitation rather than an error: the runtime is a separate free download and
 * most machines will not have it until someone goes and gets it.
 */
@Composable
private fun NdiRuntimeRow(
    status: NdiRuntimeStatus,
    path: String,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NdiRuntimeMessage(status)

        val scope = rememberCoroutineScope()
        val ndiFolderTitle = stringResource(Res.string.ndi_runtime_path)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Read-only plus a Browse button, exactly as the VLC path field on this same tab is: a
            // folder is picked, not typed, and a path typed halfway is a path that does not exist.
            SettingsTextField(
                value = path,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.ndi_runtime_path),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.width(PATH_FIELD_WIDTH),
            )
            OutlinedButton(
                shape = RoundedCornerShape(6.dp),
                onClick = {
                    scope.launch {
                        val chosen = FileChooser.platformInstance.chooseSingle(
                            path = path.takeIf { it.isNotBlank() }?.let(::Path),
                            title = ndiFolderTitle,
                            selectDirectory = true,
                            filters = emptyList(),
                        ) ?: return@launch
                        val selected = chosen.absolutePathString()
                        onSettingsChange { s ->
                            s.copy(projectionSettings = s.projectionSettings.copy(ndiRuntimePath = selected))
                        }
                        withContext(Dispatchers.IO) { NdiManager.ensureStarted(selected) }
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.ndi_runtime_browse), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                shape = RoundedCornerShape(6.dp),
                // Off the UI thread for the same reason main.kt's first start is: this is a
                // `Native.load`, and on the click handler it would freeze the dialog while it ran.
                onClick = { scope.launch(Dispatchers.IO) { NdiManager.ensureStarted(path) } },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.ndi_runtime_check_again), style = MaterialTheme.typography.labelSmall)
            }
            if (!status.isReady) {
                Button(
                    shape = RoundedCornerShape(6.dp),
                    onClick = { openNdiRuntimePage() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.ndi_runtime_install_link),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.ndi_runtime_path_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What [status] means, in the operator's words. Only two of the four cases are a fault. */
@Composable
private fun NdiRuntimeMessage(status: NdiRuntimeStatus) {
    when (status) {
        is NdiRuntimeStatus.Ready -> Text(
            text = stringResource(Res.string.ndi_runtime_ready, status.version),
            style = MaterialTheme.typography.bodyMedium,
        )
        is NdiRuntimeStatus.LoadFailed -> Text(
            text = stringResource(Res.string.ndi_runtime_load_failed, status.path),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        NdiRuntimeStatus.UnsupportedCpu -> Text(
            text = stringResource(Res.string.ndi_runtime_unsupported_cpu),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        NdiRuntimeStatus.NotInstalled -> {
            Text(
                text = stringResource(Res.string.ndi_runtime_missing_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(Res.string.ndi_runtime_missing_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One configured NDI output: its name, mode, size, rate and per-content-type toggles. */
@Composable
@Suppress("LongMethod")  // One card row, in the shape the sibling Browser Source card established.
private fun NdiOutputRow(
    index: Int,
    output: ScreenAssignment,
    receiverCount: (Int) -> Int,
    onIdentifyNdi: (Int) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    contentGroup: List<ContentCol>,
    backgroundGroup: List<ContentCol>,
    displayModes: List<Pair<String, String>>,
    songLangModes: List<Pair<String, String>>,
    translationDisplays: List<BibleTranslationDisplay>,
    translationNames: List<String>,
) {
    val defaultLabel = stringResource(Res.string.ndi_output_numbered, index + 1)
    val outputLabel = output.ndiLabelOr(defaultLabel)
    val fullScreenLabel = stringResource(Res.string.display_fullscreen)
    val cellWidth = 95.dp
    val labelHeight = 32.dp
    var showRemoveConfirm by remember { mutableStateOf(false) }

    fun update(updated: ScreenAssignment) {
        onSettingsChange { s -> s.copy(projectionSettings = s.projectionSettings.withNdiOutput(index, updated)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(10.dp),
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
                    checked = output.ndiEnabled,
                    onCheckedChange = { update(output.copy(ndiEnabled = it)) },
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
                        value = output.ndiName,
                        onValueChange = { update(output.copy(ndiName = it)) },
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
                // An NDI source nobody has subscribed to looks exactly like a broken one without
                // this, which is why the count is worth the space.
                //
                // Polled rather than read once: the count lives in the NDI runtime, not in Compose
                // state, so nothing invalidates this composition when someone attaches a receiver.
                // Read inline it froze at whatever it was when the card opened — reported from a
                // real install as "No receivers" with a receiver attached. The effect is keyed on
                // the output and dies with the card, so it is bounded by the dialog being open.
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
            // Flashes this output's name into the feed itself, the way the Browser Source card
            // does. Worth having even though an NDI source is named in the receiver's list: with
            // several outputs live, it answers "which of these am I actually looking at".
            Button(
                shape = RoundedCornerShape(6.dp),
                onClick = { onIdentifyNdi(index) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(Res.string.identify_screen), style = MaterialTheme.typography.labelSmall)
            }
            Button(
                shape = RoundedCornerShape(6.dp),
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

        if (showRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                title = { Text(stringResource(Res.string.confirm_delete)) },
                text = { Text(stringResource(Res.string.ndi_confirm_remove_message, outputLabel)) },
                confirmButton = {
                    TextButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = {
                            showRemoveConfirm = false
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.removeNdiOutput(index))
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(shape = RoundedCornerShape(6.dp), onClick = { showRemoveConfirm = false }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }

        // Dimmed rather than disabled while the output is off, as the Browser Source card does: it
        // is obvious at a glance which outputs are inactive, and the controls still work.
        Column(modifier = Modifier.alpha(if (output.ndiEnabled) 1f else DISABLED_ALPHA)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NdiModePicker(
                    output = output,
                    defaultLabel = outputLabel,
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                ) {
                    update(output.copy(ndiMode = it))
                }
                NdiDropdownCell(
                    label = stringResource(Res.string.display_mode),
                    value = displayModes.find { it.second == output.displayMode }?.first ?: fullScreenLabel,
                    options = displayModes.map { it.first to it.second },
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                ) { update(output.copy(displayMode = it)) }
                NdiDropdownCell(
                    label = stringResource(Res.string.ndi_resolution),
                    value = "${output.ndiWidth}×${output.ndiHeight}",
                    options = NDI_RESOLUTIONS.map { (w, h) -> "$w×$h" to "$w:$h" },
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                ) { chosen ->
                    val (w, h) = chosen.split(':').map(String::toInt)
                    update(output.copy(ndiWidth = w, ndiHeight = h))
                }
                NdiDropdownCell(
                    label = stringResource(Res.string.ndi_fps),
                    value = output.ndiFps.toString(),
                    options = NDI_FRAME_RATES.map { it.toString() to it.toString() },
                    cellWidth = cellWidth,
                    labelHeight = labelHeight,
                ) { update(output.copy(ndiFps = it.toInt())) }

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(labelHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            text = stringResource(Res.string.content_outputs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    var showContentDialog by remember { mutableStateOf(false) }
                    val enabledCount = contentOutputsEnabledCount(output, contentGroup, backgroundGroup)
                    val totalCount = contentOutputsTotalCount(contentGroup, backgroundGroup)
                    OutlinedButton(
                        shape = RoundedCornerShape(6.dp),
                        onClick = { showContentDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.content_outputs_enabled_short, enabledCount, totalCount),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (showContentDialog) {
                        ContentOutputsDialog(
                            title = stringResource(Res.string.content_outputs_for, outputLabel),
                            screenLabel = outputLabel,
                            assignment = output,
                            contentGroup = contentGroup,
                            backgroundGroup = backgroundGroup,
                            bibleLabel = stringResource(Res.string.content_bible),
                            songsLabel = stringResource(Res.string.content_songs),
                            translationNames = translationNames,
                            translationDisplays = translationDisplays,
                            songLangModes = songLangModes,
                            webDeckLinkTooltip = stringResource(Res.string.projection_web_decklink_tooltip),
                            webSnapshotTooltip = stringResource(Res.string.projection_web_decklink_tooltip),
                            isBrowserSource = true,
                            onApply = { updated -> update(updated) },
                            onDismiss = { showContentDialog = false },
                        )
                    }
                }
            }
        }
    }
}

/** The mode picker, with each mode's one-line explanation as its tooltip. */
@Composable
private fun NdiModePicker(
    output: ScreenAssignment,
    defaultLabel: String,
    cellWidth: Dp,
    labelHeight: Dp,
    onPick: (String) -> Unit,
) {
    val modes = listOf(
        NdiOutputMode.ALPHA to stringResource(Res.string.ndi_mode_alpha),
        NdiOutputMode.FILL to stringResource(Res.string.ndi_mode_fill),
        NdiOutputMode.FILL_AND_KEY to stringResource(Res.string.ndi_mode_fill_key),
    )
    val helps = mapOf(
        NdiOutputMode.ALPHA to stringResource(Res.string.ndi_mode_alpha_help),
        NdiOutputMode.FILL to stringResource(Res.string.ndi_mode_fill_help),
        NdiOutputMode.FILL_AND_KEY to stringResource(
            Res.string.ndi_mode_fill_key_help,
            NdiSender.keyNameFor(defaultLabel),
        ),
    )
    val current = NdiVideoRenderer.modeOf(output)
    Column(modifier = Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().height(labelHeight), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = stringResource(Res.string.ndi_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        var expanded by remember { mutableStateOf(false) }
        @OptIn(ExperimentalMaterial3Api::class)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(helps.getValue(current)) } },
            state = rememberTooltipState(),
        ) {
            OutlinedButton(
                shape = RoundedCornerShape(6.dp),
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = modes.first { it.first == current }.second,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        expanded = false
                        onPick(NdiVideoRenderer.storedModeOf(mode))
                    },
                )
            }
        }
    }
}

/** A labelled dropdown cell, the shape the sibling cards use for display mode, resolution and fps. */
@Composable
private fun NdiDropdownCell(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    cellWidth: Dp,
    labelHeight: Dp,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().height(labelHeight), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        var expanded by remember { mutableStateOf(false) }
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        expanded = false
                        onPick(optionValue)
                    },
                )
            }
        }
    }
}

/** Opens the NDI download page in the operator's browser. */
private fun openNdiRuntimePage() {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(NDI_RUNTIME_URL)) }
}
