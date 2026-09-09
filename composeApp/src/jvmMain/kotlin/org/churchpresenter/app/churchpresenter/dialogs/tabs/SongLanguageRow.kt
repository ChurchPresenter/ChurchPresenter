package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.screen_lang_language_1
import churchpresenter.composeapp.generated.resources.screen_lang_language_2
import churchpresenter.composeapp.generated.resources.screen_lang_language_n
import churchpresenter.composeapp.generated.resources.song_language_scope_label
import churchpresenter.composeapp.generated.resources.song_translation_follows_primary
import churchpresenter.composeapp.generated.resources.song_translation_own_style
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.LabeledControl
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.LabeledControl
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.withTranslationSettings
import org.jetbrains.compose.resources.stringResource

private val LANGUAGE_BUTTON_WIDTH = 74.dp

/** What language [position] is called on this tab when nobody has named it. */
@Composable
internal fun songLanguageName(position: Int, label: String): String = when {
    label.isNotBlank() -> label
    position == 0 -> stringResource(Res.string.screen_lang_language_1)
    position == 1 -> stringResource(Res.string.screen_lang_language_2)
    else -> stringResource(Res.string.screen_lang_language_n, position + 1)
}

/**
 * Which language the styling controls below are pointed at, and what it is called.
 *
 * The third axis of this tab, beside the output and the element. It only exists because a song may
 * now be sung in up to [MAX_SONG_TRANSLATIONS] languages at once, and a church that puts Ukrainian
 * beside English may well want the Cyrillic a size smaller.
 *
 * The switch that gives a language a look of its own is [SongLanguageStyleSwitch], and it is drawn
 * at the far end of the card rather than here -- see its own note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongLanguageRow(
    settings: AppSettings,
    translation: Int,
    onTranslationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The bottom padding is the gap to the element tabs underneath. `SettingsSection` stacks its
    // children flush, so without it this row's buttons and that row's buttons share an edge and
    // read as one two-line control rather than two separate ones.
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Flowing rather than a hard row, for the same reason the chunk/language row above it
        // flows: four language buttons are wider than a narrow pane, and a `Row` clips rather than
        // wraps.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LabeledControl(stringResource(Res.string.song_language_scope_label)) {
                SegmentedButton(
                    items = List(MAX_SONG_TRANSLATIONS) { position ->
                        SegmentedButtonItem(position, songLanguageName(position, languageLabelAt(settings, position)))
                    },
                    selectedValue = translation,
                    onValueChange = onTranslationChange,
                    buttonWidth = LANGUAGE_BUTTON_WIDTH,
                    buttonHeight = 30.dp,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                )
            }
        }
    }
}

/**
 * The switch that gives the selected language a look of its own, and the typography panel it opens.
 *
 * Drawn at the far end of the card, immediately above that panel, rather than up beside the language
 * buttons where it started. Between the two sit the element tabs, the chunk control and the Show
 * row, and every one of those writes the output's own settings whichever language is selected -- so
 * a switch above them read as governing them, and ticking it appeared to do nothing. It governs
 * exactly one thing, and now it sits on top of it.
 *
 * Nothing at all for language 1: it *is* the look the others inherit, so there is nothing for it to
 * override.
 */
@Composable
internal fun SongLanguageStyleSwitch(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    translation: Int,
    modifier: Modifier = Modifier,
) {
    if (translation <= 0) return
    val stored = settings.songSettings.translationSettings(translation - 1)
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        LabeledCheckbox(
            checked = stored.overrideStyle,
            label = stringResource(Res.string.song_translation_own_style),
            controlModifier = Modifier.size(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            onCheckedChange = { on ->
                onSettingsChange { s ->
                    s.copy(
                        songSettings = s.songSettings.withTranslationSettings(translation - 1) { current ->
                            if (!on) current.copy(overrideStyle = false)
                            // Seeded from what is already on screen, so switching this on is not
                            // a jump to the defaults -- the operator starts from the look they
                            // have, and changes the one thing they came here to change.
                            else current.seededFrom { perElement, lowerThird ->
                                s.songSettings.elementStyle(
                                    perElement.styleElement,
                                    if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN,
                                )
                            }
                        },
                    )
                }
            },
        )
        if (!stored.overrideStyle) {
            Text(
                text = stringResource(Res.string.song_translation_follows_primary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** The stored name of language [position], or blank for the primary and for an unnamed one. */
private fun languageLabelAt(settings: AppSettings, position: Int): String =
    if (position == 0) "" else settings.songSettings.translationSettings(position - 1).label
