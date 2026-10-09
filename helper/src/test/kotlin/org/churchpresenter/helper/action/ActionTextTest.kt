package org.churchpresenter.helper.action

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_confirm_bg_saved
import org.churchpresenter.strings.generated.resources.helper_confirm_bg_service
import org.churchpresenter.strings.generated.resources.helper_confirm_font_bigger
import org.churchpresenter.strings.generated.resources.helper_confirm_font_smaller
import org.churchpresenter.strings.generated.resources.helper_confirm_undo
import org.churchpresenter.strings.generated.resources.helper_nothing_to_undo
import org.churchpresenter.strings.generated.resources.helper_open_converter
import org.churchpresenter.strings.generated.resources.helper_open_converter_documents
import org.churchpresenter.strings.generated.resources.helper_open_converter_from
import org.churchpresenter.strings.generated.resources.helper_scope_all
import org.churchpresenter.strings.generated.resources.helper_scope_bible
import org.churchpresenter.strings.generated.resources.helper_scope_song
import org.churchpresenter.strings.generated.resources.helper_show_me
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ActionTextTest {

    private val screen = HelperScreen(index = 1, isPrimary = false, x = 0, y = 0, width = 1920, height = 1080)
    private val tour = GuideTour(listOf(GuideStep(GuideTargets.TAKE, HelperText.Plain("take"))))

    private val everyAction: List<HelperAction> = listOf(
        HelperAction.ShowBibleVerse("John", 3, 16, 16, "John 3:16"),
        HelperAction.NextSlide,
        HelperAction.PreviousSlide,
        HelperAction.ClearOutput,
        HelperAction.Take,
        HelperAction.SetBackgroundColor(ContentScope.SONG, "#000000", "black", Persistence.SAVED),
        HelperAction.SetBackgroundColor(ContentScope.BIBLE, "#000000", "black", Persistence.THIS_SERVICE),
        HelperAction.ChangeFontSize(ContentScope.ALL, 1),
        HelperAction.ChangeFontSize(ContentScope.ALL, -1),
        HelperAction.OpenSettings(SettingsPage.BIBLE),
        HelperAction.OpenSetupWizard,
        HelperAction.OpenKeyboardShortcuts,
        HelperAction.StartDisplaySetup,
        HelperAction.AssignAudienceScreen(screen),
        HelperAction.IdentifyScreens,
        HelperAction.ToggleOutputWindows,
        HelperAction.SelectTab(Tabs.SONGS),
        HelperAction.ShowTab(Tabs.WEB),
        HelperAction.Highlight(tour),
        HelperAction.ShowShortcut(ShortcutAction.CLEAR_OUTPUT),
        HelperAction.OpenConverter(),
        HelperAction.OpenConverter(DOCUMENTS_SOURCE),
        HelperAction.OpenConverter("openlp", "OpenLP"),
        HelperAction.OpenSongLibrary(),
        HelperAction.OpenSongLibrary(compare = true),
        HelperAction.OpenStatistics,
        HelperAction.StartCountdown(5),
        HelperAction.ShowAnnouncement("Welcome"),
        HelperAction.FindSong("245"),
        HelperAction.AddSongToSchedule("Amazing Grace"),
        HelperAction.AddVerseToSchedule("John", 3, 16, 16, "John 3:16"),
        HelperAction.ScheduleStep(forward = true),
        HelperAction.ScheduleStep(forward = false),
        HelperAction.ScheduleGoTo("sermon"),
        HelperAction.WhatsLive,
        HelperAction.ShowCommands,
        HelperAction.CheckForUpdates,
        HelperAction.Greet,
        HelperAction.Thanks,
        HelperAction.UndoLast,
    ) + CalendarTopic.entries.map { HelperAction.OpenCalendar(it) }

    @Test
    fun `every action has words to ask with and a label to choose by`() {
        everyAction.forEach { action ->
            assertIs<HelperText.Res>(action.describe(), "$action")
            assertIs<HelperText.Res>(action.optionLabel(), "$action")
        }
    }

    @Test
    fun `a background change says whether it is saved or only for now`() {
        val saved = HelperAction.SetBackgroundColor(ContentScope.SONG, "#1565C0", "blue", Persistence.SAVED)
        assertEquals(Res.string.helper_confirm_bg_saved, (saved.describe() as HelperText.Res).res)
        val forNow = saved.copy(persistence = Persistence.THIS_SERVICE)
        assertEquals(Res.string.helper_confirm_bg_service, (forNow.describe() as HelperText.Res).res)
        assertEquals(scopeName(ContentScope.SONG), saved.optionLabel())
    }

    @Test
    fun `a font change says bigger or smaller`() {
        val bigger = HelperAction.ChangeFontSize(ContentScope.BIBLE, 1)
        assertEquals(Res.string.helper_confirm_font_bigger, (bigger.describe() as HelperText.Res).res)
        val smaller = bigger.copy(direction = -1).describe() as HelperText.Res
        assertEquals(Res.string.helper_confirm_font_smaller, smaller.res)
        assertEquals(scopeName(ContentScope.BIBLE), bigger.optionLabel())
    }

    @Test
    fun `the converter says where songs come from`() {
        fun res(action: HelperAction) = (action.describe() as HelperText.Res).res
        assertEquals(Res.string.helper_open_converter, res(HelperAction.OpenConverter()))
        assertEquals(Res.string.helper_open_converter_documents, res(HelperAction.OpenConverter(DOCUMENTS_SOURCE)))
        assertEquals(Res.string.helper_open_converter_from, res(HelperAction.OpenConverter("openlp", "OpenLP")))
    }

    @Test
    fun `undo names what it takes back, or says there is nothing`() {
        val label = HelperText.Plain("the background")
        assertEquals(helperText(Res.string.helper_confirm_undo, label), HelperAction.UndoLast.describe(label))
        assertEquals(helperText(Res.string.helper_nothing_to_undo), HelperAction.UndoLast.describe())
    }

    @Test
    fun `a highlight is labelled by its own label when it has one`() {
        val labelled = HelperAction.Highlight(tour, label = HelperText.Plain("make one"))
        assertEquals(HelperText.Plain("make one"), labelled.optionLabel())
        assertEquals(helperText(Res.string.helper_show_me), HelperAction.Highlight(tour).optionLabel())
    }

    @Test
    fun `each scope has its name`() {
        assertEquals(helperText(Res.string.helper_scope_song), scopeName(ContentScope.SONG))
        assertEquals(helperText(Res.string.helper_scope_bible), scopeName(ContentScope.BIBLE))
        assertEquals(helperText(Res.string.helper_scope_all), scopeName(ContentScope.ALL))
    }
}
