package org.churchpresenter.sharedui.guide

import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideTargetsTest {

    private val named = listOf(
        GuideTargets.SCHEDULE_PANEL,
        GuideTargets.LIVE_PREVIEW,
        GuideTargets.TOGGLE_OUTPUTS,
        GuideTargets.CLEAR_OUTPUT,
        GuideTargets.TAKE,
        GuideTargets.COMPANION_SIDEBAR,
        GuideTargets.BACKGROUND_BUTTON,
        GuideTargets.SETTINGS_BUTTON,
        GuideTargets.NEW_SONG,
        GuideTargets.EDIT_SONG,
        GuideTargets.ADD_SONG_LANGUAGE,
        GuideTargets.SONG_CHORDS_SWITCH,
        GuideTargets.SONG_CHORD_PALETTE,
        GuideTargets.BIBLE_DOWNLOAD,
        GuideTargets.BIBLE_ADD_TRANSLATION,
        GuideTargets.BIBLE_CATALOG_LIST,
        GuideTargets.PICTURES_SELECT_FOLDER,
        GuideTargets.PICTURES_GO_LIVE,
        GuideTargets.PICTURES_PLAY,
        GuideTargets.PRESENTATION_SELECT_FILE,
        GuideTargets.PRESENTATION_GO_LIVE,
        GuideTargets.MEDIA_SELECT_FILE,
        GuideTargets.MEDIA_GO_LIVE,
        GuideTargets.LOWER_THIRD_GENERATE,
        GuideTargets.LOWER_THIRD_GO_LIVE,
        GuideTargets.LOWER_THIRD_NAME,
        GuideTargets.LOWER_THIRD_INFO,
        GuideTargets.LOWER_THIRD_SAVE,
        GuideTargets.ANNOUNCEMENT_TEXT,
        GuideTargets.ANNOUNCEMENT_GO_LIVE,
        GuideTargets.TIMER_GO_LIVE,
        GuideTargets.PROFILE_NEW,
        GuideTargets.PROFILE_NAME,
        GuideTargets.SCREEN_PROFILE_PICKER,
        GuideTargets.IDENTIFY_SCREENS,
        GuideTargets.CALENDAR_SYNC,
        GuideTargets.QA_REMOTE,
        GuideTargets.QA_PUBLIC_ACCESS,
        GuideTargets.SONG_TEMPO,
        GuideTargets.PROFILE_CONTENT_PAGE,
        GuideTargets.STAGE_LAYOUT_PAGE,
        GuideTargets.STAGE_ZONES,
        GuideTargets.STAGE_ARRANGEMENT,
        GuideTargets.STAGE_SHOW_CHORDS,
        GuideTargets.STAGE_TEXT_ZONE,
        GuideTargets.ANNOUNCEMENT_TO_STAGE,
        GuideTargets.TIMER_TO_STAGE,
        GuideTargets.SONG_SEARCH,
        GuideTargets.SONG_SEARCH_FILTER,
        GuideTargets.SONG_FAVORITES,
        GuideTargets.SONG_BACKGROUND,
        GuideTargets.BIBLE_SEARCH,
        GuideTargets.BIBLE_SEARCH_MODE,
        GuideTargets.BIBLE_VERSES,
        GuideTargets.BIBLE_HISTORY,
        GuideTargets.SONG_LYRICS,
        GuideTargets.PROFILE_SONGS_PAGE,
        GuideTargets.PROFILE_BIBLE_PAGE,
        GuideTargets.PROFILE_TEXT_FONT,
        GuideTargets.PROFILE_TEXT_SIZE,
        GuideTargets.PROFILE_TEXT_STYLE,
        GuideTargets.PROFILE_TEXT_ALIGNMENT,
        GuideTargets.PROFILE_TEXT_SHADOW,
        GuideTargets.PROFILE_VERTICAL_ALIGNMENT,
        GuideTargets.PROFILE_MARGINS,
        GuideTargets.PROFILE_END_MARKER,
        GuideTargets.BIBLE_CROSS_REFS,
        GuideTargets.PLANNING_CENTER_IMPORT,
        GuideTargets.SERVER_ENABLE,
        GuideTargets.SERVER_QR,
        GuideTargets.WEB_URL,
        GuideTargets.WEB_GO_LIVE,
    )

    private val built = Tabs.entries.map { GuideTargets.mainTab(it) } +
        SettingsPage.entries.map { GuideTargets.settingsPage(it) } +
        listOf(
            GuideTargets.option("group", "value"),
            GuideTargets.displayMode("fullscreen"),
            GuideTargets.stageContent("BIBLE"),
            GuideTargets.timerMode("duration"),
            GuideTargets.lookElement("SONG_LYRICS"),
        )

    @Test
    fun `every target has its own id, so two controls never answer to one`() {
        val ids = (named + built).map { it.id }
        assertEquals(emptySet(), ids.groupBy { it }.filterValues { it.size > 1 }.keys)
    }

    @Test
    fun `every id is plain dotted words`() {
        val bad = (named + built).map { it.id }.filterNot { it.matches(Regex("[A-Za-z0-9_.]+")) }
        assertEquals(emptyList(), bad)
    }

    @Test
    fun `the built targets say what they are built from`() {
        assertEquals("tab.BIBLE", GuideTargets.mainTab(Tabs.BIBLE).id)
        assertEquals("settings.PROFILES", GuideTargets.settingsPage(SettingsPage.PROFILES).id)
        assertEquals("option.displayMode.stage", GuideTargets.displayMode("stage").id)
        assertEquals("option.lookElement.SONG_TITLE", GuideTargets.lookElement("SONG_TITLE").id)
        assertTrue(GuideTargets.stageContent("CLOCK").id.endsWith("CLOCK"))
        assertTrue(GuideTargets.timerMode("clock").id.endsWith("clock"))
    }
}
