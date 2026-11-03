package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.STTTabView
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.obs_mode_stt
import org.churchpresenter.strings.generated.resources.stt_tab_font_size
import org.churchpresenter.strings.generated.resources.stt_tab_highlighting
import org.churchpresenter.strings.generated.resources.stt_tab_in_progress
import org.churchpresenter.strings.generated.resources.stt_tab_layout
import org.churchpresenter.strings.generated.resources.stt_tab_layout_side_by_side
import org.churchpresenter.strings.generated.resources.stt_tab_layout_stacked
import org.churchpresenter.strings.generated.resources.stt_tab_lines
import org.churchpresenter.strings.generated.resources.stt_tab_section
import org.churchpresenter.strings.generated.resources.stt_tab_shows
import org.churchpresenter.strings.generated.resources.stt_tab_shows_both
import org.churchpresenter.strings.generated.resources.stt_tab_shows_hint
import org.churchpresenter.strings.generated.resources.stt_tab_translation_in_progress
import org.churchpresenter.strings.generated.resources.stt_translation_label
import org.jetbrains.compose.resources.stringResource

/**
 * The STT tab's own preview, as its settings dialog edits it ([STTTabView]): what it shows, how many
 * lines, the text still being spoken, highlighting, size and layout. Outputs ignore all of it.
 */
@Composable
internal fun STTTabViewFields(view: STTTabView, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val edit: (STTTabView.() -> STTTabView) -> Unit = { change ->
        onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(tab = s.sttSettings.tab.change())) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.stt_tab_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(
                label = stringResource(Res.string.stt_tab_shows),
                current = view.shows,
                labels = mapOf(
                    STTTabView.SHOWS_BOTH to stringResource(Res.string.stt_tab_shows_both),
                    STTTabView.SHOWS_TRANSCRIPTION to stringResource(Res.string.obs_mode_stt),
                    STTTabView.SHOWS_TRANSLATION to stringResource(Res.string.stt_translation_label),
                ),
            ) { edit { copy(shows = it) } }
            Choice(
                label = stringResource(Res.string.stt_tab_layout),
                current = view.layout,
                labels = mapOf(
                    STTTabView.LAYOUT_SIDE_BY_SIDE to stringResource(Res.string.stt_tab_layout_side_by_side),
                    STTTabView.LAYOUT_STACKED to stringResource(Res.string.stt_tab_layout_stacked),
                ),
            ) { edit { copy(layout = it) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberSettingsTextField(
                label = stringResource(Res.string.stt_tab_lines),
                initialText = view.maxSegments,
                range = STTTabView.MAX_SEGMENTS,
                onValueChange = { edit { copy(maxSegments = it) } },
            )
            NumberSettingsTextField(
                label = stringResource(Res.string.stt_tab_font_size),
                initialText = view.fontSize,
                range = STTTabView.FONT_SIZES,
                onValueChange = { edit { copy(fontSize = it) } },
            )
        }
        Check(stringResource(Res.string.stt_tab_in_progress), view.showInProgress) {
            edit { copy(showInProgress = it) }
        }
        Check(stringResource(Res.string.stt_tab_translation_in_progress), view.showTranslationInProgress) {
            edit { copy(showTranslationInProgress = it) }
        }
        Check(stringResource(Res.string.stt_tab_highlighting), view.wordHighlighting) {
            edit { copy(wordHighlighting = it) }
        }
        Text(
            stringResource(Res.string.stt_tab_shows_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A dropdown over [labels]' values, saving the key of the one picked (always one of its own options). */
@Composable
private fun Choice(label: String, current: String, labels: Map<String, String>, onPick: (String) -> Unit) {
    val keys = labels.keys.toList()
    val options = labels.values.toList()
    DropdownSettingsField(
        value = labels[current] ?: options.first(),
        options = options,
        onValueChange = { picked -> onPick(keys[options.indexOf(picked)]) },
        label = label,
    )
}

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabeledCheckbox(
        checked = checked,
        onCheckedChange = onChange,
        label = label,
        color = MaterialTheme.colorScheme.onSurface,
        spacing = 4.dp,
    )
}
