package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.withTranslationSettings

/**
 * Which language of a bilingual song the tab is styling.
 *
 * The Bible tab has one of these per translation, each with a full profile of its own; a song has
 * two languages rather than a list, so this is a pair rather than an index -- but it is the same
 * move, and the switch above the preview is the same control.
 *
 * The lyrics and the title have a second profile each -- both are text the song carries twice. The
 * number, the look-ahead and the credits do not: they are drawn once whatever language the words
 * are in, so [SECONDARY] narrows the element strip to the two that can answer it.
 */
internal enum class SongStyleLanguage(val translation: Int) { PRIMARY(0), SECONDARY(1) }

internal val SongStyleLanguage.isSecondary: Boolean get() = this == SongStyleLanguage.SECONDARY

/**
 * [element] on [target] as the panel should show it for [language].
 *
 * The second language is the first extra translation, and until it has a look of its own it reads
 * back as the first's -- so picking it on a song that has never been styled shows what is on the
 * slide. An element with no per-language form is drawn once whatever the words are in, so asking
 * for its second gives its only one.
 */
internal fun SongSettings.elementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): SongElementStyle = elementStyle(element, target, language.translation)

/**
 * [elementStyle]'s inverse: write [style] to whichever profile that triple names.
 *
 * Writing to a language that follows the first turns its own look on, seeded from the first's on
 * both outputs, so the edit changes the one property the operator touched and nothing else.
 */
internal fun SongSettings.withElementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
    style: SongElementStyle,
): SongSettings {
    if (!language.isSecondary || element.translationElement == null) return withElementStyle(element, target, style)
    return withOwnLook(language).withElementStyle(element, target, language.translation, style)
}

/**
 * What Reset means for that triple.
 *
 * For a second language it is not "back to the factory look" but "back to being drawn like the
 * first" -- the state it is in until something is typed, and the only way back to it.
 */
internal fun SongSettings.withElementReset(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): SongSettings {
    if (!language.isSecondary || element.translationElement == null) {
        return withElementStyle(element, target, defaultSongElementStyle(element, target))
    }
    return withTranslationSettings(language.translation - 1) { it.copy(overrideStyle = false) }
}

/** The elements that have a second language's profile -- the two a song carries twice. */
internal val SECOND_LANGUAGE_ELEMENTS = listOf(SongStyleElement.LYRICS, SongStyleElement.TITLE)

/** These settings with [language]'s own look switched on, seeded from the first language's. */
private fun SongSettings.withOwnLook(language: SongStyleLanguage): SongSettings {
    if (translationSettings(language.translation - 1).overrideStyle) return this
    return withTranslationSettings(language.translation - 1) { settings ->
        settings.seededFrom { element, lowerThird ->
            val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
            elementStyle(element.styleElement, target)
        }
    }
}
