package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_history
import org.churchpresenter.strings.generated.resources.bible_cross_references_title
import org.churchpresenter.strings.generated.resources.bible_cross_references_enable
import org.churchpresenter.strings.generated.resources.helper_hint_bible_history
import org.churchpresenter.strings.generated.resources.helper_hint_bible_cross_refs
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.filter
import org.churchpresenter.strings.generated.resources.helper_hint_bible_multi_verse
import org.churchpresenter.strings.generated.resources.helper_hint_bible_search
import org.churchpresenter.strings.generated.resources.helper_hint_bible_search_mode
import org.churchpresenter.strings.generated.resources.helper_hint_pick_and_edit
import org.churchpresenter.strings.generated.resources.helper_hint_song_background
import org.churchpresenter.strings.generated.resources.helper_hint_song_favorites
import org.churchpresenter.strings.generated.resources.helper_hint_song_search
import org.churchpresenter.strings.generated.resources.helper_hint_song_search_filter
import org.churchpresenter.strings.generated.resources.helper_hint_web_go_live
import org.churchpresenter.strings.generated.resources.helper_hint_web_url
import org.churchpresenter.strings.generated.resources.web_go_live
import org.jetbrains.compose.resources.StringResource

/**
 * Finding a song or a verse, several verses at once, favorites, a song's own background, Planning
 * Center, CCLI reports, the phone remote and websites — asked or told.
 */
internal fun featureTopicsRule(r: Request): Resolution? {
    val text = r.text
    // "Open the web tab" is the tab rules'; "clear the website" the clear rule's.
    if (text.containsPhrase("tab") || r.has(Vocabulary.CLEAR) || r.first in Vocabulary.TAKE_DOWN) return null
    val aboutSongs = r.has(Vocabulary.SONG)
    val aboutBible = Vocabulary.BIBLE_NAMES.any { text.containsWordPrefix(it) } || r.has(Vocabulary.BIBLE)
    // "Search for a Bible translation" is downloading one, not searching in it.
    val aboutLanguage = Vocabulary.TRANSLATION.any { text.containsWordPrefix(it) }
    val searching = !aboutLanguage && (text.containsWordPrefix("search") || r.hasPhrase(Vocabulary.LOOK_UP))
    val action = when {
        r.hasPhrase(Vocabulary.CCLI) -> HelperAction.OpenStatistics
        r.hasPhrase(Vocabulary.PLANNING_CENTER) -> highlight(SetupTopics.planningCenter())
        r.hasPhrase(Vocabulary.PHONE_REMOTE) -> highlight(NavigationTopics.remoteServer())
        Vocabulary.FAVORITE.any { text.containsWordPrefix(it) } || r.hasPhrase(Vocabulary.STAR_A_SONG) ->
            highlight(FeatureTours.favorites())
        aboutSongs && r.has(Vocabulary.BACKGROUND) && r.hasPhrase(Vocabulary.ONE_SONG) ->
            highlight(FeatureTours.songBackground())
        r.hasPhrase(Vocabulary.CROSS_REFS) -> highlight(FeatureTours.crossReferences())
        r.hasPhrase(Vocabulary.BIBLE_HISTORY) -> highlight(FeatureTours.bibleHistory())
        text.any { it.isDigit() } -> null
        r.hasPhrase(Vocabulary.MULTI_VERSE) -> highlight(FeatureTours.multiVerse())
        aboutBible && (searching || r.hasPhrase(Vocabulary.FIND_VERSE)) -> highlight(FeatureTours.bibleSearch())
        aboutSongs && (searching || r.hasPhrase(Vocabulary.FIND_SONG)) -> highlight(FeatureTours.songSearch())
        r.has(Vocabulary.WEBSITE) || r.hasPhrase(Vocabulary.WEB_PAGE) -> highlight(FeatureTours.website())
        else -> null
    }
    return action?.let(::act)
}

private fun highlight(tour: GuideTour) = HelperAction.Highlight(tour)

/** The tours [featureTopicsRule] chooses between. */
internal object FeatureTours {
    fun songSearch() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.SONG_SEARCH,
                helperText(Res.string.helper_hint_song_search),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
            step(GuideTargets.SONG_SEARCH_FILTER, Res.string.helper_hint_song_search_filter, Res.string.filter),
        ),
    )

    fun favorites() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.SONG_FAVORITES,
                helperText(Res.string.helper_hint_song_favorites),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
        ),
    )

    /** Pick the song and Edit it; the Background button is in the editor's own window. */
    fun songBackground() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.EDIT_SONG,
                helperText(Res.string.helper_hint_pick_and_edit, helperText(Res.string.edit_song)),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
            step(GuideTargets.SONG_BACKGROUND, Res.string.helper_hint_song_background, Res.string.background),
        ),
    )

    fun bibleSearch() = GuideTour(
        listOf(
            tabStep(Tabs.BIBLE),
            GuideStep(
                GuideTargets.BIBLE_SEARCH,
                helperText(Res.string.helper_hint_bible_search),
                before = HelperAction.SelectTab(Tabs.BIBLE),
            ),
            GuideStep(GuideTargets.BIBLE_SEARCH_MODE, helperText(Res.string.helper_hint_bible_search_mode)),
        ),
    )

    fun multiVerse() = GuideTour(
        listOf(
            tabStep(Tabs.BIBLE),
            GuideStep(
                GuideTargets.BIBLE_VERSES,
                helperText(Res.string.helper_hint_bible_multi_verse),
                before = HelperAction.SelectTab(Tabs.BIBLE),
            ),
        ),
    )

    fun bibleHistory() = GuideTour(
        listOf(
            tabStep(Tabs.BIBLE),
            GuideStep(
                GuideTargets.BIBLE_HISTORY,
                helperText(Res.string.helper_hint_bible_history, helperText(Res.string.bible_history)),
                before = HelperAction.SelectTab(Tabs.BIBLE),
            ),
        ),
    )

    fun crossReferences() = GuideTour(
        listOf(
            tabStep(Tabs.BIBLE),
            GuideStep(
                GuideTargets.BIBLE_CROSS_REFS,
                helperText(
                    Res.string.helper_hint_bible_cross_refs,
                    helperText(Res.string.bible_cross_references_title),
                    helperText(Res.string.bible_cross_references_enable),
                ),
                before = HelperAction.SelectTab(Tabs.BIBLE),
            ),
        ),
    )

    fun website() = GuideTour(
        listOf(
            tabStep(Tabs.WEB),
            GuideStep(
                GuideTargets.WEB_URL,
                helperText(Res.string.helper_hint_web_url),
                before = HelperAction.SelectTab(Tabs.WEB),
            ),
            step(GuideTargets.WEB_GO_LIVE, Res.string.helper_hint_web_go_live, Res.string.web_go_live),
        ),
    )

    /** A step whose [hint] names the [button] it rings, in the button's own words. */
    private fun step(target: GuideTarget, hint: StringResource, button: StringResource) =
        GuideStep(target, helperText(hint, helperText(button)))
}
