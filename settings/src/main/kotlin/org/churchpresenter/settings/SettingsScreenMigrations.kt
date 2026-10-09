package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// The raw-JSON steps for screens, Companion Satellite, the stage monitor, the lower third and
// projection (1, 2, 3, 4, 7, 8, 9).

/** The key the band height is stored under, in all three of its homes. */
private const val LOWER_THIRD_HEIGHT_KEY = "lowerThirdHeightPercent"

/** The three placement-field prefixes used throughout companionSatelliteConnections[] entries
 * (tabRows, leftSidebarRows, rightSidebarRows, etc.) — shared by the migrations below. */
private val CompanionSurfacePlacementPrefixes = listOf("tab", "leftSidebar", "rightSidebar")

/** One screen assignment with showBible/showSongs turned into modes, or null if untouched. */
private fun assignmentWithModes(obj: JsonObject): JsonObject? {
    val showBibleFalse = (obj["showBible"] as? JsonPrimitive)?.content == "false"
    val showSongsFalse = (obj["showSongs"] as? JsonPrimitive)?.content == "false"
    if (!showBibleFalse && !showSongsFalse) return null
    return buildJsonObject {
        obj.forEach { (k, v) -> if (k != "showBible" && k != "showSongs") put(k, v) }
        if (showBibleFalse && !obj.containsKey("bibleMode")) put("bibleMode", JsonPrimitive("off"))
        if (showSongsFalse && !obj.containsKey("songMode")) put("songMode", JsonPrimitive("off"))
    }
}

/** One satellite connection with row/column ranges turned back into counts, or null if untouched. */
private fun connectionWithCounts(obj: JsonObject, rangeKeys: Set<String>): JsonObject? {
    val additions = buildJsonObject {
        for (prefix in CompanionSurfacePlacementPrefixes) {
            val startRow = (obj["${prefix}StartRow"] as? JsonPrimitive)?.content?.toIntOrNull()
            val endRow = (obj["${prefix}EndRow"] as? JsonPrimitive)?.content?.toIntOrNull()
            val startColumn = (obj["${prefix}StartColumn"] as? JsonPrimitive)?.content?.toIntOrNull()
            val endColumn = (obj["${prefix}EndColumn"] as? JsonPrimitive)?.content?.toIntOrNull()
            if (startRow != null && endRow != null && !obj.containsKey("${prefix}Rows")) {
                put("${prefix}Rows", JsonPrimitive((endRow - startRow + 1).coerceAtLeast(1)))
            }
            if (startColumn != null && endColumn != null && !obj.containsKey("${prefix}Columns")) {
                put("${prefix}Columns", JsonPrimitive((endColumn - startColumn + 1).coerceAtLeast(1)))
            }
        }
    }
    // Stray range keys with no rows/columns to derive (shouldn't normally happen) are still
    // stripped, so they don't linger as dead unknown keys forever.
    if (additions.isEmpty() && rangeKeys.none { it in obj }) return null
    return buildJsonObject {
        obj.forEach { (k, v) -> if (k !in rangeKeys) put(k, v) }
        additions.forEach { (k, v) -> put(k, v) }
    }
}


/** Schema version 1. Converts old showBible:false/showSongs:false booleans to
 * bibleMode:"off"/songMode:"off" strings. */
internal fun migrateScreenAssignmentModes(raw: String): String {
    if (!raw.contains("\"showBible\"") && !raw.contains("\"showSongs\"")) return raw
    val root = parseSettingsRoot(raw) ?: return raw
    val proj = root["projectionSettings"]?.jsonObject ?: return raw
    val assignments = proj["screenAssignments"]?.jsonArray ?: return raw
    var changed = false
    val newAssignments = buildJsonArray {
        for (element in assignments) {
            val migrated = assignmentWithModes(element.jsonObject)
            if (migrated != null) changed = true
            add(migrated ?: element)
        }
    }
    if (!changed) return raw
    val newProj = buildJsonObject {
        proj.forEach { (k, v) -> if (k == "screenAssignments") put(k, newAssignments) else put(k, v) }
    }
    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k == "projectionSettings") put(k, newProj) else put(k, v) }
    }
    return newRoot.toString()
}

/** Schema version 3. Renames the old single companionSatelliteConnections[] fields (rows/columns/bitmapSize) to
 * their tab-prefixed placement-specific equivalents, so existing users' configured values
 * survive the placement-per-connection rework instead of silently resetting via
 * ignoreUnknownKeys. TAB is the migration target for all of these since it was the only
 * placement that existed before. (The old single "startPage" field has no equivalent to rename
 * to — per-placement start page was tried and dropped again; see
 * [migrateCompanionSatelliteRowColumnRangeBackToCount].) */
