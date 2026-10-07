package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.allows
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_hint_new_song
import org.churchpresenter.strings.generated.resources.helper_hint_schedule
import org.churchpresenter.strings.generated.resources.helper_hint_tab
import org.churchpresenter.strings.generated.resources.helper_hint_toggle_outputs
import org.churchpresenter.strings.generated.resources.helper_suggest_bible_none
import org.churchpresenter.strings.generated.resources.helper_suggest_no_audience
import org.churchpresenter.strings.generated.resources.helper_suggest_outputs_hidden
import org.churchpresenter.strings.generated.resources.helper_suggest_schedule_empty
import org.churchpresenter.strings.generated.resources.helper_suggest_songs_empty
import org.jetbrains.compose.resources.StringResource

/**
 * What the app looks like right now, as far as the helper's suggestions care — plain values the app
 * works out, so the rules below need nothing live.
 */
data class HelperSignals(
    val screenCount: Int = 1,
    val hasAudienceOutput: Boolean = true,
    val outputWindowsShown: Boolean = true,
    val primaryBibleMissing: Boolean = false,
    val songLibraryEmpty: Boolean = false,
    val scheduleEmpty: Boolean = false,
    val anythingLive: Boolean = false,
    val settingsOpen: Boolean = false,
    val firstRunDone: Boolean = true,
) {
    /** Whether the operator is in the middle of something the helper must not interrupt. */
    val isBusy: Boolean get() = anythingLive || settingsOpen || !firstRunDone
}

/** Something the helper noticed and can help with. [id] is what a dismissal is stored under. */
data class Suggestion(val id: String, val text: HelperText, val action: HelperAction)

/** The suggestion ids, stored in [HelperSettings.dismissedSuggestions] — never rename one. */
object SuggestionIds {
    const val NO_AUDIENCE = "display.no_audience"
    const val OUTPUTS_HIDDEN = "display.outputs_hidden"
    const val BIBLE_NONE = "bible.none"
    const val SONGS_EMPTY = "songs.empty"
    const val SCHEDULE_EMPTY = "schedule.empty"
}

/**
 * The suggestions that apply to [signals], most useful first, leaving out what [settings] put away.
 * None at all while something is live, while Settings is open or before the first-run setup is done:
 * the helper never speaks up in the middle of a service.
 */
fun suggestionsFor(signals: HelperSignals, settings: HelperSettings, nowMillis: Long): List<Suggestion> {
    if (!settings.enabled || signals.isBusy) return emptyList()
    return buildList {
        if (signals.screenCount >= 2 && !signals.hasAudienceOutput) {
            val setup = HelperAction.StartDisplaySetup
            add(suggestion(SuggestionIds.NO_AUDIENCE, Res.string.helper_suggest_no_audience, setup))
        }
        if (signals.hasAudienceOutput && !signals.outputWindowsShown) {
            val point = point(step(GuideTargets.TOGGLE_OUTPUTS, Res.string.helper_hint_toggle_outputs))
            add(suggestion(SuggestionIds.OUTPUTS_HIDDEN, Res.string.helper_suggest_outputs_hidden, point))
        }
        if (signals.primaryBibleMissing) {
            val open = HelperAction.OpenSettings(SettingsPage.BIBLE)
            add(suggestion(SuggestionIds.BIBLE_NONE, Res.string.helper_suggest_bible_none, open))
        }
        if (signals.songLibraryEmpty) {
            val songsTab = GuideStep(
                GuideTargets.mainTab(Tabs.SONGS),
                helperText(Res.string.helper_hint_tab, helperTabName(Tabs.SONGS)),
            )
            val openSongs = HelperAction.SelectTab(Tabs.SONGS)
            val newSong = step(GuideTargets.NEW_SONG, Res.string.helper_hint_new_song, openSongs)
            add(suggestion(SuggestionIds.SONGS_EMPTY, Res.string.helper_suggest_songs_empty, point(songsTab, newSong)))
        }
        if (signals.scheduleEmpty) {
            val point = point(step(GuideTargets.SCHEDULE_PANEL, Res.string.helper_hint_schedule))
            add(suggestion(SuggestionIds.SCHEDULE_EMPTY, Res.string.helper_suggest_schedule_empty, point))
        }
    }.filter { settings.allows(it.id, nowMillis) }
}

private fun suggestion(id: String, text: StringResource, action: HelperAction) =
    Suggestion(id, helperText(text), action)

private fun step(target: GuideTarget, hint: StringResource, before: HelperAction? = null) =
    GuideStep(target, helperText(hint), before)

private fun point(vararg steps: GuideStep) = HelperAction.Highlight(GuideTour(steps.toList()))
