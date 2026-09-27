package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTextStyle
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.withTranslationSettings

/*
 * Editing a song's look for all its languages at once, or for one alone.
 *
 * The first language's look is the one every other language follows until it has a look of its own
 * (`overrideStyle`), so "All" edits the first language. A language with a look of its own still
 * takes an All edit at every field where it matched the first language, and keeps the ones where it
 * does not -- so its own values are exactly the fields that differ from the first language's, and
 * "Only Ukrainian ×" hands one back by copying the first language's value over it.
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

/**
 * [style] written for every language: to the first language's [element] on [target], and to each
 * language with a look of its own at every changed field where it still matched the first.
 */
internal fun SongSettings.withAllLanguagesStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    style: SongElementStyle,
): SongSettings {
    val before = elementStyle(element, target)
    val next = withElementStyle(element, target, style)
    val perLanguage = element.translationElement ?: return next
    val lowerThird = target.isLowerThird
    val changed = songStyleFieldsChanged(before, style)
    return translations.indices.fold(next) { song, index ->
        if (!song.translationSettings(index).overrideStyle) return@fold song
        song.withTranslationSettings(index) { t ->
            val mine = t.style(perLanguage, lowerThird)
            val following = changed - songStyleFieldsChanged(before, mine)
            t.withStyle(perLanguage, lowerThird, mine.withFieldsFrom(style, following))
        }
    }
}

/** The fields of [element] on [target] that [language] has of its own: where it differs from the first. */
internal fun SongSettings.languageOwnFields(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): Set<String> {
    if (!language.isTranslation || element.translationElement == null) return emptySet()
    if (!translationSettings(language.translation - 1).overrideStyle) return emptySet()
    return songStyleFieldsChanged(elementStyle(element, target), elementStyle(element, target, language))
}

/** [language] taking the first language's value again at [fields] of [element] on [target]. */
internal fun SongSettings.clearLanguageOwn(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
    fields: Collection<String>,
): SongSettings {
    val perLanguage = element.translationElement
    if (!language.isTranslation || perLanguage == null) return this
    val first = elementStyle(element, target)
    return withTranslationSettings(language.translation - 1) { t ->
        val lowerThird = target.isLowerThird
        t.withStyle(perLanguage, lowerThird, t.style(perLanguage, lowerThird).withFieldsFrom(first, fields))
    }
}
