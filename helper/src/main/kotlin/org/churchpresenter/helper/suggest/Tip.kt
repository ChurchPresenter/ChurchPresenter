package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.HelperText
import org.churchpresenter.strings.generated.resources.helper_tip_presets
import org.churchpresenter.strings.generated.resources.helper_tip_compare_translations
import org.churchpresenter.strings.generated.resources.helper_tip_song_library
import org.churchpresenter.strings.generated.resources.helper_tip_stt
import org.churchpresenter.strings.generated.resources.helper_tip_song_features
import org.churchpresenter.strings.generated.resources.helper_tip_metronome
import org.churchpresenter.strings.generated.resources.helper_hint_song_tempo
import org.churchpresenter.strings.generated.resources.helper_hint_pick_and_edit
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.song_tempo
import org.churchpresenter.strings.generated.resources.helper_tip_hide_slides
import org.churchpresenter.strings.generated.resources.helper_tip_two_translations
import org.churchpresenter.strings.generated.resources.helper_tip_lottie_background
import org.churchpresenter.helper.intent.tabStep
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.helper_tip_qa_tunnel
import org.churchpresenter.strings.generated.resources.helper_hint_qa_remote
import org.churchpresenter.strings.generated.resources.helper_hint_qa_public_access
import org.churchpresenter.strings.generated.resources.tooltip_qa_remote
import org.churchpresenter.strings.generated.resources.qa_enable_public_access
import org.churchpresenter.helper.action.CalendarTopic
import org.churchpresenter.strings.generated.resources.helper_tip_calendar_cues
import org.churchpresenter.strings.generated.resources.helper_tip_calendar
import org.churchpresenter.strings.generated.resources.helper_tip_calendar_sync
import org.churchpresenter.strings.generated.resources.helper_hint_calendar_sync
import org.churchpresenter.strings.generated.resources.calendar_sync_enable
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.strings.generated.resources.helper_hint_identify
import org.churchpresenter.strings.generated.resources.identify_screen
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_tip_auto_fit
import org.churchpresenter.strings.generated.resources.helper_tip_identify
import org.churchpresenter.strings.generated.resources.helper_tip_profiles
import org.churchpresenter.strings.generated.resources.helper_tip_quick_background
import org.churchpresenter.strings.generated.resources.helper_tip_remote
import org.churchpresenter.strings.generated.resources.helper_tip_schedule_drag
import org.churchpresenter.strings.generated.resources.helper_tip_shortcut
import org.churchpresenter.strings.generated.resources.helper_tip_tell_me
import org.churchpresenter.strings.generated.resources.helper_tip_verse_typing

/** A tip of the day, with what the helper can open to show it. */
data class Tip(val text: HelperText, val action: HelperAction? = null)

/** The shortcuts worth a tip — the everyday ones, not the whole list. */
private val TIP_SHORTCUTS = listOf(
    ShortcutAction.CLEAR_OUTPUT,
    ShortcutAction.TAKE,
    ShortcutAction.SONGS_NEXT_SECTION,
    ShortcutAction.BIBLE_NEXT_VERSE,
    ShortcutAction.SWITCH_TO_BIBLE,
    ShortcutAction.SWITCH_TO_SONGS,
    ShortcutAction.ADD_TO_SCHEDULE,
    ShortcutAction.UNDO,
    ShortcutAction.QUICK_BACKGROUND_RESET,
)

