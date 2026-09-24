package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_subtitle_settings_hint
import org.churchpresenter.app.churchpresenter.composables.SubtitleStyleSettings
import org.churchpresenter.app.churchpresenter.dialogs.QADisplaySettings
import org.churchpresenter.app.churchpresenter.dialogs.STTDisplaySettings
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The editor for one of the whole-form style categories -- captions, subtitles, Q&A and the
 * dictionary card -- over this profile's own copy.
 *
 * Each is the form that used to live on its own tab or dialog, unchanged, handed the profile's
 * resolved settings and the editor's write-back ([ProfileEditor]'s `onDraftSettingsChange`), so a
 * control edits exactly one profile and nothing global. The stage monitor has its own branch in
 * [CustomizeControls], as it always has.
 */
@Composable
internal fun ProfileFormPane(
    pane: CustomizePane,
    draft: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    when (pane) {
        // Scrolls and pads itself -- it was a whole settings tab.
        CustomizePane.DICTIONARY -> DictionarySettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        CustomizePane.CAPTIONS -> PaneScaffold {
            STTDisplaySettings(
                appSettings = draft,
                onSettingsChange = onSettingsChange,
                availableFonts = rememberSystemFonts(),
            )
        }
        CustomizePane.QA -> PaneScaffold {
            QADisplaySettings(
                appSettings = draft,
                onSettingsChange = onSettingsChange,
                availableFonts = rememberSystemFonts(),
            )
        }
        CustomizePane.SUBTITLES -> PaneScaffold {
            // Which subtitles this reaches: only the files the app draws itself. Without it an
            // embedded track ignoring every control here reads as a broken tab.
            Text(
                text = stringResource(Res.string.media_subtitle_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SubtitleStyleSettings(settings = draft, onSettingsChange = onSettingsChange)
        }
        // Not whole-form categories; [CustomizeControls] never sends them here.
        CustomizePane.STAGE_MONITOR, CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND -> Unit
    }
}
