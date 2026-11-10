package org.churchpresenter.signage.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The version of the signage protocol this build speaks. A player states the range it speaks in its
 * [PlayerMessage.Hello]; ChurchPresenter refuses a player whose range does not include its own.
 *
 * Bump it for any change an older peer would misread. Adding an optional field with a default is not
 * such a change: both sides ignore keys they do not know.
 */
const val PROTOCOL_VERSION = 1

/**
 * The UDP port a player listens on for a [Nudge]: ChurchPresenter's knock on a paired screen's last
 * address, so the screen connects at once instead of waiting out its retry pause or finding it by
 * mDNS, which Wi-Fi often drops.
 */
const val NUDGE_PORT = 47640

/**
 * "This ChurchPresenter is up at [port]", sent over UDP to [NUDGE_PORT] at a paired screen's last
 * address while the screen is away. [owner] is [nudgeOwner] of the sender's owner id: a screen
 * acts only on its own owner's nudge, and even then only by connecting, where its token is checked.
 *
 * [player], when set, is [nudgePlayer] of an unpaired player's id: "come and pair here", sent when
 * someone presses Pair here on a screen this ChurchPresenter removed. Only an unpaired player whose
 * id it names acts on it, by connecting there, where pairing still needs approval with its code.
 */
@Serializable
data class Nudge(val owner: String, val port: Int, val player: String? = null)

/** An owner id as a [Nudge] carries it: enough to recognise, not the id itself. */
fun nudgeOwner(ownerId: String): String = sha256Hex(ownerId.toByteArray()).take(NUDGE_OWNER_CHARS)

/** A player id as a [Nudge] carries it. */
fun nudgePlayer(playerId: String): String = nudgeOwner(playerId)

private const val NUDGE_OWNER_CHARS = 16

/** The one JSON configuration for everything on the wire and on the player's disk. */
val SignageJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
