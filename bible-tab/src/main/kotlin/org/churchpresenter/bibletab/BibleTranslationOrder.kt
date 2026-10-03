package org.churchpresenter.bibletab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_translation_order_panel_subtitle
import org.churchpresenter.strings.generated.resources.move_translation_down
import org.churchpresenter.strings.generated.resources.move_translation_up
import org.churchpresenter.settings.BibleTranslationSettings
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.OrderEntry
import org.churchpresenter.sharedui.composables.OrderSelector

private fun translationTitle(displayNames: Map<String, String>, translation: BibleTranslationSettings): String =
    (displayNames[translation.fileName] ?: translation.fileName.substringBeforeLast('.'))
        .substringBefore("  (")

@Composable
internal fun TranslationOrderSelector(
    label: String,
    translations: List<BibleTranslationSettings>,
    displayNames: Map<String, String>,
    onMove: (index: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) = OrderSelector(
    label = label,
    entries = translations.map { OrderEntry(it.fileName, translationTitle(displayNames, it), detail = it.fileName) },
    subtitle = stringResource(Res.string.bible_translation_order_panel_subtitle),
    moveUpLabel = stringResource(Res.string.move_translation_up),
    moveDownLabel = stringResource(Res.string.move_translation_down),
    onMove = onMove,
    modifier = modifier,
)
