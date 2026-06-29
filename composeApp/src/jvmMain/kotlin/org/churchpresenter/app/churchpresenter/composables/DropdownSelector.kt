package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import org.jetbrains.compose.resources.painterResource

@Composable
fun DropdownSelector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == value }?.second ?: value

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .height(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
                .padding(start = 11.dp, end = 11.dp, top = 0.dp, bottom = 6.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            if (label.isNotEmpty()) {
                Text(
                    text = label.uppercase(),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onValueChange(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DropdownSelector(
    label: String,
    items: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .height(42.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded.value = true }
                .padding(start = 11.dp, end = 11.dp, top = 0.dp, bottom = 6.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onSelectedChange(item)
                        expanded.value = false
                    }
                )
            }
        }
    }
}
