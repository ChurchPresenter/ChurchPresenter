package org.churchpresenter.helper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.describe
import org.churchpresenter.helper.action.optionLabel
import org.churchpresenter.helper.suggest.Suggestion
import org.churchpresenter.helper.suggest.Tip
import org.churchpresenter.settings.dismissing
import org.churchpresenter.settings.snoozing
import org.churchpresenter.settings.tipDue
import org.churchpresenter.settings.tipShown
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_cancel
import org.churchpresenter.strings.generated.resources.helper_confirm_hide
import org.churchpresenter.strings.generated.resources.helper_hide_confirm_button
import org.churchpresenter.strings.generated.resources.helper_dont_show_again
import org.churchpresenter.strings.generated.resources.helper_example_bg
import org.churchpresenter.strings.generated.resources.helper_example_display
import org.churchpresenter.strings.generated.resources.helper_example_verse
import org.churchpresenter.strings.generated.resources.helper_example_where
import org.churchpresenter.strings.generated.resources.helper_greeting
import org.churchpresenter.strings.generated.resources.helper_next_tip
import org.churchpresenter.strings.generated.resources.helper_not_now
import org.churchpresenter.strings.generated.resources.helper_ok
import org.churchpresenter.strings.generated.resources.helper_previous_tip
import org.churchpresenter.strings.generated.resources.helper_shortcut_is
import org.churchpresenter.strings.generated.resources.helper_shortcut_unbound
import org.churchpresenter.strings.generated.resources.helper_show_me
import org.churchpresenter.strings.generated.resources.helper_tip_title
import org.churchpresenter.strings.generated.resources.helper_tips_off
import org.churchpresenter.strings.generated.resources.helper_tour_done
import org.churchpresenter.strings.generated.resources.helper_tour_next
import org.churchpresenter.strings.generated.resources.helper_tour_step
import org.churchpresenter.strings.generated.resources.helper_tour_stop
import org.churchpresenter.strings.generated.resources.helper_undo
import org.churchpresenter.strings.generated.resources.helper_unknown
import org.churchpresenter.strings.generated.resources.helper_yes
import org.jetbrains.compose.resources.stringResource

private const val SNOOZE_MS = 24L * 60L * 60L * 1000L

private val EXAMPLES = listOf(
    Res.string.helper_example_bg,
    Res.string.helper_example_verse,
    Res.string.helper_example_where,
    Res.string.helper_example_display,
)

/** The bubble's middle: whatever the conversation is at. */
@Composable
internal fun ReplyBody(state: HelperState, inputs: HelperInputs, executor: HelperActionExecutor, tip: Tip?) {
    when (val reply = state.reply) {
        HelperReply.Idle -> IdleBody(state, inputs, executor, tip)
        is HelperReply.Confirm -> ConfirmCard(
            text = reply.action.describe(state.undoLabel),
            action = reply.action,
            onConfirm = { state.confirm(executor) },
            onCancel = state::reset,
        )
        is HelperReply.Clarify -> {
            Said(reply.question)
            ChipRow(reply.options.map { it.optionLabel() to { state.request(it, executor) } })
        }
        is HelperReply.Message -> MessageBody(state, reply, executor)
        is HelperReply.Shortcut -> ShortcutBody(reply.action, onOk = state::reset)
        HelperReply.Unknown -> UnknownBody(state)
        is HelperReply.Touring -> TourBody(state, reply, executor)
        HelperReply.DisplaySetup -> DisplaySetupPanel(state, inputs.screens, executor)
        HelperReply.ConfirmHide -> {
            Said(HelperText.Res(Res.string.helper_confirm_hide), Modifier.testTag("helper.confirmHide"))
            Actions(
                Res.string.helper_cancel to state::reset,
                primary = Res.string.helper_hide_confirm_button to {
                    state.close()
                    inputs.onSettingsChange(inputs.settings.copy(enabled = false))
                },
            )
        }
    }
}

