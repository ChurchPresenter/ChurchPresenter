package org.churchpresenter.controlin

import kotlinx.serialization.Serializable
import org.churchpresenter.showcontrol.Action

/** What a trigger does: runs [actions] in order, as a macro would. */
@Serializable
data class ControlMapping(
    val id: String,
    val name: String = "",
    val trigger: Trigger,
    val actions: List<Action> = emptyList(),
)

/** The show events a [ControlOutput] can send on, stored by these names. */
object OutputEvents {
    const val GO_LIVE = "goLive"
    const val TAKE = "take"
    const val CLEAR = "clear"
}

/** What to send: a MIDI note or control change, or an OSC message. [kind] is a [TriggerKinds] name. */
@Serializable
data class OutMessage(
    val kind: String = TriggerKinds.MIDI_NOTE,
    val channel: Int = 1,
    val number: Int = 0,
    val value: Int = DEFAULT_VELOCITY,
    val address: String = "",
    /** The OSC argument, sent as an integer, a float or a string, whichever it reads as; blank sends none. */
    val argument: String = "",
)

/** Sends [message] whenever the show event [on] -- an [OutputEvents] name -- happens. */
@Serializable
data class ControlOutput(
    val id: String,
    val on: String = OutputEvents.GO_LIVE,
    val message: OutMessage = OutMessage(),
)

/**
 * The ports and the tables. A blank device name or a port of 0 is off. [mappings] run actions when
 * something arrives; [outputs] send something when the show does.
 */
@Serializable
data class ControlSettings(
    val midiInput: String = "",
    val midiOutput: String = "",
    val oscInPort: Int = 0,
    val oscOutHost: String = "",
    val oscOutPort: Int = 0,
    val mappings: List<ControlMapping> = emptyList(),
    val outputs: List<ControlOutput> = emptyList(),
)

internal const val DEFAULT_VELOCITY = 127
