package org.churchpresenter.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.action_atem_key
import org.churchpresenter.strings.generated.resources.action_atem_macro
import org.churchpresenter.strings.generated.resources.action_clear
import org.churchpresenter.strings.generated.resources.action_clear_all
import org.churchpresenter.strings.generated.resources.action_clear_group
import org.churchpresenter.strings.generated.resources.action_companion
import org.churchpresenter.strings.generated.resources.action_go_live
import org.churchpresenter.strings.generated.resources.action_lower_third
import org.churchpresenter.strings.generated.resources.action_macro
import org.churchpresenter.strings.generated.resources.action_media
import org.churchpresenter.strings.generated.resources.action_message
import org.churchpresenter.strings.generated.resources.action_next
import org.churchpresenter.strings.generated.resources.action_obs_scene
import org.churchpresenter.strings.generated.resources.action_preview
import org.churchpresenter.strings.generated.resources.action_previous
import org.churchpresenter.strings.generated.resources.action_prop
import org.churchpresenter.strings.generated.resources.action_take
import org.churchpresenter.strings.generated.resources.action_timer
import org.churchpresenter.strings.generated.resources.action_unknown
import org.churchpresenter.strings.generated.resources.action_wait
import org.churchpresenter.strings.generated.resources.action_wait_seconds
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A saved message the editor offers, with the blanks its words leave to fill in. */
data class MessageChoice(val name: String, val tokens: List<String> = emptyList())

/** A Companion Satellite connection the editor offers: its id, which the action keeps, and its name. */
data class CompanionChoice(val id: String, val name: String)

/**
 * What the row-action editor offers to pick from: the app's saved clear groups, messages, props
 * and lower thirds, and what OBS and Companion have. The app provides it through
 * [LocalActionChoices]; [onOpen] is called when the editor opens, to ask OBS for its scenes.
 */
data class ActionChoices(
    val clearGroups: List<String> = emptyList(),
    val messages: List<MessageChoice> = emptyList(),
    val props: List<String> = emptyList(),
    val lowerThirds: List<String> = emptyList(),
    val obsScenes: List<String> = emptyList(),
    val companion: List<CompanionChoice> = emptyList(),
    val macros: List<String> = emptyList(),
    val onOpen: () -> Unit = {},
)

/** The choices the app offers the row-action editor -- none outside the app. */
val LocalActionChoices = staticCompositionLocalOf { ActionChoices() }

/**
 * Whether a row's cue actions are offered -- its Actions button and chip. They are dev mode only
 * (AGENT.md), so off unless the app says otherwise; what a row holds stays in its file either way.
 */
val LocalShowControlEnabled = staticCompositionLocalOf { false }

/** Each kind of action the editor can add, in the order its menu offers them. */
internal enum class ActionKind(val label: StringResource) {
    OBS_SCENE(Res.string.action_obs_scene),
    ATEM_KEY(Res.string.action_atem_key),
    ATEM_MACRO(Res.string.action_atem_macro),
    COMPANION(Res.string.action_companion),
    LOWER_THIRD(Res.string.action_lower_third),
    MESSAGE(Res.string.action_message),
    PROP(Res.string.action_prop),
    TIMER(Res.string.action_timer),
    MEDIA(Res.string.action_media),
    CLEAR(Res.string.action_clear),
    CLEAR_GROUP(Res.string.action_clear_group),
    CLEAR_ALL(Res.string.action_clear_all),
    GO_LIVE(Res.string.action_go_live),
    PREVIEW(Res.string.action_preview),
    TAKE(Res.string.action_take),
    NEXT(Res.string.action_next),
    PREVIOUS(Res.string.action_previous),
    WAIT(Res.string.action_wait),
    MACRO(Res.string.action_macro),
}

