package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Comparing a linked profile with its master, value by value: what differs, what the master has,
 * and a profile built from the master's values and the linked one's own. See `LinkedProfiles.kt`.
 */

/**
 * The paths whose values differ between [a] and [b], restricted to those [b] actually uses: a
 * background surface [b] follows rather than owns is drawn from the Background tab, so what it
 * happens to hold is not a difference anyone made.
 */
fun changedPaths(a: OutputProfile, b: OutputProfile): Set<String> {
    val before = a.settingPaths()
    val after = b.settingPaths()
    return (before.keys + after.keys)
        .filter { before[it] != after[it] }
        .filter { b.usesPath(it) }
        .toSet()
}

/** False for a value [this] carries but does not draw with -- a background surface it follows. */
private fun OutputProfile.usesPath(path: String): Boolean {
    if (!path.startsWith("backgroundSettings.")) return true
    val field = path.removePrefix("backgroundSettings.").substringBefore('.')
    val surface = BackgroundSurface.entries.firstOrNull { field in it.fieldKeys } ?: return true
    return surface.name in backgroundOverrides
}

/**
 * [child] as its master [master] has it, except at each of [child]'s own paths: [master]'s values,
 * [child]'s values where it has overridden them, and [child]'s identity throughout.
 */
fun materialize(master: OutputProfile, child: OutputProfile): OutputProfile {
    var tree = master.tree()
    val own = child.settingPaths()
    child.overrides.forEach { path ->
        val value = own[path] ?: return@forEach
        tree = setAt(tree, parsePath(path), value) ?: tree
    }
    return tree.toProfile().copy(
        id = child.id,
        name = child.name,
        parentId = child.parentId,
        overrides = child.overrides,
        previewWidth = child.previewWidth,
        previewHeight = child.previewHeight,
        merge = child.merge,
    )
}

/** [this] with [value] at [path], or unchanged when it has no value there to replace. */
fun OutputProfile.withValueAt(path: String, value: JsonElement): OutputProfile =
    setAt(tree(), parsePath(path), value)?.toProfile() ?: this

/** How many of [profile]'s values are its own, under [prefixes] -- or at all, when [prefixes] is empty. */
fun overrideCount(profile: OutputProfile, prefixes: Collection<String> = emptyList()): Int =
    if (prefixes.isEmpty()) profile.overrides.size
    else profile.overrides.count { o -> prefixes.any { pathWithin(o, it) } }

/** [master]'s value at [path], for a row to say what it would take back -- or null if it has none. */
fun OutputProfile.valueAt(path: String): JsonElement? = settingPaths()[path]

/** A short plain reading of a stored value: its text, a number, On/Off. */
fun JsonElement.plainText(on: String = "On", off: String = "Off"): String = when (this) {
    is JsonPrimitive -> when {
        isString -> content
        content == "true" -> on
        content == "false" -> off
        else -> content.removeSuffix(".0")
    }
    is JsonArray -> joinToString(", ") { it.plainText(on, off) }
    is JsonObject -> ""
}
