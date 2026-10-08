package org.churchpresenter.helper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.intent.IntentResolver
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.resolve
import org.churchpresenter.helper.suggest.Suggestion
import org.churchpresenter.helper.suggest.Tip
import org.churchpresenter.helper.suggest.allTips
import org.churchpresenter.helper.suggest.tipAt
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.helperDayOf
import org.churchpresenter.settings.tipDue
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_clear
import org.churchpresenter.strings.generated.resources.helper_close_bubble
import org.churchpresenter.strings.generated.resources.helper_hide
import org.churchpresenter.strings.generated.resources.helper_input_placeholder
import org.churchpresenter.strings.generated.resources.helper_more
import org.churchpresenter.strings.generated.resources.helper_name
import org.churchpresenter.strings.generated.resources.helper_send
import org.churchpresenter.strings.generated.resources.helper_status
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.semantic
import org.churchpresenter.theme.sunken
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
 * The helper in the window's corner: the lamp, a teaser and a count when it has something to say,
 * and the panel it opens. Never opens by itself, and holds still while anything is live.
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
    val tipWaiting = idleLongEnough && inputs.settings.tipsEnabled && inputs.settings.tipDue(inputs.nowMillis())
    val waiting = if (inputs.anythingLive) 0 else inputs.suggestions.size + if (tipWaiting) 1 else 0
    val tip = todaysTip(state, inputs)

    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            HelperPanel(state, inputs, executor, resolver, tip, animate)
        }
        val teaser = inputs.suggestions.firstOrNull()?.text ?: tip?.text?.takeIf { tipWaiting }
        when {
            state.isOpen -> Unit
            state.introPointer -> IntroPointer(
                onOpen = {
                    state.introPointer = false
                    state.isOpen = true
                },
                onReplay = {
                    state.introPointer = false
                    state.replayIntro = true
                },
            )
            waiting > 0 && teaser != null -> Teaser(teaser, onOpen = { state.isOpen = true })
        }
        Spacer(Modifier.size(12.dp))
        Launcher(
            open = state.isOpen,
            waiting = waiting,
            animate = animate && !inputs.anythingLive,
            mood = moodFor(state.reply),
            onClick = {
                state.introPointer = false
                if (state.isOpen) state.close() else state.isOpen = true
            },
        )
    }
}

/** Today's tip, moved on by however many times the operator stepped through the tips. */
@Composable
private fun todaysTip(state: HelperState, inputs: HelperInputs): Tip? {
    val shortcuts = LocalShortcuts.current
    // Shuffled once per run, so each day's tip is a random one while Previous and Next still step
    // back and forth through the same order.
    val tips = remember(shortcuts) { allTips(shortcuts).shuffled() }
    // Today's tip stays today's once offered: the rotation moved on when it was, so step back one.
    val offeredToday = inputs.settings.lastTipDay == helperDayOf(inputs.nowMillis())
    val todaysIndex = inputs.settings.nextTipIndex - if (offeredToday) 1 else 0
    return tipAt(tips, todaysIndex + state.tipOffset)
}

private fun moodFor(reply: HelperReply): LampMood = when (reply) {
    is HelperReply.Unknown -> LampMood.CONFUSED
    is HelperReply.Confirm, is HelperReply.Clarify -> LampMood.THINKING
    is HelperReply.Message -> if (reply.canUndo) LampMood.HAPPY else LampMood.IDLE
    else -> LampMood.IDLE
}

