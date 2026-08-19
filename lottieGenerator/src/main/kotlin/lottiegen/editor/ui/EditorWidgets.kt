package lottiegen.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lottiegen.ui.components.LottieDropdown
import lottiegen.ui.components.LottieTextField

/** Small shared widgets for the Style Editor's inspector panels. */

/** Hover tooltip wrapper for editor controls. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        },
        content = content
    )
}

/**
 * Numeric field that commits only when the text parses — transient invalid input
 * (empty, lone minus sign) is tolerated without corrupting the spec.
 */
@Composable
fun NumberField(
    label: String,
    value: Double,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    LottieTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let { parsed ->
                if (parsed != value) onCommit(parsed)
            }
        },
        label = label,
        modifier = modifier
    )
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Generic dropdown over a fixed option list, built on the shared LottieDropdown row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionDropdown(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        LottieDropdown(
            label = label,
            value = display(selected),
            expanded = expanded,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Height-capped dialog list with a visible scrollbar — keeps long template/project/
 * style lists from stretching a dialog to full screen height.
 */
@Composable
fun ScrollableDialogColumn(
    maxHeight: Dp = 400.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.heightIn(max = maxHeight)) {
        val scrollState = rememberScrollState()
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(end = 14.dp)
        ) {
            content()
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
