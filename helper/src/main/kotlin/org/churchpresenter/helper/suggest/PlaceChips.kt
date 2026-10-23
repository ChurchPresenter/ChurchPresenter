package org.churchpresenter.helper.suggest

import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.guide.WickPlace
import org.churchpresenter.sharedui.models.Tabs

/**
 * The chips Wick offers unasked at [place]: what can be done from there. A place with nothing that fits
 * offers none — a chip that belongs somewhere else reads as Wick not knowing where the operator is.
 */
fun chipsFor(place: WickPlace): List<SuggestedRequest> = when (place) {
    is WickPlace.Main -> place.tab?.let(::tabChips) ?: SuggestedRequest.DEFAULTS
    is WickPlace.Settings -> place.page?.let(::pageChips).orEmpty()
    WickPlace.Other -> emptyList()
}

private fun tabChips(tab: Tabs): List<SuggestedRequest> = when (tab) {
    Tabs.BIBLE -> listOf(
        SuggestedRequest.VERSE,
        SuggestedRequest.BIBLE_SEARCH,
        SuggestedRequest.MULTI_VERSE,
        SuggestedRequest.CROSS_REFS,
    )
    Tabs.SONGS -> listOf(
        SuggestedRequest.NEW_SONG,
        SuggestedRequest.SONG_SEARCH,
        SuggestedRequest.CHORDS,
        SuggestedRequest.EDIT_LYRICS,
    )
    Tabs.PICTURES -> listOf(SuggestedRequest.PICTURES, SuggestedRequest.SLIDESHOW)
    Tabs.PRESENTATION -> listOf(SuggestedRequest.PRESENTATION, SuggestedRequest.NEXT_SLIDE)
    Tabs.MEDIA -> listOf(SuggestedRequest.VIDEO)
    Tabs.LOWER_THIRD -> listOf(SuggestedRequest.LOWER_THIRD, SuggestedRequest.LOWER_THIRD_OUTPUT)
    Tabs.ANNOUNCEMENTS -> listOf(SuggestedRequest.ANNOUNCEMENT, SuggestedRequest.COUNTDOWN, SuggestedRequest.CLOCK)
    Tabs.WEB -> listOf(SuggestedRequest.WEBSITE)
    Tabs.CANVAS, Tabs.QA, Tabs.STT, Tabs.CROSSWORD, Tabs.DICTIONARY, Tabs.COMPANION_SURFACE,
    Tabs.SIGNAGE -> emptyList()
}

private fun pageChips(page: SettingsPage): List<SuggestedRequest> = when (page) {
    SettingsPage.SYSTEM -> listOf(SuggestedRequest.SHORTCUTS)
    SettingsPage.BIBLE -> listOf(SuggestedRequest.BIBLE_TRANSLATION)
    SettingsPage.BACKGROUND -> listOf(SuggestedRequest.BACKGROUND)
    SettingsPage.PROFILES -> listOf(
        SuggestedRequest.TEXT_SIZE,
        SuggestedRequest.LYRICS_STYLE,
        SuggestedRequest.LYRICS_POSITION,
        SuggestedRequest.TITLE_STYLE,
    )
    SettingsPage.PROJECTION -> listOf(
        SuggestedRequest.PROJECTOR,
        SuggestedRequest.IDENTIFY,
        SuggestedRequest.STAGE_MONITOR,
        SuggestedRequest.FULL_SCREEN_OUTPUT,
    )
    SettingsPage.SERVER -> listOf(SuggestedRequest.REMOTE)
    SettingsPage.ATEM, SettingsPage.INTEGRATIONS -> emptyList()
}
