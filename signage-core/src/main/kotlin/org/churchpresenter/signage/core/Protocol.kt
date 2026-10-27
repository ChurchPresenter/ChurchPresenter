package org.churchpresenter.signage.core

import kotlinx.serialization.json.Json

/**
 * The version of the signage protocol this build speaks. A player states the range it speaks in its
 * [PlayerMessage.Hello]; ChurchPresenter refuses a player whose range does not include its own.
 *
 * Bump it for any change an older peer would misread. Adding an optional field with a default is not
 * such a change: both sides ignore keys they do not know.
 */
const val PROTOCOL_VERSION = 1

/** The one JSON configuration for everything on the wire and on the player's disk. */
val SignageJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
