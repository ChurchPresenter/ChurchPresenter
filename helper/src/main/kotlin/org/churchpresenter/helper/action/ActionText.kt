package org.churchpresenter.helper.action

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.display.screenLabel
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_confirm_assign
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
    HelperAction.UndoLast -> undoLabel
        ?.let { helperText(Res.string.helper_confirm_undo, it) }
        ?: helperText(Res.string.helper_nothing_to_undo)
}

/** The short label a choice between actions shows: the scope, when the choices differ only in that. */
fun HelperAction.optionLabel(): HelperText = when (this) {
    is HelperAction.SetBackgroundColor -> scopeName(scope)
    is HelperAction.ChangeFontSize -> scopeName(scope)
    else -> describe()
}
