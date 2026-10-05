package org.churchpresenter.profiles

import org.churchpresenter.presenter.styleElement
import org.churchpresenter.presenter.withElementStyle
import org.churchpresenter.presenter.elementStyle
import org.churchpresenter.presenter.SongElementStyle
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.SongStyleTarget
import org.churchpresenter.presenter.isLowerThird
import org.churchpresenter.presenter.translationElement
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.SongAllLanguages
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTranslationElement
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.withTranslationSettings

/*
 * Editing a song's look for all its languages at once, or for one alone -- the first included.
 *
 * Every language has a look of its own to draw with; All is the one they follow. The first language
 * stores its look in the song's own fields, and All is that look everywhere the first language has
 * no value of its own -- so only those fields of All are stored apart ([SongAllLanguages]). Another
 * language's own values are the fields where it differs from All, and "Only Ukrainian ×" hands one
 * back by copying All's value over it. The first language's own values are named, since there All
 * and the first language are stored in two places.
 */

/** The stored profile [element] on [lowerThird]'s output is kept in -- `lyricsLowerThird`. */
private fun layerProperty(element: SongTranslationElement, lowerThird: Boolean): String {
    val name = when (element) {
        SongTranslationElement.TITLE -> "title"
        SongTranslationElement.LYRICS -> "lyrics"
        SongTranslationElement.LOOK_AHEAD -> "lookAhead"
        SongTranslationElement.NEXT_SECTION -> "nextSection"
    }
    return if (lowerThird) "${name}LowerThird" else name
}

/** The first language's own fields of [element] on [lowerThird]'s output. */
private fun SongSettings.firstLanguageOwn(element: SongTranslationElement, lowerThird: Boolean): Set<String> {
    val prefix = layerProperty(element, lowerThird) + "."
    return layoutExtras.allLanguages.firstLanguageOwnKeys
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .toSet()
}

private fun SongSettings.withAllLanguages(all: SongAllLanguages): SongSettings =
    copy(layoutExtras = layoutExtras.copy(allLanguages = all))

/** All's look for [element] on [target] -- the first language's, except where it has values of its own. */
internal fun SongSettings.allLanguagesStyle(element: SongStyleElement, target: SongStyleTarget): SongElementStyle {
    val first = elementStyle(element, target)
    val perLanguage = element.translationElement ?: return first
    val lowerThird = target.isLowerThird
    val stored = layoutExtras.allLanguages.style.style(perLanguage, lowerThird)
    return first.withFieldsFrom(stored, firstLanguageOwn(perLanguage, lowerThird))
}

/**
 * [style] written for every language: to All, to the first language at every changed field it has
 * no value of its own for, and to each other language with a look of its own wherever it still
 * matched All.
 */
internal fun SongSettings.withAllLanguagesStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    style: SongElementStyle,
): SongSettings {
    val perLanguage = element.translationElement ?: return withElementStyle(element, target, style)
    val lowerThird = target.isLowerThird
    val before = allLanguagesStyle(element, target)
    val changed = songStyleFieldsChanged(before, style)
    val firstOwn = firstLanguageOwn(perLanguage, lowerThird)
    val all = layoutExtras.allLanguages
    val first = elementStyle(element, target).withFieldsFrom(style, changed - firstOwn)
    val next = withElementStyle(element, target, first)
        .withAllLanguages(all.copy(style = all.style.withStyle(perLanguage, lowerThird, style)))
    return translations.indices.fold(next) { song, index ->
        if (!song.translationSettings(index).overrideStyle) return@fold song
        song.withTranslationSettings(index) { t ->
            val mine = t.style(perLanguage, lowerThird)
            val following = changed - songStyleFieldsChanged(before, mine)
            t.withStyle(perLanguage, lowerThird, mine.withFieldsFrom(style, following))
        }
    }
}

/**
 * [style] written for the first language alone: every field it changes becomes the first language's
 * own, with All's value kept aside for it.
 *
 * A language still drawing like the first is given a look of its own first -- All's -- since from
 * here on the first language no longer stands for All.
 */
internal fun SongSettings.withFirstLanguageStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    style: SongElementStyle,
): SongSettings {
    val perLanguage = element.translationElement ?: return withElementStyle(element, target, style)
    val lowerThird = target.isLowerThird
    val changed = songStyleFieldsChanged(elementStyle(element, target), style)
    if (changed.isEmpty()) return this
    val all = allLanguagesStyle(element, target)
    val layer = layoutExtras.allLanguages
    val property = layerProperty(perLanguage, lowerThird)
    return followersGivenOwnLook()
        .withElementStyle(element, target, style)
        .withAllLanguages(
            SongAllLanguages(
                style = layer.style.withStyle(
                    perLanguage,
                    lowerThird,
                    layer.style.style(perLanguage, lowerThird).withFieldsFrom(all, changed),
                ),
                firstLanguageOwnKeys = layer.firstLanguageOwnKeys + changed.map { "$property.$it" },
            ),
        )
}

/** Every language after the first that still draws like it, given All's look as its own. */
private fun SongSettings.followersGivenOwnLook(): SongSettings =
    (0 until MAX_SONG_TRANSLATIONS - 1).fold(this) { song, index ->
        if (song.translationSettings(index).overrideStyle) return@fold song
        song.withTranslationSettings(index) { t ->
            t.seededFrom { element, lowerThird ->
                val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
                allLanguagesStyle(element.styleElement, target)
            }
        }
    }

/** The fields of [element] on [target] that [language] has of its own: where it differs from All. */
internal fun SongSettings.languageOwnFields(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): Set<String> {
    val perLanguage = element.translationElement ?: return emptySet()
    if (!language.isTranslation) return firstLanguageOwn(perLanguage, target.isLowerThird)
    if (!translationSettings(language.translation - 1).overrideStyle) return emptySet()
    return songStyleFieldsChanged(allLanguagesStyle(element, target), elementStyle(element, target, language))
}

/** [language] taking All's value again at [fields] of [element] on [target]. */
internal fun SongSettings.clearLanguageOwn(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
    fields: Collection<String>,
): SongSettings {
    val perLanguage = element.translationElement ?: return this
    val lowerThird = target.isLowerThird
    val all = allLanguagesStyle(element, target)
    if (!language.isTranslation) {
        val layer = layoutExtras.allLanguages
        val property = layerProperty(perLanguage, lowerThird)
        val cleared = fields.map { "$property.$it" }.toSet()
        return withElementStyle(element, target, elementStyle(element, target).withFieldsFrom(all, fields))
            .withAllLanguages(layer.copy(firstLanguageOwnKeys = layer.firstLanguageOwnKeys - cleared))
    }
    return withTranslationSettings(language.translation - 1) { t ->
        t.withStyle(perLanguage, lowerThird, t.style(perLanguage, lowerThird).withFieldsFrom(all, fields))
    }
}
