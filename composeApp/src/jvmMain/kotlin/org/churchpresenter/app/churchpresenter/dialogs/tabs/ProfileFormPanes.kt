package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_subtitle_settings_hint
import churchpresenter.composeapp.generated.resources.profile_group_look
import org.churchpresenter.app.churchpresenter.composables.SubtitleStyleSettings
import org.churchpresenter.app.churchpresenter.dialogs.QADisplaySettings
import org.churchpresenter.app.churchpresenter.dialogs.STTDisplaySettings
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The pages for the whole-form categories -- live captions, subtitles, Q&A, the dictionary card and
 * the stage monitor's layout -- over this profile's own copy.
 *
 * Each is the form that used to live on its own tab or dialog, handed the profile's resolved
 * settings and the editor's write-back, so a control edits exactly one profile and nothing global.
 * They sit in one card each, on the page like every other group, until each is broken into rows
 * of its own.
 */
@Composable
internal fun ProfileFormPage(
    pane: CustomizePane,
    draft: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsGroup(stringResource(Res.string.profile_group_look)) {
        SettingsWideRow {
            when (pane) {
                CustomizePane.CAPTIONS -> STTDisplaySettings(
                    appSettings = draft,
                    onSettingsChange = onSettingsChange,
                    availableFonts = rememberSystemFonts(),
                )
                CustomizePane.QA -> QADisplaySettings(
                    appSettings = draft,
                    onSettingsChange = onSettingsChange,
                    availableFonts = rememberSystemFonts(),
                )
                CustomizePane.SUBTITLES -> {
                    // Which subtitles this reaches: only the files the app draws itself. Without it an
                    // embedded track ignoring every control here reads as a broken page.
                    Text(
                        text = stringResource(Res.string.media_subtitle_settings_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SubtitleStyleSettings(settings = draft, onSettingsChange = onSettingsChange)
                }
                // Pages of their own; the editor never sends them here.
                CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND, CustomizePane.DICTIONARY,
                CustomizePane.STAGE_MONITOR,
                -> Unit
            }
        }
    }
}
