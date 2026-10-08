package org.churchpresenter.helper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.DesktopAccessDisabled
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.ThreadEntry
import org.churchpresenter.helper.nextStep
import org.churchpresenter.helper.action.describe
import org.churchpresenter.helper.action.optionLabel
import org.churchpresenter.helper.resolve
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.helper.suggest.Suggestion
import org.churchpresenter.helper.suggest.SuggestionIds
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
import org.churchpresenter.strings.generated.resources.helper_do_it
import org.churchpresenter.strings.generated.resources.helper_dont_show_again
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val SNOOZE_MS = 24L * 60L * 60L * 1000L

/** The id a kept tip is filed under in the conversation, beside the suggestion ids. */
private const val TIP_ABOUT = "tip"

/** Asks [request] as if typed, showing [shown] in the conversation as what the operator said. */
internal typealias Ask = (shown: String, request: String) -> Unit

/** The icon on a suggested request's chip. */
private fun requestIcon(request: SuggestedRequest): ImageVector = when (request) {
    SuggestedRequest.BACKGROUND -> Icons.Filled.Palette
    SuggestedRequest.VERSE, SuggestedRequest.BIBLE_TRANSLATION -> Icons.AutoMirrored.Filled.MenuBook
    SuggestedRequest.NEW_SONG, SuggestedRequest.CHORDS -> Icons.Filled.MusicNote
    SuggestedRequest.PROJECTOR, SuggestedRequest.IDENTIFY -> Icons.Filled.Tv
    SuggestedRequest.TEXT_SIZE -> Icons.Filled.FormatSize
    SuggestedRequest.CLEAR -> Icons.Filled.DesktopAccessDisabled
    SuggestedRequest.NEXT_SLIDE -> Icons.AutoMirrored.Filled.NavigateNext
    SuggestedRequest.SONG_LANGUAGE -> Icons.Filled.Translate
    SuggestedRequest.SCHEDULE -> Icons.Filled.Event
    SuggestedRequest.REMOTE -> Icons.Filled.PhoneAndroid
    SuggestedRequest.UNDO -> Icons.AutoMirrored.Filled.Undo
    SuggestedRequest.SHORTCUTS -> Icons.Filled.Keyboard
    SuggestedRequest.ANNOUNCEMENT -> Icons.Filled.Campaign
    SuggestedRequest.COUNTDOWN -> Icons.Filled.Timer
    SuggestedRequest.CLOCK -> Icons.Filled.AccessTime
    SuggestedRequest.PICTURES -> Icons.Filled.Image
    SuggestedRequest.SLIDESHOW -> Icons.Filled.Slideshow
    SuggestedRequest.PRESENTATION -> Icons.Filled.PictureAsPdf
    SuggestedRequest.VIDEO -> Icons.Filled.Movie
    SuggestedRequest.LOWER_THIRD -> Icons.Filled.Subtitles
    SuggestedRequest.CONVERT -> Icons.Filled.ImportExport
    SuggestedRequest.SONG_LIBRARY -> Icons.Filled.LibraryMusic
    SuggestedRequest.CALENDAR -> Icons.Filled.CalendarMonth
    SuggestedRequest.STAGE_MONITOR -> Icons.Filled.Mic
    SuggestedRequest.LOWER_THIRD_OUTPUT -> Icons.Filled.Cast
    SuggestedRequest.FULL_SCREEN_OUTPUT -> Icons.Filled.Fullscreen
    SuggestedRequest.STAGE_LAYOUT -> Icons.Filled.Dashboard
    SuggestedRequest.SONG_SEARCH, SuggestedRequest.BIBLE_SEARCH -> Icons.Filled.Search
    SuggestedRequest.MULTI_VERSE -> Icons.AutoMirrored.Filled.MenuBook
    SuggestedRequest.FAVORITES -> Icons.Filled.Star
    SuggestedRequest.SONG_BACKGROUND -> Icons.Filled.Wallpaper
    SuggestedRequest.PLANNING_CENTER -> Icons.Filled.CloudDownload
    SuggestedRequest.CCLI -> Icons.Filled.BarChart
    SuggestedRequest.WEBSITE -> Icons.Filled.Language
}

