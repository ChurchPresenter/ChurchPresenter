package org.churchpresenter.atem

import java.io.IOException

// What the switcher reports and what a key cut names: the models AtemClient hands its callers.

/** A single slot in the ATEM media pool (still or clip). */
data class AtemMediaSlot(val index: Int, val name: String, val isUsed: Boolean)

/**
 * Which key a cut is aimed at.
 *
 * The three values are never chosen independently — every caller picks a keyer *kind* and the two
 * indices that go with it, then passes all three to whatever cuts it (`setKeyOnAir`, `cutKey`, and
 * the app's own `validateKeyTarget`). Grouping them names that, and takes `cutKey` under detekt's
 * six-parameter limit without a suppression. Named `AtemKey`, not `AtemKeyTarget`: `:composeApp`'s
 * `LowerThirdAndAtemRoutes.kt` already has a private `AtemKeyTarget` in this same package, which is
 * this plus the request's on/off flag.
 *
 * @param useDsk    true to drive a downstream keyer, false for an upstream keyer
 * @param mixEffect 0-based M/E index; ignored when [useDsk] is true, since DSKs are global
 * @param keyer     0-based keyer index — the DSK index when [useDsk], else the USK on [mixEffect]
 */
data class AtemKey(val useDsk: Boolean, val mixEffect: Int, val keyer: Int)

/**
 * ATEM device state read on connect.
 *
 * @param fps              exact frame rate derived from [videoMode], e.g. 25.0, 29.97, 59.94
 * @param videoMode        human-readable video standard, e.g. "1080p25", "1080p29.97"
 * @param stillSlots       list of still-store slots (from MPfe commands, pool 0)
 * @param clipSlots        list of clip-store slots (from MPCS commands)
 * @param clipMaxFrames    frame capacity per clip bank (from MPSp; empty on pre-8.0 firmware)
 * @param unassignedFrames media pool frames not allocated to any clip bank (from MPSp)
 * @param mixEffectCount   number of M/E buses / program outputs (from _top topology; 0 = unknown)
 * @param keyersPerMe      upstream keyer count per M/E, indexed by M/E (from _MeC)
 * @param downstreamKeyers number of downstream keyers (from _top topology; 0 = unknown)
 */
data class AtemState(
    val fps: Double,
    val videoMode: String,
    val stillSlots: List<AtemMediaSlot>,
    val clipSlots: List<AtemMediaSlot>,
    val clipMaxFrames: List<Int> = emptyList(),
    val unassignedFrames: Int = 0,
    val mixEffectCount: Int = 0,
    val keyersPerMe: List<Int> = emptyList(),
    val downstreamKeyers: Int = 0
)

/**
 * The switcher did not hold up its end of the protocol: it never answered, never acknowledged, or
 * asked to have a packet resent that is no longer buffered.
 *
 * An [IOException] because that is what it is — a UDP conversation with a device on the network
 * that stopped going anywhere, and every caller already treats a failed upload as an I/O failure.
 * Deliberately distinct from the [IllegalArgumentException]s this client throws when the *caller*
 * asked for something the device does not have (a slot outside the media pool, a frame with no
 * pixels): those are a bug on this side of the wire, not a fault on it.
 */
class AtemProtocolException(message: String) : IOException(message)
