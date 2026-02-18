package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import java.awt.Window
import javax.swing.JColorChooser
import javax.swing.SwingUtilities

@Composable
fun ColorPickerField(
    color: String, // Hex color string like "#FFFFFF"
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentColor = remember(color) { parseHexColor(color) }

    Row(
        modifier = modifier
            .height(32.dp)
            .clickable {
                SwingUtilities.invokeLater {
                    // Get the parent window to ensure dialog appears on top
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    val initialColor = java.awt.Color(
                        currentColor.red.toInt(),
                        currentColor.green.toInt(),
                        currentColor.blue.toInt()
                    )
                    val selectedColor = JColorChooser.showDialog(
                        parentWindow,
                        "Choose Color",
                        initialColor
                    )
                    if (selectedColor != null) {
                        val hexColor = String.format(
                            "#%02X%02X%02X",
                            selectedColor.red,
                            selectedColor.green,
                            selectedColor.blue
                        )
                        onColorChange(hexColor)
                    }
                }
            }
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color preview box
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(currentColor, RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        )

        // Hex color text
        Text(
            text = color,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
