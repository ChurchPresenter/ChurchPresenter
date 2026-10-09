package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.CalendarTopic
import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.DOCUMENTS_SOURCE
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleTableTest {

    private val resolver = RuleIntentResolver()

    private fun resolve(text: String, tab: Tabs? = null, visible: Set<Tabs> = Tabs.entries.toSet()) =
        resolver.resolveNow(text, ResolveContext(currentTab = tab, visibleTabs = visible, language = "en"))

    private fun act(text: String, tab: Tabs? = null): HelperAction {
        val resolution = resolve(text, tab)
        assertIs<Resolution.Act>(resolution, "\"$text\" gave $resolution")
        return resolution.action
    }

    private fun actionOf(text: String) = (resolve(text) as? Resolution.Act)?.action

    private fun kinds(rows: List<Pair<String, KClass<out HelperAction>>>, tab: Tabs? = null) {
        val wrong = rows.mapNotNull { (text, kind) ->
            val got = (resolve(text, tab) as? Resolution.Act)?.action
            if (got != null && kind.isInstance(got)) null else "\"$text\" → $got, expected ${kind.simpleName}"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `command rules`() {
        kinds(
            listOf(
                "undo" to HelperAction.UndoLast::class,
                "take that back" to HelperAction.UndoLast::class,
                "setup wizard" to HelperAction.OpenSetupWizard::class,
                "identify screens" to HelperAction.IdentifyScreens::class,
                "which screen is which" to HelperAction.IdentifyScreens::class,
                "help me set up the screens" to HelperAction.StartDisplaySetup::class,
                "the projector is not working" to HelperAction.StartDisplaySetup::class,
                "connect the second screen" to HelperAction.StartDisplaySetup::class,
                "make the song background #abc" to HelperAction.SetBackgroundColor::class,
                "make the bible background light blue" to HelperAction.SetBackgroundColor::class,
                "make both backgrounds dark red for this service" to HelperAction.SetBackgroundColor::class,
                "song text is too small" to HelperAction.ChangeFontSize::class,
                "bible text is too big" to HelperAction.ChangeFontSize::class,
                "make the song font smaller" to HelperAction.ChangeFontSize::class,
                "keyboard shortcut to take" to HelperAction.ShowShortcut::class,
                "shortcut for next bible verse" to HelperAction.ShowShortcut::class,
                "shortcut for next song section" to HelperAction.ShowShortcut::class,
                "shortcut for next" to HelperAction.ShowShortcut::class,
                "shortcut for previous" to HelperAction.ShowShortcut::class,
                "hotkey for undo" to HelperAction.ShowShortcut::class,
                "keyboard shortcuts" to HelperAction.OpenKeyboardShortcuts::class,
                "black out" to HelperAction.ClearOutput::class,
                "take" to HelperAction.Take::class,
                "show the screens" to HelperAction.ToggleOutputWindows::class,
                "turn off the output" to HelperAction.ToggleOutputWindows::class,
            ),
        )
        val blue = assertIs<HelperAction.SetBackgroundColor>(act("make both backgrounds dark red for this service"))
        assertEquals(ContentScope.ALL to Persistence.THIS_SERVICE, blue.scope to blue.persistence)
        val shortcut = act("shortcut for next bible verse") as HelperAction.ShowShortcut
        assertEquals(ShortcutAction.BIBLE_NEXT_VERSE, shortcut.action)
        assertIs<Resolution.Clarify>(resolve("make the text bigger"))
        assertEquals(ContentScope.SONG, (act("make the text bigger", Tabs.SONGS) as HelperAction.ChangeFontSize).scope)
    }

    @Test
    fun `settings pages`() {
        val pages = mapOf(
            "open projection settings" to SettingsPage.PROJECTION,
            "open background settings" to SettingsPage.BACKGROUND,
            "open bible settings" to SettingsPage.BIBLE,
            "open profile settings" to SettingsPage.PROFILES,
            "open server settings" to SettingsPage.SERVER,
            "open atem settings" to SettingsPage.ATEM,
            "open obs settings" to SettingsPage.INTEGRATIONS,
            "open settings" to SettingsPage.SYSTEM,
        )
        pages.forEach { (text, page) -> assertEquals(HelperAction.OpenSettings(page), act(text), text) }
    }

    @Test
    fun `navigation, tabs and stepping`() {
        kinds(
            listOf(
                "where is the schedule" to HelperAction.Highlight::class,
                "where is the remote" to HelperAction.Highlight::class,
                "where do i find the projection" to HelperAction.Highlight::class,
                "how do i clear the screen" to HelperAction.Highlight::class,
                "where is the bible" to HelperAction.Highlight::class,
                "go to psalm 23" to HelperAction.ShowBibleVerse::class,
                "john chapter 3 verse 16" to HelperAction.ShowBibleVerse::class,
                "jn 3 16" to HelperAction.ShowBibleVerse::class,
                "open the bible tab" to HelperAction.SelectTab::class,
                "switch to songs" to HelperAction.SelectTab::class,
                "next slide" to HelperAction.NextSlide::class,
                "go back" to HelperAction.PreviousSlide::class,
            ),
        )
        assertIs<HelperAction.ShowShortcut>(act("next", Tabs.SONGS))
        assertIs<HelperAction.ShowShortcut>(act("previous", Tabs.BIBLE))
        assertIs<HelperAction.ShowShortcut>(act("next", Tabs.BIBLE))
        assertIs<HelperAction.ShowShortcut>(act("previous", Tabs.SONGS))
        val hidden = resolve("open the web tab", visible = Tabs.entries.toSet() - Tabs.WEB)
        assertEquals(HelperAction.ShowTab(Tabs.WEB), (hidden as Resolution.Act).action)
        val hiddenTour = resolve("where is the web tab", visible = Tabs.entries.toSet() - Tabs.WEB)
        assertEquals(HelperAction.ShowTab(Tabs.WEB), (hiddenTour as Resolution.Act).action)
    }

    @Test
    fun `how-to, media and announcement tours`() {
        kinds(
            listOf(
                "add a translation to this song" to HelperAction.Highlight::class,
                "add a language" to HelperAction.Highlight::class,
                "bilingual hymn" to HelperAction.Highlight::class,
                "add chords to a song" to HelperAction.Highlight::class,
                "song cords" to HelperAction.Highlight::class,
                "download the russian bible" to HelperAction.Highlight::class,
                "add another bible version" to HelperAction.Highlight::class,
                "add photos" to HelperAction.Highlight::class,
                "show pictures" to HelperAction.Highlight::class,
                "play a slideshow" to HelperAction.Highlight::class,
                "show a powerpoint" to HelperAction.Highlight::class,
                "play a video" to HelperAction.Highlight::class,
                "page a parent" to HelperAction.Highlight::class,
                "show a countdown" to HelperAction.Highlight::class,
                "start a stopwatch" to HelperAction.Highlight::class,
                "show the clock" to HelperAction.Highlight::class,
            ),
        )
        assertIs<HelperAction.Highlight>(act("translate this", Tabs.SONGS))
        assertIs<HelperAction.Highlight>(act("add spanish", Tabs.SONGS))
        assertIs<HelperAction.ClearOutput>(act("clear the announcement"))
    }

    @Test
    fun `stage monitor and outputs`() {
        kinds(
            listOf(
                "show chords on the stage monitor" to HelperAction.Highlight::class,
                "send a message to the stage monitor" to HelperAction.Highlight::class,
                "put a countdown on the stage monitor" to HelperAction.Highlight::class,
                "change the stage monitor layout" to HelperAction.Highlight::class,
                "make the stage monitor text bigger" to HelperAction.Highlight::class,
                "show the bible on the stage monitor" to HelperAction.Highlight::class,
                "show next on the stage monitor" to HelperAction.Highlight::class,
                "show songs on the stage monitor" to HelperAction.Highlight::class,
                "show the clock on the stage monitor" to HelperAction.Highlight::class,
                "show speaker notes on the stage monitor" to HelperAction.Highlight::class,
                "show announcements on the stage monitor" to HelperAction.Highlight::class,
                "what goes where on the stage monitor" to HelperAction.Highlight::class,
                "set up a stage monitor" to HelperAction.Highlight::class,
                "set up a full screen display" to HelperAction.Highlight::class,
                "set up a lower third display" to HelperAction.Highlight::class,
                "make a lower third" to HelperAction.Highlight::class,
            ),
        )
        assertIs<Resolution.Clarify>(resolve("lower third"))
        assertIs<Resolution.Clarify>(resolve("make the lower third text bigger"))
    }

    @Test
    fun `converter, library and calendar`() {
        assertEquals(HelperAction.OpenConverter("openlp", "OpenLP"), act("convert songs from openlp"))
        assertEquals(HelperAction.OpenConverter(DOCUMENTS_SOURCE), act("convert songs from pdf"))
        assertEquals(HelperAction.OpenConverter(), act("open the converter"))
        assertEquals(HelperAction.OpenConverter(), act("import my songs"))
        assertEquals(HelperAction.OpenSongLibrary(compare = true), act("compare song translations"))
        assertEquals(HelperAction.OpenSongLibrary(compare = true), act("find missing verses"))
        assertEquals(HelperAction.OpenSongLibrary(), act("batch edit songs"))
        assertEquals(HelperAction.OpenSongLibrary(), act("change many songs at once"))
        val calendar = mapOf(
            "make sunday a recurring service" to CalendarTopic.REPEAT,
            "save a service template" to CalendarTopic.TEMPLATE,
            "load the service into the schedule" to CalendarTopic.LOAD,
            "automate the service" to CalendarTopic.AUTOMATE,
            "open the calendar" to CalendarTopic.PLAN,
        )
        calendar.forEach { (text, topic) -> assertEquals(HelperAction.OpenCalendar(topic), act(text), text) }
    }

    @Test
    fun `talking, answers and the jobs wick does`() {
        assertEquals(HelperAction.Thanks, act("thanks"))
        assertEquals(HelperAction.Greet, act("who are you"))
        assertEquals(HelperAction.Greet, act("hi there wick"))
        assertTrue(actionOf("thanks for everything you did this week, it was great") != HelperAction.Thanks)
        val version = resolve("check the bible version")
        assertTrue(version !is Resolution.Act || version.action != HelperAction.CheckForUpdates)
        assertTrue(actionOf("countdown 999 minutes") !is HelperAction.StartCountdown)
        assertEquals(HelperAction.ShowAnnouncement("Hello"), act("show the text: Hello"))
        assertEquals(HelperAction.ShowAnnouncement("Coffee"), act("announce Coffee"))
        assertEquals(HelperAction.FindSong("12"), act("hymn 12"))
        assertEquals(HelperAction.FindSong("how great thou art"), act("sing how great thou art"))
        assertEquals(HelperAction.ScheduleGoTo("sermon"), act("the sermon in the schedule go to"))
        assertEquals(HelperAction.AddSongToSchedule("amazing grace"), act("amazing grace to the schedule add"))
        assertTrue(actionOf("add it to the schedule") !is HelperAction.AddSongToSchedule)
        assertTrue(actionOf("show me") !is HelperAction.FindSong)
        assertEquals(HelperAction.ScheduleStep(forward = false), act("last item"))
    }

    @Test
    fun `look tours`() {
        kinds(
            listOf(
                "how do i add a shadow to the lyrics" to HelperAction.Highlight::class,
                "add a drop shadow to the song text" to HelperAction.Highlight::class,
                "move the lyrics to the top" to HelperAction.Highlight::class,
                "how do i change the font of the lyrics" to HelperAction.Highlight::class,
                "change how the song looks" to HelperAction.Highlight::class,
                "make the song number bold" to HelperAction.Highlight::class,
                "style the reference" to HelperAction.Highlight::class,
                "make the verse text italic" to HelperAction.Highlight::class,
                "how do i make the bible verses bigger" to HelperAction.Highlight::class,
            ),
        )
        assertIs<Resolution.Unknown>(resolve("move the song up in the schedule"))
    }

    @Test
    fun `feature tours`() {
        kinds(
            listOf(
                "look up a verse" to HelperAction.Highlight::class,
                "find a song by number" to HelperAction.Highlight::class,
                "my favourite songs" to HelperAction.Highlight::class,
                "a different background for each song" to HelperAction.Highlight::class,
                "a range of verses" to HelperAction.Highlight::class,
                "open a web page" to HelperAction.Highlight::class,
                "show the qr code" to HelperAction.Highlight::class,
                "song statistics" to HelperAction.OpenStatistics::class,
            ),
        )
    }
}
