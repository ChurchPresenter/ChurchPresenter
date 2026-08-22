package org.churchpresenter.lottiegen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens

/** The chevron points down when closed and is turned over, not spun, when open. */
private const val CHEVRON_FLIPPED_DEGREES = 180f


/**
 * A dropdown anchor styled as a field card: a tiny uppercase label above the current value,
 * with a caret that flips when the menu is open.
 */
@Composable
fun LottieDropdown(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (hovered || expanded) Tokens.FieldBorderHover else Tokens.FieldBorder,
        label = "dropdownBorder"
    )

    Row(
        modifier = modifier
            .height(Tokens.FieldHeight)
            .clip(Tokens.FieldShape)
            .background(Tokens.FieldBg)
            .border(1.dp, borderColor, Tokens.FieldShape)
            .hoverable(interaction)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = Tokens.FieldLabelTracking,
                color = Tokens.FieldLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                color = Tokens.PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(13.dp).rotate(if (expanded) CHEVRON_FLIPPED_DEGREES else 0f),
            tint = Tokens.FieldLabel
        )
    }
}
