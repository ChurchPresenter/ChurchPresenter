package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTranslationElement
import org.churchpresenter.settings.translationStyle
import org.churchpresenter.settings.withTranslationSettings

/**
 * The per-language form of [SongStyleElement], or `null` for one that is the same in every language.
 *
 * Only the song number is `null`: the digits do not change with the language, so it has one profile
 * and not four.
 */
internal val SongStyleElement.translationElement: SongTranslationElement?
    get() = when (this) {
        SongStyleElement.NUMBER -> null
        SongStyleElement.TITLE -> SongTranslationElement.TITLE
        SongStyleElement.LYRICS -> SongTranslationElement.LYRICS
        SongStyleElement.LOOK_AHEAD -> SongTranslationElement.LOOK_AHEAD
        SongStyleElement.NEXT_SECTION -> SongTranslationElement.NEXT_SECTION
    }

/**
 * What language [translation] draws [element] with on [target] -- `0` being the primary.
 *
 * The one entry point the presenter and the settings panel share. Language 0, and any element with
 * no per-language form, resolve straight to the flat fields [elementStyle] reads; the rest go
 * through [SongSettings.translationStyle], which applies the inheritance rule.
 */
internal fun SongSettings.elementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    translation: Int,
): SongElementStyle {
    val perLanguage = element.translationElement
    if (translation <= 0 || perLanguage == null) return elementStyle(element, target)
    return translationStyle(translation, perLanguage, target.isLowerThird) { perElement, lowerThird ->
        val perTarget = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
        elementStyle(perElement.styleElement, perTarget)
    }
}

/** The inverse of [SongStyleElement.translationElement]. */
internal val SongTranslationElement.styleElement: SongStyleElement
    get() = when (this) {
        SongTranslationElement.TITLE -> SongStyleElement.TITLE
        SongTranslationElement.LYRICS -> SongStyleElement.LYRICS
        SongTranslationElement.LOOK_AHEAD -> SongStyleElement.LOOK_AHEAD
        SongTranslationElement.NEXT_SECTION -> SongStyleElement.NEXT_SECTION
    }

/**
 * These settings with [element] on [target] set to [style] for language [translation].
 *
 * Language 0 writes the flat fields; the rest write their own stored profile, which only has an
 * effect while that language has `overrideStyle` on -- the panel turns it on before offering the
 * controls, so there is no path here that writes somewhere nothing reads.
 */
internal fun SongSettings.withElementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    translation: Int,
    style: SongElementStyle,
): SongSettings {
    val perLanguage = element.translationElement
    if (translation <= 0 || perLanguage == null) return withElementStyle(element, target, style)
    return withTranslationSettings(translation - 1) { it.withStyle(perLanguage, target.isLowerThird, style) }
}
