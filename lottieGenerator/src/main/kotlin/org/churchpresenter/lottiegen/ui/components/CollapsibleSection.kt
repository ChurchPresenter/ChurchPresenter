package org.churchpresenter.lottiegen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens

/** The amber marker that opens every section header. */
@Composable
private fun SectionTick() {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Tokens.Tick)
    )
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = Tokens.SectionTitleTracking,
        color = Tokens.TitleText,
        maxLines = 1
    )
}

/**
 * A section card that is always open — used for the panels that have no reason to collapse
 * (Style & Layout, Text, Library). Shares the card chrome with [CollapsibleSection] so the
 * two read as one family.
 *
 * [trailing] renders at the right edge of the header, e.g. the Library's "Batch Import".
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Tokens.CardShape)
            .background(Tokens.CardBg)
            .border(1.dp, Tokens.CardBorder, Tokens.CardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.SectionHeaderHeight)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            SectionTick()
            SectionTitle(title, Modifier.weight(1f))
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            content()
        }
    }
}

/**
 * A collapsible section card: amber tick, title, an optional right-aligned [hint] summarising
 * the collapsed contents (e.g. "7.0s", "Off"), and a caret that rotates as it opens. The card
 * border and header background lift when expanded so the open section stands out in the stack.
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        if (expanded) Tokens.CardBorderOpen else Tokens.CardBorder,
        label = "sectionBorder"
    )
    val headBg by animateColorAsState(
        when {
            hovered -> Tokens.HeadBgHover
            expanded -> Tokens.HeadBgOpen
            else -> Color.Transparent
        },
        label = "sectionHeadBg"
    )
    val caretRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sectionCaret")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Tokens.CardShape)
            .background(Tokens.CardBg)
            .border(1.dp, borderColor, Tokens.CardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.SectionHeaderHeight)
                .background(headBg)
                .hoverable(interaction)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            SectionTick()
            SectionTitle(title, Modifier.weight(1f))
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 10.5.sp,
                    color = Tokens.HintText,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Tokens.Caret,
                modifier = Modifier.size(14.dp).rotate(caretRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}
