package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * What a profile changes from the defaults: compared, path by path, with the profile a new one would
 * be -- the same comparison a linked profile makes against its master.
 */

/**
 * [this] as a new profile would hold it: every setting at its default, with [this]'s identity and
 * its translations and song languages -- each at default values, so a translation's size is
 * compared with the default size rather than with a translation that is not there.
 */
fun OutputProfile.defaultBaseline(): OutputProfile {
    val fresh = newOutputProfile(emptyList())
    return fresh.copy(
        id = id,
        name = name,
        parentId = parentId,
        overrides = overrides,
        sectionMasters = sectionMasters,
        previewWidth = previewWidth,
        previewHeight = previewHeight,
        bibleSettings = fresh.bibleSettings.copy(
            translations = bibleSettings.translations.map {
                BibleTranslationSettings(
                    fileName = it.fileName,
                    customName = it.customName,
                    customAbbreviation = it.customAbbreviation,
                )
            },
        ),
        songSettings = fresh.songSettings.copy(
            translations = songSettings.translations.map { SongTranslationSettings(label = it.label) },
        ),
    )
}

/**
 * Where the Profiles tab keeps its own working state, which is no setting anyone changed -- and which
 * background surfaces the profile owns, which says where a background comes from, not how it looks.
 */
private val BOOKKEEPING_PATHS = listOf(
    "bibleSettings.allTranslationStyle",
    "songSettings.layoutExtras.allLanguages",
    "backgroundOverrides",
)
private const val OWN_KEYS_FIELD = ".ownStyleKeys"
private const val OWN_BACKGROUND_FIELD = ".ownBackgroundType"

/**
 * Every setting [profile] holds at other than its default, by path, sorted. A value set where the
 * default has none -- a position where the default is "not positioned" -- is listed by what it holds,
 * not also as the empty value it replaced.
 */
fun defaultChanges(profile: OutputProfile): List<String> {
    val changed = changedPaths(profile.defaultBaseline(), profile)
        .filterNot { path -> BOOKKEEPING_PATHS.any { pathWithin(path, it) } ||
            path.endsWith(OWN_KEYS_FIELD) || path.endsWith(OWN_BACKGROUND_FIELD) }
    return changed.filterNot { path -> changed.any { it != path && it.startsWith("$path.") } }.sorted()
}

/**
 * [this] with the setting at [path] back at its default. A value a new profile has no place for at
 * all -- one element's move, kept in a map a new profile leaves empty -- is taken out, from the first
 * step of [path] a new profile lacks.
 */
fun OutputProfile.withDefaultAt(path: String): OutputProfile {
    val baseline = defaultBaseline()
    baseline.valueAt(path)?.let { return withValueAt(path, it) }
    val steps = parsePath(path)
    val base = baseline.tree()
    // The first step a new profile has nothing at -- an empty value, which is put back, or no key at
    // all, which is taken out.
    val cut = (1..steps.size).firstOrNull { n -> base.valueAlong(steps.take(n)).let { it == null || it is JsonNull } }
        ?: return this
    val along = steps.take(cut)
    val updated = if (base.valueAlong(along) is JsonNull) setAt(tree(), along, JsonNull) else tree().withoutAlong(along)
    return updated?.toProfile() ?: this
}

/** The value at [steps] under [this], or null where there is none. */
private fun JsonObject.valueAlong(steps: List<PathStep>): JsonElement? {
    var current: JsonElement? = this
    steps.forEach { step ->
        val child = (current as? JsonObject)?.get(step.key)
        current = if (step.entry == null) child else (child as? JsonArray)?.firstOrNull { it.entryName() == step.entry }
    }
    return current
}

/** [this] with the key at the end of [steps] taken out, or null where [steps] do not lead to one. */
private fun JsonObject.withoutAlong(steps: List<PathStep>): JsonObject? {
    val step = steps.first()
    val child = this[step.key]
    return when {
        step.entry != null || child == null -> null
        steps.size == 1 -> JsonObject(this - step.key)
        else -> (child as? JsonObject)?.withoutAlong(steps.drop(1))?.let { JsonObject(this + (step.key to it)) }
    }
}

private fun JsonElement.entryName(): String? =
    ((this as? JsonObject)?.get("fileName") as? JsonPrimitive)?.takeIf { it.isString }?.content