internal fun migrateCompanionSatelliteStartPage(raw: String): String {
    if (!raw.contains("\"companionSatelliteConnections\"")) return raw
    val root = parseSettingsRoot(raw) ?: return raw
    val connections = root["companionSatelliteConnections"]?.jsonArray ?: return raw
    val renames = mapOf("rows" to "tabRows", "columns" to "tabColumns", "bitmapSize" to "tabBitmapSize")
    var changed = false
    val newConnections = buildJsonArray {
        for (element in connections) {
            val obj = element.jsonObject
            val toRename = renames.filterKeys { obj.containsKey(it) && !obj.containsKey(renames.getValue(it)) }
            if (toRename.isNotEmpty()) {
                changed = true
                add(buildJsonObject {
                    obj.forEach { (k, v) -> if (k !in toRename) put(k, v) }
                    toRename.forEach { (oldKey, newKey) -> put(newKey, obj.getValue(oldKey)) }
                })
            } else { add(element) }
        }
    }
    if (!changed) return raw
    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k == "companionSatelliteConnections") put(k, newConnections) else put(k, v) }
    }
    return newRoot.toString()
}

/** Schema version 4. Converts each placement's briefly-introduced start/end row/column RANGE
 * fields back into a
 * plain rows/columns COUNT, so anyone who saved settings while that experiment was live doesn't
 * lose their configured grid size via ignoreUnknownKeys. That start/end scheme (backed by
 * LAYOUT_MANIFEST registration, letting a placement show an arbitrary sub-rectangle of a larger
 * page) worked when probed directly against the protocol, but wasn't respected reliably in
 * practice — Companion already exposes equivalent per-surface start-page/offset configuration
 * of its own (Settings → Surfaces → device), so ChurchPresenter dropped its own version rather
 * than keep two conflicting sources of truth. A startRow=0/endRow=N-1 range becomes plain
 * rows=N — identical count to what was already configured, just without the (unreliable) offset. */
internal fun migrateCompanionSatelliteRowColumnRangeBackToCount(raw: String): String {
    if (!raw.contains("\"companionSatelliteConnections\"")) return raw
    val root = parseSettingsRoot(raw) ?: return raw
    val connections = root["companionSatelliteConnections"]?.jsonArray ?: return raw
    var changed = false
    val rangeKeys = CompanionSurfacePlacementPrefixes.flatMap { prefix ->
        listOf("${prefix}StartRow", "${prefix}EndRow", "${prefix}StartColumn", "${prefix}EndColumn")
    }.toSet()
    val newConnections = buildJsonArray {
        for (element in connections) {
            val migrated = connectionWithCounts(element.jsonObject, rangeKeys)
            if (migrated != null) changed = true
            add(migrated ?: element)
        }
    }
    if (!changed) return raw
    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k == "companionSatelliteConnections") put(k, newConnections) else put(k, v) }
    }
    return newRoot.toString()
}

/** Schema version 7. Moves the global `stageMonitorSettings.showChords` switch onto every
 * output, where it now lives. Only an operator who had switched it off has anything to carry —
 * the per-output field defaults to on, as the old global did. The old key is left in place so a
 * downgrade still finds its switch. */
internal fun migrateStageMonitorChords(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val showChords = root["stageMonitorSettings"]?.jsonObject
        ?.get("showChords")
        ?.let { (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull() }
    val proj = if (showChords == false) root["projectionSettings"]?.jsonObject else null
    if (proj == null) return raw

    fun withChordsOff(key: String): JsonArray? {
        val outputs = proj[key]?.jsonArray ?: return null
        return buildJsonArray {
            for (element in outputs) {
                val obj = element.jsonObject
                if ("showChords" in obj) add(element) else add(buildJsonObject {
                    obj.forEach { (k, v) -> put(k, v) }
                    put("showChords", JsonPrimitive(false))
                })
            }
        }
    }
    val newAssignments = withChordsOff("screenAssignments")
    val newBrowserSources = withChordsOff("browserSourceOutputs")
    if (newAssignments == null && newBrowserSources == null) return raw

    val newProj = buildJsonObject {
        proj.forEach { (k, v) ->
            when (k) {
                "screenAssignments" -> put(k, newAssignments ?: v)
                "browserSourceOutputs" -> put(k, newBrowserSources ?: v)
                else -> put(k, v)
            }
        }
    }
    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k == "projectionSettings") put(k, newProj) else put(k, v) }
    }
    return newRoot.toString()
}

/** Schema version 7. Renames the stage monitor's fixed zone names to the layout slots that
 * replaced them, so a saved screen keeps the arrangement it had. The five positions become the
 * five slots of the CLASSIC layout in the order it draws them, which is the layout every
 * existing document opens with. */
