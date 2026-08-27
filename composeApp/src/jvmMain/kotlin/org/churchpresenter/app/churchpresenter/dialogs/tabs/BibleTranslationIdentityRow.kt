package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_custom_abbreviation
import churchpresenter.composeapp.generated.resources.bible_custom_name
import churchpresenter.composeapp.generated.resources.bible_reference_element
import churchpresenter.composeapp.generated.resources.bible_reset_element
import churchpresenter.composeapp.generated.resources.bible_verse_text
import churchpresenter.composeapp.generated.resources.show_abbreviation
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.settings.BibleTranslationSettings
import org.jetbrains.compose.resources.stringResource

private val ELEMENT_TAB_WIDTH = 106.dp

/** Below this the header wraps: the two element tabs, both boxes, the switch and Reset side by side. */
private val HEADER_ONE_ROW_WIDTH = 820.dp

/** Enough for the box's own "NAME" caption, which is what truncated first when it was weighted alone. */
private val NAME_FIELD_MIN_WIDTH = 150.dp
private val ABBREVIATION_FIELD_WIDTH = 104.dp

/**
 * The element tabs, what this church calls the translation, and the way back to the defaults.
 *
 * One row when the pane is wide enough for all of it, two when it is not: at the dialog's minimum
 * width the name and abbreviation boxes were squeezed until their own labels read "N..." and
 * "ABBREVIATI...", which is worse than a second row.
 */
@Composable
internal fun ElementHeaderRow(
    translation: BibleTranslationSettings,
    moduleTitle: String,
    element: BibleStyleElement,
    onElementChange: (BibleStyleElement) -> Unit,
    onTranslationChange: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
    onReset: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val oneRow = maxWidth >= HEADER_ONE_ROW_WIDTH
        val tabs: @Composable () -> Unit = {
            SegmentedButton(
                items = listOf(
                    SegmentedButtonItem(BibleStyleElement.TEXT, stringResource(Res.string.bible_verse_text)),
                    SegmentedButtonItem(
                        BibleStyleElement.REFERENCE,
                        stringResource(Res.string.bible_reference_element),
                    ),
                ),
                selectedValue = element,
                onValueChange = onElementChange,
                buttonWidth = ELEMENT_TAB_WIDTH,
                buttonHeight = 34.dp,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
        val reset: @Composable () -> Unit = {
            TextButton(
                onClick = onReset,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(stringResource(Res.string.bible_reset_element), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (oneRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs()
                TranslationIdentityFields(translation, moduleTitle, onTranslationChange, Modifier.weight(1f))
                reset()
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs()
                    Spacer(Modifier.weight(1f))
                    reset()
                }
                TranslationIdentityFields(translation, moduleTitle, onTranslationChange, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * What this church calls the translation, as against what its `.spb` header calls it.
 *
 * Both boxes stand empty until the operator types in one, and both show what the module gives
 * itself as their placeholder: blank means "keep using that", so the placeholder is the live value
 * rather than a hint about one. The abbreviation is the string that labels every verse on screen,
 * which is why it is editable separately -- a module titled "King James Version" abbreviates itself
 * to "KJV" whatever the church actually puts under its scripture.
 */
@Composable
private fun TranslationIdentityFields(
    translation: BibleTranslationSettings,
    moduleTitle: String,
    onTranslationChange: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackName = moduleTitle.ifBlank { translation.fileName.substringBeforeLast('.') }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextField(
            value = translation.customName,
            onValueChange = { typed -> onTranslationChange { it.copy(customName = typed) } },
            label = stringResource(Res.string.bible_custom_name),
            placeholder = { PanelPlaceholder(fallbackName) },
            modifier = Modifier.weight(1f).widthIn(min = NAME_FIELD_MIN_WIDTH),
            fillWidth = true,
        )
        SettingsTextField(
            value = translation.customAbbreviation,
            onValueChange = { typed -> onTranslationChange { it.copy(customAbbreviation = typed) } },
            label = stringResource(Res.string.bible_custom_abbreviation),
            placeholder = { PanelPlaceholder(defaultTranslationAbbreviation(moduleTitle, translation.fileName)) },
            modifier = Modifier.width(ABBREVIATION_FIELD_WIDTH),
            fillWidth = true,
        )
        LabeledCheckbox(
            checked = translation.showAbbreviation,
            onCheckedChange = { on -> onTranslationChange { it.copy(showAbbreviation = on) } },
            label = stringResource(Res.string.show_abbreviation),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