@Composable
private fun IdleBody(state: HelperState, inputs: HelperInputs, executor: HelperActionExecutor, tip: Tip?) {
    val suggestion = inputs.suggestions.firstOrNull()
    if (suggestion != null) {
        SuggestionCard(suggestion, inputs, onAct = { state.request(suggestion.action, executor) })
        return
    }
    if (tip == null || !inputs.settings.tipsEnabled) {
        Said(HelperText.Res(Res.string.helper_greeting))
        ExampleChips(state)
        return
    }
    // Opening the bubble is what counts as the day's tip having been offered.
    LaunchedEffect(Unit) {
        val now = inputs.nowMillis()
        if (inputs.settings.tipDue(now)) inputs.onSettingsChange(inputs.settings.tipShown(now))
    }
    BubbleHeading(Res.string.helper_tip_title)
    Said(tip.text)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { inputs.onSettingsChange(inputs.settings.copy(tipsEnabled = false)) }) {
            Text(stringResource(Res.string.helper_tips_off))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { state.tipOffset-- }) { Text(stringResource(Res.string.helper_previous_tip)) }
        TextButton(onClick = { state.tipOffset++ }) { Text(stringResource(Res.string.helper_next_tip)) }
        tip.action?.let { action ->
            FilledTonalButton(onClick = { state.request(action, executor) }) {
                Text(stringResource(Res.string.helper_show_me))
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: Suggestion, inputs: HelperInputs, onAct: () -> Unit) {
    Said(suggestion.text, Modifier.testTag("helper.suggestion"))
    val settings = inputs.settings
    Actions(
        Res.string.helper_dont_show_again to { inputs.onSettingsChange(settings.dismissing(suggestion.id)) },
        Res.string.helper_not_now to {
            inputs.onSettingsChange(settings.snoozing(suggestion.id, inputs.nowMillis() + SNOOZE_MS))
        },
        primary = Res.string.helper_show_me to onAct,
    )
}

@Composable
private fun MessageBody(state: HelperState, reply: HelperReply.Message, executor: HelperActionExecutor) {
    Said(reply.text, Modifier.testTag("helper.message"))
    val offer = reply.offer
    when {
        offer != null -> Actions(
            Res.string.helper_cancel to state::reset,
            primary = Res.string.helper_yes to { state.request(offer, executor) },
        )
        reply.canUndo -> Actions(Res.string.helper_undo to state::undo, primary = Res.string.helper_ok to state::reset)
        else -> Actions(primary = Res.string.helper_ok to state::reset)
    }
}

@Composable
private fun ShortcutBody(action: ShortcutAction, onOk: () -> Unit) {
    val chord = LocalShortcuts.current.chordsFor(action).firstOrNull()
    val what = stringResource(action.descriptionRes)
    Text(
        if (chord != null) {
            stringResource(Res.string.helper_shortcut_is, what, chord.label())
        } else {
            stringResource(Res.string.helper_shortcut_unbound, what)
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("helper.shortcut"),
    )
    Actions(primary = Res.string.helper_ok to onOk)
}

@Composable
private fun UnknownBody(state: HelperState) {
    Said(HelperText.Res(Res.string.helper_unknown))
    ExampleChips(state)
}

/** Example requests; picking one puts it in the field, ready to send or change. */
@Composable
private fun ExampleChips(state: HelperState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EXAMPLES.forEach { res ->
            val text = stringResource(res)
            AssistChip(onClick = { state.input = text }, label = { Text(text) })
        }
    }
}

@Composable
private fun TourBody(state: HelperState, reply: HelperReply.Touring, executor: HelperActionExecutor) {
    val step = reply.tour.steps[reply.index]
    // Pressing the ringed control moves the tour on, as if Next had been clicked.
    val atStart = remember(reply) { state.session.activePresses }
    val presses = state.session.activePresses
    LaunchedEffect(presses) {
        if (presses > atStart) state.nextStep(executor)
    }
    if (reply.tour.steps.size > 1) {
        Text(
            stringResource(Res.string.helper_tour_step, reply.index + 1, reply.tour.steps.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Said(step.hint, Modifier.testTag("helper.tourHint"))
    val last = reply.index == reply.tour.steps.lastIndex
    val onward = if (last) Res.string.helper_tour_done else Res.string.helper_tour_next
    Actions(Res.string.helper_tour_stop to state::reset, primary = onward to { state.nextStep(executor) })
}
