package org.churchpresenter.controlin

import kotlinx.serialization.Serializable

/** The kinds of thing a [Trigger] or a [ControlEvent] can be, stored by these names. */
object TriggerKinds {
    const val MIDI_NOTE = "midiNote"
    const val MIDI_CC = "midiCc"
    const val MSC = "msc"
    const val OSC = "osc"
}

/** "Any" for the fields of a [Trigger] that can match anything. */
const val ANY = -1

/**
 * Something that arrived on a port: a MIDI note on, a control change, a MIDI Show Control command
 * or an OSC message. Flat on purpose, so the same shape serves every kind.
 *
 * MIDI [channel] counts from 1. For MSC, [address] is the command (`GO`, `STOP`...) and [cue] the
 * cue number. For OSC, [address] is the path, and [value] the first number argument, if any.
 */
data class ControlEvent(
    val kind: String,
    val channel: Int = 1,
    val number: Int = 0,
    val value: Int = ANY,
    val address: String = "",
    val cue: String = "",
)

/**
 * What a saved mapping listens for. [kind] is a [TriggerKinds] name -- a kind from a newer build
 * simply never matches. MIDI [channel] 0 and a [value] of [ANY] match anything; a blank [cue]
 * matches every cue.
 */
@Serializable
data class Trigger(
    val kind: String,
    val channel: Int = 1,
    val number: Int = 0,
    val value: Int = ANY,
    val address: String = "",
    val cue: String = "",
) {
    /** Whether [event] is what this trigger waits for. */
    fun matches(event: ControlEvent): Boolean = kind == event.kind && when (kind) {
        TriggerKinds.MIDI_NOTE -> channelMatches(event) && number == event.number
        TriggerKinds.MIDI_CC -> channelMatches(event) && number == event.number &&
            (value == ANY || value == event.value)
        TriggerKinds.MSC -> address.equals(event.address, ignoreCase = true) &&
            (cue.isBlank() || cue == event.cue)
        TriggerKinds.OSC -> address == event.address && (value == ANY || value == event.value)
        else -> false
    }

    private fun channelMatches(event: ControlEvent) = channel == 0 || channel == event.channel
}

/**
 * The trigger that [this] event would be saved as: a control change or an OSC message matches any
 * value, so learning a fader learns the fader and not the position it was in.
 */
fun ControlEvent.toTrigger(): Trigger = when (kind) {
    TriggerKinds.MIDI_NOTE -> Trigger(kind, channel, number)
    TriggerKinds.MIDI_CC -> Trigger(kind, channel, number)
    TriggerKinds.MSC -> Trigger(kind, address = address, cue = cue)
    else -> Trigger(kind, address = address)
}
