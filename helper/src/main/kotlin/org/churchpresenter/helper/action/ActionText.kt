package org.churchpresenter.helper.action

import org.churchpresenter.helper.HelperText
import org.churchpresenter.strings.generated.resources.helper_open_song_library_compare
import org.churchpresenter.strings.generated.resources.helper_open_calendar
import org.churchpresenter.strings.generated.resources.helper_open_calendar_repeat
import org.churchpresenter.strings.generated.resources.helper_open_calendar_template
import org.churchpresenter.strings.generated.resources.helper_open_calendar_load
import org.churchpresenter.strings.generated.resources.helper_open_calendar_automate
import org.churchpresenter.strings.generated.resources.helper_open_song_library
import org.churchpresenter.strings.generated.resources.helper_open_statistics
import org.churchpresenter.strings.generated.resources.helper_check_updates
import org.churchpresenter.strings.generated.resources.helper_whats_live
import org.churchpresenter.strings.generated.resources.helper_commands_intro
import org.churchpresenter.strings.generated.resources.helper_confirm_schedule_goto
import org.churchpresenter.strings.generated.resources.helper_confirm_schedule_previous
import org.churchpresenter.strings.generated.resources.helper_confirm_schedule_next
import org.churchpresenter.strings.generated.resources.helper_confirm_add_verse
import org.churchpresenter.strings.generated.resources.helper_confirm_add_song
import org.churchpresenter.strings.generated.resources.helper_find_song
import org.churchpresenter.strings.generated.resources.helper_confirm_announcement
import org.churchpresenter.strings.generated.resources.helper_confirm_countdown
import org.churchpresenter.strings.generated.resources.helper_open_converter
import org.churchpresenter.strings.generated.resources.helper_open_converter_documents
import org.churchpresenter.strings.generated.resources.helper_open_converter_from
import org.churchpresenter.helper.display.screenLabel
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_confirm_assign
import org.churchpresenter.strings.generated.resources.helper_greeting
import org.churchpresenter.strings.generated.resources.helper_youre_welcome
import org.churchpresenter.strings.generated.resources.helper_confirm_bg_saved
import org.churchpresenter.strings.generated.resources.helper_confirm_bg_service
import org.churchpresenter.strings.generated.resources.helper_confirm_clear
import org.churchpresenter.strings.generated.resources.helper_confirm_font_bigger
import org.churchpresenter.strings.generated.resources.helper_confirm_font_smaller
import org.churchpresenter.strings.generated.resources.helper_confirm_identify
import org.churchpresenter.strings.generated.resources.helper_confirm_next
import org.churchpresenter.strings.generated.resources.helper_confirm_previous
import org.churchpresenter.strings.generated.resources.helper_confirm_setup_wizard
import org.churchpresenter.strings.generated.resources.helper_confirm_show_tab
import org.churchpresenter.strings.generated.resources.helper_confirm_take
import org.churchpresenter.strings.generated.resources.helper_confirm_toggle_outputs
import org.churchpresenter.strings.generated.resources.helper_confirm_undo
import org.churchpresenter.strings.generated.resources.helper_confirm_verse
import org.churchpresenter.strings.generated.resources.helper_nothing_to_undo
import org.churchpresenter.strings.generated.resources.helper_open_display_setup
import org.churchpresenter.strings.generated.resources.helper_open_settings
import org.churchpresenter.strings.generated.resources.helper_open_shortcuts
import org.churchpresenter.strings.generated.resources.helper_scope_all
import org.churchpresenter.strings.generated.resources.helper_scope_bible
import org.churchpresenter.strings.generated.resources.helper_scope_song
import org.churchpresenter.strings.generated.resources.helper_select_tab
import org.churchpresenter.strings.generated.resources.helper_show_me

/** The name of [scope] inside a sentence: "song", "Bible", "song and Bible". */
fun scopeName(scope: ContentScope): HelperText = helperText(
    when (scope) {
        ContentScope.SONG -> Res.string.helper_scope_song
        ContentScope.BIBLE -> Res.string.helper_scope_bible
        ContentScope.ALL -> Res.string.helper_scope_all
    },
)

/**
 * What the helper asks before doing [this] — or, for what needs no asking, what it is about to do.
 * [undoLabel] names the change an [HelperAction.UndoLast] would take back.
 */
