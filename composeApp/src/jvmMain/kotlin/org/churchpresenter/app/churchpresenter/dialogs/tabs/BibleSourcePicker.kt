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
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.output_profile_add_translation
import churchpresenter.composeapp.generated.resources.output_profile_bible_count
import churchpresenter.composeapp.generated.resources.output_profile_bible_none_loaded
import churchpresenter.composeapp.generated.resources.output_profile_bible_off
import churchpresenter.composeapp.generated.resources.output_profile_bible_order_header
import churchpresenter.composeapp.generated.resources.output_profile_move_down
import churchpresenter.composeapp.generated.resources.output_profile_move_up
import churchpresenter.composeapp.generated.resources.output_profile_remove_translation
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.components.KeyIconButton
import org.churchpresenter.theme.elevationPalette
import org.jetbrains.compose.resources.stringResource

private const val DISABLED_KEY_ALPHA = 0.3f
private val MENU_WIDTH = 380.dp
private val ROW_KEY = 26.dp
private val ROW_ICON = 16.dp

/**
 * The profile's Bible source: which translations of the stack it draws, and in what order.
 *
 * The stack itself -- which Bibles are loaded, what each is called on screen -- is the Bible tab's,
 * and is one list for every output; a profile picks from it and orders what it picked.
 */
@Composable
internal fun BibleSourcePicker(
    profile: OutputProfile,
    stack: List<TranslationChoiceDisplay>,
    onProfileChange: (OutputProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shown = shownBiblePositions(profile, stack.size)
    val value = when {
        stack.isEmpty() || shown.isEmpty() -> stringResource(Res.string.output_profile_bible_off)
        else -> shown.mapNotNull { stack.getOrNull(it)?.code }.joinToString(" · ")
    }
    Box(modifier = modifier) {
        SourceField(
            label = stringResource(Res.string.customize_bible),
            value = value,
            sub = stringResource(Res.string.output_profile_bible_count, shown.size, stack.size),
            open = open,
            dimmed = shown.isEmpty(),
            onClick = { open = true },
            modifier = Modifier.testTag(BIBLE_SOURCE_TRIGGER_TAG),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(MENU_WIDTH),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            BibleSourceMenu(profile, stack, shown, onProfileChange)
        }
    }
}

@Composable
private fun BibleSourceMenu(
    profile: OutputProfile,
    stack: List<TranslationChoiceDisplay>,
    shown: List<Int>,
    onProfileChange: (OutputProfile) -> Unit,
) {
    if (stack.isEmpty()) {
        MenuNote(stringResource(Res.string.output_profile_bible_none_loaded))
        return
    }
    fun write(next: List<Int>) = onProfileChange(withBiblePositions(profile, next, stack.size))
    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        CustomizeCaption(stringResource(Res.string.output_profile_bible_order_header))
    }
    shown.forEachIndexed { slot, position ->
        val info = stack.getOrNull(position) ?: return@forEachIndexed
        BibleOrderRow(
            slot = slot,
            info = info,
            canMoveUp = slot > 0,
            canMoveDown = slot < shown.lastIndex,
            onMoveUp = { write(swapped(shown, slot, slot - 1)) },
            onMoveDown = { write(swapped(shown, slot, slot + 1)) },
            onRemove = { write(shown - position) },
        )
    }
    val addable = stack.indices.filter { it !in shown }
    if (addable.isNotEmpty()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            CustomizeCaption(stringResource(Res.string.output_profile_add_translation))
        }
        addable.forEach { position ->
            AddTranslationRow(info = stack[position], index = position, onAdd = { write(shown + position) })
        }
    }
}

@Composable
private fun BibleOrderRow(
    slot: Int,
    info: TranslationChoiceDisplay,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .testTag(bibleOrderRowTag(slot)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${slot + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 14.dp),
        )
        TranslationName(info, modifier = Modifier.weight(1f))
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
private fun AddTranslationRow(info: TranslationChoiceDisplay, index: Int, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag(bibleAddRowTag(index)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ROW_ICON),
        )
        TranslationName(info, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TranslationName(info: TranslationChoiceDisplay, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = info.code,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
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
