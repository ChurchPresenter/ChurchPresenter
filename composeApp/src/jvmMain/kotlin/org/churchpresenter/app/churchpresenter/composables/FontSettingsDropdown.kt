package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FontSettingsDropdown(
    modifier: Modifier = Modifier,
    value: String,
    fonts: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (isFocused) {
        2.dp
    } else {
        1.dp
    }

    Box {
        OutlinedButton(
            interactionSource = interactionSource,
            onClick = { expanded = true },
            modifier = modifier
                .fillMaxWidth()
                .height(32.dp),
            shape = RoundedCornerShape(2.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            border = BorderStroke(borderWidth, borderColor),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(200.dp)
        ) {
            val scrollState = rememberScrollState()
            // Fixed height Box — avoids Int.MAX_VALUE height crash from fillMaxHeight
            // inside SubcomposeLayout (DropdownMenu)
            Box(modifier = Modifier.height(300.dp).width(200.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(scrollState)
                        .padding(end = 10.dp)
                ) {
                    fonts.forEach { font ->
                        DropdownMenuItem(
                            text = { Text(font, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                onValueChange(font)
                                expanded = false
                            }
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).height(300.dp),
                    style = LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        minimalHeight = 24.dp,
                        unhoverColor = Color.Gray.copy(alpha = 0.5f),
                        hoverColor = Color.Gray.copy(alpha = 0.9f)
                    )
                )
            }
        }
    }
}