/** The icon on the tag over a suggestion or tip, by what it is about. */
internal fun topicIcon(about: String?): ImageVector = when (about) {
    SuggestionIds.SCHEDULE_EMPTY -> Icons.Filled.Event
    SuggestionIds.SONGS_EMPTY -> Icons.Filled.MusicNote
    SuggestionIds.BIBLE_NONE -> Icons.AutoMirrored.Filled.MenuBook
    SuggestionIds.NO_AUDIENCE -> Icons.Filled.Tv
    SuggestionIds.OUTPUTS_HIDDEN -> Icons.Filled.Visibility
    else -> Icons.Filled.Lightbulb
}

/** The conversation so far, each line as it was said. */
@Composable
internal fun ThreadLines(thread: List<ThreadEntry>) {
    thread.forEach { entry ->
        when (entry) {
            is ThreadEntry.Operator -> Asked(entry.text)
            is ThreadEntry.Wick -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.topic?.let { BubbleHeading(stringResource(it), topicIcon(entry.about)) }
                Said(entry.text)
            }
        }
    }
}

/** The reply still waiting on the operator, at the bottom of the conversation. */
@Composable
internal fun ReplyBody(
    state: HelperState,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
    tip: Tip?,
    ask: Ask,
) {
    when (val reply = state.reply) {
        HelperReply.Idle -> IdleBody(state, inputs, executor, tip, ask)
        is HelperReply.Confirm -> {
            val doIt = stringResource(Res.string.helper_do_it)
            val cancel = stringResource(Res.string.helper_cancel)
            ConfirmCard(
                text = reply.action.describe(state.undoLabel),
                action = reply.action,
                onConfirm = {
                    state.answer(doIt)
                    state.run(reply.action, executor)
                },
                onCancel = { state.answer(cancel) },
            )
        }
        is HelperReply.Clarify -> {
            Said(reply.question)
            val options = reply.options.map { it to it.optionLabel() }
            val labels = options.map { (_, label) -> label.resolve() }
            ChipRow(
                options.mapIndexed { i, (action, label) ->
                    ChipOption(label) {
                        state.answer(labels[i])
                        state.request(action, executor)
                    }
                },
            )
        }
        is HelperReply.Message -> MessageBody(state, reply, executor)
        is HelperReply.Shortcut -> {
            val ok = stringResource(Res.string.helper_ok)
            ShortcutBody(reply.action, onOk = { state.answer(ok) })
        }
        is HelperReply.Unknown -> {
            Said(HelperText.Res(Res.string.helper_unknown))
            RequestChips(reply.closest, ask)
        }
        HelperReply.Greeting -> {
            Said(HelperText.Res(Res.string.helper_greeting))
            RequestChips(SuggestedRequest.DEFAULTS, ask)
        }
        is HelperReply.Touring -> TourBody(state, reply, executor)
        HelperReply.DisplaySetup -> DisplaySetupPanel(state, inputs.screens, executor)
    }
}

@Composable
private fun IdleBody(
    state: HelperState,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
    tip: Tip?,
    ask: Ask,
) {
    val suggestion = inputs.suggestions.firstOrNull()
    if (suggestion != null) {
        SuggestionCard(state, suggestion, inputs, executor)
        return
    }
    // Once there is a conversation, the greeting and the tip have had their turn.
    if (state.thread.entries.isNotEmpty()) return
    if (tip == null || !inputs.settings.tipsEnabled) {
        Said(HelperText.Res(Res.string.helper_greeting))
        RequestChips(SuggestedRequest.DEFAULTS, ask)
        return
    }
    // Opening the bubble is what counts as the day's tip having been offered.
    LaunchedEffect(Unit) {
        val now = inputs.nowMillis()
        if (inputs.settings.tipDue(now)) inputs.onSettingsChange(inputs.settings.tipShown(now))
    }
    BubbleHeading(stringResource(Res.string.helper_tip_title), topicIcon(TIP_ABOUT))
    Said(tip.text)
    val showMe = stringResource(Res.string.helper_show_me)
    Actions(
        Res.string.helper_previous_tip to { state.tipOffset-- },
        Res.string.helper_next_tip to { state.tipOffset++ },
        primary = tip.action?.let { action ->
            Res.string.helper_show_me to {
                state.thread.keep(tip.text, Res.string.helper_tip_title, TIP_ABOUT)
                state.thread.said(showMe)
                state.request(action, executor)
            }
        },
        primaryLeads = true,
    )
    QuietLink(Res.string.helper_tips_off, onClick = {
        inputs.onSettingsChange(inputs.settings.copy(tipsEnabled = false))
    })
}

