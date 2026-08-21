package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.editor.EditorState
import org.churchpresenter.lottiegen.editor.NewElementKind
import org.churchpresenter.lottiegen.editor.addElement
import org.churchpresenter.lottiegen.editor.moveElement
import org.churchpresenter.lottiegen.editor.removeElement
import org.churchpresenter.lottiegen.spec.BackgroundElement
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.spec.EllipseElement
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.LogoElement
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.ui.Strings

/**
 * The layer stack: elements listed top → bottom in z-order (first = topmost layer),
 * with selection, reorder, delete, and an add-element menu.
 */
@Composable
fun ElementListPanel(state: EditorState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                Strings.editorElements,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AddElementButton(state)
        }

        for (element in state.spec.elements) {
            ElementRow(state, element)
        }
    }
}

@Composable
private fun AddElementButton(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        EditorTooltip(Strings.editorTipAddElement) {
            TextButton(onClick = { expanded = true }) {
                Text(Strings.editorAddElement)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (kind in NewElementKind.entries) {
                DropdownMenuItem(
                    text = { Text(newElementLabel(kind)) },
                    onClick = {
                        expanded = false
                        state.updateSpec { it.addElement(kind) }
                        state.selectElement(state.spec.elements.firstOrNull()?.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ElementRow(state: EditorState, element: ElementSpec) {
    val selected = element.id == state.selectedElementId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(4.dp)
            )
            .clickable { state.selectElement(if (selected) null else element.id) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = elementIcon(element),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            element.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        EditorTooltip(Strings.editorTipMoveUp) {
            IconButton(onClick = { state.updateSpec { it.moveElement(element.id, -1) } }) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = Strings.editorMoveUp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        EditorTooltip(Strings.editorTipMoveDown) {
            IconButton(onClick = { state.updateSpec { it.moveElement(element.id, +1) } }) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = Strings.editorMoveDown,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        EditorTooltip(Strings.editorTipDeleteElement) {
            IconButton(onClick = {
                if (selected) state.selectElement(null)
                state.updateSpec { it.removeElement(element.id) }
            }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = Strings.editorDelete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun elementIcon(element: ElementSpec): ImageVector = when (element) {
    is RectElement -> Icons.Default.CropSquare
    is EllipseElement -> Icons.Default.Circle
    is PolygonElement -> Icons.Outlined.ChangeHistory
    is PathElement -> Icons.Default.Gesture
    is TextElement -> Icons.Default.TextFields
    is LogoElement -> Icons.Default.Image
    is ImageElement -> Icons.Outlined.Photo
    is BackgroundElement -> Icons.Default.Rectangle
}

private fun newElementLabel(kind: NewElementKind): String = when (kind) {
    NewElementKind.RECT -> Strings.editorElementRect
    NewElementKind.ELLIPSE -> Strings.editorElementEllipse
    NewElementKind.POLYGON -> Strings.editorElementPolygon
    NewElementKind.PATH -> Strings.editorElementPath
    NewElementKind.NAME_TEXT -> Strings.editorElementNameText
    NewElementKind.INFO_TEXT -> Strings.editorElementInfoText
    NewElementKind.LOGO -> Strings.editorElementLogo
    NewElementKind.IMAGE -> Strings.editorElementImage
    NewElementKind.BACKGROUND -> Strings.editorElementBackground
}
