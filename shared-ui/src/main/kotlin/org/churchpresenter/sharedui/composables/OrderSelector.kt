package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_translation_order_hint
import org.churchpresenter.strings.generated.resources.bible_translation_order_more
import org.churchpresenter.strings.generated.resources.bible_translation_order_panel_title
import org.churchpresenter.strings.generated.resources.drag_to_reorder_translation
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_drag_dots
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.dropdownField
import org.churchpresenter.theme.keyboardFocusRing

private const val EXPANDED_ROTATION = 180f

private val TRANSLATION_ORDER_ROW_HEIGHT = 46.dp

/** One row of a Display order panel: [key] follows it through a drag, [detail] is its second line. */
data class OrderEntry(
    val key: String,
    val name: String,
    val detail: String = "",
    /** Shown in place of a blank [name] -- what an entry is called until it is named. */
    val placeholder: String = "",
    /** Whether the entry is shown where [OrderShown] says; read only when the selector has one. */
    val shown: Boolean = true,
) {
    /** What the entry reads as: its [name], or the [placeholder] while it has none. */
    val shownName: String get() = name.ifBlank { placeholder }
}

/**
 * Renaming the entries of an [OrderSelector] in place: each row's name becomes a field, written
 * back through [onRename] when it loses focus or the panel closes. [label] names the field for a
 * screen reader; [hint] says, under the rows, what renaming one changes. With [onAdd], a button
 * labelled [addLabel] under the rows adds one more entry to name.
 */
data class OrderRename(
    val label: String,
    val hint: String,
    val onRename: (key: String, name: String) -> Unit,
    val addLabel: String = "",
    val onAdd: (() -> Unit)? = null,
)

/**
 * An eye on each row of an [OrderSelector] that shows or hides the entry somewhere apart from the
 * order itself -- the Songs tab's lyrics panel. [label] names the eye for a screen reader; [hint]
 * says, under the rows, what hiding one changes. The last entry shown cannot be hidden.
 */
data class OrderShown(
    val label: String,
    val hint: String,
    val onToggle: (key: String, shown: Boolean) -> Unit,
)

/**
 * A header button naming the first of [entries], opening a Display order panel that reorders them
 * by drag or by arrow -- the Bible tab's translations, and the Songs tab's languages.
 */
