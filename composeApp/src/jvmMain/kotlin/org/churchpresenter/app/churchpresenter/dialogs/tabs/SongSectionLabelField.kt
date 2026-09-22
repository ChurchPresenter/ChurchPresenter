package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_section_label
import churchpresenter.composeapp.generated.resources.song_section_label_color
import churchpresenter.composeapp.generated.resources.song_section_label_enabled
import churchpresenter.composeapp.generated.resources.song_section_label_font_size
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSectionLabel
import org.jetbrains.compose.resources.stringResource

private val LABEL_FIELD_WIDTH = 90.dp

/**
 * The current section's own label ("Verse 1", "Chorus"), drawn above the lyrics while it is shown.
 *
 * Its own card rather than folded into [SongLyricsLayoutSection]: it draws on every output the
 * lyrics do, not just the fullscreen/lower-third pair those toggles style one at a time, so it sits
 * beside them as a setting of the slide as a whole -- the same reasoning [LowerThirdHeightSection]
 * documents for its own placement.
 */
@Composable
internal fun SongSectionLabelSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val label = settings.songSettings.layoutExtras.sectionLabel
    fun update(transform: (SongSectionLabel) -> SongSectionLabel) {
        onSettingsChange { s ->
            s.copy(
                songSettings = s.songSettings.copy(
                    layoutExtras = s.songSettings.layoutExtras.copy(sectionLabel = transform(label)),
                ),
            )
        }
    }
    SettingsSection(title = stringResource(Res.string.song_section_label)) {
        LabeledCheckbox(
            checked = label.enabled,
            onCheckedChange = { on -> update { it.copy(enabled = on) } },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.song_section_label_enabled),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_sectionLabel_enabled"),
        )
        if (label.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ControlColumn(stringResource(Res.string.song_section_label_font_size), Modifier.weight(1f)) {
                    NumberSettingsTextField(
                        modifier = Modifier.width(LABEL_FIELD_WIDTH),
                        initialText = label.fontSize,
                        onValueChange = { value -> update { it.copy(fontSize = value) } },
                        range = SongSectionLabel.FONT_SIZE_RANGE,
                    )
                }
                ControlColumn(stringResource(Res.string.song_section_label_color), Modifier.weight(1f)) {
                    ColorPickerField(
                        color = label.color,
                        onColorChange = { value -> update { it.copy(color = value) } },
                        modifier = Modifier.width(LABEL_FIELD_WIDTH),
                    )
                }
            }
        }
    }
}
