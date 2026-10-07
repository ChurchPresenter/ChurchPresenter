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
import org.churchpresenter.strings.generated.resources.helper_hint_server_page
import org.churchpresenter.strings.generated.resources.helper_hint_settings
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
        listOf("remote", "phone", "tablet", "companion", "mobile", "ipad") to ::remoteServer,
        listOf(
            "schedule", "order of service", "run of show", "playlist", "plan a service", "plan the service",
            "plan service", "service plan",
        ) to
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
    fun find(normalized: String): GuideTour? =
        // Before the topics: "add a song translation" also says "add a song".
        HowToTopics.find(normalized)
            ?: TOPICS.firstOrNull { (phrases, _) -> phrases.any { normalized.containsPhrase(it) } }?.second?.invoke()
            ?: tabNamed(normalized)?.let { GuideTour(listOf(tabStep(it))) }

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

    private fun remoteServer() = GuideTour(
        listOf(
            GuideStep(GuideTargets.SETTINGS_BUTTON, helperText(Res.string.helper_hint_settings)),
            GuideStep(
                GuideTargets.settingsPage(SettingsPage.SERVER),
                helperText(Res.string.helper_hint_server_page),
                before = HelperAction.OpenSettings(SettingsPage.SERVER),
            ),
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