@Composable
fun OrderSelector(
    label: String,
    entries: List<OrderEntry>,
    subtitle: String,
    moveUpLabel: String,
    moveDownLabel: String,
    onMove: (index: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Null keeps the names fixed, which is every caller but the Songs tab's languages. */
    rename: OrderRename? = null,
    /** Null draws no eye on the rows, which is every caller but the Songs tab's languages. */
    shown: OrderShown? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val primaryName = entries.first().shownName
    val extraCount = entries.size - 1

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                // First, ahead of the field: dropdownField clips, which would cut the ring off.
                .keyboardFocusRing(AppShape(10.dp))
                .fillMaxWidth()
                .height(34.dp)
                .dropdownField(AppShape(10.dp), open = expanded)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = label.uppercase(),
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = primaryName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extraCount > 0) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, AppShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.bible_translation_order_more, extraCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Icon(
                painter = painterResource(IconRes.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(12.dp).rotate(if (expanded) EXPANDED_ROTATION else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = AppShape(13.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            offset = DpOffset(0.dp, 8.dp),
        ) {
            OrderPanel(
                entries = entries,
                subtitle = subtitle,
                moveUpLabel = moveUpLabel,
                moveDownLabel = moveDownLabel,
                onMove = onMove,
                rename = rename,
                shown = shown,
            )
        }
    }
}

@Composable
private fun OrderPanel(
    entries: List<OrderEntry>,
    subtitle: String,
    moveUpLabel: String,
    moveDownLabel: String,
    onMove: (index: Int, offset: Int) -> Unit,
    rename: OrderRename?,
    shown: OrderShown?,
) {
    Column(modifier = Modifier.width(320.dp)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(Res.string.bible_translation_order_panel_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val rowHeightPx = with(LocalDensity.current) { TRANSLATION_ORDER_ROW_HEIGHT.toPx() }
        val entriesState = rememberUpdatedState(entries)
        val drag = remember { OrderDrag() }
        val onDragEnd = {
            val current = entriesState.value
            val from = current.indexOfFirst { it.key == drag.key }
            if (from >= 0) {
                val steps = (drag.offsetY / rowHeightPx).roundToInt()
                val to = (from + steps).coerceIn(0, current.lastIndex)
                if (to != from) onMove(from, to - from)
            }
            drag.reset()
        }

        Column(modifier = Modifier.padding(6.dp)) {
            val onlyShown = entries.singleOrNull { it.shown }?.key
            entries.forEachIndexed { index, entry ->
                val eye = shown?.let { OrderRowEye(it, canHide = entry.key != onlyShown) }
                OrderRow(
                    entry, index, entries.lastIndex, drag, moveUpLabel, moveDownLabel, onMove, onDragEnd, rename, eye,
                )
                if (index != entries.lastIndex) Spacer(Modifier.height(4.dp))
            }
            rename?.onAdd?.let { onAdd ->
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painterResource(IconRes.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(rename.addLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Column {
                Text(
                    text = stringResource(Res.string.bible_translation_order_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                listOfNotNull(rename?.hint, shown?.hint).forEach { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** The row being dragged in a Display order panel, and how far it has travelled. */
private class OrderDrag {
    var key by mutableStateOf<String?>(null)
    var offsetY by mutableStateOf(0f)

    fun reset() {
        key = null
        offsetY = 0f
    }
}

private fun Modifier.orderDragHandle(key: String, drag: OrderDrag, onDragEnd: () -> Unit) = pointerInput(key) {
    detectDragGestures(
        onDragStart = {
            drag.key = key
            drag.offsetY = 0f
        },
        onDragEnd = onDragEnd,
        onDragCancel = { drag.reset() },
    ) { change, dragAmount ->
        change.consume()
        drag.offsetY += dragAmount.y
    }
}

@Composable
private fun OrderRow(
    entry: OrderEntry,
    index: Int,
    lastIndex: Int,
    drag: OrderDrag,
    moveUpLabel: String,
    moveDownLabel: String,
    onMove: (index: Int, offset: Int) -> Unit,
    onDragEnd: () -> Unit,
    rename: OrderRename?,
    eye: OrderRowEye?,
) {
    val isPrimary = index == 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRANSLATION_ORDER_ROW_HEIGHT)
            .zIndex(if (drag.key == entry.key) 1f else 0f)
            .graphicsLayer { translationY = if (drag.key == entry.key) drag.offsetY else 0f }
            .background(
                if (isPrimary) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                } else {
                    Color.Transparent
                },
                AppShape(9.dp),
            )
            .border(
                1.dp,
                if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                AppShape(9.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_drag_dots),
            contentDescription = stringResource(Res.string.drag_to_reorder_translation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .orderDragHandle(entry.key, drag, onDragEnd),
        )

        OrderIndexBadge(index, isPrimary)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val nameStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (rename != null) {
                    OrderNameField(entry, nameStyle, rename, Modifier.weight(1f))
                } else {
                    Text(
                        text = entry.shownName,
                        style = nameStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            if (entry.detail.isNotEmpty()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }

        eye?.let { OrderShownToggle(entry, it) }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ReorderArrowButton(
                icon = painterResource(IconRes.drawable.ic_arrow_up),
                contentDescription = moveUpLabel,
                enabled = index > 0,
                onClick = { onMove(index, -1) },
            )
            ReorderArrowButton(
                icon = painterResource(IconRes.drawable.ic_arrow_down),
                contentDescription = moveDownLabel,
                enabled = index < lastIndex,
                onClick = { onMove(index, 1) },
            )
        }
    }
}

@Composable
private fun OrderIndexBadge(index: Int, isPrimary: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                if (isPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                AppShape(7.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * An entry's name, typed over in place. Held here while it is typed and handed to
 * [OrderRename.onRename] only when the field loses focus or the panel closes, so a stored setting
 * is written once per rename rather than once per keystroke.
 */
@Composable
private fun OrderNameField(entry: OrderEntry, style: TextStyle, rename: OrderRename, modifier: Modifier) {
    var text by remember(entry.key, entry.name) { mutableStateOf(entry.name) }
    // Read at commit time, not captured: the panel closing disposes the field long after it was
    // first composed, and it must write what was typed against the entry as it is now.
    val typed = rememberUpdatedState(text)
    val current = rememberUpdatedState(entry)
    val onRename = rememberUpdatedState(rename.onRename)
    // Closing the panel mid-edit both blurs and disposes the field; the name is written once.
    val written = remember(entry.key, entry.name) { arrayOf(entry.name.trim()) }
    val commit = remember(written) {
        {
            val name = typed.value.trim()
            if (name != written[0]) {
                written[0] = name
                onRename.value(current.value.key, name)
            }
        }
    }
    val latestCommit = rememberUpdatedState(commit)
    DisposableEffect(entry.key) { onDispose { latestCommit.value() } }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier
            .semantics { contentDescription = rename.label }
            .onFocusChanged { if (!it.isFocused) commit() },
        decorationBox = { field ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        text = entry.placeholder,
                        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                        maxLines = 1,
                    )
                }
                field()
            }
        },
    )
}
