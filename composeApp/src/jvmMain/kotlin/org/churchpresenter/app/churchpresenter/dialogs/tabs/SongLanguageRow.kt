package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import churchpresenter.composeapp.generated.resources.song_translation_label
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
private val NAME_FIELD_WIDTH = 190.dp

/** What language [position] is called on this tab when nobody has named it. */
@Composable
internal fun songLanguageName(position: Int, label: String): String = when {
    label.isNotBlank() -> label
    position == 0 -> stringResource(Res.string.screen_lang_language_1)
    position == 1 -> stringResource(Res.string.screen_lang_language_2)
    else -> stringResource(Res.string.screen_lang_language_n, position + 1)
}

/**
 * Which language the styling controls below are pointed at, what it is called, and whether it has a
 * look of its own.
 *
 * The third axis of this tab, beside the output and the element. It only exists because a song may
 * now be sung in up to [MAX_SONG_TRANSLATIONS] languages at once, and a church that puts Ukrainian
 * beside English may well want the Cyrillic a size smaller.
 *
 * Language 1 has no switch: it *is* the look the others inherit, so there is nothing for it to
 * override.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongLanguageRow(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    translation: Int,
    onTranslationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songSettings = settings.songSettings
    val stored = if (translation > 0) songSettings.translationSettings(translation - 1) else null
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Flowing rather than a hard row, for the same reason the chunk/language row above it
        // flows: four language buttons plus a name field is wider than a narrow pane, and a `Row`
        // clips rather than wraps.
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
            if (stored != null) {
                OutlinedTextField(
                    value = stored.label,
                    onValueChange = { value ->
                        onSettingsChange { s ->
                            s.copy(
                                songSettings = s.songSettings.withTranslationSettings(translation - 1) {
                                    it.copy(label = value)
                                },
                            )
                        }
                    },
                    label = { Text(stringResource(Res.string.song_translation_label)) },
                    singleLine = true,
                    modifier = Modifier.width(NAME_FIELD_WIDTH),
                )
            }
        }
        if (stored != null) {
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
}

/** The stored name of language [position], or blank for the primary and for an unnamed one. */
private fun languageLabelAt(settings: AppSettings, position: Int): String =
    if (position == 0) "" else settings.songSettings.translationSettings(position - 1).label
