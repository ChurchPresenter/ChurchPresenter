package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_box_item
import churchpresenter.composeapp.generated.resources.profile_group_boxes
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.withBox
import org.jetbrains.compose.resources.stringResource

/**
 * The TEXT BOXES group of a page whose editor is one form -- captions, subtitles, Q&A, dictionary,
 * stage: an Item row picking one of [items], and the box rows for the item picked. [boxes] and
 * [options] are the page's own; each change is handed back whole.
 */
@Composable
internal fun ItemBoxGroup(
    items: List<BoxItem>,
    boxes: Map<String, TextBox>,
    options: TextBoxOptions,
    onBoxes: (Map<String, TextBox>) -> Unit,
    onOptions: (TextBoxOptions) -> Unit,
    paths: List<String> = emptyList(),
) {
    if (items.isEmpty()) return
    var picked by remember { mutableStateOf(items.first().key) }
    val item = items.firstOrNull { it.key == picked } ?: items.first()
    val key = textBoxKey(item.key, lowerThird = false)
    SettingsGroup(stringResource(Res.string.profile_group_boxes), paths = paths) {
        if (items.size > 1) {
            SettingsRow(stringResource(Res.string.profile_box_item)) {
                RowSegmented(
                    options = items.map { RowOption(it.key, it.label) },
                    selected = item.key,
                    onSelect = { picked = it },
                )
            }
        }
        TextBoxRows(
            box = boxes.boxAt(key),
            onBox = { box -> onBoxes(boxes.withBox(key, box)) },
            startBox = item.start,
            options = options,
            onOptions = onOptions,
            perLanguage = false,
            lowerThird = false,
            boxPaths = paths,
            optionPaths = paths,
            offerArea = false,
        )
    }
}
