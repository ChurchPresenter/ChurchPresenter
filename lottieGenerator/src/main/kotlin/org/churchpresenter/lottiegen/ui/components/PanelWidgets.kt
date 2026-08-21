package org.churchpresenter.lottiegen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens

/** A square check with the accent fill when on, a hollow outline when off. */
@Composable
fun LottieCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) Tokens.Accent else Tokens.OutlineBg)
                .then(
                    if (checked) Modifier
                    else Modifier.border(1.5.dp, Tokens.CheckOffBorder, RoundedCornerShape(5.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 12.5.sp,
            color = if (checked) Tokens.PrimaryText else Tokens.LabelText,
            maxLines = 1
        )
    }
}

/**
 * A segmented control. [selectedIndex] may be -1 when the current config matches no preset,
 * in which case every segment renders inactive.
 */
@Composable
fun SegmentedButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Tokens.FieldBg)
            .border(1.dp, Tokens.SegBorder, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) Tokens.Accent else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Tokens.OnAccent else Tokens.SegInactive,
                    maxLines = 1
                )
            }
        }
    }
}

/** The filled primary action (Download JSON / Save Lower Third). */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Tokens.AccentHover else Tokens.Accent, label = "accentBtn")

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(Tokens.ButtonShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tokens.OnAccent, maxLines = 1)
    }
}

/** The bordered secondary action. [compact] is the 25–30dp variant used inside section headers. */
@Composable
fun SubtleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(
        if (hovered) Tokens.BorderHover else Tokens.SubtleBorder,
        label = "subtleBtnBorder"
    )
    val shape = if (compact) RoundedCornerShape(7.dp) else Tokens.ButtonShape

    Box(
        modifier = modifier
            .height(if (compact) 25.dp else 38.dp)
            .clip(shape)
            .background(if (compact) Tokens.SubtleBg else Tokens.OutlineBg)
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (compact) Tokens.SmallBtnText else Tokens.OutlineText,
            maxLines = 1
        )
    }
}

/** The small ✕ used to remove a saved preset or colour theme. */
@Composable
fun DeleteIconButton(onClick: () -> Unit, contentDescription: String = "Delete") {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(
        if (hovered) Tokens.BorderHover else Tokens.SubtleBorder,
        label = "deleteBtnBorder"
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Tokens.SubtleBg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = contentDescription,
            tint = Tokens.SmallBtnText,
            modifier = Modifier.size(13.dp)
        )
    }
}
