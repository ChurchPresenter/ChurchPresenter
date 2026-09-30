package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * Several outputs showing one picture between them -- two TVs side by side as one 3840x1080 wall,
 * or two NDI feeds each carrying half of it. Set up per profile (see [OutputProfile.merge]) over the
 * outputs that follow it.
 *
 * Real displays are drawn as one window across all of them; every other kind draws the whole
 * picture in each member and shows only that member's own rectangle of it.
 */

/**
 * One output's place in a merged picture: [output] is its `previewOutputKey` -- `screen:0`, `ndi:1`
 * -- and [x], [y] where its top-left corner sits in the picture. Its size is its own resolution, so
 * it is not stored. A real display's position is its place on the desktop and these are ignored.
 */
@Serializable
data class MergeTile(val output: String, val x: Int = 0, val y: Int = 0)

/** A profile's merged picture: which of its outputs take part, and where each sits. */
@Serializable
data class OutputMerge(val tiles: List<MergeTile> = emptyList())

/** What an output is, as far as merging goes. Only outputs of one kind may be merged together. */
enum class MergeKind { REAL_DISPLAY, DEV_WINDOW, DECKLINK, NDI, OMT, BROWSER_SOURCE }

/** Why a profile's merge cannot be drawn as it stands. */
enum class MergeProblem {
    /** Fewer than two outputs: nothing to merge. */
    TOO_FEW,

    /** Outputs of different kinds -- their frames are produced on different clocks. */
    MIXED_KINDS,

    /** An output in the merge is gone, or no longer follows this profile. */
    NOT_FOLLOWING,

    /** Real displays that do not tile one rectangle on the desktop. */
    NOT_A_RECTANGLE,
}

/** One output as a merge member: what kind it is and the rectangle of the picture it shows. */
data class MergeMember(val output: String, val kind: MergeKind, val width: Int, val height: Int)

/**
 * A profile's merged picture, ready to draw: its size and, for each member by output key, the
 * rectangle of the picture it shows. [host] is the first member -- the one whose live mode the whole
 * picture follows, and for real displays the one whose window spans them all.
 */
data class ResolvedMerge(
    val profileId: String,
    val kind: MergeKind,
    val width: Int,
    val height: Int,
    val tiles: Map<String, DisplayRect>,
    /** Where the picture sits on the desktop -- real displays only, whose window covers it. */
    val desktop: DisplayRect? = null,
) {
    val host: String get() = tiles.keys.first()
}

/** Which list an output key names, and its index in it -- `ndi:1` -> ("ndi", 1). */
fun parseOutputKey(key: String): Pair<String, Int>? {
    val kind = key.substringBefore(':', "")
    val index = key.substringAfter(':', "").toIntOrNull() ?: return null
    return if (kind.isEmpty() || index < 0) null else kind to index
}

/** The assignment an output key names, or null when there is no such output. */
fun ProjectionSettings.assignmentFor(key: String): ScreenAssignment? {
    val (kind, index) = parseOutputKey(key) ?: return null
    val list = when (kind) {
        Constants.PREVIEW_OUTPUT_SCREEN -> screenAssignments
        Constants.PREVIEW_OUTPUT_NDI -> ndiOutputs
        Constants.PREVIEW_OUTPUT_OMT -> omtOutputs
        Constants.PREVIEW_OUTPUT_BROWSER_SOURCE -> browserSourceOutputs
        else -> return null
    }
    return list.getOrNull(index)
}

/**
 * [key] as a merge member: its kind and its own size. A screen slot is a real display when it has
 * one, a DeckLink device when it names one -- sized by [deckLinkSize], which only the app can ask --
 * and otherwise the dev window standing in for a display.
 */
fun ProjectionSettings.mergeMember(key: String, deckLinkSize: (Int) -> Pair<Int, Int>? = { null }): MergeMember? {
    val (kind, _) = parseOutputKey(key) ?: return null
    val a = assignmentFor(key) ?: return null
    return when (kind) {
        Constants.PREVIEW_OUTPUT_NDI -> MergeMember(key, MergeKind.NDI, a.ndiWidth, a.ndiHeight)
        Constants.PREVIEW_OUTPUT_OMT -> MergeMember(key, MergeKind.OMT, a.omtWidth, a.omtHeight)
        Constants.PREVIEW_OUTPUT_BROWSER_SOURCE ->
            MergeMember(key, MergeKind.BROWSER_SOURCE, a.browserSourceWidth, a.browserSourceHeight)
        else -> when {
            a.targetType == Constants.TARGET_TYPE_DECKLINK -> {
                val (w, h) = deckLinkSize(a.targetDisplay) ?: (DEFAULT_WIDTH to DEFAULT_HEIGHT)
                MergeMember(key, MergeKind.DECKLINK, w, h)
            }
            a.targetDisplay >= 0 && a.targetBoundsW > 0 && a.targetBoundsH > 0 ->
                MergeMember(key, MergeKind.REAL_DISPLAY, a.targetBoundsW, a.targetBoundsH)
            else -> MergeMember(key, MergeKind.DEV_WINDOW, a.devWindowWidth, a.devWindowHeight)
        }
    }.takeIf { it.width > 0 && it.height > 0 }
}

