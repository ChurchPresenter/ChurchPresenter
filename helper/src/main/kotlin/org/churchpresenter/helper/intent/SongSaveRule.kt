package org.churchpresenter.helper.intent

import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.helper_hint_pick_and_edit
import org.churchpresenter.strings.generated.resources.helper_hint_song_save
import org.churchpresenter.strings.generated.resources.helper_song_save_check
import org.churchpresenter.strings.generated.resources.helper_song_save_follower
import org.churchpresenter.strings.generated.resources.helper_song_save_no_folder

private val SAVING = listOf("save", "saving", "saved", "saves")

/** Saving that did not happen: "save doesn't work", "I click save but nothing happens", "can't save". */
private val SAVE_TROUBLE = listOf(
    "not working", "doesn't work", "does not work", "isn't working", "won't", "wont", "can't", "cannot",
    "can not", "nothing happens", "does nothing", "didn't", "did not", "doesn't save", "not saving", "not saved",
    "grey", "gray", "greyed", "grayed", "disabled", "fail", "fails", "failed", "error", "but",
)

/** Saving something other than a song, which these answers are not about. */
private val OTHER_THINGS = listOf("schedule", "template", "preset", "service", "settings", "profile", "picture")

/**
 * Saving a song. Asked how, it rings the editor's Save; told saving does not work, it says why it
 * most likely does not, from what the app knows: a computer that follows another cannot save songs,
 * one with no song folder has nowhere to save them, and otherwise the editor's own rules.
 */
internal fun songSaveRule(r: Request): Resolution? {
    if (!r.hasPhrase(SAVING) || r.hasPhrase(OTHER_THINGS)) return null
    val aboutSongs = r.has(Vocabulary.SONG) || r.context.currentTab == Tabs.SONGS
    if (!aboutSongs) return null
    return act(if (r.hasPhrase(SAVE_TROUBLE)) saveTrouble(r.context) else HelperAction.Highlight(saveSongTour()))
}

/** Why a song would not save, most certain reason first. */
internal fun saveTrouble(context: ResolveContext): HelperAction = when {
    context.followsAnotherComputer -> HelperAction.Say(helperText(Res.string.helper_song_save_follower))
    !context.songFolderSet -> HelperAction.Say(
        helperText(Res.string.helper_song_save_no_folder),
        offer = HelperAction.OpenSettings(SettingsPage.SYSTEM),
    )
    else -> HelperAction.Say(
        helperText(Res.string.helper_song_save_check),
        offer = HelperAction.OpenSettings(SettingsPage.SYSTEM),
    )
}

/** The Songs tab, the song's Edit button, then the editor's Save. */
internal fun saveSongTour() = GuideTour(
    listOf(
        tabStep(Tabs.SONGS),
        GuideStep(
            GuideTargets.EDIT_SONG,
            helperText(Res.string.helper_hint_pick_and_edit, helperText(Res.string.edit_song)),
            before = HelperAction.SelectTab(Tabs.SONGS),
        ),
        // In the song editor's own window; pressing Edit moves the tour on to it.
        GuideStep(GuideTargets.SONG_SAVE, helperText(Res.string.helper_hint_song_save)),
    ),
)
