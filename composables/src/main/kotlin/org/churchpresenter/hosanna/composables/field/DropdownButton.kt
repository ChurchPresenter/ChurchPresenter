package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.foundation.FieldState
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.firstPassClick
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import org.churchpresenter.hosanna.asOverline
import org.churchpresenter.hosanna.composables.foundation.collectIsFocusVisibleAsState

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * A compact dropdown field that opens a panel of the caller's own instead of a menu — a picker too
 * rich for a list of lines. It looks and behaves like [Dropdown]'s field: the caller draws the panel
 * (a popup placed in the same parent) while [expanded] is true.
 *
 * @param text the field's text.
 * @param expanded whether the panel is open; the field shows its focused look and turns its chevron.
 * @param onClick called when pressed, to open or close the panel.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param label an optional caption above the text, as a labelled [Dropdown] has.
 * @param leading an optional preview before the text, such as a swatch of what is picked.
 * @param enabled whether it can be pressed.
 */
@Composable
fun DropdownButton(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    label: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusVisibleAsState()
    val state = if (expanded) FieldState.Focused else fieldState(enabled, false, hovered, focused)
    val colors = ThemeColors.current.fieldColors(state, error = false)
    val spec = tween<Color>(FieldDefaults.TRANSITION_MILLIS, easing = EaseOut)
    val container by animateColorAsState(colors.container, spec)
    val border by animateColorAsState(colors.border, spec)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(CHEVRON_MILLIS))
    val shape = HosannaTheme.shapes.m
    Row(
        modifier = modifier
            .then(colors.ring?.let { Modifier.focusRing(it, HosannaRadius.M) } ?: Modifier)
            .background(container, shape)
            .border(FieldDefaults.Border, border, shape)
            .height(if (label != null) FieldDefaults.LabeledHeight else FieldDefaults.CompactHeight)
            .firstPassClick(enabled, onClick)
            .clickable(interactionSource, null, enabled, role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = FieldDefaults.HorizontalPadding),
        // Between, so a field given a width keeps its chevron at the end.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(FieldDefaults.IconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(verticalArrangement = Arrangement.Center) {
                if (label != null) {
                    BasicText(
                        label.asOverline(),
                        style = HosannaTheme.typography.overline.copy(color = colors.label),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(FieldDefaults.LabelGap))
                }
                BasicText(text, style = HosannaTheme.typography.body.copy(color = colors.value), maxLines = 1)
            }
        }
        FieldGlyph(
            painterResource(Res.drawable.ic_arrow_down),
            colors.icon,
            FieldDefaults.Icon,
            Modifier.padding(start = FieldDefaults.IconGap).rotate(turn),
        )
    }
}
