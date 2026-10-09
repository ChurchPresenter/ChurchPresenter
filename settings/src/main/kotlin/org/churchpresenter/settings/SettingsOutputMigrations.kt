package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualGrid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// The raw-JSON steps that built output profiles from the old per-screen assignments (10, 12).

/** The preview shape a profile migrated from the old vertical lower-third mode is given. */
private const val PORTRAIT_PREVIEW_WIDTH = 1080

private const val PORTRAIT_PREVIEW_HEIGHT = 1920

/** How many translations the old lower-third band drew when its grid had two cells or fewer. */
private const val LEGACY_BAND_TRANSLATIONS = 2

/**
 * The song number gained a corner it can be pinned to, defaulting to bottom right -- which is
 * where `songNumberPosition` and `songNumberHorizontalAlignment` already put it out of the box.
 *
 * A migration and not just a default, because a corner overrides those two fields. A fresh
 * install sees no difference either way, but an operator who had moved the number -- above the
 * verse, or left of it -- would have found it snapped to the bottom right corner on first
 * launch, with the controls they set it with still reading what they had chosen. So an existing
 * document is pinned to [Constants.NONE] and keeps drawing the number exactly where it was; the
 * corner is offered to them in settings rather than applied to them.
 */
/**
 * Turns each output's override from a whole settings snapshot into the difference it meant.
 *
 * An override used to be a complete copy of the settings, taken when the screen was first
 * customized. Every field the operator never touched sat in it at whatever value it had that
 * day, and beat the document for ever after -- so a setting *added* later arrived at its class
 * default on that screen, and the global one silently did nothing there. That is not a
 * hypothetical: a second language set to blue for the whole install came out white on the one
 * screen that had been customized, and nothing in the interface explained why.
 *
 * Diffing the snapshot against the document recovers what the operator actually chose: a field
 * that matches the document was never a decision, and drops out. What is left is the screen's
 * own, and everything else follows the document again -- including everything added from here
 * on. The keys named in [SONG_GLOBAL_KEYS] and [BIBLE_GLOBAL_KEYS] drop out regardless: a
 * snapshot may carry a library folder the operator has since moved, and no screen should hold
 * one at all.
 */
internal fun migrateSparseOutputOverrides(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"]?.jsonObject ?: return raw
    val assignments = projection["screenAssignments"]?.jsonArray ?: return raw

    fun globalTree(name: String): JsonObject? = root[name]?.jsonObject

    // Each override key, the document section it is a difference from, and what it never keeps.
    val categories = listOf(
        Triple("songOverride", "songSettings", SONG_GLOBAL_KEYS),
        Triple("bibleOverride", "bibleSettings", BIBLE_GLOBAL_KEYS),
        Triple("dictionaryOverride", "dictionarySettings", emptySet()),
        Triple("backgroundOverride", "backgroundSettings", emptySet()),
        Triple("stageMonitorOverride", "stageMonitorSettings", emptySet()),
    )

    fun slimmed(assignment: JsonObject): JsonObject = buildJsonObject {
        assignment.forEach { (key, value) ->
            val category = categories.firstOrNull { it.first == key }
            val snapshot = value as? JsonObject
            if (category == null || snapshot == null) {
                put(key, value)
                return@forEach
            }
            val global = globalTree(category.second)
            if (global == null) {
                put(key, value)
                return@forEach
            }
            val atomic = if (key == "bibleOverride") setOf(BIBLE_STACK_KEY) else emptySet()
            val diff = diffObjects(global, snapshot, atomic)
            put(key, JsonObject(diff.filterKeys { it !in category.third }))
        }
    }

    return buildJsonObject {
        root.forEach { (key, value) -> if (key != "projectionSettings") put(key, value) }
        put(
            "projectionSettings",
            buildJsonObject {
                projection.forEach { (key, value) ->
                    if (key != "screenAssignments") put(key, value)
                }
                put("screenAssignments", JsonArray(assignments.map { slimmed(it.jsonObject) }))
            },
        )
    }.toString()
}

