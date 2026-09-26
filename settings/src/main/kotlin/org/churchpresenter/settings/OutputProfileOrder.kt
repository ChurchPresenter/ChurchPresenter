package org.churchpresenter.settings

/**
 * Moving profiles within [ProjectionSettings.outputProfiles], whose order *is* the order every
 * profile list in the app shows -- the Profiles tab, the output pickers, the live preview's menu.
 *
 * The list is already stored in order, so reordering is only ever a rearrangement of it; nothing
 * else records a position.
 */

/**
 * [this] with the profile [id] moved to sit at [toIndex] of the list as it will be after the move,
 * clamped to the list. No-op when [id] names no profile.
 */
fun ProjectionSettings.moveOutputProfile(id: String, toIndex: Int): ProjectionSettings {
    val from = outputProfiles.indexOfFirst { it.id == id }
    if (from < 0) return this
    val rest = outputProfiles.toMutableList()
    val moved = rest.removeAt(from)
    rest.add(toIndex.coerceIn(0, rest.size), moved)
    return copy(outputProfiles = rest)
}

/** [this] with the profile [id] moved [delta] places -- negative is up the list. */
fun ProjectionSettings.moveOutputProfileBy(id: String, delta: Int): ProjectionSettings {
    val from = outputProfiles.indexOfFirst { it.id == id }
    if (from < 0 || delta == 0) return this
    return moveOutputProfile(id, from + delta)
}
