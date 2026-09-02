package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.analytics_reporting
import churchpresenter.composeapp.generated.resources.analytics_reporting_hint
import churchpresenter.composeapp.generated.resources.clear_lottie_cache_confirm
import churchpresenter.composeapp.generated.resources.clear_remote_uploads
import churchpresenter.composeapp.generated.resources.clear_remote_uploads_confirm
import churchpresenter.composeapp.generated.resources.export_settings
import churchpresenter.composeapp.generated.resources.general
import churchpresenter.composeapp.generated.resources.import_settings
import churchpresenter.composeapp.generated.resources.import_settings_confirm
import churchpresenter.composeapp.generated.resources.launch_on_login
import churchpresenter.composeapp.generated.resources.remote_uploads_cleared
import churchpresenter.composeapp.generated.resources.reset_settings
import churchpresenter.composeapp.generated.resources.reset_settings_confirm
import churchpresenter.composeapp.generated.resources.send_test_event
import churchpresenter.composeapp.generated.resources.settings_export_failed
import churchpresenter.composeapp.generated.resources.settings_exported
import churchpresenter.composeapp.generated.resources.settings_import_failed
import churchpresenter.composeapp.generated.resources.system_manage_settings
import churchpresenter.composeapp.generated.resources.test_event_dev_only
import churchpresenter.composeapp.generated.resources.test_event_failed
import churchpresenter.composeapp.generated.resources.test_event_sent
import churchpresenter.composeapp.generated.resources.test_event_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.AutoStartManager
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import javax.swing.JOptionPane

@Composable
fun SystemSettingsTab(
    settings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    companionServer: CompanionServer? = null
) {
    val fileManager = FileManager()

    val setAllDirectories: (String) -> Unit = { dir ->
        onSettingsChange { s ->
            s.copy(
                bibleSettings = s.bibleSettings.copy(storageDirectory = dir),
                songSettings = s.songSettings.copy(storageDirectory = dir),
                pictureSettings = s.pictureSettings.copy(storageDirectory = dir),
                streamingSettings = s.streamingSettings.copy(lowerThirdFolder = dir),
                presentationStorageDirectory = dir,
                mediaStorageDirectory = dir
            )
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 16.dp, bottom = 22.dp)
                .padding(end = SettingsScrollbarGutter)
        ) {
            // Side by side once there is room for the storage rows at full width beside the
            // switches: a folder row is a path plus two buttons, and squeezing it to make space for
            // a column of toggles is the wrong trade. Below that the two stack, as they always did.
            val sideBySide = maxWidth >= SIDE_BY_SIDE_MIN_WIDTH
            val storage: @Composable () -> Unit = {
                SystemStorageCard(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    fileManager = fileManager,
                    onSetAll = setAllDirectories
                )
            }
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.weight(1f)) { storage() }
                    Column(
                        modifier = Modifier.width(SIDE_COLUMN_WIDTH),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        GeneralCard(settings, onSettingsChange)
                        ManageSettingsCard(companionServer)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    storage()
                    GeneralCard(settings, onSettingsChange)
                    ManageSettingsCard(companionServer)
                }
            }
        }
        SettingsScrollbar(scrollState)
    }
}

/** Below this the storage rows lose more than the second column gains, so the cards stack. */
private val SIDE_BY_SIDE_MIN_WIDTH = 1100.dp

/** The right-hand column: wide enough for a switch row's label and its explanation. */
private val SIDE_COLUMN_WIDTH = 400.dp

/** One switch, with the sentence explaining what turning it on means. */
@Composable
private fun GeneralToggleRow(
    label: String,
    hint: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GeneralCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val scope = rememberCoroutineScope()
    SettingsCard(title = stringResource(Res.string.general)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Launch at login — the OS registration is the source of truth, not settings.json
            var autoStartEnabled by remember { mutableStateOf(AutoStartManager.isEnabled()) }
            GeneralToggleRow(
                label = stringResource(Res.string.launch_on_login),
                checked = autoStartEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { AutoStartManager.setEnabled(enabled) }
                        if (ok) autoStartEnabled = enabled
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            GeneralToggleRow(
                label = stringResource(Res.string.analytics_reporting),
                hint = stringResource(Res.string.analytics_reporting_hint),
                checked = settings.analyticsReportingEnabled,
                onCheckedChange = { enabled ->
                    CrashReporter.setReportingEnabled(enabled)
                    onSettingsChange { s -> s.copy(analyticsReportingEnabled = enabled) }
                }
            )
            // Send a diagnostic test event to Sentry to verify the crash-reporting pipeline.
            // Developer-only affordance — hidden in packaged installer releases.
            if (!BuildConfig.IS_RELEASE && settings.analyticsReportingEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TestEventRow(scope)
            }
        }
    }
}

@Composable
private fun TestEventRow(scope: CoroutineScope) {
    val testEventTitle = stringResource(Res.string.test_event_title)
    val testEventSentMsg = stringResource(Res.string.test_event_sent)
    val testEventFailedMsg = stringResource(Res.string.test_event_failed)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.send_test_event),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.test_event_dev_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Button(
            onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { CrashReporter.sendTestEvent() }
                    JOptionPane.showMessageDialog(
                        activeWindow(),
                        if (ok) testEventSentMsg else testEventFailedMsg,
                        testEventTitle,
                        if (ok) JOptionPane.INFORMATION_MESSAGE else JOptionPane.WARNING_MESSAGE
                    )
                }
            },
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 15.dp)
        ) {
            Text(stringResource(Res.string.send_test_event), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Everything that acts on the settings file as a whole — kept apart, since two of them restart the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageSettingsCard(companionServer: CompanionServer?) {
    val scope = rememberCoroutineScope()
    val exportTitle = stringResource(Res.string.export_settings)
    val importTitle = stringResource(Res.string.import_settings)
    val resetTitle = stringResource(Res.string.reset_settings)
    val clearUploadsTitle = stringResource(Res.string.clear_remote_uploads)
    val exportedMsg = stringResource(Res.string.settings_exported)
    val exportFailedMsg = stringResource(Res.string.settings_export_failed)
    val importConfirmMsg = stringResource(Res.string.import_settings_confirm)
    val importFailedMsg = stringResource(Res.string.settings_import_failed)
    val resetConfirmMsg = stringResource(Res.string.reset_settings_confirm)
    val clearCacheMsg = stringResource(Res.string.clear_lottie_cache_confirm)
    val clearUploadsConfirmMsg = stringResource(Res.string.clear_remote_uploads_confirm)
    val uploadsClearedMsg = stringResource(Res.string.remote_uploads_cleared)

    SettingsCard(title = stringResource(Res.string.system_manage_settings)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            ManageButton(exportTitle) {
                scope.launch { exportSettings(exportTitle, exportedMsg, exportFailedMsg) }
            }
            ManageButton(importTitle) {
                scope.launch { importSettings(importTitle, importConfirmMsg, importFailedMsg, companionServer) }
            }
            Spacer(modifier = Modifier.weight(1f))
            ManageButton(clearUploadsTitle, danger = true) {
                clearRemoteUploads(clearUploadsTitle, clearUploadsConfirmMsg, uploadsClearedMsg)
            }
            ManageButton(resetTitle, danger = true) {
                resetAllSettings(resetTitle, resetConfirmMsg, clearCacheMsg, companionServer)
            }
        }
    }
}

@Composable
private fun ManageButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 15.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (danger) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (danger) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}
