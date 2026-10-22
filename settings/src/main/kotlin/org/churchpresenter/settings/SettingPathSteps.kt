package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Reading a setting's path back into steps, and reaching or replacing the value it names -- the
 * paths themselves are written in `LinkedProfilePaths.kt`.
 */

/** The key a list's entries are told apart by, so each can be followed or given its own look. */
private const val ENTRY_KEY = "fileName"

internal fun JsonElement.entryName(): String? =
    (this as? JsonObject)?.get(ENTRY_KEY)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

/**
 * One step of a path: a key, and -- in brackets after it -- which entry of the list there, or which
 * member of the object there.
 */
internal data class PathStep(val key: String, val entry: String?)

internal fun parsePath(path: String): List<PathStep> {
    val steps = mutableListOf<PathStep>()
    var i = 0
    val key = StringBuilder()
    while (i <= path.length) {
        when (val c = path.getOrNull(i)) {
            null, '.' -> {
                if (key.isNotEmpty()) steps += PathStep(key.toString(), null)
                key.clear()
            }
            '[' -> {
                val (name, close) = bracketedName(path, i + 1)
                steps += PathStep(key.toString(), name)
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

/**
 * The name in brackets that starts at [from] in [path], and where its closing bracket is. A
 * backslash keeps the character after it, so a name may hold a bracket.
 */
private fun bracketedName(path: String, from: Int): Pair<String, Int> {
    val name = StringBuilder()
    var i = from
    while (i < path.length && path[i] != ']') {
        if (path[i] == '\\' && i + 1 < path.length) i++
        name.append(path[i])
        i++
    }
    return name.toString() to i
}

/** The value [step] names under [this]: a key's, and then its named entry's or member's. */
internal fun JsonObject.along(step: PathStep): JsonElement? {
    val child = this[step.key]
    return when {
        step.entry == null -> child
        child is JsonArray -> child.firstOrNull { it.entryName() == step.entry }
        else -> (child as? JsonObject)?.get(step.entry)
    }
}

/** [root] with the value at [path] replaced by [value], or null when [root] has nowhere to put it. */
internal fun setAt(root: JsonObject, path: List<PathStep>, value: JsonElement): JsonObject? {
    val step = path.first()
    val rest = path.drop(1)
    val current = root[step.key]
    val replaced = when {
        step.entry != null && current is JsonObject ->
            setAt(current, listOf(PathStep(step.entry, null)) + rest, value)
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
