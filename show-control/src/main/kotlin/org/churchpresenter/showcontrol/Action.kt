package org.churchpresenter.showcontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes

/**
 * One thing that changes the show (`docs/SHOW_CONTROL.md`, Actions): the vocabulary cue actions,
 * macros, keys, the remote API and MIDI/OSC all speak.
 *
 * Stored by the explicit name each type carries in `@SerialName`, never by class name, so moving
 * this module orphans no one's schedule -- see [ActionSerializer]. A layer is named as the live
 * show spells it (`SLIDE`, `MESSAGES`).
 */
@Serializable(with = ActionSerializer::class)
sealed interface Action {

    /** Puts a schedule row, or an item carried here, on air -- played [plays] times, 0 for a loop. */
    @Serializable
    @SerialName("set")
    data class GoLive(val rowId: String = "", val item: ScheduleItem? = null, val plays: Int = 1) : Action

    /** Cues a schedule row, or an item carried here, on Preview. */
    @Serializable
    @SerialName("preview")
    data class ToPreview(val rowId: String = "", val item: ScheduleItem? = null) : Action

    /** Takes what is cued to air: one [layer], or every layer when it is blank. */
    @Serializable
    @SerialName("take")
    data class Take(val layer: String = "") : Action

    /** Takes one [layer] off air and leaves the rest up. */
    @Serializable
    @SerialName("clear")
    data class Clear(val layer: String) : Action

    /** Takes everything off air, as the Clear button does. */
    @Serializable
    @SerialName("clearAll")
    data object ClearAll : Action

    /** Clears an operator's clear group, named by its id or its name. */
    @Serializable
    @SerialName("clearGroup")
    data class ClearGroup(val group: String) : Action

    /** Puts a message up: [text], or a saved [template] filled with [tokens]. */
    @Serializable
    @SerialName("message")
    data class Message(
        val text: String = "",
        val template: String = "",
        val tokens: Map<String, String> = emptyMap(),
        val durationSeconds: Int? = null,
    ) : Action

    /** Switches a prop, named by its id or its name: on, off, or with [on] null the other way. */
    @Serializable
    @SerialName("prop")
    data class Prop(val prop: String, val on: Boolean? = null) : Action

    /** Runs a lower-third preset, named by its name. */
    @Serializable
    @SerialName("lowerThird")
    data class LowerThird(val preset: String) : Action

    /**
     * Starts an announcement timer: in [TimerModes.DURATION] a countdown of [seconds], in
     * [TimerModes.CLOCK] one to the time of day [until] ("HH:mm"), in [TimerModes.COUNT_UP] a count up.
     */
    @Serializable
    @SerialName("timer")
    data class Timer(val mode: String = TimerModes.DURATION, val seconds: Int = 0, val until: String = "") : Action

    /** Plays, pauses or stops the media that is loaded. */
    @Serializable
    @SerialName("media")
    data class Media(val command: MediaCommand) : Action

    /** Switches OBS's program scene. */
    @Serializable
    @SerialName("obsScene")
    data class ObsScene(val scene: String) : Action

    /** Puts an ATEM keyer on air or takes it off: a downstream key, or an upstream one on a mix effect. */
    @Serializable
    @SerialName("atemKey")
    data class AtemKey(
        val downstream: Boolean = false,
        val mixEffect: Int = 0,
        val keyer: Int = 0,
        val on: Boolean = true,
    ) : Action

    /** Runs the ATEM macro in slot [index], counted from zero as the switcher does. */
    @Serializable
    @SerialName("atemMacro")
    data class AtemMacro(val index: Int) : Action

    /**
     * Presses [button] on a Companion Satellite surface: the one [connection] (its id in the
     * settings) shows at [placement] (`TAB`, `LEFT_SIDEBAR`, `RIGHT_SIDEBAR`), or at any placement
     * when that is blank.
     */
    @Serializable
    @SerialName("companion")
    data class CompanionPress(val connection: String, val button: Int, val placement: String = "") : Action

    /** Goes live with the next schedule row. */
    @Serializable
    @SerialName("next")
    data object NextItem : Action

    /** Goes live with the previous schedule row. */
    @Serializable
    @SerialName("previous")
    data object PreviousItem : Action

    /** Waits before the next action in the list. */
    @Serializable
    @SerialName("wait")
    data class Wait(val seconds: Double) : Action

    /** Runs the macro called [name]. */
    @Serializable
    @SerialName("macro")
    data class RunMacro(val name: String) : Action

    /** An action from a newer build, kept as it was written and skipped. */
    data class Unknown(val json: JsonObject) : Action
}

/** What a [Action.Media] does to the loaded media. */
@Serializable
enum class MediaCommand {
    @SerialName("play") PLAY,

    @SerialName("pause") PAUSE,

    @SerialName("stop") STOP,
}
