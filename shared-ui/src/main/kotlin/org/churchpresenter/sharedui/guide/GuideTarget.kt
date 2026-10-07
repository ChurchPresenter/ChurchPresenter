package org.churchpresenter.sharedui.guide

import org.churchpresenter.sharedui.models.Tabs

/**
 * A control the helper can point at — a tab, a button, a panel. Tagged on the control with
 * [guideTarget]; the helper's spotlight draws a ring over it while it is the session's active target.
 */
@JvmInline
value class GuideTarget(val id: String)

/** The pages of the Settings dialog, by what they are rather than by where they sit in its tab row. */
enum class SettingsPage { SYSTEM, BIBLE, BACKGROUND, PROFILES, PROJECTION, SERVER, ATEM, INTEGRATIONS }

/** Every target the app tags. One place, so the helper and the tagged sites cannot drift apart. */
object GuideTargets {
    val SCHEDULE_PANEL = GuideTarget("schedule.panel")
    val LIVE_PREVIEW = GuideTarget("preview.live")
    val TOGGLE_OUTPUTS = GuideTarget("preview.toggleOutputs")
    val CLEAR_OUTPUT = GuideTarget("preview.clear")
    val TAKE = GuideTarget("preview.take")
    val BACKGROUND_BUTTON = GuideTarget("toolbar.background")
    val SETTINGS_BUTTON = GuideTarget("toolbar.settings")
    val NEW_SONG = GuideTarget("songs.new")
    val EDIT_SONG = GuideTarget("songs.edit")
    val ADD_SONG_LANGUAGE = GuideTarget("songEditor.addLanguage")
    val SONG_CHORDS_SWITCH = GuideTarget("songEditor.chordsSwitch")
    val SONG_CHORD_PALETTE = GuideTarget("songEditor.chordPalette")
    val BIBLE_DOWNLOAD = GuideTarget("settings.system.downloadBibles")
    val BIBLE_ADD_TRANSLATION = GuideTarget("settings.bible.addTranslation")
    val BIBLE_CATALOG_LIST = GuideTarget("bibleCatalog.list")
    val PICTURES_SELECT_FOLDER = GuideTarget("pictures.selectFolder")
    val PICTURES_GO_LIVE = GuideTarget("pictures.goLive")
    val PICTURES_PLAY = GuideTarget("pictures.play")
    val PRESENTATION_SELECT_FILE = GuideTarget("presentation.selectFile")
    val PRESENTATION_GO_LIVE = GuideTarget("presentation.goLive")
    val MEDIA_SELECT_FILE = GuideTarget("media.selectFile")
    val MEDIA_GO_LIVE = GuideTarget("media.goLive")

    /** The main window's tab for [tab]. */
    fun mainTab(tab: Tabs): GuideTarget = GuideTarget("tab.${tab.name}")

    /** The Settings dialog's tab for [page]. */
    fun settingsPage(page: SettingsPage): GuideTarget = GuideTarget("settings.${page.name}")
}
