package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.defaults.MessageDefaults
import org.churchpresenter.hosanna.composables.foundation.MessageColors
import org.churchpresenter.hosanna.composables.foundation.messageColors
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.MessageAction
import org.churchpresenter.hosanna.composables.model.MessageIntent
import org.churchpresenter.hosanna.composables.model.buttonIntent
import org.churchpresenter.hosanna.composables.model.dismissible
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_check_circle
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_error
import org.churchpresenter.icons.generated.resources.ic_info
import org.churchpresenter.icons.generated.resources.ic_warning
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A message across the top of a panel: an icon, an optional title, the text, and the fix as a button.
 *
 * @param body the message.
 * @param modifier applied to the outermost layout.
 * @param intent what the message is about, which picks its colors and icon.
 * @param title an optional title above the message.
 * @param action the main button, filled in the intent's color.
 * @param otherActions text buttons before the main one.
 * @param dismiss turns on the close button for info, success and neutral messages.
 * @param visible shows or hides the banner with a fade and slide.
 * @param extra optional content under the message, such as a pairing code.
 */
@Composable
fun Banner(
    body: String,
    modifier: Modifier = Modifier,
    intent: MessageIntent = MessageIntent.Info,
    title: String? = null,
    action: MessageAction? = null,
    otherActions: List<MessageAction> = emptyList(),
    dismiss: MessageAction? = null,
    visible: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    val slide = with(LocalDensity.current) { MessageDefaults.Slide.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(MessageDefaults.APPEAR_MILLIS)) +
            slideInVertically(tween(MessageDefaults.APPEAR_MILLIS)) { -slide },
        exit = fadeOut(tween(MessageDefaults.DISMISS_MILLIS)) + shrinkVertically(tween(MessageDefaults.DISMISS_MILLIS)),
    ) {
        BannerBody(body, intent, title, action, otherActions, dismiss?.takeIf { intent.dismissible() }, extra)
    }
}

@Composable
private fun BannerBody(
    body: String,
    intent: MessageIntent,
    title: String?,
    action: MessageAction?,
    otherActions: List<MessageAction>,
    dismiss: MessageAction?,
    extra: (@Composable () -> Unit)?,
) {
    val colors = AppTheme.colors.messageColors(intent)
    val shape = RoundedCornerShape(MessageDefaults.BannerRadius)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .announce(intent)
            .background(colors.container, shape)
            .border(MessageDefaults.Edge, colors.edge, shape)
            .padding(horizontal = MessageDefaults.BannerPaddingX, vertical = MessageDefaults.BannerPaddingY),
    ) {
        val narrow = maxWidth < MessageDefaults.NarrowWidth
        val hasActions = action != null || otherActions.isNotEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(MessageDefaults.BannerGap)) {
            MessageIcon(
                intent, colors, MessageDefaults.BannerIcon,
                Modifier.padding(top = MessageDefaults.BannerTextGap),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MessageDefaults.ActionGap)) {
                Column(verticalArrangement = Arrangement.spacedBy(MessageDefaults.BannerTextGap)) {
                    title?.let { BasicText(it, style = AppTheme.typography.subtitle.copy(color = colors.content)) }
                    BasicText(body, style = AppTheme.typography.body.copy(color = colors.content))
                    extra?.invoke()
                }
                if (narrow && hasActions) BannerActions(intent, action, otherActions)
            }
            if (!narrow && hasActions) {
                Box(Modifier.align(Alignment.CenterVertically)) { BannerActions(intent, action, otherActions) }
            }
            dismiss?.let {
                Tooltip(it.label) {
                    IconButton(
                        painterResource(Res.drawable.ic_close),
                        it.label,
                        it.onClick,
                        size = IconButtonSize.Small,
                        hierarchy = ButtonHierarchy.Tertiary,
                        intent = ButtonIntent.Neutral,
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerActions(intent: MessageIntent, action: MessageAction?, otherActions: List<MessageAction>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MessageDefaults.ActionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        otherActions.forEach {
            StandardButton(it.label, it.onClick, hierarchy = ButtonHierarchy.Tertiary, intent = intent.buttonIntent())
        }
        action?.let { StandardButton(it.label, it.onClick, intent = intent.buttonIntent()) }
    }
}

/**
 * A small message inside a form, next to what it is about.
 *
 * @param text the message.
 * @param modifier applied to the outermost layout.
 * @param intent what the message is about, which picks its colors and icon.
 */
@Composable
fun InlineMessage(text: String, modifier: Modifier = Modifier, intent: MessageIntent = MessageIntent.Info) {
    val colors = AppTheme.colors.messageColors(intent)
    val shape = RoundedCornerShape(MessageDefaults.InlineRadius)
    Row(
        modifier = modifier
            .announce(intent)
            .background(colors.container, shape)
            .border(MessageDefaults.Edge, colors.edge, shape)
            .padding(horizontal = MessageDefaults.InlinePaddingX, vertical = MessageDefaults.InlinePaddingY),
        horizontalArrangement = Arrangement.spacedBy(MessageDefaults.InlineGap),
    ) {
        MessageIcon(intent, colors, MessageDefaults.InlineIcon)
        BasicText(text, style = AppTheme.typography.caption.copy(color = colors.content))
    }
}

/**
 * A line of help text with an info icon and no container.
 *
 * @param text the hint.
 * @param modifier applied to the outermost layout.
 */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    val ink = AppTheme.colors.onSurfaceVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MessageDefaults.HintGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(Res.drawable.ic_info),
            null,
            Modifier.size(MessageDefaults.HintIcon),
            colorFilter = ColorFilter.tint(ink),
        )
        BasicText(text, style = AppTheme.typography.caption.copy(color = ink))
    }
}

@Composable
private fun MessageIcon(intent: MessageIntent, colors: MessageColors, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painterResource(intent.icon()),
        null,
        modifier.size(size),
        colorFilter = ColorFilter.tint(colors.icon),
    )
}

internal fun MessageIntent.icon(): DrawableResource = when (this) {
    MessageIntent.Info, MessageIntent.Neutral -> Res.drawable.ic_info
    MessageIntent.Success -> Res.drawable.ic_check_circle
    MessageIntent.Warning -> Res.drawable.ic_warning
    MessageIntent.Error -> Res.drawable.ic_error
}

private fun Modifier.announce(intent: MessageIntent): Modifier = semantics {
    liveRegion = if (intent == MessageIntent.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite
}