@Composable
private fun HelperPanel(
    state: HelperState,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
    resolver: IntentResolver,
    tip: Tip?,
    animate: Boolean,
) {
    val scope = rememberCoroutineScope()
    // [shown] is what goes in the conversation; [request] what the rules read — the same when typed,
    // a chip's translated label and its English request when picked.
    val ask: Ask = { shown, request ->
        if (request.isNotBlank()) {
            scope.launch {
                val resolution = resolver.resolve(request, inputs.context)
                state.answer(shown)
                state.onResolved(resolution, executor)
            }
        }
    }
    val scroll = rememberScrollState()
    // The newest line is at the bottom: keep it in view as the conversation grows.
    LaunchedEffect(state.thread.entries.size, state.reply, state.confirmingHide) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    val panelShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .width(352.dp)
            .heightIn(max = 478.dp)
            .floating(panelShape)
            .testTag("helper.bubble"),
        shape = panelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = LINE_ALPHA)),
    ) {
        Column {
            PanelHeader(
                animate = animate,
                canClear = state.thread.entries.isNotEmpty() || state.reply != HelperReply.Idle,
                onClear = state::clear,
                // Asked first, in the conversation: the answer says where to get Wick back.
                onHide = { state.confirmingHide = true },
                onClose = state::close,
            )
            // The conversation scrolls once it outgrows the panel; the bar says so, in the panel's
            // right margin.
            Box(Modifier.weight(1f, fill = false)) {
                Column(
                    Modifier
                        .verticalScroll(scroll)
                        .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThreadLines(state.thread.entries)
                    ReplyBody(state, inputs, executor, tip, ask)
                    if (state.confirmingHide) {
                        HideCard(
                            onCancel = { state.confirmingHide = false },
                            onHide = {
                                state.close()
                                inputs.onSettingsChange(inputs.settings.copy(enabled = false))
                            },
                        )
                    }
                }
                SettingsScrollbar(scroll)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = LINE_ALPHA))
            Composer(state, ask)
        }
    }
}

@Composable
private fun PanelHeader(
    animate: Boolean,
    canClear: Boolean,
    onClear: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 13.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Avatar(animate)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.helper_name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).background(MaterialTheme.semantic.success, CircleShape))
                Text(
                    stringResource(Res.string.helper_status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HeaderMenu(canClear, onClear)
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.VisibilityOff),
            text = stringResource(Res.string.helper_hide),
            onClick = onHide,
            iconSize = 17.dp,
            buttonSize = 30.dp,
            modifier = Modifier.testTag("helper.hide"),
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Close),
            text = stringResource(Res.string.helper_close_bubble),
            onClick = onClose,
            iconSize = 15.dp,
            buttonSize = 30.dp,
            modifier = Modifier.testTag("helper.close"),
        )
    }
}

/** The header's small menu; for now it holds Clear. */
@Composable
private fun HeaderMenu(canClear: Boolean, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.MoreVert),
            text = stringResource(Res.string.helper_more),
            onClick = { expanded = true },
            iconSize = 17.dp,
            buttonSize = 30.dp,
            modifier = Modifier.testTag("helper.menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.helper_clear)) },
                enabled = canClear,
                onClick = {
                    expanded = false
                    onClear()
                },
                modifier = Modifier.testTag("helper.clear"),
            )
        }
    }
}

/** The message box: a rounded field, and a send button that lights up once there is something to send. */
@Composable
private fun Composer(state: HelperState, ask: Ask) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hasText = state.input.isNotBlank()
    val send = {
        val text = state.input
        if (text.isNotBlank()) {
            ask(text, text)
            state.input = ""
        }
    }
    val fieldShape = RoundedCornerShape(22.dp)
    Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .then(
                    if (focused) {
                        Modifier.border(3.dp, colors.primary.copy(alpha = FOCUS_RING_ALPHA), fieldShape)
                    } else {
                        Modifier
                    },
                )
                .sunken(fieldShape, elevationPalette(), rim = if (focused) colors.primary else Color.Unspecified)
                .padding(start = 15.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.input.isEmpty()) {
                    // One line in every language: a long translation ends in "…" rather than wrapping.
                    Text(
                        stringResource(Res.string.helper_input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = state.input,
                    onValueChange = { state.input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("helper.input")
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type != KeyEventType.KeyDown -> false
                                event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                                    send()
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
            }
            val sendLabel = stringResource(Res.string.helper_send)
            val sendModifier = Modifier.size(34.dp).semantics { contentDescription = sendLabel }.testTag("helper.send")
            if (hasText) {
                RaisedButton(
                    onClick = { send() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = sendModifier,
                ) {
                    SendArrow(colors.onPrimary)
                }
            } else {
                // Nothing to send: a flat, quiet circle, the arrow in the muted ink rather than the
                // button's white, which all but vanished on it.
                Box(
                    sendModifier.background(lifted(CARD_LIFT), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SendArrow(colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SendArrow(tint: Color) {
    Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
}
