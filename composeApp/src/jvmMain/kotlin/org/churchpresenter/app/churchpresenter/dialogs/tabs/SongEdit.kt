package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.churchpresenter.settings.SongSettings

/**
 * What the Songs Text group is pointed at -- one element, on this output's shape, for all languages
 * or one of them, the first included -- and how it writes.
 *
 * [language] null is All: every language follows it except where it has a value of its own, and an
 * All edit reaches a language wherever it still matches. A class rather than local functions, for
 * the reason [ProfileSongsPage] gives.
 */
internal class SongEdit(
    val song: SongSettings,
    val element: SongStyleElement,
    val target: SongStyleTarget,
    val language: SongStyleLanguage?,
    /** Two or more languages reach this output and [element] has a look per language. */
    val perLanguage: Boolean,
    private val updateSong: ((SongSettings) -> SongSettings) -> Unit,
) {
    /** One language is being edited, rather than All. */
    val picked: Boolean get() = perLanguage && language != null

    val style: SongElementStyle = when {
        language == null || !perLanguage -> song.allLanguagesStyle(element, target)
        else -> song.elementStyle(element, target, language)
    }

    /** [edited] written for the language being edited, or for all of them. */
    fun write(edited: SongElementStyle) {
        updateSong { it.written(edited) }
    }

    /** [this] with [edited] written the way [write] writes it. */
    fun SongSettings.written(edited: SongElementStyle): SongSettings = when {
        !picked || language == null -> withAllLanguagesStyle(element, target, edited)
        !language.isTranslation -> withFirstLanguageStyle(element, target, edited)
        else -> withElementStyle(element, target, language, edited)
    }

    /**
     * Reset: the picked language following All again at everything it has of its own, or -- under
     * All -- All back to how it starts.
     */
    fun reset() {
        val picked = language.takeIf { this.picked }
        updateSong {
            if (picked == null) {
                it.withAllLanguagesStyle(element, target, defaultSongElementStyle(element, target))
            } else {
                it.clearLanguageOwn(element, target, picked, it.languageOwnFields(element, target, picked))
            }
        }
    }

    /** Whether [reset] would change anything. */
    val resettable: Boolean
        get() = if (picked && language != null) {
            song.languageOwnFields(element, target, language).isNotEmpty()
        } else {
            style != defaultSongElementStyle(element, target)
        }

    /** The picked language as the rows mark its own values; null under All. */
    @Composable
    fun styleTarget(): StyleTarget? {
        val picked = language.takeIf { this.picked } ?: return null
        return StyleTarget(
            label = picked.nameLabel(song),
            entryPath = languagePath(picked),
            ownKeys = song.languageOwnFields(element, target, picked),
            onClear = { fields -> updateSong { it.clearLanguageOwn(element, target, picked, fields) } },
        )
    }

    /**
     * Which of the picked language's own fields each Text row writes, as paths under
     * [languagePath] -- for its "Only Ukrainian ×" chip. Found the way [probeTextLookPaths] finds a
     * linked profile's, one level down: which fields of the style each row changes.
     */
    @Composable
    fun targetPaths(): TextLookPaths {
        val picked = language.takeIf { this.picked } ?: return TextLookPaths.NONE
        val look = style.toLook(element)
        return remember(element, target, picked) {
            TextLookPaths(
                TextLookField.entries.associateWith { field ->
                    songStyleFieldsChanged(style, style.withLook(look.perturbed(field)))
                        .map { "${languagePath(picked)}.$it" }
                },
            )
        }
    }
}

/** Where a song language's own values are addressed for the Text rows' chip. */
private fun languagePath(language: SongStyleLanguage): String = "songLanguage[${language.translation}]"
