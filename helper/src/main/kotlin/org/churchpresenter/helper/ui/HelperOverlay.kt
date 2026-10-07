package org.churchpresenter.helper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.sharedui.composables.TooltipIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.intent.IntentResolver
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.suggest.Suggestion
import org.churchpresenter.helper.suggest.allTips
import org.churchpresenter.helper.suggest.tipAt
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.helperDayOf
import org.churchpresenter.settings.tipDue
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_close
import org.churchpresenter.strings.generated.resources.helper_close_bubble
import org.churchpresenter.strings.generated.resources.helper_hide
import org.churchpresenter.strings.generated.resources.helper_input_placeholder
import org.churchpresenter.strings.generated.resources.helper_name
import org.churchpresenter.strings.generated.resources.helper_open
import org.churchpresenter.strings.generated.resources.helper_send
import org.jetbrains.compose.resources.stringResource

/** How long nothing has to be live before the lamp offers a tip. */
private const val TIP_IDLE_MS = 60_000L

/**
 * Everything the app hands the helper for one frame: what to say, what it may know, and where to
 * write its own settings. Values and callbacks only — the helper never holds the app's view models.
 */
class HelperInputs(
    val settings: HelperSettings,
    val onSettingsChange: (HelperSettings) -> Unit,
    val suggestions: List<Suggestion>,
    val screens: List<HelperScreen>,
    val context: ResolveContext,
    val anythingLive: Boolean,
    val nowMillis: () -> Long = System::currentTimeMillis,
)

/**
 * The helper in the window's corner: the lamp, a dot when it has something to say, and the bubble
 * it opens. Never opens by itself, and holds still while anything is live.
 */
@Composable
fun HelperOverlay(
    state: HelperState,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
    resolver: IntentResolver,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    if (!inputs.settings.enabled) return
    var idleLongEnough by remember { mutableStateOf(false) }
    LaunchedEffect(inputs.anythingLive) {
        idleLongEnough = false
        if (!inputs.anythingLive) {
            delay(TIP_IDLE_MS)
            idleLongEnough = true
        }
    }
    val tipWaiting = idleLongEnough && inputs.settings.tipDue(inputs.nowMillis())
    val hasNews = !inputs.anythingLive && (inputs.suggestions.isNotEmpty() || tipWaiting)

    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            HelperBubble(state, inputs, executor, resolver, animate)
        }
        Spacer(Modifier.size(8.dp))
        LampButton(
            open = state.isOpen,
            hasNews = hasNews,
            animate = animate && !inputs.anythingLive,
            mood = moodFor(state.reply),
            onClick = { if (state.isOpen) state.close() else state.isOpen = true },
        )
    }
}

private fun moodFor(reply: HelperReply): LampMood = when (reply) {
    HelperReply.Unknown -> LampMood.CONFUSED
    is HelperReply.Confirm, is HelperReply.Clarify -> LampMood.THINKING
    is HelperReply.Message -> if (reply.canUndo) LampMood.HAPPY else LampMood.IDLE
    else -> LampMood.IDLE
}

@Composable
private fun LampButton(open: Boolean, hasNews: Boolean, animate: Boolean, mood: LampMood, onClick: () -> Unit) {
    val label = stringResource(if (open) Res.string.helper_close else Res.string.helper_open)
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClickLabel = label, onClick = onClick)
            .testTag("helper.lamp"),
        contentAlignment = Alignment.Center,
    ) {
        LampMascot(size = 44.dp, mood = mood, animate = animate)
        if (hasNews && !open) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .testTag("helper.badge"),
            )
        }
    }
}

@Composable
private fun HelperBubble(
    state: HelperState,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
    resolver: IntentResolver,
    animate: Boolean,
) {
    val shortcuts = LocalShortcuts.current
    val tips = remember(shortcuts) { allTips(shortcuts) }
    // Today's tip stays today's once offered: the rotation moved on when it was, so step back one.
    val offeredToday = inputs.settings.lastTipDay == helperDayOf(inputs.nowMillis())
    val todaysIndex = inputs.settings.nextTipIndex - if (offeredToday) 1 else 0
    val tip = tipAt(tips, todaysIndex + state.tipOffset)
    Surface(
        modifier = Modifier.width(360.dp).heightIn(max = 520.dp).testTag("helper.bubble"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            BubbleHeader(
                animate = animate,
                // Asked first, in the bubble: the answer says where to get the lamp back.
                onHide = { state.reply = HelperReply.ConfirmHide },
                onClose = state::close,
            )
            HorizontalDivider()
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReplyBody(state, inputs, executor, tip)
            }
            HorizontalDivider()
            RequestField(state, inputs, executor, resolver)
        }
    }
}

@Composable
private fun BubbleHeader(animate: Boolean, onHide: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LampMascot(size = 28.dp, animate = animate)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.helper_name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.VisibilityOff),
            text = stringResource(Res.string.helper_hide),
            onClick = onHide,
            modifier = Modifier.testTag("helper.hide"),
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Close),
            text = stringResource(Res.string.helper_close_bubble),
            onClick = onClose,
            modifier = Modifier.testTag("helper.close"),
        )
    }
}

@Composable
private fun RequestField(
    state: HelperState,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
    resolver: IntentResolver,
) {
    val scope = rememberCoroutineScope()
    val submit = {
        val text = state.input
        if (text.isNotBlank()) {
            scope.launch {
                state.onResolved(resolver.resolve(text, inputs.context), executor)
                state.input = ""
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.input,
            onValueChange = { state.input = it },
            placeholder = { Text(stringResource(Res.string.helper_input_placeholder)) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("helper.input")
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            submit()
                            true
                        }
                        event.key == Key.Escape -> {
                            state.close()
                            true
                        }
                        else -> false
                    }
                },
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.Send),
            text = stringResource(Res.string.helper_send),
            onClick = { submit() },
            modifier = Modifier.testTag("helper.send"),
        )
    }
}
