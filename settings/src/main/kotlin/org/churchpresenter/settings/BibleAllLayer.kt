package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/*
 * The All layer of a Bible translation stack: one look every translation follows, field by field,
 * except where a translation has been given a value of its own. A translation still stores every
 * value in full, so what an output draws is read from the translation exactly as before; the layer
 * only decides what an edit under All reaches, and what "Only KJV ×" puts back.
 */

private val layerJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** The fields that say which translation this is, rather than how it looks. */
private val IDENTITY_FIELDS = setOf("fileName", "customName", "customAbbreviation", "ownStyleKeys")

private fun BibleTranslationSettings.fields(): JsonObject = layerJson.encodeToJsonElement(this).jsonObject

/** The style fields whose values differ between [a] and [b]. */
fun styleFieldsChanged(a: BibleTranslationSettings, b: BibleTranslationSettings): Set<String> {
    val before = a.fields()
    val after = b.fields()
    return (before.keys + after.keys).filter { it !in IDENTITY_FIELDS && before[it] != after[it] }.toSet()
}

/** [this] with [from]'s value at each of [keys]. */
fun BibleTranslationSettings.withFieldsFrom(
    from: BibleTranslationSettings,
    keys: Collection<String>,
): BibleTranslationSettings {
    if (keys.isEmpty()) return this
    val source = from.fields()
    val merged = fields().toMutableMap()
    keys.forEach { key -> source[key]?.let { merged[key] = it } }
    return layerJson.decodeFromJsonElement(BibleTranslationSettings.serializer(), JsonObject(merged))
}

/** The All layer's look -- the first translation's, until the layer has been seeded. */
fun BibleSettings.allStyle(): BibleTranslationSettings =
    allTranslationStyle ?: translationList().firstOrNull() ?: BibleTranslationSettings()

/**
 * [transform] applied under All: to the layer, and -- field by field -- to every translation that
 * has no value of its own for the field it changed.
 */
fun BibleSettings.updateAllLayer(transform: (BibleTranslationSettings) -> BibleTranslationSettings): BibleSettings {
    val before = allStyle()
    val after = transform(before)
    val changed = styleFieldsChanged(before, after)
    val layered = copy(allTranslationStyle = after.asLayer())
    return translationList().indices.fold(layered) { bs, index ->
        bs.updateTranslation(index) { it.withFieldsFrom(after, changed - it.ownStyleKeys) }
    }
}

/** [transform] applied to translation [index] alone: every field it changed becomes that translation's own. */
fun BibleSettings.updateOwnStyle(
    index: Int,
    transform: (BibleTranslationSettings) -> BibleTranslationSettings,
): BibleSettings =
    updateTranslation(index) { t ->
        val next = transform(t)
        next.copy(ownStyleKeys = next.ownStyleKeys + styleFieldsChanged(t, next))
    }

/** Translation [index] following All again at each of [keys], with All's values put back. */
fun BibleSettings.clearOwnStyle(index: Int, keys: Collection<String>): BibleSettings {
    val all = allStyle()
    return updateTranslation(index) { t ->
        t.withFieldsFrom(all, keys).copy(ownStyleKeys = t.ownStyleKeys - keys.toSet())
    }
}

/**
 * The All layer seeded from a stack that predates it: the first translation's look is All's, and
 * every other translation owns the fields where it differs from that. Nothing drawn changes. A no-op
 * once seeded, and on an empty stack.
 */
fun BibleSettings.migrateAllLayer(): BibleSettings {
    val stack = translationList()
    val first = stack.firstOrNull()
    if (allTranslationStyle != null || first == null) return this
    val seeded = copy(allTranslationStyle = first.asLayer())
    return stack.indices.fold(seeded) { bs, index ->
        bs.updateTranslation(index) { it.copy(ownStyleKeys = it.ownStyleKeys + styleFieldsChanged(first, it)) }
    }
}

/** [this] as the layer stores it: a look belonging to no translation. */
private fun BibleTranslationSettings.asLayer(): BibleTranslationSettings =
    copy(fileName = "", customName = "", customAbbreviation = "", ownStyleKeys = emptySet())
