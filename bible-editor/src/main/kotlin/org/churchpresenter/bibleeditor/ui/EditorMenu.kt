package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.keyboardFocusRing

private val MenuPadding = 5.dp
private val ScrollbarGutter = 12.dp
private val ItemShape = RoundedCornerShape(8.dp)

/**
 * A dropdown of [count] rows of [itemHeight], at most [maxHeight] tall, with a scrollbar down its
 * edge. It opens scrolled so [selected] is in view.
 */
@Composable
internal fun EditorMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    width: Dp,
    maxHeight: Dp,
    itemHeight: Dp,
    count: Int,
    selected: Int,
    item: @Composable (index: Int) -> Unit,
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss, containerColor = editorColors().raise) {
        val scroll = rememberScrollState()
        val height = minOf(itemHeight * count + MenuPadding * 2, maxHeight)
        val density = LocalDensity.current
        LaunchedEffect(Unit) {
            if (selected < 0) return@LaunchedEffect
            val target = with(density) { (itemHeight * selected - (height - itemHeight) / 2).toPx() }
            scroll.scrollTo(target.toInt().coerceIn(0, scroll.maxValue))
        }
        Box(Modifier.width(width).height(height)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(start = MenuPadding, top = MenuPadding, bottom = MenuPadding, end = ScrollbarGutter),
            ) {
                repeat(count) { item(it) }
            }
            VerticalScrollbar(
                rememberScrollbarAdapter(scroll),
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 3.dp),
            )
        }
    }
}

/** One row of an [EditorMenu]: tinted when [picked], lit under the pointer. */
@Composable
internal fun EditorMenuRow(
    height: Dp,
    picked: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = editorColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().keyboardFocusRing(ItemShape).height(height).clip(ItemShape)
            .background(
                when {
                    picked -> colors.accentBg
                    hovered -> colors.hover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
