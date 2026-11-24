package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaColors
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.IconSegmentDefaults
import org.churchpresenter.hosanna.composables.defaults.SplitButtonDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.IconSegmentGroup
import org.churchpresenter.hosanna.composables.foundation.IconSegmentOption
import org.churchpresenter.hosanna.composables.foundation.IconSegmentTrack
import org.churchpresenter.hosanna.composables.foundation.SegmentGlyph
import org.churchpresenter.hosanna.composables.foundation.SegmentRole
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.model.TextStyleToggle
import org.churchpresenter.hosanna.hosannaElevation
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_format_bold
import org.churchpresenter.icons.generated.resources.ic_format_italic
import org.churchpresenter.icons.generated.resources.ic_format_strikethrough
import org.churchpresenter.icons.generated.resources.ic_format_underline
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal fun TextStyleToggle.icon(): DrawableResource = when (this) {
    TextStyleToggle.Bold -> Res.drawable.ic_format_bold
    TextStyleToggle.Italic -> Res.drawable.ic_format_italic
    TextStyleToggle.Underline -> Res.drawable.ic_format_underline
    TextStyleToggle.Strikethrough -> Res.drawable.ic_format_strikethrough
}

/**
 * Bold, italic, underline and strikethrough toggles in one group. A style can be mixed across a selection.
 *
 * @param states each style's state: on, off or mixed.
 * @param onToggle called with the style pressed.
 * @param contentDescription each style's screen-reader name and tooltip.
 * @param modifier applied to the outermost layout.
 * @param interactionSources per style; pass them to watch or freeze each toggle's looks.
 * @param enabled whether any style can be pressed.
 * @param disabled styles that cannot be pressed right now.
 */
@Composable
fun TextStyleToggles(
    states: Map<TextStyleToggle, ToggleableState>,
    onToggle: (TextStyleToggle) -> Unit,
    contentDescription: (TextStyleToggle) -> String,
    modifier: Modifier = Modifier,
    interactionSources: Map<TextStyleToggle, MutableInteractionSource> =
        remember { TextStyleToggle.entries.associateWith { MutableInteractionSource() } },
    enabled: Boolean = true,
    disabled: Set<TextStyleToggle> = emptySet(),
) {
    IconSegmentGroup(
        options = TextStyleToggle.entries.map {
            IconSegmentOption(
                icon = it.icon(),
                contentDescription = contentDescription(it),
                value = states[it] ?: ToggleableState.Off,
                enabled = it !in disabled,
                interactionSource = interactionSources.getValue(it),
                onClick = { onToggle(it) },
            )
        },
        role = SegmentRole.Toggle,
        metrics = IconSegmentDefaults.Regular,
        enabled = enabled,
        modifier = modifier,
    )
}

internal fun HosannaColors.splitDivider(checked: Boolean, enabled: Boolean): Color =
    if (checked && enabled) onPrimary.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT) else outlineVariant

internal fun splitPartOverlay(content: Color, state: ButtonState): Color = when (state) {
    ButtonState.Hovered -> content.copy(alpha = HosannaStateAlpha.HOVER)
    ButtonState.Pressed -> content.copy(alpha = HosannaStateAlpha.PRESSED)
    ButtonState.Focused -> content.copy(alpha = HosannaStateAlpha.FOCUS)
    else -> Color.Transparent
}

/**
 * A toggle with a second part that opens its options, such as text outline with its settings.
 *
 * @param icon the toggle's icon.
 * @param contentDescription the toggle's screen-reader name.
 * @param checked whether the toggle is on.
 * @param onCheckedChange called with the new state when the toggle is pressed.
 * @param onOpenOptions called when the options part is pressed.
 * @param optionsContentDescription the options part's screen-reader name.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the toggle part's looks.
 * @param optionsInteractionSource drives the options part's looks.
 * @param indicatorColor an optional bar under the icon, such as the current outline color.
 * @param enabled whether either part can be pressed.
 */
@Composable
fun SplitToggleButton(
    icon: Painter,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenOptions: () -> Unit,
    optionsContentDescription: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    optionsInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indicatorColor: Color? = null,
    enabled: Boolean = true,
) {
    val metrics = IconSegmentDefaults.Regular
    val colors = HosannaTheme.colors
    val base = colors.segmentColors(checked, if (enabled) ButtonState.Rest else ButtonState.Disabled)
    val radius = IconSegmentDefaults.SegmentRadius
    val shape = RoundedCornerShape(radius)
    val level = if (checked && enabled) HosannaLevel.Level1 else HosannaLevel.Level0
    IconSegmentTrack(enabled = enabled, modifier = modifier) {
        Row(
            modifier = Modifier
                .hosannaElevation(level, shape, HosannaTheme.elevation)
                .background(base.container, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SplitPart(
                width = metrics.width,
                shape = RoundedCornerShape(topStart = radius, bottomStart = radius),
                content = base.content,
                interactionSource = interactionSource,
                enabled = enabled,
                contentDescription = contentDescription,
                click = Modifier.toggleable(checked, interactionSource, null, enabled, Role.Checkbox, onCheckedChange),
            ) {
                if (indicatorColor == null) {
                    SegmentGlyph(icon, base.content, metrics.icon)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SplitButtonDefaults.IndicatorGap),
                    ) {
                        SegmentGlyph(icon, base.content, SplitButtonDefaults.IndicatedIcon)
                        Box(
                            Modifier
                                .width(SplitButtonDefaults.IndicatorWidth)
                                .height(SplitButtonDefaults.Indicator)
                                .background(indicatorColor, HosannaTheme.shapes.full),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .size(SplitButtonDefaults.Divider, SplitButtonDefaults.DividerHeight)
                    .background(colors.splitDivider(checked, enabled)),
            )
            SplitPart(
                width = SplitButtonDefaults.ChevronWidth,
                shape = RoundedCornerShape(topEnd = radius, bottomEnd = radius),
                content = base.content,
                interactionSource = optionsInteractionSource,
                enabled = enabled,
                contentDescription = optionsContentDescription,
                click = Modifier.clickable(
                    optionsInteractionSource, null, enabled, role = Role.Button, onClick = onOpenOptions,
                ),
            ) {
                SegmentGlyph(painterResource(Res.drawable.ic_arrow_down), base.content, SplitButtonDefaults.ChevronIcon)
            }
        }
    }
}

@Composable
private fun SplitPart(
    width: Dp,
    shape: Shape,
    content: Color,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    contentDescription: String,
    click: Modifier,
    glyph: @Composable () -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val ring = if (state == ButtonState.Focused) {
        Modifier.focusRing(
            HosannaTheme.colors.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
            IconSegmentDefaults.SegmentRadius,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .then(ring)
            .size(width, IconSegmentDefaults.Regular.height)
            .background(splitPartOverlay(content, state), shape)
            .then(click)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}
