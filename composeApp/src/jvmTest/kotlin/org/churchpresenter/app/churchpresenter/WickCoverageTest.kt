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
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done_announcement_cued
import org.churchpresenter.strings.generated.resources.helper_live_now
import org.churchpresenter.strings.generated.resources.helper_refused_live
import org.churchpresenter.strings.generated.resources.helper_schedule_at_start
import org.churchpresenter.strings.generated.resources.helper_schedule_live
import org.churchpresenter.strings.generated.resources.helper_schedule_ready
import org.churchpresenter.strings.generated.resources.helper_schedule_selected
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WickCoverageTest {

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
    private val executor = AppHelperExecutor(root)

    @AfterTest
    fun tearDown() {
        listeners.forEach { it.cancel() }
        scope.cancel()
    }

    private fun <T> heard(flow: Flow<T>): List<T> {
        val got = mutableListOf<T>()
        listeners += CoroutineScope(Dispatchers.Unconfined).launch { flow.collect { got += it } }
        return got
    }

    private fun said(outcome: ActionOutcome): String? = when (outcome) {
        is ActionOutcome.Done -> (outcome.message as? HelperText.Res)?.res?.key
        is ActionOutcome.Refused -> (outcome.reason as? HelperText.Res)?.res?.key
        else -> null
    }

    @Test
    fun `next and previous step a live presentation`() {
        root.presenterManager.setPresentingMode(Presenting.PRESENTATION)
        val next = heard(root.companionServer.onNextSlide)
        val previous = heard(root.companionServer.onPreviousSlide)
        assertIs<ActionOutcome.Done>(executor.execute(HelperAction.NextSlide))
        assertIs<ActionOutcome.Done>(executor.execute(HelperAction.PreviousSlide))
        assertEquals(1, next.size)
        assertEquals(1, previous.size)
    }

    @Test
    fun `next and previous step live pictures`() {
        root.presenterManager.setPresentingMode(Presenting.PICTURES)
        val next = heard(root.companionServer.onNextPicture)
        val previous = heard(root.companionServer.onPreviousPicture)
        executor.execute(HelperAction.NextSlide)
        executor.execute(HelperAction.PreviousSlide)
        assertEquals(1, next.size)
        assertEquals(1, previous.size)
    }

    @Test
    fun `take puts what is cued on air`() {
        root.appSettings = root.appSettings.copy(
            projectionSettings = root.appSettings.projectionSettings.copy(previewModeEnabled = true),
        )
        val bus = root.presenterManager.previewBus
        bus.setEnabled(true)
        bus.manager.setPresentingMode(Presenting.BIBLE)
        assertIs<ActionOutcome.Done>(executor.execute(HelperAction.Take))
        assertTrue(root.presenterManager.isLive(Presenting.BIBLE))
        assertFalse(bus.anythingCued)
    }

    @Test
    fun `preview mode on with nothing cued refuses take`() {
        root.appSettings = root.appSettings.copy(
            projectionSettings = root.appSettings.projectionSettings.copy(previewModeEnabled = true),
        )
        assertIs<ActionOutcome.Refused>(executor.execute(HelperAction.Take))
    }

    @Test
    fun `identify screens is refused while something is live`() {
        root.presenterManager.setPresentingMode(Presenting.BIBLE)
        val outcome = executor.execute(HelperAction.IdentifyScreens)
        assertEquals(Res.string.helper_refused_live.key, said(outcome))
        assertFalse(root.identifyingScreen)
    }

    @Test
    fun `a service background undoes back to the one before`() {
        val outcome = executor.execute(
            HelperAction.SetBackgroundColor(ContentScope.ALL, "#C62828", "red", Persistence.THIS_SERVICE),
        )
        val undo = assertNotNull(assertIs<ActionOutcome.Done>(outcome).undo)
        assertTrue(undo.revert())
        assertNull(root.activeQuickBackground)
    }

    @Test
    fun `a service background replaced since is not undone`() {
        val outcome = executor.execute(
            HelperAction.SetBackgroundColor(ContentScope.ALL, "#C62828", "red", Persistence.THIS_SERVICE),
        )
        executor.execute(HelperAction.SetBackgroundColor(ContentScope.ALL, "#1565C0", "blue", Persistence.THIS_SERVICE))
        val undo = assertNotNull(assertIs<ActionOutcome.Done>(outcome).undo)
        assertFalse(undo.revert())
        assertEquals("#1565C0", root.activeQuickBackground?.background?.color)
    }

    @Test
    fun `a settings undo waits while the Settings window is open`() {
        val outcome = executor.execute(HelperAction.ChangeFontSize(ContentScope.SONG, 1))
        val undo = assertNotNull(assertIs<ActionOutcome.Done>(outcome).undo)
        root.showOptionsDialog = true
        assertFalse(undo.revert())
    }

    @Test
    fun `a tab shown while Settings is open is not switched to`() {
        root.showOptionsDialog = true
        val sent = heard(root.helperSelectTabFlow)
        assertIs<ActionOutcome.Refused>(executor.execute(HelperAction.ShowTab(Tabs.QA)))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `actions the helper handles itself are just done`() {
        listOf(
            HelperAction.UndoLast, HelperAction.Greet, HelperAction.Thanks, HelperAction.ShowCommands,
            HelperAction.ShowShortcut(ShortcutAction.CLEAR_OUTPUT), HelperAction.Highlight(GuideTour(emptyList())),
        ).forEach {
            assertIs<ActionOutcome.Done>(executor.execute(it))
        }
    }

    @Test
    fun `what's live names every kind of content`() {
        Presenting.entries.filter { it != Presenting.NONE }.forEach { mode ->
            root.presenterManager.setPresentingMode(Presenting.NONE)
            root.presenterManager.setPresentingMode(mode)
            val outcome = executor.execute(HelperAction.WhatsLive)
            assertIs<ActionOutcome.Done>(outcome, mode.name)
        }
    }

    @Test
    fun `what's live names the verses on screen`() {
        root.presenterManager.setDisplayedVerses(
            listOf(
                SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16),
                SelectedVerse(bookName = "John", chapter = 3, verseNumber = 17),
            ),
        )
        root.presenterManager.setPresentingMode(Presenting.BIBLE)
        assertEquals(Res.string.helper_live_now.key, said(executor.execute(HelperAction.WhatsLive)))
        root.presenterManager.setDisplayedVerses(
            listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16)),
        )
        assertEquals(Res.string.helper_live_now.key, said(executor.execute(HelperAction.WhatsLive)))
    }

    @Test
    fun `what's live names the song section and a running countdown`() {
        root.presenterManager.setDisplayedLyricSection(LyricSection(title = "Amazing Grace"))
        root.presenterManager.setPresentingMode(Presenting.LYRICS)
        assertEquals(Res.string.helper_live_now.key, said(executor.execute(HelperAction.WhatsLive)))
        root.presenterManager.startAnnouncementCountdown(600, "")
        try {
            root.presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
            assertEquals(Res.string.helper_live_now.key, said(executor.execute(HelperAction.WhatsLive)))
        } finally {
            root.presenterManager.pauseAnnouncementTimer()
        }
    }

    @Test
    fun `an announcement in preview mode is cued`() {
        root.appSettings = root.appSettings.copy(
            projectionSettings = root.appSettings.projectionSettings.copy(previewModeEnabled = true),
        )
        val outcome = executor.execute(HelperAction.ShowAnnouncement("Welcome"))
        assertEquals(Res.string.helper_done_announcement_cued.key, said(outcome))
    }

    private val label = ScheduleItem.LabelItem(id = "label", text = "Worship", textColor = "", backgroundColor = "")
    private val announcement = ScheduleItem.AnnouncementItem(id = "ann", text = "Coffee")
    private val scene = ScheduleItem.SceneItem(id = "scene", sceneId = "s1", sceneName = "Stage")
    private val picture = ScheduleItem.PictureItem(id = "pic", folderPath = "", folderName = "Photos", imageCount = 2)
    private val website = ScheduleItem.WebsiteItem(id = "web", url = "https://example.org")

    @Test
    fun `previous item with nothing selected readies the last row`() {
        val scenes = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(presentScene = { scenes += it })
        root.currentScheduleItems = listOf(label, announcement, scene)
        val outcome = executor.execute(HelperAction.ScheduleStep(forward = false))
        assertEquals(listOf("s1"), scenes)
        assertEquals(Res.string.helper_schedule_live.key, said(outcome))
    }

    @Test
    fun `previous item at the first row says so`() {
        root.currentScheduleItems = listOf(label, announcement)
        root.selectedScheduleItemId = "ann"
        assertEquals(Res.string.helper_schedule_at_start.key, said(executor.execute(HelperAction.ScheduleStep(false))))
    }

    @Test
    fun `next item with nothing selected puts an announcement up`() {
        root.currentScheduleItems = listOf(label, announcement)
        val outcome = executor.execute(HelperAction.ScheduleStep(forward = true))
        assertEquals(Res.string.helper_schedule_live.key, said(outcome))
        assertTrue(queued.isNotEmpty())
    }

    @Test
    fun `go to a picture row readies it on its tab`() {
        root.currentScheduleItems = listOf(picture)
        val sent = heard(root.remoteSelectPictureFlow)
        val outcome = executor.execute(HelperAction.ScheduleGoTo("photos"))
        assertEquals(Res.string.helper_schedule_ready.key, said(outcome))
        while (queued.isNotEmpty()) queued.removeFirst().run()
        assertEquals(1, sent.size)
    }

    @Test
    fun `go to a row with no tab of its own only selects it`() {
        val selected = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(selectItem = { selected += it })
        root.currentScheduleItems = listOf(website)
        val outcome = executor.execute(HelperAction.ScheduleGoTo("example"))
        assertEquals(listOf("web"), selected)
        assertEquals(Res.string.helper_schedule_selected.key, said(outcome))
    }

    @Test
    fun `show helper turns the lamp back on and opens it`() {
        root.saveHelperSettings(HelperSettings(enabled = false))
        root.showHelper()
        assertTrue(root.appSettings.helper.enabled)
        assertTrue(root.helperState.isOpen)
        root.helperState.isOpen = false
        root.showHelper()
        assertTrue(root.helperState.isOpen)
    }

    @Test
    fun `the intro shows when asked for again`() {
        root.helperState.replayIntro = true
        assertTrue(root.wickIntroShowing)
    }

    @Test
    fun `the intro waits for the app to be ready and stays away once seen`() {
        root.saveHelperSettings(HelperSettings(enabled = true, introSeen = false))
        root.developerMenuUnlocked = true
        root.appReady = true
        root.eulaAccepted = true
        root.showSetupWizard = false
        root.startupChecksDone = true
        root.pendingUpdateResult = null
        root.showStoryPrompt = false
        assertTrue(root.wickIntroShowing)
        root.saveHelperSettings(root.appSettings.helper.copy(introSeen = true))
        assertFalse(root.wickIntroShowing)
        root.appReady = false
        assertFalse(root.wickIntroShowing)
    }

    @Test
    fun `screens are empty when none can be read`() {
        assertTrue(helperScreens(root.appSettings).isEmpty())
    }

    @Test
    fun `a settings undo is skipped once the operator changed the same part again`() {
        val outcome = executor.execute(HelperAction.ChangeFontSize(ContentScope.SONG, 1))
        val undo = assertNotNull(assertIs<ActionOutcome.Done>(outcome).undo)
        executor.execute(HelperAction.ChangeFontSize(ContentScope.SONG, 1))
        assertFalse(undo.revert())
    }

    @Test
    fun `a single verse goes to the Bible tab with no range`() {
        val sent = heard(root.companionServer.onSelectBibleVerse)
        executor.execute(HelperAction.ShowBibleVerse("John", 3, 16, 16, "John 3:16"))
        assertEquals("", sent.single().verseRange)
    }

    @Test
    fun `what's live with no verses or song title names the tab`() {
        root.presenterManager.setPresentingMode(Presenting.BIBLE)
        assertEquals(Res.string.helper_live_now.key, said(executor.execute(HelperAction.WhatsLive)))
        root.presenterManager.setPresentingMode(Presenting.LYRICS)
        assertEquals(Res.string.helper_live_now.key, said(executor.execute(HelperAction.WhatsLive)))
    }

    @Test
    fun `a song with no number is named and added by its title`() {
        root.helperSongs = listOf(SongItem(number = "", title = "Untitled Hymn"))
        root.helperSongCount = 1
        val added = mutableListOf<Int>()
        root.scheduleActions = ScheduleActions(addSong = { number, _, _, _ -> added += number })
        assertIs<ActionOutcome.Done>(executor.execute(HelperAction.FindSong("untitled")))
        assertIs<ActionOutcome.Done>(executor.execute(HelperAction.AddSongToSchedule("untitled")))
        assertEquals(listOf(0), added)
    }

    @Test
    fun `a single verse is added to the schedule with no range`() {
        val ranges = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(addBibleVerse = { _, _, _, _, range, _ -> ranges += range })
        executor.execute(HelperAction.AddVerseToSchedule("John", 3, 16, 16, "John 3:16"))
        assertEquals(listOf(""), ranges)
    }

    @Test
    fun `the intro stays away until every condition is met`() {
        val conditions: List<Pair<() -> Unit, () -> Unit>> = listOf(
            { root.saveHelperSettings(root.appSettings.helper.copy(enabled = false)) } to
                { root.saveHelperSettings(root.appSettings.helper.copy(enabled = true)) },
            { root.eulaAccepted = false } to { root.eulaAccepted = true },
            { root.showSetupWizard = true } to { root.showSetupWizard = false },
            { root.startupChecksDone = false } to { root.startupChecksDone = true },
            { root.showStoryPrompt = true } to { root.showStoryPrompt = false },
            { root.presenterManager.setPresentingMode(Presenting.BIBLE) } to
                { root.presenterManager.setPresentingMode(Presenting.NONE) },
        )
        root.saveHelperSettings(HelperSettings(enabled = true, introSeen = false))
        root.developerMenuUnlocked = true
        root.appReady = true
        root.eulaAccepted = true
        root.showSetupWizard = false
        root.startupChecksDone = true
        root.showStoryPrompt = false
        conditions.forEach { (unmet, met) ->
            unmet()
            assertFalse(root.wickIntroShowing)
            met()
            assertTrue(root.wickIntroShowing)
        }
    }
}
