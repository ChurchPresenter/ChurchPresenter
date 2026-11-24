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
import org.churchpresenter.hosanna.HosannaTheme

internal fun fabHierarchy(tonal: Boolean): ButtonHierarchy =
    if (tonal) ButtonHierarchy.Secondary else ButtonHierarchy.Primary

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
