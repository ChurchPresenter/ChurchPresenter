package org.churchpresenter.theme.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DropdownSelector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    itemTrailingContent: (@Composable RowScope.(key: String, index: Int) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == value }?.second ?: value
    val sizeModifier = if (compact) modifier.height(34.dp) else modifier.heightIn(min = 42.dp)

    Box(
        modifier = sizeModifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                expanded = true
            }
            .padding(
                start = 11.dp,
                end = 11.dp,
                top = if (compact) 2.dp else 4.dp,
                bottom = if (compact) 2.dp else 4.dp,
            ),
        // The row wraps its content, which is shorter than the field's fixed height, so without
        // this it sits against the top edge and the value reads high in the box -- most visibly on
        // a `compact` field, where a 34dp box holds about 20dp of text. `DropdownSettingsField` and
        // the font picker's trigger centre theirs the same way.
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label.uppercase(),
                        fontSize = if (compact) 8.sp else 10.sp,
                        lineHeight = if (compact) 9.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (compact) 11.sp else 13.sp,
                        lineHeight = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = DropdownArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val items: @Composable () -> Unit = {
                options.forEachIndexed { index, (key, display) ->
                    DropdownMenuItem(
                        text = { Text(display, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onValueChange(key)
                            expanded = false
                        },
                        trailingIcon = itemTrailingContent?.let { content ->
                            { Row { content(key, index) } }
                        },
                    )
                }
            }
            if (MENU_ITEM_HEIGHT * options.size <= MENU_MAX_HEIGHT) {
                items()
            } else {
                // A long list — a folder of templates, say — scrolls inside a capped box with a
                // bar beside it; the menu's own scroll has none. The height is explicit rather
                // than a cap because a scrollbar in an uncapped box reports an infinite height.
                val listState = rememberScrollState()
                Box(Modifier.height(MENU_MAX_HEIGHT)) {
                    Column(Modifier.verticalScroll(listState).padding(end = MENU_SCROLLBAR_GUTTER)) { items() }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
                    )
                }
            }
        }
    }
}

/** Material's menu item height; the list is sized from it so short menus stay short. */
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_MAX_HEIGHT = 380.dp
private val MENU_SCROLLBAR_GUTTER = 10.dp

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
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    expanded.value = true
                }
                .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label.uppercase(),
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                }
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = DropdownArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
                    onClick = {
                        onSelectedChange(item)
                        expanded.value = false
                    }
                )
            }
        }
    }
}

private const val ARROW_VIEWPORT = 24f

/**
 * The chevron on every dropdown: Material's `keyboard_arrow_down`, drawn here so this module
 * needs no icon dependency — the same path the app's `ic_arrow_down` drawable carries.
 */
private val DropdownArrow: ImageVector = ImageVector.Builder(
    name = "DropdownArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = ARROW_VIEWPORT,
    viewportHeight = ARROW_VIEWPORT,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7.41f, 8.59f)
        lineTo(12f, 13.17f)
        lineToRelative(4.59f, -4.58f)
        lineTo(18f, 10f)
        lineToRelative(-6f, 6f)
        lineToRelative(-6f, -6f)
        lineToRelative(1.41f, -1.41f)
        close()
    }
}.build()