/** A new action of [kind], aimed at the first thing [choices] and [rows] offer for it. */
@Suppress("CyclomaticComplexMethod") // One branch per kind of action, by design.
internal fun newAction(kind: ActionKind, choices: ActionChoices, rows: List<ScheduleItem>): Action = when (kind) {
    ActionKind.OBS_SCENE -> Action.ObsScene(choices.obsScenes.firstOrNull().orEmpty())
    ActionKind.ATEM_KEY -> Action.AtemKey(downstream = true)
    ActionKind.ATEM_MACRO -> Action.AtemMacro(0)
    ActionKind.COMPANION -> Action.CompanionPress(choices.companion.firstOrNull()?.id.orEmpty(), button = 0)
    ActionKind.LOWER_THIRD -> Action.LowerThird(choices.lowerThirds.firstOrNull().orEmpty())
    ActionKind.MESSAGE -> Action.Message(template = choices.messages.firstOrNull()?.name.orEmpty())
    ActionKind.PROP -> Action.Prop(choices.props.firstOrNull().orEmpty(), on = true)
    ActionKind.TIMER -> Action.Timer(TimerModes.DURATION, seconds = DEFAULT_TIMER_SECONDS)
    ActionKind.MEDIA -> Action.Media(MediaCommand.PLAY)
    ActionKind.CLEAR -> Action.Clear(ROW_ACTION_LAYERS.first())
    ActionKind.CLEAR_GROUP -> Action.ClearGroup(choices.clearGroups.firstOrNull().orEmpty())
    ActionKind.CLEAR_ALL -> Action.ClearAll
    ActionKind.GO_LIVE -> Action.GoLive(rowId = rows.firstOrNull { it.isContent() }?.id.orEmpty())
    ActionKind.PREVIEW -> Action.ToPreview(rowId = rows.firstOrNull { it.isContent() }?.id.orEmpty())
    ActionKind.TAKE -> Action.Take()
    ActionKind.NEXT -> Action.NextItem
    ActionKind.PREVIOUS -> Action.PreviousItem
    ActionKind.WAIT -> Action.Wait(1.0)
    ActionKind.MACRO -> Action.RunMacro(choices.macros.firstOrNull().orEmpty())
}

/** The kind of [this], or null for one from a newer build. */
@Suppress("CyclomaticComplexMethod") // One branch per action, by design.
internal val Action.kind: ActionKind?
    get() = when (this) {
        is Action.ObsScene -> ActionKind.OBS_SCENE
        is Action.AtemKey -> ActionKind.ATEM_KEY
        is Action.AtemMacro -> ActionKind.ATEM_MACRO
        is Action.CompanionPress -> ActionKind.COMPANION
        is Action.LowerThird -> ActionKind.LOWER_THIRD
        is Action.Message -> ActionKind.MESSAGE
        is Action.Prop -> ActionKind.PROP
        is Action.Timer -> ActionKind.TIMER
        is Action.Media -> ActionKind.MEDIA
        is Action.Clear -> ActionKind.CLEAR
        is Action.ClearGroup -> ActionKind.CLEAR_GROUP
        Action.ClearAll -> ActionKind.CLEAR_ALL
        is Action.GoLive -> ActionKind.GO_LIVE
        is Action.ToPreview -> ActionKind.PREVIEW
        is Action.Take -> ActionKind.TAKE
        Action.NextItem -> ActionKind.NEXT
        Action.PreviousItem -> ActionKind.PREVIOUS
        is Action.Wait -> ActionKind.WAIT
        is Action.RunMacro -> ActionKind.MACRO
        is Action.Unknown -> null
    }

/** One line saying what [action] does, for the chip under a row: its kind, then what it names. */
@Composable
internal fun actionSummary(action: Action, rows: List<ScheduleItem>): String {
    val kind = stringResource(action.kind?.label ?: Res.string.action_unknown)
    val detail = when (action) {
        is Action.ObsScene -> action.scene
        is Action.LowerThird -> action.preset
        is Action.Message -> action.template.ifBlank { action.text }
        is Action.Prop -> action.prop
        is Action.ClearGroup -> action.group
        is Action.Clear -> layerLabel(action.layer)
        is Action.GoLive -> rows.firstOrNull { it.id == action.rowId }?.displayText.orEmpty()
        is Action.ToPreview -> rows.firstOrNull { it.id == action.rowId }?.displayText.orEmpty()
        is Action.Wait -> stringResource(Res.string.action_wait_seconds, action.seconds.secondsText())
        is Action.AtemMacro -> (action.index + 1).toString()
        is Action.RunMacro -> action.name
        else -> ""
    }
    return if (detail.isBlank()) kind else "$kind: $detail"
}

/** [this] seconds as typed: whole seconds without a point. */
internal fun Double.secondsText(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

/** Whether [this] is a row with something to put on screen. */
internal fun ScheduleItem.isContent(): Boolean =
    this !is ScheduleItem.LabelItem && this !is ScheduleItem.CueItem

/** The layers an action can clear, as the live show names them, bottom to top. */
internal val ROW_ACTION_LAYERS = listOf("MEDIA", "SLIDE", "CAPTIONS", "GRAPHICS", "PROPS", "ANNOUNCEMENTS", "MESSAGES")

private const val DEFAULT_TIMER_SECONDS = 300
