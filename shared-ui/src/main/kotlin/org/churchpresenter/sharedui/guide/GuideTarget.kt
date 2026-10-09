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

    /** The Companion surface routed to the right sidebar, from the divider above it down. */
    val COMPANION_SIDEBAR = GuideTarget("preview.companion")
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
    val LOWER_THIRD_GENERATE = GuideTarget("lowerThird.generate")
    val LOWER_THIRD_GO_LIVE = GuideTarget("lowerThird.goLive")
    val LOWER_THIRD_NAME = GuideTarget("lottieGen.name")
    val LOWER_THIRD_INFO = GuideTarget("lottieGen.info")
    val LOWER_THIRD_SAVE = GuideTarget("lottieGen.save")
    val ANNOUNCEMENT_TEXT = GuideTarget("announcements.text")
    val ANNOUNCEMENT_GO_LIVE = GuideTarget("announcements.goLive")
    val TIMER_GO_LIVE = GuideTarget("announcements.timerGoLive")
    val PROFILE_NEW = GuideTarget("settings.profiles.new")
    val PROFILE_NAME = GuideTarget("settings.profiles.name")
    val SCREEN_PROFILE_PICKER = GuideTarget("settings.projection.screenProfile")
    val IDENTIFY_SCREENS = GuideTarget("settings.projection.identify")
    val CALENDAR_SYNC = GuideTarget("settings.server.calendarSync")
    val QA_REMOTE = GuideTarget("qa.remote")
    val QA_PUBLIC_ACCESS = GuideTarget("qa.publicAccess")
    val SONG_TEMPO = GuideTarget("songEditor.tempo")
    val PROFILE_CONTENT_PAGE = GuideTarget("settings.profiles.contentPage")
    val STAGE_LAYOUT_PAGE = GuideTarget("settings.profiles.stageLayoutPage")
    val STAGE_ZONES = GuideTarget("settings.profiles.stageZones")
    val STAGE_ARRANGEMENT = GuideTarget("settings.profiles.stageArrangement")
    val STAGE_SHOW_CHORDS = GuideTarget("settings.profiles.stageShowChords")
    val STAGE_TEXT_ZONE = GuideTarget("settings.profiles.stageTextZone")
    val ANNOUNCEMENT_TO_STAGE = GuideTarget("announcements.toStage")
    val TIMER_TO_STAGE = GuideTarget("announcements.timerToStage")
    val SONG_SEARCH = GuideTarget("songs.search")
    val SONG_SEARCH_FILTER = GuideTarget("songs.searchFilter")
    val SONG_FAVORITES = GuideTarget("songs.favorites")
    val SONG_BACKGROUND = GuideTarget("songEditor.background")
    val BIBLE_SEARCH = GuideTarget("bible.search")
    val BIBLE_SEARCH_MODE = GuideTarget("bible.searchMode")
    val BIBLE_VERSES = GuideTarget("bible.verses")
    val BIBLE_HISTORY = GuideTarget("bible.history")
    val SONG_LYRICS = GuideTarget("songEditor.lyrics")
    val PROFILE_SONGS_PAGE = GuideTarget("settings.profiles.songsPage")
    val PROFILE_BIBLE_PAGE = GuideTarget("settings.profiles.biblePage")
    val PROFILE_TEXT_FONT = GuideTarget("settings.profiles.textFont")
    val PROFILE_TEXT_SIZE = GuideTarget("settings.profiles.textSize")
    val PROFILE_TEXT_STYLE = GuideTarget("settings.profiles.textStyle")
    val PROFILE_TEXT_ALIGNMENT = GuideTarget("settings.profiles.textAlignment")
    val PROFILE_TEXT_SHADOW = GuideTarget("settings.profiles.textShadow")
    val PROFILE_VERTICAL_ALIGNMENT = GuideTarget("settings.profiles.verticalAlignment")
    val PROFILE_MARGINS = GuideTarget("settings.profiles.margins")
    val PROFILE_END_MARKER = GuideTarget("settings.profiles.endMarker")
    val BIBLE_CROSS_REFS = GuideTarget("bible.crossRefs")
    val PLANNING_CENTER_IMPORT = GuideTarget("schedule.planningCenter")
    val SERVER_ENABLE = GuideTarget("settings.server.enable")
    val SERVER_QR = GuideTarget("settings.server.qr")
    val WEB_URL = GuideTarget("web.url")
    val WEB_GO_LIVE = GuideTarget("web.goLive")
    val SONGS_ADD_TO_SCHEDULE = GuideTarget("songs.addToSchedule")
    val BIBLE_ADD_TO_SCHEDULE = GuideTarget("bible.addToSchedule")
    val PICTURES_ADD_TO_SCHEDULE = GuideTarget("pictures.addToSchedule")
    val PRESENTATION_ADD_TO_SCHEDULE = GuideTarget("presentation.addToSchedule")
    val MEDIA_ADD_TO_SCHEDULE = GuideTarget("media.addToSchedule")
    val LOWER_THIRD_ADD_TO_SCHEDULE = GuideTarget("lowerThird.addToSchedule")
    val ANNOUNCEMENT_ADD_TO_SCHEDULE = GuideTarget("announcements.addToSchedule")
    val WEB_ADD_TO_SCHEDULE = GuideTarget("web.addToSchedule")
    val CANVAS_ADD_TO_SCHEDULE = GuideTarget("canvas.addToSchedule")
    val SCHEDULE_NEW = GuideTarget("schedule.new")
    val SCHEDULE_OPEN = GuideTarget("schedule.open")
    val SCHEDULE_SAVE = GuideTarget("schedule.save")
    val SCHEDULE_ADD_FILES = GuideTarget("schedule.addFiles")

    /** The schedule's first row: dragged to reorder, right-clicked for its menu. */
    val SCHEDULE_FIRST_ROW = GuideTarget("schedule.firstRow")
    val NDI_ADD = GuideTarget("settings.projection.ndiAdd")
    val OMT_ADD = GuideTarget("settings.projection.omtAdd")
    val BROWSER_SOURCE_ADD = GuideTarget("settings.projection.browserSourceAdd")

    /** The first NDI / Browser Source output's name field, once one is added. */
    val NDI_FIRST_NAME = GuideTarget("settings.projection.ndiFirstName")
    val BROWSER_SOURCE_FIRST_NAME = GuideTarget("settings.projection.browserSourceFirstName")
    val OBS_CONNECT = GuideTarget("settings.integrations.obsConnect")
    val OBS_DEFAULT_SCENE = GuideTarget("settings.integrations.obsDefaultScene")
    val ATEM_TEST_CONNECTION = GuideTarget("settings.atem.testConnection")
    val ATEM_DSK_SWITCH = GuideTarget("settings.atem.dskSwitch")
    val PCO_CONNECT = GuideTarget("planningCenter.connect")
    val PCO_SERVICE_TYPE = GuideTarget("planningCenter.serviceType")
    val PCO_PLAN = GuideTarget("planningCenter.plan")
    val PCO_IMPORT = GuideTarget("planningCenter.import")
    val SONG_AUTHOR = GuideTarget("songEditor.author")
    val SONG_COMPOSER = GuideTarget("songEditor.composer")
    val SONG_CCLI = GuideTarget("songEditor.ccli")
    val SONG_SONGBOOK = GuideTarget("songEditor.songbook")
    val SONG_NUMBER = GuideTarget("songEditor.number")
    val SONG_TUNE = GuideTarget("songEditor.tune")
    val SONG_CAPO = GuideTarget("songEditor.capo")

    /** One choice, [value], of a segmented control or picker named [group] — where to click, exactly. */
    fun option(group: String, value: String): GuideTarget = GuideTarget("option.$group.$value")

    /** A profile's display mode segment, by its `Constants.DISPLAY_MODE_*` value. */
    fun displayMode(mode: String): GuideTarget = option("displayMode", mode)

    /** A stage monitor profile's zone picker for one kind of content, by its `StageMonitorContentType` name. */
    fun stageContent(type: String): GuideTarget = option("stageContent", type)

    /** The Announcements timer's mode segment, by its `Constants.TIMER_MODE_*` value. */
    fun timerMode(mode: String): GuideTarget = option("timerMode", mode)

    /** A Songs or Bible page's element chip — which part the Text rows style — by the element's name. */
    fun lookElement(name: String): GuideTarget = option("lookElement", name)

    /** A Profiles settings row, by the string key of its label. */
    fun settingsRow(labelKey: String): GuideTarget = GuideTarget("settingsRow.$labelKey")

    /**
     * The profile picker on the Projection page's card for output [index] of [kind]: `screen`, `ndi`,
     * `omt` or `browser`.
     */
    fun outputProfilePicker(kind: String, index: Int): GuideTarget =
        GuideTarget("settings.projection.$kind.$index.profile")

    /** One shortcut's row in the Keyboard Shortcuts window, by its `ShortcutAction` name. */
    fun shortcutRow(actionName: String): GuideTarget = GuideTarget("shortcuts.$actionName")

    /** The main window's tab for [tab]. */
    fun mainTab(tab: Tabs): GuideTarget = GuideTarget("tab.${tab.name}")

    /** The Settings dialog's tab for [page]. */
    fun settingsPage(page: SettingsPage): GuideTarget = GuideTarget("settings.${page.name}")
}