/**
 * Schema version 12. An output no longer carries its own display mode, content selection or
 * style overrides at all -- it only ever *assigns* an [OutputProfile]. This creates one profile
 * per existing output (screen, Browser Source, NDI), seeded from exactly what that output drew
 * before this version -- its own former `displayMode`/`bibleMode`/`songMode`/`show*` fields, and
 * its former sparse override (already normalized to the post-[migrateSparseOutputOverrides]
 * shape) merged onto the global document -- and points that output at its new profile. Nothing
 * any existing output shows changes across this migration; consolidating near-identical profiles
 * afterward is left to the operator, not attempted here, because collapsing two outputs into one
 * profile is only safe when every one of their fields agrees, and getting that wrong would be a
 * silent visual change on upgrade.
 *
 * A brand-new install never runs this -- it has no `settings.json` to migrate -- and instead
 * gets its one factory profile from [ProjectionSettings]'s own default.
 */
/** Each override key and the global section it is a difference from. */
private val outputProfileOverrideCategories = listOf(
    "songOverride" to "songSettings",
    "bibleOverride" to "bibleSettings",
    "backgroundOverride" to "backgroundSettings",
    "stageMonitorOverride" to "stageMonitorSettings",
    "dictionaryOverride" to "dictionarySettings",
)

/** What used to live directly on an assignment and now lives on its profile instead. */
private val outputProfileOwnKeys = setOf(
    "displayMode", "bibleMode", "bibleTranslations", "songMode", "songTranslations",
    "showPictures", "showMedia", "showSubtitles", "showStreaming", "showAnnouncements",
    "showWebsite", "songLookAhead", "showChords", "showQA", "showSTT", "showDictionary",
    "showCanvas", "showFullscreenBackground", "showLowerThirdBackground",
    "showBibleBackground", "showSongsBackground",
)

/**
 * A document old enough to still carry the pre-stack "primary"/"secondary" `bibleMode`
 * shorthand is normalized here rather than relying on the old (now-removed) typed step that
 * used to run after decode: by then an assignment's `bibleMode` field is gone from the type
 * entirely, so this raw step is the last point anything can still read it. See
 * [Constants.SONG_LANG_PRIMARY]/[Constants.SONG_LANG_SECONDARY].
 */
private fun legacyBibleModeFields(assignment: JsonObject): Map<String, JsonElement> =
    when ((assignment["bibleMode"] as? JsonPrimitive)?.contentOrNull) {
        Constants.SONG_LANG_PRIMARY -> mapOf(
            "bibleMode" to JsonPrimitive(Constants.SONG_LANG_BOTH),
            "bibleTranslations" to JsonArray(listOf(JsonPrimitive(0))),
        )
        Constants.SONG_LANG_SECONDARY -> mapOf(
            "bibleMode" to JsonPrimitive(Constants.SONG_LANG_BOTH),
            "bibleTranslations" to JsonArray(listOf(JsonPrimitive(1))),
        )
        else -> emptyMap()
    }

/** [assignment]'s resolved styling and behavior, as a new profile named [label]. */
private fun migratedOutputProfile(assignment: JsonObject, id: String, label: String, root: JsonObject): JsonObject {
    val normalizedBibleFields = legacyBibleModeFields(assignment)
    fun globalTree(name: String): JsonObject = root[name]?.jsonObject ?: JsonObject(emptyMap())
    return buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive("Migrated — $label"))
        val resolved = outputProfileOverrideCategories.associate { (overrideKey, globalKey) ->
            val override = assignment[overrideKey] as? JsonObject
            val global = globalTree(globalKey)
            globalKey to if (override != null && override.isNotEmpty()) mergeObjects(global, override) else global
        }
        val bandTranslations = bandDrawnTranslations(
            assignment + normalizedBibleFields,
            resolved.getValue("bibleSettings"),
            bibleStackSize(globalTree("bibleSettings")),
        )
        outputProfileOwnKeys.forEach { key ->
            val value = if (key == "bibleTranslations" && bandTranslations != null) {
                JsonArray(bandTranslations.map { JsonPrimitive(it) })
            } else {
                normalizedBibleFields[key] ?: assignment[key]
            }
            value?.let { put(key, it) }
        }
        resolved.forEach { (globalKey, tree) -> put(globalKey, tree) }
        // A profile's background copy is only drawn for the surfaces it names as overridden;
        // every other one follows the Background tab. So the surfaces the old override touched
        // are named, and exactly those keep drawing the screen's own.
        val backgroundKeys = (assignment["backgroundOverride"] as? JsonObject)?.keys.orEmpty()
        val overridden = BackgroundSurface.entries.filter { surface ->
            surface.fieldKeys.any { it in backgroundKeys }
        }
        if (overridden.isNotEmpty()) {
            put("backgroundOverrides", JsonArray(overridden.map { JsonPrimitive(it.name) }))
        }
        // Vertical is read off the profile's shape now, not its mode. A document still carrying
        // the old mode is given a portrait shape so its band keeps stacking.
        val displayMode = (assignment["displayMode"] as? JsonPrimitive)?.contentOrNull
        if (displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL) {
            put("previewWidth", JsonPrimitive(PORTRAIT_PREVIEW_WIDTH))
            put("previewHeight", JsonPrimitive(PORTRAIT_PREVIEW_HEIGHT))
        }
    }
}

