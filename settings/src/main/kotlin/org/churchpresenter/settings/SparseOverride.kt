package org.churchpresenter.settings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * A screen's overrides, stored as **only the settings that screen actually changed**.
 *
 * The overrides used to be a whole settings object -- a snapshot taken when the screen was first
 * customized. That works until a setting is *added*: the snapshot carries it at its class default,
 * the default beats the global document, and the global setting silently does nothing on that one
 * screen with nothing in the interface to say why. It is not a hypothetical; it is how a second
 * language set to blue everywhere came out white on the one screen that had been customized.
 *
 * So an override is a sparse tree of the keys that differ. A setting the operator never touched is
 * simply absent, and absent means *follow the document* -- today, and for every setting added after
 * this screen was customized. The only way a screen holds a value is by having been given one.
 *
 * [encodeDefaults] is on: the diff compares complete objects, so a field left at its default on one
 * side and set to that same value on the other reads as equal rather than as missing.
 */
private val overrideJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** [global] with [override]'s keys written over it, or [global] itself when there is no override. */
fun <T> withSparseOverride(global: T, override: JsonObject?, serializer: KSerializer<T>): T {
    if (override == null || override.isEmpty()) return global
    val globalTree = overrideJson.encodeToJsonElement(serializer, global) as JsonObject
    return overrideJson.decodeFromJsonElement(serializer, mergeObjects(globalTree, override))
}

/**
 * [full] encoded whole, with [ignoredKeys] dropped -- a **keep-list projection**, not a diff.
 *
 * Where [diffObjects] asks "what does one object say that another does not," this asks nothing
 * about any other object at all: every field of [full] outside [ignoredKeys] is kept, whether or
 * not it happens to equal a class default. Feeding the result into [withSparseOverride] is what
 * lets an [OutputProfile] carry a *full* settings object as its styling while the fields that must
 * stay one per install -- a library folder, a file list, column widths -- still come from whichever
 * object [withSparseOverride] is applied to.
 */
fun <T> styleTreeOf(full: T, serializer: KSerializer<T>, ignoredKeys: Set<String>): JsonObject {
    val tree = overrideJson.encodeToJsonElement(serializer, full) as JsonObject
    return JsonObject(tree.filterKeys { it !in ignoredKeys })
}

/**
 * The entries of [customized] that differ from [global].
 *
 * Nested objects are descended into, so changing one corner of a backdrop stores that corner rather
 * than the whole record -- which is the entire point: a record stored whole is a snapshot again, and
 * pins every field in it. Anything that is not an object on both sides is compared and kept whole,
 * lists included: a list diffed entry by entry is a list whose meaning depends on position, and
 * these do not all work that way.
 */
internal fun diffObjects(
    global: JsonObject,
    customized: JsonObject,
    atomicKeys: Set<String> = emptySet(),
): JsonObject = buildJsonObject {
    customized.forEach { (key, mine) ->
        val theirs: JsonElement? = global[key]
        when {
            theirs == mine -> Unit
            key !in atomicKeys && theirs is JsonObject && mine is JsonObject -> {
                val nested = diffObjects(theirs, mine)
                if (nested.isNotEmpty()) put(key, nested)
            }
            else -> put(key, mine)
        }
    }
}

/** [base] with [over] written into it, descending into objects present on both sides. */
internal fun mergeObjects(base: JsonObject, over: JsonObject): JsonObject = buildJsonObject {
    base.forEach { (key, value) ->
        val mine = over[key]
        when {
            mine == null -> put(key, value)
            value is JsonObject && mine is JsonObject -> put(key, mergeObjects(value, mine))
            else -> put(key, mine)
        }
    }
    // Keys the override carries that the document does not: a setting removed from the global
    // object since, which the decoder will ignore, rather than something to drop silently here.
    over.forEach { (key, value) -> if (key !in base) put(key, value) }
}
