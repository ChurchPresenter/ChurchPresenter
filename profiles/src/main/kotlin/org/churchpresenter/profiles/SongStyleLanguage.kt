package org.churchpresenter.profiles

import org.churchpresenter.presenter.styleElement
import org.churchpresenter.presenter.withElementStyle
import org.churchpresenter.presenter.elementStyle
import org.churchpresenter.presenter.SongElementStyle
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.SongStyleTarget
import org.churchpresenter.presenter.translationElement
import androidx.compose.runtime.Composable
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.languageDisplayOrder
import org.churchpresenter.settings.songLanguagePicks
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_bible_translation_place
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.withTranslationSettings
import org.churchpresenter.sharedui.utils.songLanguageName

/**
 * Which look of a song's languages the tab is styling -- a place's look in the Songs tab's display
 * order where the profile's looks follow the place, a language's own where they follow the language
 * (`SongLayoutExtras.languageLooksFollowPlace`).
 *
 * The Bible tab has one of these per translation, each with a full profile of its own; a song has
 * two languages rather than a list, so this is a pair rather than an index -- but it is the same
 * move, and the switch above the preview is the same control.
 *
 * The lyrics and the title have a second profile each -- both are text the song carries twice. The
 * number, the look-ahead and the credits do not: they are drawn once whatever language the words
 * are in, so a translation narrows the element strip to the two that can answer it.
 */
internal enum class SongStyleLanguage(val translation: Int) { PRIMARY(0), SECONDARY(1), THIRD(2), FOURTH(3) }

/** True for every language beside the first, each of which has a look of its own to edit. */
internal val SongStyleLanguage.isTranslation: Boolean get() = translation > 0

/**
 * The switch's label in the per-output dialog, which names them: a place's look by its number and
 * the language in it now, since the look stays when another language moves in -- as the Bible page
 * labels a translation's place -- and a language's own look by its name.
 */
@Composable
internal fun SongStyleLanguage.nameLabel(song: SongSettings): String =
    if (song.layoutExtras.languageLooksFollowPlace) {
        stringResource(Res.string.profile_bible_translation_place, translation + 1, placeName(song))
    } else {
        songLanguageName(song, translation)
    }

/** The name of the language in this place of the display order now. */
@Composable
internal fun SongStyleLanguage.placeName(song: SongSettings): String =
    songLanguageName(song, song.languageDisplayOrder().getOrElse(translation) { translation })

/**
 * The looks an output offers to style, given what it has been told to show and [song]'s display
 * order and looks switch.
 *
 * Read through the same [songLanguagePicks] the presenter draws by, so the switch can never offer a
 * look the output does not draw, or miss one it does.
 */
internal fun styleLanguagesFor(songMode: String, selection: List<Int>, song: SongSettings): List<SongStyleLanguage> =
    songLanguagePicks(
        songMode,
        selection,
        MAX_SONG_TRANSLATIONS,
        song.languageDisplayOrder(),
        song.layoutExtras.languageLooksFollowPlace,
    )
        .mapNotNull { pick -> SongStyleLanguage.entries.firstOrNull { it.translation == pick.look } }
        .ifEmpty { listOf(SongStyleLanguage.PRIMARY) }

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
    if (!language.isTranslation || element.translationElement == null) return withElementStyle(element, target, style)
    return withOwnLook(language).withElementStyle(element, target, language.translation, style)
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
