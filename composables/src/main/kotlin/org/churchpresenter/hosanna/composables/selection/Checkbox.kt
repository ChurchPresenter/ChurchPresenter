package org.churchpresenter.hosanna.composables.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.CheckboxDefaults
import org.churchpresenter.hosanna.composables.foundation.CheckboxColors
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.checkboxColors
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_check
import org.churchpresenter.icons.generated.resources.ic_remove
import org.jetbrains.compose.resources.painterResource

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    description: String? = null,
    trailing: String? = null,
    compact: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
) {
    TriStateCheckbox(
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        onClick = { onCheckedChange(!checked) },
        label = label,
        modifier = modifier,
        interactionSource = interactionSource,
        description = description,
        trailing = trailing,
        compact = compact,
        error = error,
        enabled = enabled,
    )
}

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    description: String? = null,
    trailing: String? = null,
    compact: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val interaction = buttonState(enabled, false, hovered, pressed, focused)
    val colors = HosannaTheme.colors.checkboxColors(state, interaction, error)
    val typography = HosannaTheme.typography
    val described = description != null && !compact
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) CheckboxDefaults.CompactHeight else CheckboxDefaults.MinHeight)
            .triStateToggleable(state, interactionSource, null, enabled, Role.Checkbox, onClick)
            .padding(vertical = if (described) CheckboxDefaults.Gap / 2 else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) CheckboxDefaults.CompactGap else CheckboxDefaults.Gap,
        ),
        verticalAlignment = if (described) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(Modifier.height(CheckboxDefaults.FirstLine), contentAlignment = Alignment.Center) {
            CheckboxBox(state, colors)
        }
        Column(
            modifier = if (trailing != null) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.spacedBy(CheckboxDefaults.DescriptionGap),
        ) {
            BasicText(label, style = typography.body.copy(color = colors.label))
            if (described) {
                BasicText(description.orEmpty(), style = typography.caption.copy(color = colors.description))
            }
        }
        trailing?.let {
            BasicText(
                it,
                modifier = Modifier.padding(start = CheckboxDefaults.TrailingGap - CheckboxDefaults.Gap),
                style = typography.caption.tabular().copy(color = colors.description),
            )
        }
    }
}

@Composable
private fun CheckboxBox(state: ToggleableState, colors: CheckboxColors) {
    val fill by animateColorAsState(colors.fill, tween(CheckboxDefaults.TRANSITION_MILLIS))
    val shape = RoundedCornerShape(CheckboxDefaults.BoxRadius)
    val halo = colors.halo
    Box(
        modifier = Modifier
            .size(CheckboxDefaults.Box)
            .drawBehind {
                if (halo != Color.Transparent) drawCircle(halo, radius = CheckboxDefaults.Halo.toPx() / 2)
            }
            .then(colors.ring?.let { Modifier.focusRing(it, CheckboxDefaults.BoxRadius) } ?: Modifier)
            .background(fill, shape)
            .border(CheckboxDefaults.Edge, colors.edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        val (icon, size) = when (state) {
            ToggleableState.On -> Res.drawable.ic_check to CheckboxDefaults.Check
            ToggleableState.Indeterminate -> Res.drawable.ic_remove to CheckboxDefaults.Dash
            ToggleableState.Off -> null to CheckboxDefaults.Check
        }
        icon?.let { Image(painterResource(it), null, Modifier.size(size), colorFilter = ColorFilter.tint(colors.mark)) }
    }
}
