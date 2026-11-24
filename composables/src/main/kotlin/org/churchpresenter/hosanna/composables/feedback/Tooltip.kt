package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.TooltipDefaults
import org.churchpresenter.hosanna.composables.model.TooltipPositionProvider
import org.churchpresenter.hosanna.composables.model.TooltipWarmth
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

/**
 * Shows a short label beside [content] on hover or keyboard focus, such as an icon button's name. It works on
 * disabled controls too.
 *
 * @param text the label.
 * @param modifier applied to the outermost layout.
 * @param shortcut an optional key shortcut after the label.
 * @param enabled turns the tooltip off, such as for text that is not cut off.
 * @param content the control the tooltip belongs to.
 */
@Composable
fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var pointerFocus by remember { mutableStateOf(false) }
    val shown = remember { MutableTransitionState(false) }
    val wanted = enabled && ((hovered && !dismissed) || (focused && !pointerFocus && !dismissed))
    var counted by remember { mutableStateOf<Any?>(null) }
    DisposableEffect(Unit) {
        onDispose { counted?.let { TooltipWarmth.forget(it) } }
    }
    LaunchedEffect(wanted) {
        val owner: Any = coroutineContext[MonotonicFrameClock] ?: Unit
        if (wanted) {
            val now = withFrameNanos { it }
            val instant = !hovered || TooltipWarmth.isWarm(owner, now, TooltipDefaults.WARM_MILLIS)
            if (!instant) delay(TooltipDefaults.SHOW_DELAY_MILLIS)
            shown.targetState = true
            if (counted == null) TooltipWarmth.opened(owner)
            counted = owner
        } else if (shown.targetState) {
            if (!dismissed) delay(TooltipDefaults.HIDE_DELAY_MILLIS)
            shown.targetState = false
            if (counted != null) TooltipWarmth.closed(owner, withFrameNanos { it })
            counted = null
        }
    }
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> {
                                hovered = false
                                dismissed = false
                            }
                            PointerEventType.Press -> {
                                dismissed = true
                                pointerFocus = true
                            }
                        }
                    }
                }
            }
            .onFocusChanged {
                focused = it.hasFocus
                if (!it.hasFocus) pointerFocus = false
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && shown.targetState) {
                    dismissed = true
                }
                false
            },
    ) {
        content()
        if (shown.currentState || shown.targetState) {
            TooltipPopup(text, shortcut, shown)
        }
    }
}

@Composable
private fun TooltipPopup(text: String, shortcut: String?, shown: MutableTransitionState<Boolean>) {
    val density = LocalDensity.current
    val gap = with(density) { TooltipDefaults.Gap.roundToPx() }
    val margin = with(density) { TooltipDefaults.Margin.roundToPx() }
    val slide = with(density) { TooltipDefaults.Slide.roundToPx() }
    Popup(popupPositionProvider = remember(gap, margin) { TooltipPositionProvider(gap, margin) }) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(TooltipDefaults.FADE_IN_MILLIS)) +
                slideInVertically(tween(TooltipDefaults.FADE_IN_MILLIS)) { -slide },
            exit = fadeOut(tween(TooltipDefaults.FADE_OUT_MILLIS)),
        ) {
            TooltipLabel(text, shortcut)
        }
    }
}

@Composable
internal fun TooltipLabel(text: String, shortcut: String?) {
    val colors = ThemeColors.current
    val style = HosannaTheme.typography.captionStrong
    Row(
        modifier = Modifier
            .widthIn(max = TooltipDefaults.MaxWidth)
            .defaultMinSize(minHeight = TooltipDefaults.MinHeight)
            .background(colors.inverseSurface, RoundedCornerShape(TooltipDefaults.Radius))
            .padding(horizontal = TooltipDefaults.PaddingX, vertical = TooltipDefaults.PaddingY),
        horizontalArrangement = Arrangement.spacedBy(TooltipDefaults.ShortcutGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, Modifier.weight(1f, fill = false), style = style.copy(color = colors.inverseOnSurface))
        shortcut?.let {
            BasicText(
                it,
                style = style.copy(color = colors.inverseOnSurface.copy(alpha = HosannaStateAlpha.MUTED_CONTENT)),
            )
        }
    }
}