private const val DEFAULT_WIDTH = 1920
private const val DEFAULT_HEIGHT = 1080

/**
 * [profile]'s merge worked out against the outputs as they are now: the picture, or why it cannot
 * be drawn. Null picture and null problem together mean the profile merges nothing.
 */
fun ProjectionSettings.resolveMerge(
    profile: OutputProfile,
    deckLinkSize: (Int) -> Pair<Int, Int>? = { null },
): Pair<ResolvedMerge?, MergeProblem?> {
    val merge = profile.merge ?: return null to null
    val tiles = merge.tiles.distinctBy { it.output }
    val members = tiles.map { tile ->
        mergeMember(tile.output, deckLinkSize)?.takeIf { assignmentFor(tile.output)?.activeProfileId == profile.id }
    }
    val placed = if (members.any { it == null }) null else placedRects(tiles, members.filterNotNull())
    val problem = mergeProblem(tiles.size, members, placed)
    if (problem != null || placed == null) return null to problem
    val union = unionOf(placed)
    val relative = tiles.zip(placed).associate { (tile, r) ->
        tile.output to DisplayRect(r.x - union.x, r.y - union.y, r.width, r.height)
    }
    val kind = members.firstNotNullOf { it }.kind
    val desktop = union.takeIf { kind == MergeKind.REAL_DISPLAY }
    return ResolvedMerge(profile.id, kind, union.width, union.height, relative, desktop) to null
}

/**
 * Where each tile sits: a real display where the desktop has it, anything else where the merge
 * placed it, at its own size.
 */
private fun ProjectionSettings.placedRects(tiles: List<MergeTile>, members: List<MergeMember>): List<DisplayRect> =
    tiles.zip(members).map { (tile, m) ->
        val a = assignmentFor(tile.output)
        if (m.kind == MergeKind.REAL_DISPLAY && a != null) {
            DisplayRect(a.targetBoundsX, a.targetBoundsY, a.targetBoundsW, a.targetBoundsH)
        } else {
            DisplayRect(tile.x, tile.y, m.width, m.height)
        }
    }

/** Why [members] -- null where an output is gone or follows another profile -- cannot be drawn. */
private fun mergeProblem(tileCount: Int, members: List<MergeMember?>, placed: List<DisplayRect>?): MergeProblem? {
    val kinds = members.filterNotNull().map { it.kind }.distinct()
    return when {
        tileCount < 2 -> MergeProblem.TOO_FEW
        placed == null -> MergeProblem.NOT_FOLLOWING
        kinds.size > 1 -> MergeProblem.MIXED_KINDS
        kinds.single() == MergeKind.REAL_DISPLAY && spanProblem(placed) != null -> MergeProblem.NOT_A_RECTANGLE
        else -> null
    }
}

/** Every profile's merge that can be drawn, by the output keys taking part in each. */
fun ProjectionSettings.resolvedMerges(deckLinkSize: (Int) -> Pair<Int, Int>? = { null }): Map<String, ResolvedMerge> =
    buildMap {
        outputProfiles.forEach { profile ->
            val merge = resolveMerge(profile, deckLinkSize).first ?: return@forEach
            merge.tiles.keys.forEach { put(it, merge) }
        }
    }

/**
 * The profile whose merged picture the output [key] is part of -- the one it follows, when that
 * profile's merge names it -- or null. What the Projection tab says instead of offering a choice.
 */
fun List<OutputProfile>.mergingProfileOf(key: String, activeProfileId: String?): OutputProfile? =
    firstOrNull { it.id == activeProfileId }?.takeIf { p -> p.merge?.tiles?.any { it.output == key } == true }

/**
 * [tiles] laid out left to right in the order given, each starting where the one before it ends --
 * the merge editor's **Snap side by side**. Sizes come from [sizeOf]; a tile it cannot size keeps
 * its place.
 */
fun sideBySide(tiles: List<MergeTile>, sizeOf: (String) -> Int?): List<MergeTile> {
    var x = 0
    return tiles.map { tile ->
        val width = sizeOf(tile.output) ?: return@map tile
        tile.copy(x = x, y = 0).also { x += width }
    }
}
