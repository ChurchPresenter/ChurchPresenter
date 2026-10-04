package org.churchpresenter.profiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.churchpresenter.settings.SongTextStyle

/*
 * A song element's look field by field -- what an edit changed, and one look's values copied onto
 * another -- which is how All and a language's own values are told apart.
 */

private val styleJson = Json { encodeDefaults = true }

private fun SongTextStyle.fields(): JsonObject = styleJson.encodeToJsonElement(this).jsonObject

/** The fields whose values differ between [a] and [b]. */
internal fun songStyleFieldsChanged(a: SongTextStyle, b: SongTextStyle): Set<String> {
    val before = a.fields()
    val after = b.fields()
    return (before.keys + after.keys).filter { before[it] != after[it] }.toSet()
}

/** [this] with [from]'s value at each of [keys]. */
internal fun SongTextStyle.withFieldsFrom(from: SongTextStyle, keys: Collection<String>): SongTextStyle {
    if (keys.isEmpty()) return this
    val source = from.fields()
    val merged = fields().toMutableMap()
    keys.forEach { key -> source[key]?.let { merged[key] = it } }
    return styleJson.decodeFromJsonElement(SongTextStyle.serializer(), JsonObject(merged))
}
