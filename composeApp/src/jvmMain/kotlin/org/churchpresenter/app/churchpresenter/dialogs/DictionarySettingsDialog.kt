package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.dictionary_settings_dialog_title
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DictionarySettingsTab
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 620.dp
private val DIALOG_HEIGHT = 700.dp

/**
 * How the dictionary looks on screen, opened from the gear beside Go Live on the Dictionary tab.
 *
 * Here rather than in the Options dialog, and global rather than per profile, for the reason the
 * STT tab's own settings are: this is one look for one kind of content, and an operator changing it
 * is looking at the dictionary at the time. It followed `SttSettings` out of the settings dialog
 * for exactly that reason -- see `STTSettingsDialog`, which this mirrors.
 *
 * The surface inside is [DictionarySettingsTab] unchanged: it was already the superset of every
 * dictionary control, including the bold/italic, backdrop and outline fields the Profiles tab's own
 * pane never offered.
 */
@Composable
fun DictionarySettingsDialog(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val mainWindowState = LocalMainWindowState.current
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, DIALOG_WIDTH, DIALOG_HEIGHT),
        width = DIALOG_WIDTH,
        height = DIALOG_HEIGHT,
    )
    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.dictionary_settings_dialog_title),
    ) {
        ProvideUiFontScale {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        DictionarySettingsTab(settings = appSettings, onSettingsChange = onSettingsChange)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        RaisedButton(onClick = onDismiss, shape = RoundedCornerShape(6.dp)) {
                            Text(stringResource(Res.string.close))
                        }
                    }
                }
            }
        }
    }
}
