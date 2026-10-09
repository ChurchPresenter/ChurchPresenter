package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.CalendarTopic
import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.helper.action.describe
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.helperText
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.dialogs.optionsTabIndexOf
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done
import org.churchpresenter.strings.generated.resources.helper_done_added_schedule
import org.churchpresenter.strings.generated.resources.helper_done_announcement
import org.churchpresenter.strings.generated.resources.helper_done_assign
import org.churchpresenter.strings.generated.resources.helper_done_bg
import org.churchpresenter.strings.generated.resources.helper_done_bg_service
import org.churchpresenter.strings.generated.resources.helper_done_countdown
import org.churchpresenter.strings.generated.resources.helper_done_font
import org.churchpresenter.strings.generated.resources.helper_live_nothing
import org.churchpresenter.strings.generated.resources.helper_nothing_to_undo
import org.churchpresenter.strings.generated.resources.helper_refused_follower
import org.churchpresenter.strings.generated.resources.helper_refused_no_preview
import org.churchpresenter.strings.generated.resources.helper_refused_nothing_to_step
import org.churchpresenter.strings.generated.resources.helper_refused_settings_open
import org.churchpresenter.strings.generated.resources.helper_schedule_at_end
import org.churchpresenter.strings.generated.resources.helper_schedule_empty
import org.churchpresenter.strings.generated.resources.helper_schedule_live
import org.churchpresenter.strings.generated.resources.helper_schedule_not_found
import org.churchpresenter.strings.generated.resources.helper_schedule_ready
import org.churchpresenter.strings.generated.resources.helper_song_found
import org.churchpresenter.strings.generated.resources.helper_song_not_found
import org.churchpresenter.strings.generated.resources.helper_songs_not_loaded
import org.churchpresenter.strings.generated.resources.helper_undo_done
import org.churchpresenter.strings.generated.resources.helper_version
import org.churchpresenter.strings.generated.resources.helper_youre_welcome
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.ProjectionSettings
import org.jetbrains.compose.resources.StringResource
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WickActionsTest {

    // Launched work waits here until a test runs it, so nothing reaches the network by itself.
    private val queued = ArrayDeque<Runnable>()
    private val scope = CoroutineScope(SupervisorJob() + Executor { queued.addLast(it) }.asCoroutineDispatcher())
    private val listeners = mutableListOf<Job>()

    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )
    private val wick = HelperState()
    private val executor = AppHelperExecutor(root)

    @AfterTest
    fun tearDown() {
        listeners.forEach { it.cancel() }
        scope.cancel()
    }

    private fun run(action: HelperAction) = wick.run(action, executor)

    /** Everything [flow] sends from now on, as it is sent. */
    private fun <T> heard(flow: Flow<T>): List<T> {
        val got = mutableListOf<T>()
        listeners += CoroutineScope(Dispatchers.Unconfined).launch { flow.collect { got += it } }
        return got
    }

    /** Runs what was launched, until [done] or two seconds pass. */
    private fun runQueuedUntil(done: () -> Boolean) {
        val until = System.currentTimeMillis() + 2_000
        while (!done() && System.currentTimeMillis() < until) {
            while (queued.isNotEmpty()) queued.removeFirst().run()
            if (!done()) Thread.onSpinWait()
        }
    }

    private fun assertSaid(res: StringResource, vararg args: String, canUndo: Boolean = false) {
        val reply = assertIs<HelperReply.Message>(wick.reply)
        assertEquals(helperText(res, *args).plain(), reply.text.plain())
        assertEquals(canUndo, reply.canUndo)
    }

    /** [HelperText] with every argument flattened to text, so a test need not rebuild the scope names. */
    private fun HelperText.plain(): String = when (this) {
        is HelperText.Plain -> text
        is HelperText.Res -> res.key + args.joinToString(",", "(", ")") { it.plain() }
        is HelperText.Joined -> parts.joinToString(separator) { it.plain() }
        is HelperText.KeyFor -> action.name
    }

    private val doneSaid = helperText(Res.string.helper_done).plain()

    private fun assertDone() =
        assertEquals(doneSaid, assertIs<HelperReply.Message>(wick.reply).text.plain())

    @Test
    fun `show a verse sends it to the Bible tab as a phone would`() {
        val sent = heard(root.companionServer.onSelectBibleVerse)
        run(HelperAction.ShowBibleVerse("John", 3, 16, 17, "John 3:16-17"))
        assertEquals(listOf(SelectBibleVerseRequest("John", 3, 16, verseRange = "16-17")), sent)
        assertDone()
    }

    @Test
    fun `next slide with nothing to step says so`() {
        val sent = heard(root.companionServer.onNextSlide)
        run(HelperAction.NextSlide)
        assertTrue(sent.isEmpty())
        assertSaid(Res.string.helper_refused_nothing_to_step)
    }

    @Test
    fun `previous slide with nothing to step says so`() {
        val sent = heard(root.companionServer.onPreviousSlide)
        run(HelperAction.PreviousSlide)
        assertTrue(sent.isEmpty())
        assertSaid(Res.string.helper_refused_nothing_to_step)
    }

    @Test
    fun `clear the screen clears it`() {
        val sent = heard(root.companionServer.onClear)
        run(HelperAction.ClearOutput)
        assertEquals(1, sent.size)
        assertDone()
    }

    @Test
    fun `take with no preview cued says so`() {
        run(HelperAction.Take)
        assertSaid(Res.string.helper_refused_no_preview)
    }

    @Test
    fun `a saved background color changes the setting and can be undone`() {
        run(HelperAction.SetBackgroundColor(ContentScope.SONG, "#1565C0", "blue", Persistence.SAVED))
        val song = root.appSettings.backgroundSettings.songBackground
        assertEquals(Constants.BACKGROUND_COLOR, song.backgroundType)
        assertEquals("#1565C0", song.backgroundColor)
        val reply = assertIs<HelperReply.Message>(wick.reply)
        assertEquals(Res.string.helper_done_bg.key, (reply.text as HelperText.Res).res.key)
        assertTrue(reply.canUndo)
    }

    @Test
    fun `a background for this service only is put up and nothing is saved`() {
        val before = root.appSettings
        run(HelperAction.SetBackgroundColor(ContentScope.ALL, "#C62828", "red", Persistence.THIS_SERVICE))
        assertEquals("#C62828", root.activeQuickBackground?.background?.color)
        assertEquals(before, root.appSettings)
        assertSaid(Res.string.helper_done_bg_service, "red", canUndo = true)
    }

    @Test
    fun `a background color on an Instance Link follower is refused`() {
        root.mirroredBackgroundSettings = BackgroundSettings()
        val before = root.appSettings
        run(HelperAction.SetBackgroundColor(ContentScope.SONG, "#1565C0", "blue", Persistence.SAVED))
        assertEquals(before, root.appSettings)
        assertSaid(Res.string.helper_refused_follower)
    }

    @Test
    fun `bigger text changes the profiles and can be undone`() {
        val before = root.appSettings
        run(HelperAction.ChangeFontSize(ContentScope.SONG, 1))
        assertNotEquals(before, root.appSettings)
        val reply = assertIs<HelperReply.Message>(wick.reply)
        assertEquals(Res.string.helper_done_font.key, (reply.text as HelperText.Res).res.key)
        assertTrue(reply.canUndo)
    }

    @Test
    fun `a settings change is refused while the Settings window is open`() {
        root.showOptionsDialog = true
        val before = root.appSettings
        run(HelperAction.ChangeFontSize(ContentScope.BIBLE, -1))
        assertEquals(before, root.appSettings)
        assertSaid(Res.string.helper_refused_settings_open)
    }

    @Test
    fun `undo takes back the last change`() {
        val before = root.appSettings
        run(HelperAction.ChangeFontSize(ContentScope.ALL, 1))
        run(HelperAction.UndoLast)
        assertEquals(before, root.appSettings)
        assertSaid(Res.string.helper_undo_done)
    }

    @Test
    fun `undo with nothing done says so`() {
        run(HelperAction.UndoLast)
        assertSaid(Res.string.helper_nothing_to_undo)
    }

    @Test
    fun `open settings opens the named page`() {
        run(HelperAction.OpenSettings(SettingsPage.PROFILES))
        assertTrue(root.showOptionsDialog)
        assertEquals(optionsTabIndexOf(SettingsPage.PROFILES), root.optionsDialogInitialTab)
        assertDone()
    }

    @Test
    fun `open the setup wizard opens it`() {
        root.showSetupWizard = false
        run(HelperAction.OpenSetupWizard)
        assertTrue(root.showSetupWizard)
        assertDone()
    }

    @Test
    fun `keyboard shortcuts opens the list`() {
        run(HelperAction.OpenKeyboardShortcuts)
        assertTrue(root.showKeyboardShortcutsDialog)
        assertDone()
    }

    @Test
    fun `display setup starts its walkthrough`() {
        run(HelperAction.StartDisplaySetup)
        assertEquals(HelperReply.DisplaySetup, wick.reply)
    }

    @Test
    fun `assigning the audience screen saves it and can be undone`() {
        // From the defaults, not whatever this fork's home last saved: an earlier run of this very
        // test leaves the same assignment on disk, and the change would then be no change.
        root.appSettings = root.appSettings.copy(projectionSettings = ProjectionSettings())
        val before = root.appSettings.projectionSettings
        val screen = HelperScreen(index = 1, isPrimary = false, x = 1920, y = 0, width = 1920, height = 1080)
        run(HelperAction.AssignAudienceScreen(screen))
        assertNotEquals(before, root.appSettings.projectionSettings)
        assertEquals(1920, root.appSettings.projectionSettings.getAssignment(0).targetBoundsX)
        val reply = assertIs<HelperReply.Message>(wick.reply)
        assertEquals(Res.string.helper_done_assign.key, (reply.text as HelperText.Res).res.key)
        assertTrue(reply.canUndo)
    }

    @Test
    fun `identify screens shows the outputs and numbers them`() {
        root.presenterManager.setShowPresenterWindow(false)
        run(HelperAction.IdentifyScreens)
        assertTrue(root.identifyingScreen)
        assertTrue(root.presenterManager.showPresenterWindow.value)
        assertDone()
    }

    @Test
    fun `toggle outputs shows or hides the output windows`() {
        val was = root.presenterManager.showPresenterWindow.value
        run(HelperAction.ToggleOutputWindows)
        assertEquals(!was, root.presenterManager.showPresenterWindow.value)
        assertDone()
    }

    @Test
    fun `select a tab switches to it`() {
        val sent = heard(root.helperSelectTabFlow)
        run(HelperAction.SelectTab(Tabs.BIBLE))
        assertEquals(listOf(Tabs.BIBLE), sent)
        assertDone()
    }

    @Test
    fun `show a hidden tab unhides it and switches to it`() {
        root.appSettings = root.appSettings.copy(hiddenTabs = root.appSettings.hiddenTabs + Tabs.QA.name)
        val sent = heard(root.helperSelectTabFlow)
        run(HelperAction.ShowTab(Tabs.QA))
        assertFalse(Tabs.QA.name in root.appSettings.hiddenTabs)
        assertEquals(listOf(Tabs.QA), sent)
        assertTrue(assertIs<HelperReply.Message>(wick.reply).canUndo)
    }

    @Test
    fun `a tour rings its first control and says its line`() {
        val tour = GuideTour(listOf(GuideStep(GuideTargets.NEW_SONG, HelperText.Plain("Click here"))))
        run(HelperAction.Highlight(tour))
        assertEquals(HelperReply.Touring(tour, 0), wick.reply)
        assertEquals(GuideTargets.NEW_SONG, wick.session.activeTarget)
    }

    @Test
    fun `a shortcut question shows the key`() {
        run(HelperAction.ShowShortcut(ShortcutAction.CLEAR_OUTPUT))
        assertEquals(HelperReply.Shortcut(ShortcutAction.CLEAR_OUTPUT), wick.reply)
    }

    @Test
    fun `open the converter opens it on songs with the program picked`() {
        val action = HelperAction.OpenConverter(sourceId = "openlp", sourceName = "OpenLP")
        run(action)
        assertTrue(root.showConverterWindow)
        assertEquals("openlp", root.converterInitialSource)
        assertEquals(action.describe(), assertIs<HelperReply.Message>(wick.reply).text)
    }

    @Test
    fun `open the calendar opens it and says how`() {
        val action = HelperAction.OpenCalendar(CalendarTopic.REPEAT)
        run(action)
        assertTrue(root.showCalendarWindow)
        assertEquals(action.describe(), assertIs<HelperReply.Message>(wick.reply).text)
    }

    @Test
    fun `open the song library opens it and says how`() {
        val action = HelperAction.OpenSongLibrary(compare = true)
        run(action)
        assertTrue(root.showSongLibraryWindow)
        assertEquals(action.describe(), assertIs<HelperReply.Message>(wick.reply).text)
    }

    @Test
    fun `a countdown goes up and says how long`() {
        run(HelperAction.StartCountdown(5))
        assertSaid(Res.string.helper_done_countdown, "5")
        runQueuedUntil { root.presenterManager.liveContent.value.isNotEmpty() }
        assertTrue(root.presenterManager.liveContent.value.isNotEmpty())
    }

    @Test
    fun `an announcement goes up as typed`() {
        run(HelperAction.ShowAnnouncement("Coffee after the service"))
        assertSaid(Res.string.helper_done_announcement)
        runQueuedUntil { root.presenterManager.announcementText.value == "Coffee after the service" }
        assertEquals("Coffee after the service", root.presenterManager.announcementText.value)
    }

    private fun withLibrary() {
        root.helperSongs = listOf(SongItem(number = "245", title = "Amazing Grace"))
        root.helperSongCount = root.helperSongs.size
    }

    @Test
    fun `find a song opens it on the Songs tab without putting it live`() {
        withLibrary()
        val sent = heard(root.remoteSelectSongFlow)
        run(HelperAction.FindSong("amazing grace"))
        val selection = sent.single()
        assertEquals("Amazing Grace", selection.item.title)
        assertFalse(selection.goLive)
        assertSaid(Res.string.helper_song_found, "245. Amazing Grace")
    }

    @Test
    fun `a song not in the library is not found`() {
        withLibrary()
        run(HelperAction.FindSong("blessed assurance"))
        assertSaid(Res.string.helper_song_not_found, "blessed assurance")
    }

    @Test
    fun `a song looked up before the library loads waits for it`() {
        run(HelperAction.FindSong("245"))
        assertSaid(Res.string.helper_songs_not_loaded)
    }

    @Test
    fun `add a song to the schedule adds that song`() {
        withLibrary()
        val added = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(addSong = { number, title, _, _ -> added += "$number $title" })
        run(HelperAction.AddSongToSchedule("245"))
        assertEquals(listOf("245 Amazing Grace"), added)
        assertSaid(Res.string.helper_done_added_schedule, "245. Amazing Grace")
    }

    @Test
    fun `add a verse to the schedule adds that verse`() {
        val added = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(
            addBibleVerse = { book, chapter, verse, _, range, _ -> added += "$book $chapter:$verse $range" },
        )
        run(HelperAction.AddVerseToSchedule("John", 3, 16, 17, "John 3:16-17"))
        assertEquals(listOf("John 3:16 16-17"), added)
        assertSaid(Res.string.helper_done_added_schedule, "John 3:16-17")
    }

    private val verseRow = ScheduleItem.BibleVerseItem(
        id = "verse",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "",
        displayText = "John 3:16",
    )
    private val songRow = ScheduleItem.SongItem(id = "song", songNumber = 0, title = "Sermon hymn", songbook = "")

    @Test
    fun `next item puts the next row up, a verse straight on screen`() {
        val selected = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(selectItem = { selected += it })
        root.currentScheduleItems = listOf(verseRow, songRow)
        val sent = heard(root.companionServer.onSelectBibleVerse)
        run(HelperAction.ScheduleStep(forward = true))
        assertEquals(listOf("verse"), selected)
        assertEquals("John", sent.single().bookName)
        assertSaid(Res.string.helper_schedule_live, "John 3:16")
    }

    @Test
    fun `next item at the end of the schedule says so`() {
        root.currentScheduleItems = listOf(verseRow)
        root.selectedScheduleItemId = "verse"
        run(HelperAction.ScheduleStep(forward = true))
        assertSaid(Res.string.helper_schedule_at_end)
    }

    @Test
    fun `next item in an empty schedule says so`() {
        run(HelperAction.ScheduleStep(forward = false))
        assertSaid(Res.string.helper_schedule_empty)
    }

    @Test
    fun `go to a row by name readies a song for Go Live`() {
        root.currentScheduleItems = listOf(verseRow, songRow)
        val sent = heard(root.remoteSelectSongFlow)
        run(HelperAction.ScheduleGoTo("sermon"))
        assertEquals("Sermon hymn", sent.single().item.title)
        assertFalse(sent.single().goLive)
        assertSaid(Res.string.helper_schedule_ready, "Sermon hymn")
    }

    @Test
    fun `go to a row that is not there says so`() {
        root.currentScheduleItems = listOf(verseRow)
        run(HelperAction.ScheduleGoTo("offering"))
        assertSaid(Res.string.helper_schedule_not_found, "offering")
    }

    @Test
    fun `what's live with nothing on screen says so`() {
        run(HelperAction.WhatsLive)
        assertSaid(Res.string.helper_live_nothing)
    }

    @Test
    fun `check for updates says the version`() {
        run(HelperAction.CheckForUpdates)
        assertSaid(Res.string.helper_version, BuildConfig.VERSION_DISPLAY)
    }

    @Test
    fun `ccli report opens the statistics`() {
        val action = HelperAction.OpenStatistics
        run(action)
        assertTrue(root.showStatisticsDialog)
        assertEquals(action.describe(), assertIs<HelperReply.Message>(wick.reply).text)
    }

    @Test
    fun `help shows every command`() {
        run(HelperAction.ShowCommands)
        assertEquals(HelperReply.Commands, wick.reply)
    }

    @Test
    fun `hello greets`() {
        run(HelperAction.Greet)
        assertEquals(HelperReply.Greeting, wick.reply)
    }

    @Test
    fun `thanks is answered`() {
        run(HelperAction.Thanks)
        assertSaid(Res.string.helper_youre_welcome)
        assertNull(wick.session.activeTarget)
    }
}
