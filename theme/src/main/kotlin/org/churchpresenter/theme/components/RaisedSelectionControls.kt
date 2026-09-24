package org.churchpresenter.theme.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.RaisedFill
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken
import androidx.compose.ui.graphics.graphicsLayer

private const val DISABLED_ALPHA = 0.45f
private const val SEGMENT_HOVER_ALPHA = 0.08f
private val SEGMENT_HOVER_SHIFT = 1.dp
private const val HOVER_RIM_ALPHA = 0.55f
private val BOX_SIZE = 18.dp
/** Material draws its checkbox and radio 18dp inside a 20dp box; this is the difference. */
private val CONTROL_PADDING = 1.dp
private val BOX_RADIUS = 5.dp
private val RADIO_DOT = 7.dp
private val CHIP_RADIUS = 8.dp
private val CHIP_HEIGHT = 32.dp
private val SEGMENT_INSET = 3.dp

// The check mark, as fractions of the box.
private const val CHECK_START_X = 0.24f
private const val CHECK_START_Y = 0.52f
private const val CHECK_KNEE_X = 0.42f
private const val CHECK_KNEE_Y = 0.70f
private const val CHECK_END_X = 0.76f
private const val CHECK_END_Y = 0.32f
private const val CHECK_STROKE_FRACTION = 0.13f

/**
 * `Checkbox` in the elevated look: a sunken box while clear, a raised accent key with a check mark
 * when ticked. [colors]' checked color, when a caller sets one, tints the ticked key.
 */
@Composable
fun RaisedCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverRim = MaterialTheme.colorScheme.primary.copy(alpha = HOVER_RIM_ALPHA)
    val shape = RoundedCornerShape(BOX_RADIUS)
    val fill = checkedFill(colors.checkedBoxColor, colors.checkedCheckmarkColor)
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            // Only a control with a handler of its own needs the touch target; a display-only one
            // sits in a row that is itself clickable, and Material's is its bare size there too.
            .then(if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .padding(CONTROL_PADDING)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (onCheckedChange != null && enabled) Modifier.hoverable(interaction) else Modifier)
            .then(toggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // At most BOX_SIZE, and smaller when the caller sizes the control smaller: a fixed box
            // in a tighter space was cropped, which cut its rounded corners square.
            modifier = Modifier
                .sizeIn(maxWidth = BOX_SIZE, maxHeight = BOX_SIZE)
                .fillMaxSize()
                .aspectRatio(1f)
                .then(
                    if (checked) {
                        Modifier.raised(shape, fill, palette, hovered = hovered, lift = 2.dp, moves = false)
                    } else {
                        Modifier.sunken(shape, palette, rim = if (hovered) hoverRim else Color.Unspecified)
                    }
                ),
        ) {
            if (checked) CheckMark(fill.ink)
        }
    }
}

@Composable
private fun CheckMark(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val path = Path().apply {
            moveTo(size.width * CHECK_START_X, size.height * CHECK_START_Y)
            lineTo(size.width * CHECK_KNEE_X, size.height * CHECK_KNEE_Y)
            lineTo(size.width * CHECK_END_X, size.height * CHECK_END_Y)
        }
        drawPath(
            path,
            color,
            style = Stroke(size.width * CHECK_STROKE_FRACTION, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * `RadioButton` in the elevated look: a sunken well while unselected, a raised accent disc with a
 * dot when selected.
 */
@Composable
fun RaisedRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverRim = MaterialTheme.colorScheme.primary.copy(alpha = HOVER_RIM_ALPHA)
    val fill = checkedFill(colors.selectedColor, MaterialTheme.colorScheme.onPrimary)
    val select = if (onClick != null) {
        Modifier.selectable(
            selected = selected,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .padding(CONTROL_PADDING)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (onClick != null && enabled) Modifier.hoverable(interaction) else Modifier)
            .then(select),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // At most BOX_SIZE, and smaller when the caller sizes the control smaller: a fixed box
            // in a tighter space was cropped, which cut its rounded corners square.
            modifier = Modifier
                .sizeIn(maxWidth = BOX_SIZE, maxHeight = BOX_SIZE)
                .fillMaxSize()
                .aspectRatio(1f)
                .then(
                    if (selected) {
                        Modifier.raised(CircleShape, fill, palette, hovered = hovered, lift = 2.dp, moves = false)
                    } else {
                        Modifier.sunken(CircleShape, palette, rim = if (hovered) hoverRim else Color.Unspecified)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Canvas(Modifier.size(RADIO_DOT)) {
                    drawCircle(fill.ink, center = Offset(size.width / 2, size.height / 2))
                }
            }
        }
    }
}

/** The accent fill a ticked control takes, in [color] when a caller gave one. */
@Composable
private fun checkedFill(color: Color, ink: Color): RaisedFill {
    val palette = elevationPalette()
    return if (color == MaterialTheme.colorScheme.primary) palette.accent else palette.tinted(color, ink)
}

/**
 * `FilterChip` in the elevated look: a neutral raised key, the accent key when [selected] -- or a
 * key in [selectedContainerColor], when a caller gives one.
 */
@Composable
fun RaisedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CHIP_RADIUS),
    selectedContainerColor: Color = Color.Unspecified,
    selectedLabelColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
) {
    val palette = elevationPalette()
    val selectedFill = if (selectedContainerColor.isSpecified) {
        palette.tinted(selectedContainerColor, selectedLabelColor.takeOrElse { palette.accent.ink })
    } else {
        palette.accent
    }
    RaisedChip(
        onClick = onClick,
        fill = if (selected) selectedFill else palette.key,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        interactionSource = interactionSource,
    )
}

/** A small raised key holding a label, in [fill]. The base of [RaisedFilterChip]. */
@Composable
fun RaisedChip(
    onClick: () -> Unit,
    fill: RaisedFill,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CHIP_RADIUS),
    interactionSource: MutableInteractionSource? = null,
) {
    val palette = elevationPalette()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = CHIP_HEIGHT)
            .then(
                if (enabled) {
                    Modifier.raised(shape, fill, palette, pressed, hovered, lift = 2.dp)
                } else {
                    Modifier.clip(shape)
                }
            )
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fill.ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = fill.ink)) {
                leadingIcon?.invoke()
                label()
                trailingIcon?.invoke()
            }
        }
    }
}

/** A sunken track holding [SegmentTrackItem]s side by side -- the segmented control, sized by its caller. */
@Composable
fun SegmentTrack(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.sunken(shape, elevationPalette()).padding(SEGMENT_INSET),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_INSET),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One option of a [SegmentTrack]: flat, or raised when [selected]. Weight it to share the track. */
@Composable
fun SegmentTrackItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = elevationPalette()
    val shape = RoundedCornerShape(7.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = when {
        selected -> palette.selected.ink
        hovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .then(
                when {
                    selected -> Modifier.raised(shape, palette.selected, palette, hovered = hovered, lift = 2.dp)
                    // A faint wash under the pointer, so an unchosen option shows it can be picked.
                    hovered -> Modifier.graphicsLayer { translationY = -SEGMENT_HOVER_SHIFT.toPx() }
                        .clip(shape).background(ink.copy(alpha = SEGMENT_HOVER_ALPHA))
                    else -> Modifier.clip(shape)
                }
            )
            .hoverable(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content)
        }
    }
}
