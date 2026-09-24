package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_engine_detect
import churchpresenter.composeapp.generated.resources.bible_engine_host
import churchpresenter.composeapp.generated.resources.bible_engine_port
import churchpresenter.composeapp.generated.resources.bible_engine_run_local
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.stt_help_dev_mode
import churchpresenter.composeapp.generated.resources.stt_settings_dialog_title
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.app.churchpresenter.composables.StyledTextField
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun STTSettingsDialog(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {

    val mainWindowState = LocalMainWindowState.current
    // Only the Bible-engine options are left here -- everything about captions on screen is styled
    // per profile -- so the window opens at the size they need and grows if they ever outgrow it.
    val dialogWidth = 480.dp
    val dialogHeight = 220.dp
    val maxDialogHeight = 900.dp
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, dialogWidth, dialogHeight),
        width = dialogWidth,
        height = dialogHeight
    )

    // Grow the window (never shrink) when content overflows the current viewport, instead of
    // relying on a single guessed-at fixed height — the scroll stays as a fallback beyond the cap.
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val overflow = with(density) { scrollState.maxValue.toDp() }
            val grown = (dialogState.size.height + overflow).coerceAtMost(maxDialogHeight)
            if (grown > dialogState.size.height) {
                dialogState.size = DpSize(dialogState.size.width, grown)
            }
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.stt_settings_dialog_title),
        resizable = false
    ) {
        ProvideUiFontScale {
            STTSettingsDialogContent(
                appSettings = appSettings,
                onSettingsChange = onSettingsChange,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
internal fun STTSettingsDialogContent(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sttSettings = appSettings.sttSettings
    val engine = appSettings.bibleEngineSettings
    val scrollState = rememberScrollState()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scripture detection (Bible Lookup Engine) — the engine starts with the STT connection.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LabeledCheckbox(
                        checked = engine.enabled,
                        onCheckedChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(enabled = it)) } },
                        label = stringResource(Res.string.bible_engine_detect),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Toggle hidden for now — engine always runs locally (runLocal defaults true).
                    // Flip to true to expose the local/remote choice again.
                    @Suppress("KotlinConstantConditions")
                    val showRunEngineLocallyToggle = false
                    if (showRunEngineLocallyToggle) {
                        Spacer(Modifier.weight(1f))
                        LabeledCheckbox(
                            checked = engine.runLocal,
                            enabled = engine.enabled,
                            onCheckedChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(runLocal = it)) } },
                            label = stringResource(Res.string.bible_engine_run_local),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                AnimatedVisibility(visible = engine.enabled) {
                    LabeledCheckbox(
                        checked = engine.helpDevMode,
                        onCheckedChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(helpDevMode = it)) } },
                        label = stringResource(Res.string.stt_help_dev_mode),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        spacing = 4.dp,
                    )
                }
                AnimatedVisibility(visible = engine.enabled && !engine.runLocal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledTextField(
                            value = engine.host,
                            onValueChange = { onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(host = it)) } },
                            label = stringResource(Res.string.bible_engine_host),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        StyledTextField(
                            value = engine.port.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { p -> onSettingsChange { s -> s.copy(bibleEngineSettings = s.bibleEngineSettings.copy(port = p)) } } },
                            label = stringResource(Res.string.bible_engine_port),
                            singleLine = true,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                RaisedButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(6.dp),
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(Res.string.close))
                }
            }
        }
    }
