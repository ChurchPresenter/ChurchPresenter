package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/*
 * Addressing one value of a profile by its path into the profile's serialized settings -- see
 * `LinkedProfiles.kt` for what the paths are for.
 */

private val linkJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * A profile's own identity, never inherited from its master -- nor the shape it is previewed at,
 * which stands in for the screen it is meant for, and a linked profile is usually made for another.
 */
// `merge` names this profile's own outputs, which a linked profile does not share, so it is
// never followed from a master and never listed as a change.
private val IDENTITY_KEYS = setOf("id", "name", "parentId", "overrides", "previewWidth", "previewHeight", "merge")

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

/** The key a list's entries are told apart by, so each can be followed or given its own look. */
private const val ENTRY_KEY = "fileName"

/** The path of the display mode, which a linked profile always takes from its master. */
const val DISPLAY_MODE_PATH = "displayMode"

internal fun OutputProfile.tree(): JsonObject = linkJson.encodeToJsonElement(this).jsonObject

internal fun JsonObject.toProfile(): OutputProfile = linkJson.decodeFromJsonElement(OutputProfile.serializer(), this)

/** Every value of [this] by its path, identity and document-owned keys left out. */
fun OutputProfile.settingPaths(): Map<String, JsonElement> {
    val out = LinkedHashMap<String, JsonElement>()
    tree().forEach { (key, value) -> if (key !in IDENTITY_KEYS) flattenTopLevel(key, value, out) }
    return out
}

/** One of the profile's own fields: a settings object is flattened without its document-owned keys. */
private fun flattenTopLevel(key: String, value: JsonElement, out: MutableMap<String, JsonElement>) {
    val ignored = DOCUMENT_KEYS[key].orEmpty()
    if (value is JsonObject) {
        value.forEach { (inner, v) -> if (inner !in ignored) flatten(v, "$key.$inner", out) }
    } else {
        flatten(value, key, out)
    }
}

private fun flatten(element: JsonElement, path: String, out: MutableMap<String, JsonElement>) {
    when {
        element is JsonObject && element.isNotEmpty() ->
            element.forEach { (key, value) -> flatten(value, "$path.$key", out) }
        element is JsonArray && element.isNotEmpty() && element.all { it.entryName() != null } ->
            element.forEach { flatten(it, "$path[${it.entryName()}]", out) }
        else -> out[path] = element
    }
}

private fun JsonElement.entryName(): String? =
    (this as? JsonObject)?.get(ENTRY_KEY)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

/** One step of a path: a key, and -- for a list of named entries -- which entry. */
internal data class PathStep(val key: String, val entry: String?)

internal fun parsePath(path: String): List<PathStep> {
    val steps = mutableListOf<PathStep>()
    var i = 0
    val key = StringBuilder()
    while (i <= path.length) {
        val c = path.getOrNull(i)
        when (c) {
            null, '.' -> {
                if (key.isNotEmpty()) steps += PathStep(key.toString(), null)
                key.clear()
            }
            '[' -> {
                val close = path.indexOf(']', i)
                steps += PathStep(key.toString(), path.substring(i + 1, close))
                key.clear()
                i = close
                if (path.getOrNull(i + 1) == '.') i++
            }
            else -> key.append(c)
        }
        i++
    }
    return steps
}

/** [root] with the value at [path] replaced by [value], or null when [root] has nowhere to put it. */
internal fun setAt(root: JsonObject, path: List<PathStep>, value: JsonElement): JsonObject? {
    val step = path.first()
    val rest = path.drop(1)
    val current = root[step.key]
    val replaced = when {
        step.entry != null -> (current as? JsonArray)?.let { setEntry(it, step.entry, rest, value) }
        rest.isEmpty() -> value
        current != null && current !is JsonObject -> null
        // A key the object does not have yet -- an entry of a map the master left empty -- is added.
        else -> setAt(current ?: JsonObject(emptyMap()), rest, value)
    }
    return replaced?.let { JsonObject(root + (step.key to it)) }
}

/** [list] with the entry named [name] given [value] at [rest], or null when it has no such entry. */
private fun setEntry(list: JsonArray, name: String, rest: List<PathStep>, value: JsonElement): JsonArray? {
    val index = list.indexOfFirst { it.entryName() == name }
    val entry = list.getOrNull(index)
    val newEntry = when {
        entry == null -> null
        rest.isEmpty() -> value
        else -> (entry as? JsonObject)?.let { setAt(it, rest, value) }
    }
    return newEntry?.let { replacement -> JsonArray(list.toMutableList().also { it[index] = replacement }) }
}

/** Whether [path] is [prefix] itself or lies under it. */
fun pathWithin(path: String, prefix: String): Boolean =
    path == prefix || path.startsWith("$prefix.") || path.startsWith("$prefix[")
