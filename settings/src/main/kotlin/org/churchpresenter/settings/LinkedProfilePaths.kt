package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.Collections

/*
 * Addressing one value of a profile by its path into the profile's serialized settings -- see
 * `LinkedProfiles.kt` for what the paths are for.
 */

internal val linkJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * A profile's own identity, never inherited from its master -- nor the shape it is previewed at,
 * which stands in for the screen it is meant for, and a linked profile is usually made for another.
 */
// `merge` names this profile's own outputs, which a linked profile does not share, so it is
// never followed from a master and never listed as a change.
private val IDENTITY_KEYS = setOf(
    "id", "name", "parentId", "overrides", "sectionMasters", "previewWidth", "previewHeight", "merge",
)

/**
 * The settings a profile carries but never actually uses -- the ones resolution takes from the
 * document instead (a library folder, a caption server). A difference in them is not a difference
 * the operator made, so they are left out of every path set.
 */
private val DOCUMENT_KEYS = mapOf(
    "bibleSettings" to BIBLE_GLOBAL_KEYS,
    "songSettings" to SONG_GLOBAL_KEYS,
    "sttSettings" to STT_GLOBAL_KEYS,
    "qaSettings" to QA_GLOBAL_KEYS,
)

/** The path of the display mode, which a linked profile always takes from its master. */
const val DISPLAY_MODE_PATH = "displayMode"

internal fun OutputProfile.tree(): JsonObject = linkJson.encodeToJsonElement(this).jsonObject

internal fun JsonObject.toProfile(): OutputProfile = linkJson.decodeFromJsonElement(OutputProfile.serializer(), this)

/** Every value of [this] by its path, identity and document-owned keys left out. */
fun OutputProfile.settingPaths(): Map<String, JsonElement> = RecentSettingPaths.of(this) { profile ->
    val out = LinkedHashMap<String, JsonElement>()
    profile.tree().forEach { (key, value) -> if (key !in IDENTITY_KEYS) flattenTopLevel(key, value, out) }
    Collections.unmodifiableMap(out)
}

/** One of the profile's own fields: a settings object is flattened without its document-owned keys. */
internal fun flattenTopLevel(key: String, value: JsonElement, out: MutableMap<String, JsonElement>) =
    walkTopLevel(key, value) { path, _, leaf -> out[path] = leaf }

/**
 * Each value under the profile field [key], handed to [visit] with its path and the path builds
 * before version 24 wrote for it: every key after a dot, no name escaped.
 */
private fun walkTopLevel(key: String, value: JsonElement, visit: (String, String, JsonElement) -> Unit) {
    val ignored = DOCUMENT_KEYS[key].orEmpty()
    if (value is JsonObject) {
        value.forEach { (inner, v) -> if (inner !in ignored) walk(v, member(key, inner), "$key.$inner", visit) }
    } else {
        walk(value, key, key, visit)
    }
}

private fun walk(element: JsonElement, path: String, legacy: String, visit: (String, String, JsonElement) -> Unit) {
    when {
        element is JsonObject && element.isNotEmpty() ->
            element.forEach { (key, value) -> walk(value, member(path, key), "$legacy.$key", visit) }
        element is JsonArray && element.isNotEmpty() && element.all { it.entryName() != null } ->
            element.forEach {
                val name = it.entryName().orEmpty()
                walk(it, named(path, name), "$legacy[$name]", visit)
            }
        else -> visit(path, legacy, element)
    }
}

/**
 * The path of an object's member [key] under [path]: after a dot, or -- a key a dot or a bracket
 * would cut short, like a Bible box's `REFERENCE#kjv.spb` -- named in brackets.
 */
private fun member(path: String, key: String): String =
    if (key.none { it in PATH_SYNTAX }) "$path.$key" else named(path, key)

/**
 * [path] with [name] in brackets: a list's entry, or an object's member that needs them. A bracket
 * or a backslash in the name is escaped with a backslash, so a file name may hold one.
 */
private fun named(path: String, name: String): String =
    "$path[${name.replace("\\", "\\\\").replace("]", "\\]")}]"

/** The characters a key cannot hold after a dot. */
private val PATH_SYNTAX = charArrayOf('.', '[', ']')

/**
 * Each of [profile]'s paths as builds before version 24 wrote it, mapped to how it is written now,
 * where the two differ: a key with a dot in it, or a name with a bracket or a backslash.
 */
internal fun legacyPathRenames(profile: JsonObject): Map<String, String> {
    val renames = LinkedHashMap<String, String>()
    profile.forEach { (key, value) ->
        if (key !in IDENTITY_KEYS) {
            walkTopLevel(key, value) { path, legacy, _ -> if (legacy != path) renames[legacy] = path }
        }
    }
    return renames
}

/** Every path of a serialized profile, identity keys left out. */
internal fun JsonObject.paths(): Set<String> {
    val out = LinkedHashMap<String, JsonElement>()
    forEach { (key, value) -> if (key !in IDENTITY_KEYS) flattenTopLevel(key, value, out) }
    return out.keys
}