fun HelperAction.describe(undoLabel: HelperText? = null): HelperText = when (this) {
    is HelperAction.ShowBibleVerse -> helperText(Res.string.helper_confirm_verse, display)
    HelperAction.NextSlide -> helperText(Res.string.helper_confirm_next)
    HelperAction.PreviousSlide -> helperText(Res.string.helper_confirm_previous)
    HelperAction.ClearOutput -> helperText(Res.string.helper_confirm_clear)
    HelperAction.Take -> helperText(Res.string.helper_confirm_take)
    is HelperAction.SetBackgroundColor -> when (persistence) {
        Persistence.SAVED -> helperText(Res.string.helper_confirm_bg_saved, scopeName(scope), colorName)
        Persistence.THIS_SERVICE -> helperText(Res.string.helper_confirm_bg_service, colorName)
    }
    is HelperAction.ChangeFontSize -> helperText(
        if (direction > 0) Res.string.helper_confirm_font_bigger else Res.string.helper_confirm_font_smaller,
        scopeName(scope),
    )
    is HelperAction.OpenSettings -> helperText(Res.string.helper_open_settings)
    HelperAction.OpenSetupWizard -> helperText(Res.string.helper_confirm_setup_wizard)
    HelperAction.OpenKeyboardShortcuts -> helperText(Res.string.helper_open_shortcuts)
    HelperAction.StartDisplaySetup -> helperText(Res.string.helper_open_display_setup)
    is HelperAction.AssignAudienceScreen -> helperText(Res.string.helper_confirm_assign, screen.screenLabel())
    HelperAction.IdentifyScreens -> helperText(Res.string.helper_confirm_identify)
    HelperAction.ToggleOutputWindows -> helperText(Res.string.helper_confirm_toggle_outputs)
    is HelperAction.SelectTab -> helperText(Res.string.helper_select_tab, helperTabName(tab))
    is HelperAction.ShowTab -> helperText(Res.string.helper_confirm_show_tab, helperTabName(tab))
    is HelperAction.Highlight -> helperText(Res.string.helper_show_me)
    is HelperAction.ShowShortcut -> helperText(action.descriptionRes)
    is HelperAction.OpenConverter -> when {
        sourceId == DOCUMENTS_SOURCE -> helperText(Res.string.helper_open_converter_documents)
        sourceName != null -> helperText(Res.string.helper_open_converter_from, sourceName)
        else -> helperText(Res.string.helper_open_converter)
    }
    is HelperAction.OpenSongLibrary ->
        helperText(if (compare) Res.string.helper_open_song_library_compare else Res.string.helper_open_song_library)
    is HelperAction.OpenCalendar -> helperText(
        when (topic) {
            CalendarTopic.PLAN -> Res.string.helper_open_calendar
            CalendarTopic.REPEAT -> Res.string.helper_open_calendar_repeat
            CalendarTopic.TEMPLATE -> Res.string.helper_open_calendar_template
            CalendarTopic.LOAD -> Res.string.helper_open_calendar_load
            CalendarTopic.AUTOMATE -> Res.string.helper_open_calendar_automate
        },
    )
    HelperAction.OpenStatistics -> helperText(Res.string.helper_open_statistics)
    is HelperAction.StartCountdown -> helperText(Res.string.helper_confirm_countdown, minutes)
    is HelperAction.ShowAnnouncement -> helperText(Res.string.helper_confirm_announcement, text)
    is HelperAction.FindSong -> helperText(Res.string.helper_find_song, query)
    is HelperAction.AddSongToSchedule -> helperText(Res.string.helper_confirm_add_song, query)
    is HelperAction.AddVerseToSchedule -> helperText(Res.string.helper_confirm_add_verse, display)
    is HelperAction.ScheduleStep ->
        helperText(if (forward) Res.string.helper_confirm_schedule_next else Res.string.helper_confirm_schedule_previous)
    is HelperAction.ScheduleGoTo -> helperText(Res.string.helper_confirm_schedule_goto, name)
    HelperAction.WhatsLive -> helperText(Res.string.helper_whats_live)
    HelperAction.ShowCommands -> helperText(Res.string.helper_commands_intro)
    HelperAction.CheckForUpdates -> helperText(Res.string.helper_check_updates)
    HelperAction.Greet -> helperText(Res.string.helper_greeting)
    HelperAction.Thanks -> helperText(Res.string.helper_youre_welcome)
    HelperAction.UndoLast -> undoLabel
        ?.let { helperText(Res.string.helper_confirm_undo, it) }
        ?: helperText(Res.string.helper_nothing_to_undo)
}

/** The short label a choice between actions shows: the scope, when the choices differ only in that. */
fun HelperAction.optionLabel(): HelperText = when (this) {
    is HelperAction.Highlight -> label ?: describe()
    is HelperAction.SetBackgroundColor -> scopeName(scope)
    is HelperAction.ChangeFontSize -> scopeName(scope)
    else -> describe()
}

/** The converter's id for lyrics read out of PDF, Word, PowerPoint and Keynote files. */
internal const val DOCUMENTS_SOURCE = "documents"