@Composable
private fun SuggestionCard(
    state: HelperState,
    suggestion: Suggestion,
    inputs: HelperInputs,
    executor: HelperActionExecutor,
) {
    suggestion.topic?.let { BubbleHeading(stringResource(it), topicIcon(suggestion.id)) }
    Said(suggestion.text, Modifier.testTag("helper.suggestion"))
    val settings = inputs.settings
    val showMe = stringResource(Res.string.helper_show_me)
    val notNow = stringResource(Res.string.helper_not_now)
    Actions(
        Res.string.helper_not_now to {
            state.thread.keep(suggestion.text, suggestion.topic, suggestion.id)
            state.thread.said(notNow)
            inputs.onSettingsChange(settings.snoozing(suggestion.id, inputs.nowMillis() + SNOOZE_MS))
        },
        primary = Res.string.helper_show_me to {
            state.thread.keep(suggestion.text, suggestion.topic, suggestion.id)
            state.thread.said(showMe)
            state.request(suggestion.action, executor)
        },
        primaryLeads = true,
    )
    QuietLink(Res.string.helper_dont_show_again, onClick = {
        inputs.onSettingsChange(settings.dismissing(suggestion.id))
    })
}

@Composable
private fun MessageBody(state: HelperState, reply: HelperReply.Message, executor: HelperActionExecutor) {
    Said(reply.text, Modifier.testTag("helper.message"))
    val offer = reply.offer
    val yes = stringResource(Res.string.helper_yes)
    val cancel = stringResource(Res.string.helper_cancel)
    val ok = stringResource(Res.string.helper_ok)
    val undo = stringResource(Res.string.helper_undo)
    val onOk = { state.answer(ok) }
    when {
        offer != null -> Actions(
            Res.string.helper_cancel to { state.answer(cancel) },
            primary = Res.string.helper_yes to {
                state.answer(yes)
                state.request(offer, executor)
            },
        )
        reply.canUndo -> Actions(
            Res.string.helper_undo to {
                state.answer(undo)
                state.undo()
            },
            primary = Res.string.helper_ok to onOk,
        )
        else -> Actions(primary = Res.string.helper_ok to onOk)
    }
}

@Composable
private fun ShortcutBody(action: ShortcutAction, onOk: () -> Unit) {
    val chord = LocalShortcuts.current.chordsFor(action).firstOrNull()
    val what = stringResource(action.descriptionRes)
    val text = if (chord != null) {
        stringResource(Res.string.helper_shortcut_is, what, chord.label())
    } else {
        stringResource(Res.string.helper_shortcut_unbound, what)
    }
    Said(HelperText.Plain(text), Modifier.testTag("helper.shortcut"))
    Actions(primary = Res.string.helper_ok to onOk)
}

/** Requests as chips; picking one asks it, as if it had been typed. */
@Composable
private fun RequestChips(requests: List<SuggestedRequest>, ask: Ask) {
    val labels = requests.map { stringResource(it.label) }
    val options = remember(requests, labels) {
        requests.mapIndexed { i, request ->
            ChipOption(HelperText.Res(request.label), requestIcon(request)) { ask(labels[i], request.request) }
        }
    }
    ChipRow(options)
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
    val onward: StringResource = if (last) Res.string.helper_tour_done else Res.string.helper_tour_next
    val onwardText = stringResource(onward)
    val stop = stringResource(Res.string.helper_tour_stop)
    Actions(
        Res.string.helper_tour_stop to { state.answer(stop) },
        primary = onward to { state.nextStep(executor, said = onwardText) },
    )
}
