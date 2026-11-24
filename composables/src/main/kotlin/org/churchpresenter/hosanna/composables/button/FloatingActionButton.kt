package org.churchpresenter.hosanna.composables.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.theme.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.FabDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonFrame
import org.churchpresenter.hosanna.composables.foundation.ButtonIcon
import org.churchpresenter.hosanna.composables.foundation.ButtonLook
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.FabSize

internal fun fabHierarchy(tonal: Boolean): ButtonHierarchy =
    if (tonal) ButtonHierarchy.Secondary else ButtonHierarchy.Primary

/**
 * A raised round button for a screen's main action, floating over its content.
 *
 * @param icon the icon.
 * @param contentDescription the button's name for screen readers.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param size the button's size.
 * @param intent what the button does, which picks its colors.
 * @param tonal uses the soft container colors instead of the filled ones.
 * @param enabled whether the button can be pressed.
 * @param visible shows or hides the button with a scale and fade.
 */
@Composable
fun FloatingActionButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: FabSize = FabSize.Regular,
    intent: ButtonIntent = ButtonIntent.Default,
    tonal: Boolean = false,
    enabled: Boolean = true,
    visible: Boolean = true,
) {
    val metrics = FabDefaults.metrics(size)
    FabVisibility(visible) {
        ButtonFrame(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .size(metrics.side)
                .semantics { this.contentDescription = contentDescription },
            look = ButtonLook(fabHierarchy(tonal), intent, metrics.side, cornerRadius = null, floating = true),
            enabled = enabled,
            loading = false,
        ) { content ->
            ButtonIcon(icon, content, metrics.icon)
        }
    }
}

/**
 * A floating action button with a text label.
 *
 * @param text the label.
 * @param icon the icon before the label.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param intent what the button does, which picks its colors.
 * @param tonal uses the soft container colors instead of the filled ones.
 * @param enabled whether the button can be pressed.
 * @param visible shows or hides the button with a scale and fade.
 */
@Composable
fun ExtendedFloatingActionButton(
    text: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    intent: ButtonIntent = ButtonIntent.Default,
    tonal: Boolean = false,
    enabled: Boolean = true,
    visible: Boolean = true,
) {
    FabVisibility(visible) {
        ButtonFrame(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier,
            look = ButtonLook(
                fabHierarchy(tonal),
                intent,
                FabDefaults.ExtendedHeight,
                cornerRadius = null,
                floating = true,
            ),
            enabled = enabled,
            loading = false,
        ) { content ->
            Row(
                modifier = Modifier.padding(
                    PaddingValues(start = FabDefaults.ExtendedStart, end = FabDefaults.ExtendedEnd),
                ),
                horizontalArrangement = Arrangement.spacedBy(FabDefaults.ExtendedGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ButtonIcon(icon, content, FabDefaults.ExtendedIcon)
                BasicText(
                    text = text,
                    style = HosannaTheme.typography.subtitle.copy(color = content),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun FabVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val spec = tween<Float>(FabDefaults.APPEAR_MILLIS)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spec) + scaleIn(spec, initialScale = FabDefaults.APPEAR_SCALE),
        exit = fadeOut(spec) + scaleOut(spec, targetScale = FabDefaults.APPEAR_SCALE),
    ) {
        content()
    }
}
