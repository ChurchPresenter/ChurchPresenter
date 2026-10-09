package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull

// The raw-JSON steps that moved styling into output profiles and looks (14, 15, 21, 22, 23), and
// the helpers that strip what a profile owns from the document.

/**
 * Schema version 15. Fit/Fill/Stretch is set per profile now rather than once for the install,
 * so each profile is given the document's current picture and media scaling and every output
 * keeps drawing exactly what it did. A profile that already carries its own keeps it.
 */
internal fun migrateScaleModesIntoProfiles(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"]?.jsonObject ?: return raw
    val profiles = projection["outputProfiles"]?.jsonArray ?: return raw
    val seeds = listOfNotNull(
        root["pictureSettings"]?.jsonObject?.get("scaleMode")?.let { "pictureScaleMode" to it },
        root["mediaScaleMode"]?.let { "mediaScaleMode" to it },
    )
    if (seeds.isEmpty()) return raw
    val seeded = profiles.map { element ->
        val profile = element as? JsonObject ?: return@map element
        JsonObject(profile + seeds.filter { (key, _) -> key !in profile })
    }
    val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(seeded)))
    return JsonObject(root + ("projectionSettings" to newProjection)).toString()
}

/** The document-level looks [migrateStylingIntoProfiles] hands to every profile. */
private val stylingMovedIntoProfiles = listOf("sttSettings", "dictionarySettings", "qaSettings", "mediaSettings")

/**
 * Schema version 14. Captions, the dictionary card, Q&A and video subtitles are styled per
 * profile now, as Bible and Songs are, rather than once for the install from their own tabs.
 *
 * Each profile is given the document's current copy of each, so every output draws exactly what
 * it drew before the move. A profile that already carries one -- written by a build that had
 * this, then opened by one that briefly did not -- keeps its own.
 *
 * The document's copies are removed at version 21 ([migrateTopLevelStylingOut]).
 */
internal fun migrateStylingIntoProfiles(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"]?.jsonObject ?: return raw
    val profiles = projection["outputProfiles"]?.jsonArray ?: return raw
    val seeded = profiles.map { element ->
        val profile = element as? JsonObject ?: return@map element
        JsonObject(
            profile + stylingMovedIntoProfiles
                .filter { it !in profile }
                .mapNotNull { key -> root[key]?.let { key to it } },
        )
    }
    val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(seeded)))
    return JsonObject(root + ("projectionSettings" to newProjection)).toString()
}

/**
 * Schema version 21. The document stops carrying the looks version 14 moved onto the profiles:
 * nothing draws from them, so all they did was drift from what the outputs actually show.
 *
 * Any profile still without its own copy is given the document's first, so none falls back to
 * the class defaults; then the document's copies go ([stripOverlayStyling]).
 */
internal fun migrateTopLevelStylingOut(raw: String): String {
    val root = parseSettingsRoot(migrateStylingIntoProfiles(raw)) ?: return raw
    return JsonObject(root.toMutableMap().apply { stripOverlayStyling() }).toString()
}

/**
 * Schema version 22. The document keeps only the install-wide part of the Bible and Song settings
 * ([BIBLE_GLOBAL_KEYS], [SONG_GLOBAL_KEYS], and each translation's file and names): everything
 * else is the profiles', and the main window follows one ([operatorProfile]).
 *
 * Every profile is given the document's copy of anything it lacks first -- a whole section, or a
 * translation in the stack its own Bible settings never styled, which resolution used to take
 * from the document -- so every output draws what it drew before. A document with no profiles
 * at all is given its factory profile here, made from the document's look, rather than the
 * blank one the load would otherwise add.
 */
internal fun migrateBibleAndSongsOut(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"] as? JsonObject ?: JsonObject(emptyMap())
    val profiles = (projection["outputProfiles"] as? JsonArray)?.takeIf { it.isNotEmpty() }
        ?: JsonArray(listOf(factoryProfileTree()))
    val seeded = profiles.map { element ->
        (element as? JsonObject)?.let { seedBibleAndSongs(it, root) } ?: element
    }
    val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(seeded)))
    val seededRoot = JsonObject(root + ("projectionSettings" to newProjection))
    return JsonObject(seededRoot.toMutableMap().apply { stripBibleAndSongStyling() }).toString()
}

/**
 * Schema version 23. A profile's flat `show*` switches become its look, grouped by layer
 * ([OutputLook]), and a linked profile's overrides of them are renamed to their new paths, so
 * every output draws what it did and every follower keeps its own switches.
 */
internal fun migrateShowSwitchesIntoLooks(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"]?.jsonObject ?: return raw
    val profiles = projection["outputProfiles"]?.jsonArray ?: return raw
    val moved = profiles.map { element -> (element as? JsonObject)?.let(::moveShowSwitchesIntoLook) ?: element }
    val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(moved)))
    return JsonObject(root + ("projectionSettings" to newProjection)).toString()
}

/** The factory profile without the two sections [seedBibleAndSongs] is to fill from the document. */
private fun factoryProfileTree(): JsonObject = JsonObject(
    settingsJson.encodeToJsonElement(OutputProfile.serializer(), factoryOutputProfile()).jsonObject
        .filterKeys { it != "songSettings" && it != "bibleSettings" },
)

/** [profile] with the document's Song and Bible sections, and Bible translations, where it has none. */
private fun seedBibleAndSongs(profile: JsonObject, root: JsonObject): JsonObject {
    val seeded = profile.toMutableMap()
    for (key in listOf("songSettings", "bibleSettings")) {
        if (key !in seeded) root[key]?.let { seeded[key] = it }
    }
    val documentStack = (root["bibleSettings"] as? JsonObject)?.get(BIBLE_STACK_KEY) as? JsonArray
    val bible = seeded["bibleSettings"] as? JsonObject
    if (documentStack != null && bible != null) {
        val own = bible[BIBLE_STACK_KEY] as? JsonArray ?: JsonArray(emptyList())
        val styled = own.mapNotNull { it.translationFileName() }.toSet()
        val missing = documentStack.filter { it.translationFileName() !in styled }
        if (missing.isNotEmpty()) {
            seeded["bibleSettings"] = JsonObject(bible + (BIBLE_STACK_KEY to JsonArray(own + missing)))
        }
    }
    return JsonObject(seeded)
}

private fun JsonElement.translationFileName(): String? =
    ((this as? JsonObject)?.get("fileName") as? JsonPrimitive)?.contentOrNull
