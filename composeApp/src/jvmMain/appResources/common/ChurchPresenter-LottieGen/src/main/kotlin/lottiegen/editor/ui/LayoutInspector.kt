package lottiegen.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lottiegen.editor.EditorState
import lottiegen.spec.LayoutSpec
import lottiegen.spec.SlotKind
import lottiegen.spec.SlotSpec
import lottiegen.spec.VisibilityRule
import lottiegen.ui.Strings
import lottiegen.ui.components.CollapsibleSection
import lottiegen.ui.components.LottieTextField

/** Editor for the spec's content-block layout: slot list + block-level options. */
@Composable
fun LayoutInspector(state: EditorState) {
    val layout = state.spec.layout

    fun update(transform: (LayoutSpec) -> LayoutSpec) {
        state.updateSpec { it.copy(layout = transform(it.layout)) }
    }

    CollapsibleSection(Strings.editorSectionLayout, initiallyExpanded = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                Strings.editorBlockHeight, layout.blockHeightEm,
                onCommit = { new -> update { it.copy(blockHeightEm = new) } },
                modifier = Modifier.fillMaxWidth()
            )
            CheckboxRow(
                label = Strings.editorCenterSingleLine,
                checked = layout.centerSingleLine,
                onCheckedChange = { new -> update { it.copy(centerSingleLine = new) } }
            )

            Text(
                Strings.editorSlots,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            layout.slots.forEachIndexed { index, slot ->
                SlotEditor(
                    slot = slot,
                    onChange = { new -> update { it.copy(slots = it.slots.replaceAt(index, new)) } },
                    onMove = { delta ->
                        update {
                            val target = index + delta
                            if (target < 0 || target >= it.slots.size) it
                            else {
                                val slots = it.slots.toMutableList()
                                val moved = slots.removeAt(index)
                                slots.add(target, moved)
                                it.copy(slots = slots)
                            }
                        }
                    },
                    onRemove = { update { it.copy(slots = it.slots.filterIndexed { i, _ -> i != index }) } }
                )
            }
            TextButton(onClick = {
                update {
                    val ids = it.slots.map { slot -> slot.id }.toSet()
                    var i = 1
                    while ("slot$i" in ids) i++
                    it.copy(slots = it.slots + SlotSpec("slot$i", SlotKind.FIXED, widthEm = 1.0))
                }
            }) {
                Text(Strings.editorAddSlot)
            }
        }
    }
}

@Composable
private fun SlotEditor(
    slot: SlotSpec,
    onChange: (SlotSpec) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LottieTextField(
                value = slot.id,
                onValueChange = { onChange(slot.copy(id = it)) },
                label = Strings.editorSlotId,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onMove(-1) }) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = Strings.editorMoveUp,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = { onMove(+1) }) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = Strings.editorMoveDown,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = Strings.editorDelete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        OptionDropdown(
            label = Strings.editorSlotKind,
            options = SlotKind.entries,
            selected = slot.kind,
            display = { EditorLabels.slotKind(it) },
            onSelect = { onChange(slot.copy(kind = it)) }
        )
        if (slot.kind == SlotKind.FIXED) {
            NumberField(
                Strings.editorWidthEm, slot.widthEm,
                onCommit = { onChange(slot.copy(widthEm = it)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                Strings.editorGapBefore, slot.gapBeforeEm,
                onCommit = { onChange(slot.copy(gapBeforeEm = it)) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                Strings.editorGapAfter, slot.gapAfterEm,
                onCommit = { onChange(slot.copy(gapAfterEm = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        for (rule in VisibilityRule.entries.filter { it != VisibilityRule.ALWAYS }) {
            CheckboxRow(
                label = EditorLabels.rule(rule),
                checked = rule in slot.visibleWhen,
                onCheckedChange = { checked ->
                    onChange(
                        slot.copy(
                            visibleWhen = if (checked) slot.visibleWhen + rule else slot.visibleWhen - rule
                        )
                    )
                }
            )
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    mapIndexed { i, current -> if (i == index) item else current }
