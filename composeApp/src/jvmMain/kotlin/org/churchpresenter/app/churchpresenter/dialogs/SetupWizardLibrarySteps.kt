package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.appearance
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.content_outputs
import org.churchpresenter.strings.generated.resources.content_outputs_enabled_short
import org.churchpresenter.strings.generated.resources.detected_screens
import org.churchpresenter.strings.generated.resources.full_screen
import org.churchpresenter.strings.generated.resources.display_mode
import org.churchpresenter.strings.generated.resources.identify_screen
import org.churchpresenter.strings.generated.resources.key_output
import org.churchpresenter.strings.generated.resources.key_output_none
import org.churchpresenter.strings.generated.resources.presenter_windows_count
import org.churchpresenter.strings.generated.resources.projection
import org.churchpresenter.strings.generated.resources.projection_auto_display
import org.churchpresenter.strings.generated.resources.projection_target_display
import org.churchpresenter.strings.generated.resources.screen_assignment
import org.churchpresenter.strings.generated.resources.setup_bible_choose_folder
import org.churchpresenter.strings.generated.resources.setup_bible_pick_translations
import org.churchpresenter.strings.generated.resources.setup_proj_assign_note
import org.churchpresenter.strings.generated.resources.setup_proj_lang_note
import org.churchpresenter.strings.generated.resources.setup_proj_rows_note
import org.churchpresenter.strings.generated.resources.setup_proj_step1
import org.churchpresenter.strings.generated.resources.setup_proj_step2
import org.churchpresenter.strings.generated.resources.setup_proj_step5
import org.churchpresenter.strings.generated.resources.setup_proj_subtitle
import org.churchpresenter.strings.generated.resources.setup_proj_tip
import org.churchpresenter.strings.generated.resources.setup_proj_tip2
import org.churchpresenter.strings.generated.resources.setup_proj_title
import org.churchpresenter.strings.generated.resources.setup_songs_converter_body
import org.churchpresenter.strings.generated.resources.shortcut_description_open_converter
import org.churchpresenter.strings.generated.resources.setup_songs_format_note
import org.churchpresenter.strings.generated.resources.setup_songs_samples_note
import org.churchpresenter.strings.generated.resources.setup_step2_download_hint
import org.churchpresenter.strings.generated.resources.setup_step2_step1
import org.churchpresenter.strings.generated.resources.setup_step2_step2
import org.churchpresenter.strings.generated.resources.setup_step2_step5
import org.churchpresenter.strings.generated.resources.setup_step2_subtitle
import org.churchpresenter.strings.generated.resources.setup_step2_tip
import org.churchpresenter.strings.generated.resources.setup_step2_tip2
import org.churchpresenter.strings.generated.resources.setup_step2_title
import org.churchpresenter.strings.generated.resources.setup_step3_step3
import org.churchpresenter.strings.generated.resources.setup_step3_step4
import org.churchpresenter.strings.generated.resources.setup_step3_subtitle
import org.churchpresenter.strings.generated.resources.setup_step3_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BibleStep(onOpenSettings: () -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.Book,
        title = stringResource(Res.string.setup_step2_title),
        subtitle = stringResource(Res.string.setup_step2_subtitle),
        instructionCount = BIBLE_INSTRUCTIONS,
    )
    InstructionStep(number = 1, text = stringResource(Res.string.setup_step2_step1)) {
        OpenSettingsButton(onOpenSettings)
    }
    InstructionStep(number = 2, text = stringResource(Res.string.setup_step2_step2)) {
        SettingsTabHint(highlightedTab = stringResource(Res.string.appearance))
    }
    InstructionStep(number = 3, text = stringResource(Res.string.setup_bible_choose_folder))
    InstructionStep(number = 4, text = stringResource(Res.string.setup_step2_step5)) {
        TipBox(text = stringResource(Res.string.setup_step2_download_hint))
    }
    // Names translations in the plural: the app carries an ordered stack of them, and saying only
    // "primary" left multi-translation mode undiscoverable from the wizard.
    InstructionStep(number = 5, text = stringResource(Res.string.setup_bible_pick_translations)) {
        SettingsTabHint(highlightedTab = stringResource(Res.string.bible))
    }
    TipBox(text = stringResource(Res.string.setup_step2_tip))
    TipBox(text = stringResource(Res.string.setup_step2_tip2))
}