private val FEATURE_TIPS = listOf(
    Tip(helperText(Res.string.helper_tip_tell_me)),
    // Shows where: the Identify button on the Projection page, which numbers the screens.
    Tip(
        helperText(Res.string.helper_tip_identify),
        HelperAction.Highlight(
            GuideTour(
                listOf(
                    GuideStep(
                        GuideTargets.IDENTIFY_SCREENS,
                        helperText(Res.string.helper_hint_identify, helperText(Res.string.identify_screen)),
                        before = HelperAction.OpenSettings(SettingsPage.PROJECTION),
                    ),
                ),
            ),
        ),
    ),
    Tip(helperText(Res.string.helper_tip_quick_background)),
    Tip(helperText(Res.string.helper_tip_profiles), HelperAction.OpenSettings(SettingsPage.PROFILES)),
    Tip(helperText(Res.string.helper_tip_auto_fit)),
    Tip(helperText(Res.string.helper_tip_schedule_drag)),
    Tip(helperText(Res.string.helper_tip_verse_typing)),
    Tip(helperText(Res.string.helper_tip_remote), HelperAction.OpenSettings(SettingsPage.SERVER)),
    Tip(helperText(Res.string.helper_tip_calendar), HelperAction.OpenCalendar()),
    Tip(helperText(Res.string.helper_tip_presets), HelperAction.OpenCalendar()),
    Tip(helperText(Res.string.helper_tip_lottie_background), HelperAction.OpenSettings(SettingsPage.BACKGROUND)),
    Tip(helperText(Res.string.helper_tip_two_translations), HelperAction.OpenSettings(SettingsPage.PROFILES)),
    Tip(helperText(Res.string.helper_tip_hide_slides), HelperAction.SelectTab(Tabs.PICTURES)),
    Tip(helperText(Res.string.helper_tip_song_features), HelperAction.SelectTab(Tabs.SONGS)),
    Tip(helperText(Res.string.helper_tip_stt), HelperAction.SelectTab(Tabs.STT)),
    Tip(helperText(Res.string.helper_tip_song_library), HelperAction.OpenSongLibrary()),
    Tip(helperText(Res.string.helper_tip_compare_translations), HelperAction.OpenSongLibrary(compare = true)),
    // Shows where: the song editor's Tempo, reached through the Songs tab and Edit Song.
    Tip(
        helperText(Res.string.helper_tip_metronome),
        HelperAction.Highlight(
            GuideTour(
                listOf(
                    tabStep(Tabs.SONGS),
                    GuideStep(
                        GuideTargets.EDIT_SONG,
                        helperText(Res.string.helper_hint_pick_and_edit, helperText(Res.string.edit_song)),
                        before = HelperAction.SelectTab(Tabs.SONGS),
                    ),
                    GuideStep(
                        GuideTargets.SONG_TEMPO,
                        helperText(Res.string.helper_hint_song_tempo, helperText(Res.string.song_tempo)),
                    ),
                ),
            ),
        ),
    ),
    // Shows where: the Q&A tab's sharing dialog, and its public access (the tunnel).
    Tip(
        helperText(Res.string.helper_tip_qa_tunnel),
        HelperAction.Highlight(
            GuideTour(
                listOf(
                    tabStep(Tabs.QA),
                    GuideStep(
                        GuideTargets.QA_REMOTE,
                        helperText(Res.string.helper_hint_qa_remote, helperText(Res.string.tooltip_qa_remote)),
                        before = HelperAction.SelectTab(Tabs.QA),
                    ),
                    GuideStep(
                        GuideTargets.QA_PUBLIC_ACCESS,
                        helperText(
                            Res.string.helper_hint_qa_public_access,
                            helperText(Res.string.qa_enable_public_access),
                        ),
                    ),
                ),
            ),
        ),
    ),
    Tip(helperText(Res.string.helper_tip_calendar_cues), HelperAction.OpenCalendar(CalendarTopic.AUTOMATE)),
    // Shows where: the sync switch on the Server page.
    Tip(
        helperText(Res.string.helper_tip_calendar_sync),
        HelperAction.Highlight(
            GuideTour(
                listOf(
                    GuideStep(
                        GuideTargets.CALENDAR_SYNC,
                        helperText(Res.string.helper_hint_calendar_sync, helperText(Res.string.calendar_sync_enable)),
                        before = HelperAction.OpenSettings(SettingsPage.SERVER),
                    ),
                ),
            ),
        ),
    ),
)

/**
 * Every tip, feature tips and shortcut tips taking turns. A shortcut tip names the key the operator
 * actually has bound, and a shortcut with none bound is left out rather than shown as nothing.
 */
fun allTips(shortcuts: ShortcutMap): List<Tip> {
    val shortcutTips = TIP_SHORTCUTS.mapNotNull { action ->
        if (shortcuts.chordsFor(action).isEmpty()) return@mapNotNull null
        val what = helperText(action.descriptionRes)
        val text = helperText(Res.string.helper_tip_shortcut, what, HelperText.KeyFor(action))
        Tip(text, HelperAction.ShowShortcut(action))
    }
    val interleaved = mutableListOf<Tip>()
    val longest = maxOf(FEATURE_TIPS.size, shortcutTips.size)
    for (i in 0 until longest) {
        FEATURE_TIPS.getOrNull(i)?.let(interleaved::add)
        shortcutTips.getOrNull(i)?.let(interleaved::add)
    }
    return interleaved
}

/** The tip at rotation position [index], wrapping round. */
fun tipAt(tips: List<Tip>, index: Int): Tip? = if (tips.isEmpty()) null else tips[Math.floorMod(index, tips.size)]
