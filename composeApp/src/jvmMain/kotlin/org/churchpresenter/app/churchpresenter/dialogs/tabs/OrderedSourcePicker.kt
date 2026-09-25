package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.output_profile_move_down
import churchpresenter.composeapp.generated.resources.output_profile_move_up
import churchpresenter.composeapp.generated.resources.output_profile_remove_translation
import org.churchpresenter.theme.components.KeyIconButton
import org.churchpresenter.theme.elevationPalette
import org.jetbrains.compose.resources.stringResource

private const val DISABLED_KEY_ALPHA = 0.3f
private val MENU_WIDTH = 380.dp
private val ROW_KEY = 26.dp
private val ROW_ICON = 16.dp

/**
 * The words one ordered source needs, so the widget below can serve any of them.
 *
 * [offLabel] is the closed field's value when nothing is drawn, [countFormat] its sub-line,
 * [noneLoaded] the note when there is nothing to pick from at all, and [orderHeader]/[addHeader]
 * the two captions inside the open menu.
 */
internal data class OrderedSourceStrings(
    val label: String,
    val offLabel: String,
    val countFormat: String,
    val noneLoaded: String,
    val orderHeader: String,
    val addHeader: String,
)

/** The test handles one ordered source hangs off, so each keeps the tags its own tests use. */
internal data class OrderedSourceTags(
    val trigger: String,
    val orderRow: (Int) -> String,
    val addRow: (Int) -> String,
)

/**
 * A profile's ordered source: which of [items] it draws, and in what order.
 *
 * The list of things to pick from is one per install -- the Bible stack, a song's language slots --
 * and a profile picks from it and orders what it picked. [shown] holds stack positions in drawing
 * order; [onWrite] is handed the new order.
 *
 * Shared by the Bible and the song languages rather than written twice: the two differ only in
 * their words and their test tags, and the songs went without a reorder for exactly as long as
 * this was Bible-only code.
 */
/**
 * One source as a picker lists it: its code, its name, and how much of the Bible it holds.
 *
 * Moved here from `ProfileTranslationPicker.kt` when that file was deleted. It was the only thing
 * in it anything still used — the picker it was written for was superseded by this one and had sat
 * unreferenced since, 619 lines of it.
 */
internal data class TranslationChoiceDisplay(
    val code: String,
    val title: String,
    val portion: String,
)

@Composable
internal fun OrderedSourcePicker(
    items: List<TranslationChoiceDisplay>,
    shown: List<Int>,
    strings: OrderedSourceStrings,
    tags: OrderedSourceTags,
    onWrite: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    /** False where an item's code is its slot number, which the row already prints. */
    showRowCode: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val value = when {
        items.isEmpty() || shown.isEmpty() -> strings.offLabel
        else -> shown.mapNotNull { items.getOrNull(it)?.code }.joinToString(" · ")
    }
    Box(modifier = modifier) {
        SourceField(
            label = strings.label,
            value = value,
            sub = strings.countFormat,
            open = open,
            dimmed = shown.isEmpty(),
            onClick = { open = true },
            modifier = Modifier.testTag(tags.trigger),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(MENU_WIDTH),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            OrderedSourceMenu(items, shown, strings, tags, onWrite, showRowCode)
        }
    }
}

@Composable
private fun OrderedSourceMenu(
    items: List<TranslationChoiceDisplay>,
    shown: List<Int>,
    strings: OrderedSourceStrings,
    tags: OrderedSourceTags,
    onWrite: (List<Int>) -> Unit,
    showRowCode: Boolean,
) {
    if (items.isEmpty()) {
        MenuNote(strings.noneLoaded)
        return
    }
    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        CustomizeCaption(strings.orderHeader)
    }
    shown.forEachIndexed { slot, position ->
        val info = items.getOrNull(position) ?: return@forEachIndexed
        OrderRow(
            slot = slot,
            info = info,
            canMoveUp = slot > 0,
            canMoveDown = slot < shown.lastIndex,
            onMoveUp = { onWrite(swapped(shown, slot, slot - 1)) },
            onMoveDown = { onWrite(swapped(shown, slot, slot + 1)) },
            onRemove = { onWrite(shown - position) },
            tag = tags.orderRow(slot),
            showCode = showRowCode,
        )
    }
    val addable = items.indices.filter { it !in shown }
    if (addable.isNotEmpty()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            CustomizeCaption(strings.addHeader)
        }
        addable.forEach { position ->
            AddTranslationRow(
                info = items[position],
                onAdd = { onWrite(shown + position) },
                tag = tags.addRow(position),
                showCode = showRowCode,
            )
        }
    }
}

@Composable
private fun OrderRow(
    slot: Int,
    info: TranslationChoiceDisplay,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    tag: String,
    showCode: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${slot + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 14.dp),
        )
        TranslationName(info, showCode = showCode, modifier = Modifier.weight(1f))
        RowKey(Icons.Filled.KeyboardArrowUp, stringResource(Res.string.output_profile_move_up), canMoveUp, onMoveUp)
        RowKey(
            Icons.Filled.KeyboardArrowDown,
            stringResource(Res.string.output_profile_move_down),
            canMoveDown,
            onMoveDown,
        )
        RowKey(
            Icons.Filled.Delete,
            stringResource(Res.string.output_profile_remove_translation),
            enabled = true,
            onClick = onRemove,
            tint = elevationPalette().danger.ink,
        )
    }
}

@Composable
private fun AddTranslationRow(
    info: TranslationChoiceDisplay,
    onAdd: () -> Unit,
    tag: String,
    showCode: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ROW_ICON),
        )
        TranslationName(info, showCode = showCode, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TranslationName(
    info: TranslationChoiceDisplay,
    showCode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A song language's code is its slot number, which the row already prints in its own
        // gutter -- drawing both read "1  1  Language 1". A Bible's is its abbreviation, which is
        // the most useful thing on the row, so the caller decides.
        if (showCode && info.code.isNotBlank()) {
            Text(
                text = info.code,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = info.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowKey(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    KeyIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(ROW_KEY)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = DISABLED_KEY_ALPHA),
            modifier = Modifier.size(ROW_ICON),
        )
    }
}

@Composable
private fun MenuNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Test handle for the Bible source's closed field. */
internal const val BIBLE_SOURCE_TRIGGER_TAG = "profile_bible_source"

/** Test handle for the [slot]th translation the profile draws, in its drawing order. */
internal fun bibleOrderRowTag(slot: Int): String = "profile_bible_order_$slot"

/** Test handle for the "add" row of the translation at stack position [index]. */
internal fun bibleAddRowTag(index: Int): String = "profile_bible_add_$index"
