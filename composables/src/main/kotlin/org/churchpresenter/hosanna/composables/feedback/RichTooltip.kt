package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import org.churchpresenter.theme.HosannaLevel
import org.churchpresenter.theme.HosannaTheme
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.defaults.RichTooltipDefaults
import org.churchpresenter.hosanna.composables.defaults.StandardButtonDefaults
import org.churchpresenter.hosanna.composables.defaults.TooltipDefaults
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.TooltipPositionProvider
import org.churchpresenter.theme.hosannaElevation
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

/**
 * A small card with a title, a few lines and an optional action, opened by hovering or clicking [content]. It
 * stays while the pointer is over the control or the card.
 *
 * @param title the card's title.
 * @param body the card's text.
 * @param modifier applied to the outermost layout.
 * @param actionText turns on a text button at the bottom.
 * @param onAction called when that button is pressed.
 * @param actionInteractionSource drives that button's looks.
 * @param content the control the card belongs to.
 */
@Composable
fun RichTooltip(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
    actionInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    var overAnchor by remember { mutableStateOf(false) }
    var overCard by remember { mutableStateOf(false) }
    var clicked by remember { mutableStateOf(false) }
    val shown = remember { MutableTransitionState(false) }
    val hovering = overAnchor || overCard
    LaunchedEffect(hovering, clicked) {
        when {
            clicked -> shown.targetState = true
            hovering && !shown.targetState -> {
                delay(RichTooltipDefaults.OPEN_DELAY_MILLIS)
                shown.targetState = true
            }
            !hovering && shown.targetState -> {
                delay(RichTooltipDefaults.CLOSE_DELAY_MILLIS)
                shown.targetState = false
            }
        }
    }
    fun close() {
        clicked = false
        shown.targetState = false
    }
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Enter -> overAnchor = true
                            PointerEventType.Exit -> {
                                overAnchor = false
                                clicked = false
                            }
                            PointerEventType.Press -> if (shown.targetState) close() else clicked = true
                        }
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                val escape = event.key == Key.Escape && event.type == KeyEventType.KeyDown && shown.targetState
                if (escape) close()
                escape
            },
    ) {
        content()
        if (shown.currentState || shown.targetState) {
            RichTooltipPopup(shown) {
                RichTooltipCard(
                    title, body, actionText, onAction, actionInteractionSource,
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Enter -> overCard = true
                                    PointerEventType.Exit -> overCard = false
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RichTooltipPopup(shown: MutableTransitionState<Boolean>, card: @Composable () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { TooltipDefaults.Gap.roundToPx() }
    val margin = with(density) { TooltipDefaults.Margin.roundToPx() }
    Popup(popupPositionProvider = remember(gap, margin) { TooltipPositionProvider(gap, margin) }) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(TooltipDefaults.FADE_IN_MILLIS)),
            exit = fadeOut(tween(TooltipDefaults.FADE_OUT_MILLIS)),
        ) {
            card()
        }
    }
}

@Composable
internal fun RichTooltipCard(
    title: String,
    body: String,
    actionText: String?,
    onAction: () -> Unit,
    actionInteractionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val shape = HosannaTheme.shapes.l
    val colors = ThemeColors.current
    Column(
        modifier = modifier
            .widthIn(max = RichTooltipDefaults.MaxWidth)
            .hosannaElevation(HosannaLevel.Level2, shape, HosannaTheme.elevation)
            .background(colors.surfaceContainer, shape)
            .padding(horizontal = RichTooltipDefaults.PaddingX, vertical = RichTooltipDefaults.PaddingY),
        verticalArrangement = Arrangement.spacedBy(RichTooltipDefaults.TitleGap),
    ) {
        BasicText(title, style = HosannaTheme.typography.bodyStrong.copy(color = colors.onSurface))
        BasicText(body, style = HosannaTheme.typography.caption.copy(color = colors.onSurfaceVariant))
        actionText?.let {
            StandardButton(
                it,
                onAction,
                Modifier
                    .padding(top = RichTooltipDefaults.ActionGap)
                    .offset(x = -StandardButtonDefaults.TertiaryHorizontalPadding),
                interactionSource = actionInteractionSource,
                hierarchy = ButtonHierarchy.Tertiary,
            )
        }
    }
}
