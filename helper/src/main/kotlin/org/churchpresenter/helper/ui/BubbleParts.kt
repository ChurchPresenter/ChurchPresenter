package org.churchpresenter.helper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.resolve
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A button: what it says, and what it does. */
internal typealias BubbleButton = Pair<StringResource, () -> Unit>

/** A chip: what it says, the icon before it, and what picking it does. */
internal class ChipOption(val label: HelperText, val icon: ImageVector? = null, val onClick: () -> Unit)

/** Wick's bubbles have their sharp corner top left, under the lamp; the operator's bottom right. */
private val WickShape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp)
private val OperatorShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
private val ButtonShape = RoundedCornerShape(10.dp)
private val BubblePadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)

/** The room a bubble leaves on its far side, so a long line never runs the panel's full width. */
private val BubbleInset = 24.dp
private val BUTTON_HEIGHT = 34.dp

/**
 * The panel's own color, lifted [fraction] of the way toward its ink — how a bubble, a chip or a card
 * stands off the panel in every scheme, light or dark.
 */
@Composable
@ReadOnlyComposable
internal fun lifted(fraction: Float): Color =
    lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, fraction)

internal const val BUBBLE_LIFT = 0.07f
internal const val CARD_LIFT = 0.04f
private const val CHIP_HOVER_LIFT = 0.08f

/** One line the helper says, in Wick's bubble. */
@Composable
internal fun Said(text: HelperText, modifier: Modifier = Modifier) {
    WickBubble(modifier) { BubbleText(text.resolve(), MaterialTheme.colorScheme.onSurface) }
}

/** Wick's speech bubble, holding whatever [content] it is given. */
@Composable
internal fun WickBubble(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .padding(end = BubbleInset)
            .background(lifted(BUBBLE_LIFT), WickShape)
            .padding(BubblePadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** What the operator asked, on the right in their own colour. */
@Composable
internal fun Asked(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .padding(start = BubbleInset)
                .background(MaterialTheme.colorScheme.primary, OperatorShape)
                .padding(BubblePadding)
                .testTag("helper.asked"),
        ) {
            BubbleText(text, MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
internal fun BubbleText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

/** The small tag over a message saying what it is about: "Tip of the day", "Schedule · empty". */
@Composable
internal fun BubbleHeading(text: String, icon: ImageVector) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .height(22.dp)
            .background(accent.copy(alpha = TAG_ALPHA), RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

/** Choices laid out as chips, wrapping onto more rows as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(options: List<ChipOption>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { WickChip(it) }
    }
}

@Composable
private fun WickChip(option: ChipOption) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = option.onClick,
        shape = RoundedCornerShape(16.dp),
        color = lifted(if (hovered) CHIP_HOVER_LIFT else CARD_LIFT),
        border = BorderStroke(
            1.dp,
            if (hovered) colors.primary.copy(alpha = HOVER_LINE_ALPHA) else colors.outlineVariant,
        ),
        interactionSource = interaction,
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            option.icon?.let {
                Icon(it, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
            }
            Text(option.label.resolve(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A row of buttons under a message, the main one last — where the eye ends up. The others are raised
 * keys, or flat ghost buttons when [quiet] — Cancel in a card that is already set apart. [primaryLeads]
 * puts an arrow on the main one, for a button that goes and shows something.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Actions(
    vararg buttons: BubbleButton,
    primary: BubbleButton? = null,
    primaryLeads: Boolean = false,
    quiet: Boolean = false,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { (label, onClick) ->
            val text = @Composable { Text(stringResource(label)) }
            if (quiet) {
                GhostButton(
                    onClick = onClick,
                    shape = ButtonShape,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(BUTTON_HEIGHT),
                ) { text() }
            } else {
                KeyButton(
                    onClick = onClick,
                    shape = ButtonShape,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(BUTTON_HEIGHT),
                ) { text() }
            }
        }
        primary?.let { (label, onClick) ->
            RaisedButton(
                onClick = onClick,
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(BUTTON_HEIGHT).testTag("helper.primary"),
            ) {
                Text(stringResource(label))
                if (primaryLeads) {
                    Spacer(Modifier.width(7.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** A way out that should never be hit by accident: plain text, underlined on hover. */
@Composable
internal fun QuietLink(res: StringResource, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        stringResource(res),
        style = MaterialTheme.typography.labelMedium,
        color = if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (hovered) TextDecoration.Underline else null,
        modifier = modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}

private const val TAG_ALPHA = 0.14f
private const val HOVER_LINE_ALPHA = 0.45f