internal fun migrateStageMonitorZoneNames(raw: String): String {
    val stageMonitor = parseSettingsRoot(raw)?.get("stageMonitorSettings")?.jsonObject ?: return raw
    val root = parseSettingsRoot(raw) ?: return raw
    val slots = mapOf(
        "TOP_LEFT" to "A", "TOP_RIGHT" to "B",
        "BOTTOM_LEFT" to "C", "BOTTOM_MIDDLE" to "D", "BOTTOM_RIGHT" to "E",
    )
    val zones = stageMonitor["contentZones"]?.jsonObject
    val styles = stageMonitor["zoneStyles"]?.jsonObject
    if (zones == null && styles == null) return raw

    val newStageMonitor = buildJsonObject {
        stageMonitor.forEach { (k, v) ->
            when (k) {
                // Values name the zone: "BIBLE": "TOP_LEFT".
                "contentZones" -> put(k, buildJsonObject {
                    zones?.forEach { (type, zone) ->
                        val named = (zone as? JsonPrimitive)?.content
                        put(type, JsonPrimitive(slots[named] ?: named ?: ""))
                    }
                })
                // Keys name the zone: "TOP_LEFT": { ...style... }.
                "zoneStyles" -> put(k, buildJsonObject {
                    styles?.forEach { (zone, style) -> put(slots[zone] ?: zone, style) }
                })
                else -> put(k, v)
            }
        }
    }
    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k == "stageMonitorSettings") put(k, newStageMonitor) else put(k, v) }
    }
    return newRoot.toString()
}

/** Schema version 2. Migrates old screen1-4Assignment fields to screenAssignments list. */
/**
 * The lower-third band height moved off [ProjectionSettings] and onto the two content types that
 * actually draw a band -- [BibleSettings] and [SongSettings] -- so a church can give scripture a
 * shallow band and lyrics a deeper one.
 *
 * A migration and not just a default, because the field was *removed*: `ignoreUnknownKeys` would
 * decode an existing document without complaint and drop the operator's number on the floor, and
 * the next save would write the file back without it. Someone who had set 45 would find their
 * band at 33 with nothing anywhere to explain it.
 *
 * Copies into both, and into neither where one already has a value -- a document written by a
 * newer build, opened by an older one and rolled forward again must not have its two values
 * flattened back into the one they replaced. The old key is left where it is: unknown on read,
 * and gone the first time the file is saved.
 */
internal fun migrateLowerThirdHeight(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val height = root["projectionSettings"]?.jsonObject
        ?.get(LOWER_THIRD_HEIGHT_KEY)?.jsonPrimitive?.intOrNull ?: return raw

    fun carried(name: String): JsonObject {
        val section = root[name]?.jsonObject ?: buildJsonObject { }
        if (LOWER_THIRD_HEIGHT_KEY in section) return section
        return buildJsonObject {
            section.forEach { (k, v) -> put(k, v) }
            put(LOWER_THIRD_HEIGHT_KEY, JsonPrimitive(height))
        }
    }

    val carriers = setOf("bibleSettings", "songSettings")
    return buildJsonObject {
        root.forEach { (k, v) -> if (k !in carriers) put(k, v) }
        carriers.forEach { put(it, carried(it)) }
    }.toString()
}


internal fun migrateSongNumberCorner(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val song = root["songSettings"]?.jsonObject ?: return raw
    val keys = listOf("songNumberCorner", "songNumberLowerThirdCorner")
    // Only where the document does not already carry one: a file written by a newer build,
    // opened by an older one and rolled forward again must keep the corner it chose.
    if (keys.all { it in song }) return raw
    val newSong = buildJsonObject {
        song.forEach { (k, v) -> put(k, v) }
        keys.forEach { if (it !in song) put(it, JsonPrimitive(Constants.NONE)) }
    }
    return buildJsonObject {
        root.forEach { (k, v) -> if (k == "songSettings") put(k, newSong) else put(k, v) }
    }.toString()
}

internal fun migrateProjectionSettings(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val proj = root["projectionSettings"]?.jsonObject ?: return raw
    if ("screenAssignments" in proj) return raw // already new format

    val oldKeys = setOf("screen1Assignment", "screen2Assignment",
        "screen3Assignment", "screen4Assignment", "numberOfWindows")
    val assignments = buildJsonArray {
        for (key in listOf("screen1Assignment", "screen2Assignment",
                           "screen3Assignment", "screen4Assignment")) {
            val value = proj[key]
            if (value != null) add(value)
        }
    }
    val newProj = buildJsonObject {
        proj.forEach { (k, v) -> if (k !in oldKeys) put(k, v) }
        put("screenAssignments", assignments)
    }
    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k == "projectionSettings") put(k, newProj) else put(k, v) }
    }
    return newRoot.toString()
}
