package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

// The raw-JSON step that rewrote the setting paths older builds cut short at a dot (24).

/**
 * Schema version 24. A Bible box is kept under a key with the translation's file name in it --
 * `REFERENCE#kjv.spb` -- and a linked profile's overrides used to address it after a dot, which
 * read back as a box `REFERENCE#kjv` holding `spb`: the follower's own box was lost to its master's
 * on every load, and an empty box was made under the cut-short key. Each override is renamed to the
 * path written now ([legacyPathRenames]), and those empty boxes are taken out with the overrides
 * that pointed into them.
 */
internal fun migrateDottedSettingPaths(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val projection = root["projectionSettings"]?.jsonObject ?: return raw
    val profiles = projection["outputProfiles"]?.jsonArray ?: return raw
    val fixed = profiles.map { element -> (element as? JsonObject)?.let(::withDottedPathsFixed) ?: element }
    val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(fixed)))
    return JsonObject(root + ("projectionSettings" to newProjection)).toString()
}

private fun withDottedPathsFixed(profile: JsonObject): JsonObject {
    val pruned = profile.withoutCutShortBoxes()
    val overrides = profile["overrides"] as? JsonArray ?: return pruned
    val renames = legacyPathRenames(profile)
    val gone = profile.paths() - pruned.paths()
    val renamed = overrides.mapNotNull { element ->
        val path = (element as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull element
        val now = renames[path] ?: path
        if (now in gone) null else JsonPrimitive(now)
    }
    return JsonObject(pruned + ("overrides" to JsonArray(renamed.distinct())))
}

/**
 * [this] without the boxes the old paths made: in every `textBoxes` map, a key that another key
 * continues after a dot -- `REFERENCE#kjv` beside `REFERENCE#kjv.spb`.
 */
private fun JsonObject.withoutCutShortBoxes(): JsonObject = JsonObject(
    mapValues { (key, value) ->
        when {
            value !is JsonObject -> value
            key == TEXT_BOXES_KEY -> JsonObject(value.filterKeys { box -> value.keys.none { it.startsWith("$box.") } })
            else -> value.withoutCutShortBoxes()
        }
    },
)

private const val TEXT_BOXES_KEY = "textBoxes"