/**
 * Schema version 12, last. A full-screen Bible arrangement that is a grid becomes top/bottom,
 * which is what it drew.
 *
 * The full screen read `bilingualLayout` as "side by side or not" until profiles, so 2x2, 1x3,
 * 3x1, 1x4 and 4x1 all drew as a plain stack. It honours the grid now; an install upgraded as it
 * stood would see its scripture rearranged. The lower third's own arrangement is left alone -- the
 * band already drew its grids.
 */
internal fun migrateBibleArrangementAsDrawn(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    fun asDrawn(bible: JsonObject): JsonObject {
        val layout = (bible["bilingualLayout"] as? JsonPrimitive)?.contentOrNull
        if (layout !in fullScreenGridLayouts) return bible
        return JsonObject(bible + ("bilingualLayout" to JsonPrimitive(Constants.BILINGUAL_TOP_BOTTOM)))
    }
    var updated = root
    (root["bibleSettings"] as? JsonObject)?.let { updated = JsonObject(updated + ("bibleSettings" to asDrawn(it))) }
    val projection = root["projectionSettings"] as? JsonObject
    val profiles = projection?.get("outputProfiles") as? JsonArray
    if (projection != null && profiles != null) {
        val rewritten = profiles.map { element ->
            val profile = element as? JsonObject ?: return@map element
            val bible = profile["bibleSettings"] as? JsonObject ?: return@map element
            JsonObject(profile + ("bibleSettings" to asDrawn(bible)))
        }
        updated = JsonObject(
            updated + ("projectionSettings" to JsonObject(projection + ("outputProfiles" to JsonArray(rewritten)))),
        )
    }
    return updated.toString()
}

private val fullScreenGridLayouts = setOf(
    Constants.BILINGUAL_GRID_2X2, Constants.BILINGUAL_GRID_1X3, Constants.BILINGUAL_GRID_3X1,
    Constants.BILINGUAL_GRID_1X4, Constants.BILINGUAL_GRID_4X1,
)

