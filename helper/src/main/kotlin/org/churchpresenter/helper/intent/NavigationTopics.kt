package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.models.labelRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_hint_background
import org.churchpresenter.strings.generated.resources.helper_hint_clear
import org.churchpresenter.strings.generated.resources.helper_hint_live_preview
import org.churchpresenter.strings.generated.resources.helper_hint_new_song
import org.churchpresenter.strings.generated.resources.helper_hint_projection_page
import org.churchpresenter.strings.generated.resources.helper_hint_schedule
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.helper_hint_add_song_language
import org.churchpresenter.strings.generated.resources.helper_hint_settings
import org.churchpresenter.strings.generated.resources.helper_hint_chord_palette
import org.churchpresenter.strings.generated.resources.helper_hint_chords_switch
import org.churchpresenter.strings.generated.resources.helper_hint_pick_and_edit
import org.churchpresenter.strings.generated.resources.song_add_translation
import org.churchpresenter.strings.generated.resources.song_chords
import org.churchpresenter.strings.generated.resources.helper_hint_tab
import org.churchpresenter.strings.generated.resources.helper_hint_take
import org.churchpresenter.strings.generated.resources.helper_hint_toggle_outputs
import org.jetbrains.compose.resources.StringResource

/**
 * "Where is…" and "how do I…": the topics the helper can point at, each a short tour of real controls.
 * Checked in order, so the more specific phrase ("add a song") is tried before the general one ("song").
 */
internal object NavigationTopics {
    private val TOPICS: List<Pair<List<String>, () -> GuideTour>> = listOf(
        listOf("add a song", "new song", "add song", "create a song", "write a song", "create song") to ::newSong,
        listOf("projection", "second screen", "projector", "screens", "displays", "outputs", "monitor") to ::projection,
        listOf("schedule", "order of service", "run of show", "playlist") to
            { tour(GuideTargets.SCHEDULE_PANEL, Res.string.helper_hint_schedule) },
        listOf("clear", "blank the screen", "black out") to
            { tour(GuideTargets.CLEAR_OUTPUT, Res.string.helper_hint_clear) },
        listOf("take", "go live", "send to screen") to { tour(GuideTargets.TAKE, Res.string.helper_hint_take) },
        listOf("background") to { tour(GuideTargets.BACKGROUND_BUTTON, Res.string.helper_hint_background) },
        listOf("settings", "options", "preferences") to
            { tour(GuideTargets.SETTINGS_BUTTON, Res.string.helper_hint_settings) },
        listOf("what is on screen", "live preview", "preview", "what the audience sees") to
            { tour(GuideTargets.LIVE_PREVIEW, Res.string.helper_hint_live_preview) },
        listOf("show the output", "hide the output", "output window") to
            { tour(GuideTargets.TOGGLE_OUTPUTS, Res.string.helper_hint_toggle_outputs) },
    )

    private val TAB_WORDS: List<Pair<List<String>, Tabs>> = listOf(
        listOf("bible", "scripture", "verse") to Tabs.BIBLE,
        listOf("song", "lyrics", "hymn") to Tabs.SONGS,
        listOf("picture", "photo", "image") to Tabs.PICTURES,
        listOf("presentation", "powerpoint", "slides", "keynote", "pdf") to Tabs.PRESENTATION,
        listOf("media", "video", "audio") to Tabs.MEDIA,
        listOf("lower third") to Tabs.LOWER_THIRD,
        listOf("announcement", "timer", "countdown") to Tabs.ANNOUNCEMENTS,
        listOf("web", "website", "browser") to Tabs.WEB,
        listOf("canvas", "camera", "scene") to Tabs.CANVAS,
        listOf("dictionary", "strong") to Tabs.DICTIONARY,
    )

    /** The tour for [normalized], or null when it names nothing the helper knows. */
    fun find(normalized: String): GuideTour? {
        // Before the topics: "add a song translation" also says "add a song".
        if (isSongTranslation(normalized)) return songTranslation()
        if (isSongChords(normalized)) return songChords()
        TOPICS.firstOrNull { (phrases, _) -> phrases.any { normalized.containsPhrase(it) } }?.let { return it.second() }
        val tab = tabNamed(normalized) ?: return null
        return GuideTour(listOf(tabStep(tab)))
    }

    /** The tab [normalized] names, if any. */
    fun tabNamed(normalized: String): Tabs? =
        TAB_WORDS.firstOrNull { (words, _) -> words.any { normalized.containsWordPrefix(it) } }?.second

    private fun newSong() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.NEW_SONG,
                helperText(Res.string.helper_hint_new_song),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
        ),
    )

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

    private fun projection() = GuideTour(
        listOf(
            GuideStep(GuideTargets.SETTINGS_BUTTON, helperText(Res.string.helper_hint_settings)),
            GuideStep(
                GuideTargets.settingsPage(SettingsPage.PROJECTION),
                helperText(Res.string.helper_hint_projection_page),
                before = HelperAction.OpenSettings(SettingsPage.PROJECTION),
            ),
        ),
    )

    private fun tour(target: GuideTarget, hint: StringResource) =
        GuideTour(listOf(GuideStep(target, helperText(hint))))
}

/** "This is the Songs tab", pointing at it. */
fun tabStep(tab: Tabs): GuideStep =
    GuideStep(GuideTargets.mainTab(tab), helperText(Res.string.helper_hint_tab, helperTabName(tab)))

/** [tab]'s name in the tab row, as an argument for a helper line. */
fun helperTabName(tab: Tabs): HelperText = helperText(tab.labelRes)
