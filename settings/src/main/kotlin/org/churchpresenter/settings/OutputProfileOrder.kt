package org.churchpresenter.settings

/**
 * Moving profiles within [ProjectionSettings.outputProfiles], whose order *is* the order every
 * profile list in the app shows -- the Profiles tab, the output pickers, the live preview's menu.
 *
 * The list is already stored in order, so reordering is only ever a rearrangement of it; nothing
 * else records a position. Linked profiles keep the list in blocks (see [inBlocks]): a profile that
 * follows nothing moves together with the profiles linked to it, among the other blocks, and a
 * linked profile moves only among the other profiles linked to the same master.
 */

/**
 * [this] with the profile [id] moved to sit at [toIndex] among its peers as they will be after the
 * move, clamped. Its peers are the other blocks for a profile that follows nothing, and its
 * siblings for a linked one. No-op when [id] names no profile.
 */
fun ProjectionSettings.moveOutputProfile(id: String, toIndex: Int): ProjectionSettings {
    val profile = outputProfiles.find { it.id == id } ?: return this
    val parent = profile.parentId
    return if (parent == null) {
        val blocks = blocks().toMutableList()
        val from = blocks.indexOfFirst { it.first().id == id }
        val moved = blocks.removeAt(from)
        blocks.add(toIndex.coerceIn(0, blocks.size), moved)
        copy(outputProfiles = blocks.flatten())
    } else {
        val siblings = outputProfiles.filter { it.parentId == parent }.toMutableList()
        val moved = siblings.removeAt(siblings.indexOfFirst { it.id == id })
        siblings.add(toIndex.coerceIn(0, siblings.size), moved)
        val block = listOf(outputProfiles.first { it.id == parent }) + siblings
        copy(outputProfiles = blocks().flatMap { if (it.first().id == parent) block else it })
    }
}

/** [this] with the profile [id] moved [delta] places among its peers -- negative is up the list. */
fun ProjectionSettings.moveOutputProfileBy(id: String, delta: Int): ProjectionSettings {
    val profile = outputProfiles.find { it.id == id } ?: return this
    if (delta == 0) return this
    val peers = peersOf(profile)
    return moveOutputProfile(id, peers.indexOfFirst { it.id == id } + delta)
}

/**
 * [this] with [id] dropped into the gap before list row [gap] (0 = above the first row, size = below
 * the last) -- the way a drag lands. The gap is turned into the nearest place among the profile's
 * own peers, so a block is never split and a linked profile never leaves its master.
 */
fun ProjectionSettings.dropOutputProfile(id: String, gap: Int): ProjectionSettings {
    val profile = outputProfiles.find { it.id == id } ?: return this
    val peers = peersOf(profile)
    val above = outputProfiles.take(gap.coerceIn(0, outputProfiles.size))
    // How many peers other than the dragged one sit above the gap: a gap inside another block
    // counts that block's head, so dropping anywhere in a block lands after it.
    val index = peers.count { peer -> peer.id != id && above.any { it.id == peer.id } }
    return moveOutputProfile(id, index)
}

/** The profiles [profile] moves among: the heads of the blocks, or its master's linked profiles. */
fun ProjectionSettings.peersOf(profile: OutputProfile): List<OutputProfile> =
    if (profile.parentId == null) outputProfiles.filter { it.parentId == null }
    else outputProfiles.filter { it.parentId == profile.parentId }

/** The list as blocks: each profile that follows nothing, then the profiles linked to it. */
private fun ProjectionSettings.blocks(): List<List<OutputProfile>> {
    val ordered = outputProfiles.inBlocks()
    val out = mutableListOf<MutableList<OutputProfile>>()
    ordered.forEach { p -> if (p.parentId == null || out.isEmpty()) out += mutableListOf(p) else out.last() += p }
    return out
}
