package org.churchpresenter.liveoutput.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.PreviewArea
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.toggleRow
import org.churchpresenter.theme.keyboardFocusRing

internal val ROW_HEIGHT = 32.dp
private val ROW_SHAPE = RoundedCornerShape(8.dp)
private val TEMPLATE_HEIGHT = 40.dp
private const val HOVER_WASH = 0.06f
private const val TEMPLATE_FILL = 0.6f
private const val TEMPLATE_HOVER_FILL = 0.18f
private const val TEMPLATE_CELL = 0.55f
private const val MIN_SHARE = 0.01f

/** Enough outputs to fill every area of the largest template, for drawing its picture. */
private val PICTURE_OUTPUTS = List(8) { "area$it" }

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.11.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
    )
}

@Composable
internal fun TemplateButton(
    template: PreviewLayoutTemplate,
    onHover: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val over by interaction.collectIsHoveredAsState()
    LaunchedEffect(over) { onHover(over) }
    val colors = MaterialTheme.colorScheme
    val fill = if (over) {
        colors.primary.copy(alpha = TEMPLATE_HOVER_FILL)
    } else {
        colors.surfaceVariant.copy(alpha = TEMPLATE_FILL)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(TEMPLATE_HEIGHT)
            .keyboardFocusRing(ROW_SHAPE)
            .clip(ROW_SHAPE)
            .background(fill)
            .border(1.dp, if (over) colors.primary else Color.Transparent, ROW_SHAPE)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = template.label }
            .padding(5.dp),
    ) {
        LayoutPicture(
            area = remember(template) { template.build(PICTURE_OUTPUTS) },
            cell = if (over) colors.primary else colors.onSurfaceVariant.copy(alpha = TEMPLATE_CELL),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** [area] drawn as its split: each leaf a small rounded block in [cell], sized by its share. */
@Composable
internal fun LayoutPicture(area: PreviewArea, cell: Color, modifier: Modifier = Modifier) {
    if (area.isLeaf) {
        Box(modifier.padding(0.5.dp).clip(RoundedCornerShape(2.dp)).background(cell))
        return
    }
    fun share(index: Int) = area.ratios.getOrElse(index) { 1f / area.children.size }.coerceAtLeast(MIN_SHARE)
    if (area.split == SPLIT_ACROSS) {
        Row(modifier) {
            area.children.forEachIndexed { i, child ->
                LayoutPicture(child, cell, Modifier.weight(share(i)).fillMaxSize())
            }
        }
    } else {
        Column(modifier) {
            area.children.forEachIndexed { i, child ->
                LayoutPicture(child, cell, Modifier.weight(share(i)).fillMaxSize())
            }
        }
    }
}

@Composable
internal fun MenuRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val over by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .keyboardFocusRing(ROW_SHAPE)
            .clip(ROW_SHAPE)
            .background(if (over) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_WASH) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 8.dp, end = 2.dp),
        content = content,
    )
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val over by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .keyboardFocusRing(ROW_SHAPE)
            .clip(ROW_SHAPE)
            .background(if (over) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_WASH) else Color.Transparent)
            .toggleRow(checked, onChange, interaction)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RaisedSwitch(checked = checked, onCheckedChange = null, interactionSource = interaction)
    }
}