/** How many translations the stored Bible stack holds -- the legacy pair counts when the list is empty. */
private fun bibleStackSize(bible: JsonObject): Int {
    val list = (bible["translations"] as? JsonArray)?.size ?: 0
    if (list > 0) return list
    return listOf("primaryBible", "secondaryBible").count {
        (bible[it] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true
    }
}

/**
 * The translations a lower-third output's band actually drew before profiles, when that is fewer
 * than it was set to show -- or null when it drew them all.
 *
 * The old band laid its translations out in `bilingualLayoutLowerThird`'s grid only when that grid
 * had more than two cells (and was not a vertical strip), up to [Constants.MAX_BIBLE_TRANSLATIONS];
 * every other band was the two-translation layout, and drew the first two. Anything past that was
 * dropped without a word. The band draws every visible translation now, so an output upgraded as
 * it stood would suddenly show three or four where it showed two. Pinning the profile to the ones
 * it drew keeps the screen as it was.
 */
private fun bandDrawnTranslations(
    assignment: Map<String, JsonElement>,
    bible: JsonObject,
    stackSize: Int,
): List<Int>? {
    val displayMode = (assignment["displayMode"] as? JsonPrimitive)?.contentOrNull
    val vertical = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
    val lowerThird = vertical || displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
    val bibleOff = (assignment["bibleMode"] as? JsonPrimitive)?.contentOrNull == Constants.SONG_LANG_OFF
    if (!lowerThird || bibleOff) return null
    val chosen = (assignment["bibleTranslations"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        .orEmpty()
    val visible = if (chosen.isEmpty()) (0 until stackSize).toList() else chosen.filter { it in 0 until stackSize }
    val bandLayout = (bible["bilingualLayoutLowerThird"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val (rows, cols) = bilingualGrid(bandLayout)
    val cells = if (!vertical && rows * cols > LEGACY_BAND_TRANSLATIONS) {
        (rows * cols).coerceAtMost(Constants.MAX_BIBLE_TRANSLATIONS)
    } else {
        LEGACY_BAND_TRANSLATIONS
    }
    return if (visible.size > cells) visible.take(cells) else null
}

/** [assignment] with its behavioral fields dropped and [profileId] assigned in their place. */
private fun slimmedToProfileReference(assignment: JsonObject, profileId: String): JsonObject = buildJsonObject {
    assignment.forEach { (key, value) ->
        if (key !in outputProfileOwnKeys && outputProfileOverrideCategories.none { it.first == key }) {
            put(key, value)
        }
    }
    put("activeProfileId", JsonPrimitive(profileId))
}

/**
 * Schema version 12. An output no longer carries its own display mode, content selection or
 * style overrides at all -- it only ever *assigns* an [OutputProfile]. This creates one profile
 * per existing output (screen, Browser Source, NDI), seeded from exactly what that output drew
 * before this version -- its own former `displayMode`/`bibleMode`/`songMode`/`show*` fields, and
 * its former sparse override (already normalized to the post-[migrateSparseOutputOverrides]
 * shape) merged onto the global document -- and points that output at its new profile. Nothing
 * any existing output shows changes across this migration; consolidating near-identical profiles
 * afterward is left to the operator, not attempted here, because collapsing two outputs into one
 * profile is only safe when every one of their fields agrees, and getting that wrong would be a
 * silent visual change on upgrade.
 *
 * A brand-new install never runs this -- it has no `settings.json` to migrate -- and instead
 * gets its one factory profile from [ProjectionSettings]'s own default.
 */
internal fun migrateOutputProfiles(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"]?.jsonObject ?: return raw
    if ("outputProfiles" in projection) return raw

    var profileCounter = 0
    val newProfiles = mutableListOf<JsonObject>()

    fun migrateList(assignments: JsonArray, kindLabel: String, nameKey: String): JsonArray {
        val migrated = assignments.mapIndexed { index, element ->
            val assignment = element.jsonObject
            profileCounter++
            val profileId = "profile$profileCounter"
            val ownName = (assignment[nameKey] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val label = ownName ?: "$kindLabel ${index + 1}"

            newProfiles.add(migratedOutputProfile(assignment, profileId, label, root))
            slimmedToProfileReference(assignment, profileId)
        }
        return JsonArray(migrated)
    }

    val migratedScreens = migrateList(
        (projection["screenAssignments"] as? JsonArray) ?: JsonArray(emptyList()), "Screen", "screenName",
    )
    val migratedBrowser = migrateList(
        (projection["browserSourceOutputs"] as? JsonArray) ?: JsonArray(emptyList()),
        "Browser Source",
        "browserSourceName",
    )
    val migratedNdi = migrateList(
        (projection["ndiOutputs"] as? JsonArray) ?: JsonArray(emptyList()), "NDI Output", "ndiName",
    )

    return buildJsonObject {
        root.forEach { (key, value) -> if (key != "projectionSettings") put(key, value) }
        put(
            "projectionSettings",
            buildJsonObject {
                projection.forEach { (key, value) ->
                    when (key) {
                        "screenAssignments" -> put(key, migratedScreens)
                        "browserSourceOutputs" -> put(key, migratedBrowser)
                        "ndiOutputs" -> put(key, migratedNdi)
                        else -> put(key, value)
                    }
                }
                if ("screenAssignments" !in projection) put("screenAssignments", migratedScreens)
                if ("browserSourceOutputs" !in projection) put("browserSourceOutputs", migratedBrowser)
                if ("ndiOutputs" !in projection) put("ndiOutputs", migratedNdi)
                put("outputProfiles", JsonArray(newProfiles))
            },
        )
    }.toString()
}
