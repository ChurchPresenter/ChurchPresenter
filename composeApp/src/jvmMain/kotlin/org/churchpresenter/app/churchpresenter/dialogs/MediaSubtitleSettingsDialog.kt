package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.media_subtitle_settings
import churchpresenter.composeapp.generated.resources.media_subtitle_settings_hint
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.SubtitleStyleSettings
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/**
 * Font, colour, size, backdrop, outline and position for the Media tab's subtitle overlay --
 * opened from its own gear icon next to Go Live, the same way `STTSettingsDialog` sits next to the
 * STT tab's. Applies to SRT/WebVTT subtitles the app draws itself (`SubtitleOverlay`); an embedded
 * track or an ASS/SSA/SUB file keeps VLC's own look, so there is nothing here to configure for it.
 */
@Composable
fun MediaSubtitleSettingsDialog(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val mainWindowState = LocalMainWindowState.current
    val dialogWidth = 560.dp
    // Snug for this dialog's own content -- unlike STTSettingsDialog's, there's no mode/layout/drip
    // -feed section above the styling, so matching that dialog's 640.dp starting height left a
    // slab of empty space below Close.
    val dialogHeight = 420.dp
    val maxDialogHeight = 900.dp
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, dialogWidth, dialogHeight),
        width = dialogWidth,
        height = dialogHeight
    )

    // The height this dialog would be from its own content overflowing -- grows, never shrinks,
    // same as STTSettingsDialog. Tracked apart from `dialogState.size` because the font picker
    // below temporarily asks for more than this on top.
    var contentHeight by remember { mutableStateOf(dialogHeight) }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val overflow = with(density) { scrollState.maxValue.toDp() }
            contentHeight = (contentHeight + overflow).coerceAtMost(maxDialogHeight)
        }
    }

    // The font picker's own panel sizes itself as a fraction of *this* window (see
    // FontSettingsDropdown's `onExpandedChange` doc) -- so a dialog kept snug for its own content
    // left too little room for a usable, scrollable font list. Grown only while the picker is
    // actually open, and dropped back to `contentHeight` once it closes, rather than staying that
    // tall (and that empty below Close) the rest of the time.
    var fontPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(fontPickerOpen, contentHeight) {
        dialogState.size = DpSize(dialogWidth, if (fontPickerOpen) maxDialogHeight else contentHeight)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = stringResource(Res.string.media_subtitle_settings),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.media_subtitle_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SubtitleStyleSettings(
                    settings = appSettings,
                    onSettingsChange = onSettingsChange,
                    onFontPickerExpandedChange = { fontPickerOpen = it },
                )

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
}
