package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.strings.generated.resources.bible_catalog_download
import org.churchpresenter.strings.generated.resources.helper_hint_bible_catalog
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.helper_hint_add_song_language
import org.churchpresenter.strings.generated.resources.helper_hint_settings
import org.churchpresenter.strings.generated.resources.add_bible_translation
import org.churchpresenter.strings.generated.resources.bible_catalog_button
import org.churchpresenter.strings.generated.resources.helper_hint_bible_add_translation
import org.churchpresenter.strings.generated.resources.helper_hint_bible_download
import org.churchpresenter.strings.generated.resources.helper_hint_bible_page
import org.churchpresenter.strings.generated.resources.helper_hint_chord_palette
import org.churchpresenter.strings.generated.resources.helper_hint_chords_switch
import org.churchpresenter.strings.generated.resources.helper_hint_pick_and_edit
import org.churchpresenter.strings.generated.resources.song_add_translation
import org.churchpresenter.strings.generated.resources.song_chords

/**
 * "How do I add…" for what the operator makes themselves — a song's other language, its chords, a
 * Bible translation. Asked or told, each is a tour: the helper shows where, and the typing is theirs.
 */
internal object HowToTopics {
    /** The tour for [normalized], or null when it is none of these. */
    fun find(normalized: String, currentTab: Tabs? = null): GuideTour? = when {
        isSongTranslation(normalized, currentTab) -> songTranslation()
        isSongChords(normalized, currentTab) -> songChords()
        isBibleTranslation(normalized) -> bibleTranslation()
        else -> MediaTopics.find(normalized) ?: AnnouncementTopics.find(normalized)
    }

    /**
     * Whether [normalized] asks about a song in another language — "add a translation to this song",
     * "bilingual hymn", "add a language" — and not about a Bible translation.
     */
    fun isSongTranslation(normalized: String, currentTab: Tabs? = null): Boolean {
        if (Vocabulary.BIBLE_NAMES.any { normalized.containsWordPrefix(it) }) return false
        if (Vocabulary.ADD_LANGUAGE.any { Vocabulary.normalizeLanguage(normalized).containsPhrase(it) }) return true
        val words = normalized.split(' ')
        // On the Songs tab, "translate this" or "add Spanish" is about a song without saying so.
        val aboutSong = words.any { it in Vocabulary.SONG } || currentTab == Tabs.SONGS
        val aboutLanguage = Vocabulary.TRANSLATION.any { normalized.containsWordPrefix(it) } ||
            words.any { it in Vocabulary.LANGUAGE_NAMES } ||
            Vocabulary.OTHER_LANGUAGE.any { normalized.containsPhrase(it) }
        return aboutSong && aboutLanguage
    }

    /** The Songs tab, then the song's Edit button, where another language is added. */
    fun songTranslation() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.EDIT_SONG,
                helperText(Res.string.helper_hint_pick_and_edit, helperText(Res.string.edit_song)),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
            // In the song editor's own window; pressing Edit moves the tour on to it.
            GuideStep(
                GuideTargets.ADD_SONG_LANGUAGE,
                helperText(Res.string.helper_hint_add_song_language, helperText(Res.string.song_add_translation)),
            ),
        ),
    )

    /**
     * Whether [normalized] asks about a song's chords — "add chords to a song", "where are the
     * chords". Not chords on a stage monitor or an output, which are a profile's to show.
     */
    fun isSongChords(normalized: String, currentTab: Tabs? = null): Boolean {
        val words = normalized.split(' ')
        if (words.any { it in Vocabulary.SCREEN } || normalized.containsWordPrefix("stage")) return false
        val aboutSong = words.any { it in Vocabulary.SONG } || currentTab == Tabs.SONGS
        return normalized.containsWordPrefix(Vocabulary.CHORD) ||
            aboutSong && words.any { it in Vocabulary.CHORD_MISSPELLINGS }
    }

    /** The Songs tab, Edit, then the editor's Chords switch and the chords to insert from. */
    fun songChords() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.EDIT_SONG,
                helperText(Res.string.helper_hint_pick_and_edit, helperText(Res.string.edit_song)),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
            GuideStep(
                GuideTargets.SONG_CHORDS_SWITCH,
                helperText(Res.string.helper_hint_chords_switch, helperText(Res.string.song_chords)),
            ),
            GuideStep(GuideTargets.SONG_CHORD_PALETTE, helperText(Res.string.helper_hint_chord_palette)),
        ),
    )

    /**
     * Whether [normalized] asks to add a Bible translation — "add a Bible translation", "download
     * the Russian Bible", "get another version of the Bible".
     */
    fun isBibleTranslation(normalized: String): Boolean {
        if (Vocabulary.BIBLE_NAMES.none { normalized.containsWordPrefix(it) }) return false
        // A chapter or verse number makes it a verse to show, in whichever Bible it names.
        if (normalized.any { it.isDigit() }) return false
        val words = normalized.split(' ')
        return Vocabulary.TRANSLATION.any { normalized.containsWordPrefix(it) } ||
            words.any { it in Vocabulary.GET || it in Vocabulary.LANGUAGE_NAMES }
    }

    /** Settings, Download Bibles, a Bible from the catalog, then the Bible page's Add translation. */
    fun bibleTranslation() = GuideTour(
        listOf(
            GuideStep(GuideTargets.SETTINGS_BUTTON, helperText(Res.string.helper_hint_settings)),
            GuideStep(
                GuideTargets.BIBLE_DOWNLOAD,
                helperText(Res.string.helper_hint_bible_download, helperText(Res.string.bible_catalog_button)),
                before = HelperAction.OpenSettings(SettingsPage.SYSTEM),
            ),
            // In the Download Bibles window; pressing a Bible's Download moves the tour on.
            GuideStep(
                GuideTargets.BIBLE_CATALOG_LIST,
                helperText(Res.string.helper_hint_bible_catalog, helperText(Res.string.bible_catalog_download)),
            ),
            // No page switch of its own: it would close the download catalog the steps before opened.
            GuideStep(GuideTargets.settingsPage(SettingsPage.BIBLE), helperText(Res.string.helper_hint_bible_page)),
            GuideStep(
                GuideTargets.BIBLE_ADD_TRANSLATION,
                helperText(Res.string.helper_hint_bible_add_translation, helperText(Res.string.add_bible_translation)),
            ),
        ),
    )
}