@Composable
internal fun SongsStep(onOpenSettings: () -> Unit, onOpenConverter: () -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.MusicNote,
        title = stringResource(Res.string.setup_step3_title),
        subtitle = stringResource(Res.string.setup_step3_subtitle),
        instructionCount = SONG_INSTRUCTIONS,
    )
    InstructionStep(number = 1, text = stringResource(Res.string.setup_step2_step1)) {
        OpenSettingsButton(onOpenSettings)
    }
    InstructionStep(number = 2, text = stringResource(Res.string.setup_step2_step2)) {
        SettingsTabHint(highlightedTab = stringResource(Res.string.appearance))
    }
    InstructionStep(number = 3, text = stringResource(Res.string.setup_step3_step3))
    InstructionStep(number = 4, text = stringResource(Res.string.setup_step3_step4)) {
        TipBox(text = stringResource(Res.string.setup_songs_format_note))
    }
    ConverterCallout(onOpenConverter)
    TipBox(text = stringResource(Res.string.setup_songs_samples_note))
}

/**
 * The way in for a user whose songs are in another app's format.
 *
 * The step above tells them the folder reads `.song` and nothing else, which is true and, on its
 * own, a dead end — so this names the formats the bundled Converter does read and opens it.
 */
@Composable
private fun ConverterCallout(onOpenConverter: () -> Unit) {
    val shape = AppShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.setup_songs_converter_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        RaisedButton(shape = AppShape(8.dp), onClick = onOpenConverter) {
            Text(stringResource(Res.string.shortcut_description_open_converter))
        }
    }
}

// ── Step 6: projection ───────────────────────────────────────────────────────────────────────

@Composable
internal fun ProjectionStep(onOpenSettings: () -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.Tv,
        title = stringResource(Res.string.setup_proj_title),
        subtitle = stringResource(Res.string.setup_proj_subtitle),
        instructionCount = PROJECTION_INSTRUCTIONS,
    )
    InstructionStep(number = 1, text = stringResource(Res.string.setup_proj_step1))
    InstructionStep(number = 2, text = stringResource(Res.string.setup_proj_step2)) {
        OpenSettingsButton(onOpenSettings)
        SettingsTabHint(highlightedTab = stringResource(Res.string.projection))
    }
    // Replaces "set the number of projection windows": there is no such control, and never was one
    // the user could reach — "Presenter windows" is a read-only line that follows the assignments.
    InstructionStep(number = 3, text = stringResource(Res.string.setup_proj_rows_note))
    InstructionStep(number = 4, text = stringResource(Res.string.setup_proj_assign_note)) {
        ScreenAssignmentHint()
    }
    InstructionStep(number = 5, text = stringResource(Res.string.setup_proj_step5))
    TipBox(text = stringResource(Res.string.setup_proj_tip))
    TipBox(text = stringResource(Res.string.setup_proj_tip2))
    TipBox(text = stringResource(Res.string.setup_proj_lang_note))
}

/**
 * A sketch of the Screen Assignment card, so instruction 4 points at something recognisable.
 *
 * Deliberately one row of a real-looking table rather than a screenshot: it is built from the same
 * strings the card itself uses, so a renamed column follows it here instead of going stale.
 */
@Composable
private fun ScreenAssignmentHint() {
    WizardMockPanel(
        title = stringResource(Res.string.screen_assignment),
        trailing = {
            Box(
                modifier = Modifier
                    .clip(AppShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = stringResource(Res.string.identify_screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.detected_screens, SAMPLE_SCREENS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.presenter_windows_count, SAMPLE_SCREENS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockColumn(
                    stringResource(Res.string.projection_target_display),
                    stringResource(Res.string.projection_auto_display),
                )
                MockColumn(stringResource(Res.string.key_output), stringResource(Res.string.key_output_none))
                MockColumn(stringResource(Res.string.display_mode), stringResource(Res.string.full_screen))
                MockColumn(
                    stringResource(Res.string.content_outputs),
                    stringResource(Res.string.content_outputs_enabled_short, SAMPLE_OUTPUTS_ON, SAMPLE_OUTPUTS_TOTAL),
                )
            }
        }
    }
}

/** One labelled cell of the sample row: the column's name over the value it would hold. */
@Composable
private fun MockColumn(label: String, value: String) {
    Column(modifier = Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShape(5.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Step 7: media ────────────────────────────────────────────────────────────────────────────

private const val BIBLE_INSTRUCTIONS = 5

// ── Step 5: song books ───────────────────────────────────────────────────────────────────────

private const val SONG_INSTRUCTIONS = 4

private const val PROJECTION_INSTRUCTIONS = 5

/** The sample row's figures — one screen, one window, and all but one content type switched on. */
private const val SAMPLE_SCREENS = 1

private const val SAMPLE_OUTPUTS_ON = 15

private const val SAMPLE_OUTPUTS_TOTAL = 16
