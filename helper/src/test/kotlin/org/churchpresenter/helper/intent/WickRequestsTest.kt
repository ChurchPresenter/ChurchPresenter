package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WickRequestsTest {

    private val resolver = RuleIntentResolver()

    private fun resolve(text: String, language: String = "en") =
        resolver.resolveNow(text, ResolveContext(language = language))

    private fun action(text: String, language: String = "en"): HelperAction {
        val resolution = resolve(text, language)
        assertIs<Resolution.Act>(resolution, "\"$text\" was not understood: $resolution")
        return resolution.action
    }

    private fun tourTargets(text: String): List<GuideTarget> =
        assertIs<HelperAction.Highlight>(action(text), "\"$text\" is not a tour").tour.steps.map { it.target }

    @Test
    fun `a countdown of so many minutes starts one`() {
        assertEquals(HelperAction.StartCountdown(5), action("5 minute countdown"))
        assertEquals(HelperAction.StartCountdown(10), action("start a 10 min timer"))
    }

    @Test
    fun `an announcement shows the words as typed`() {
        assertEquals(HelperAction.ShowAnnouncement("Coffee after the service"), action("announce: Coffee after the service"))
        assertEquals(
            HelperAction.ShowAnnouncement("Parents of Sam, please come to the nursery."),
            action("tell the parents of Sam to come to the nursery"),
        )
        assertEquals(HelperAction.ShowAnnouncement("Welcome!"), action("show the message \"Welcome!\""))
    }

    @Test
    fun `a song is found by number or by name`() {
        assertEquals(HelperAction.FindSong("245"), action("song 245"))
        assertEquals(HelperAction.FindSong("amazing grace"), action("show the song amazing grace"))
        assertEquals(HelperAction.FindSong("amazing grace"), action("amazing grace song"))
    }

    @Test
    fun `a verse or a song is added to the schedule`() {
        val verse = assertIs<HelperAction.AddVerseToSchedule>(action("add John 3:16 to the schedule"))
        assertEquals(Triple("John", 3, 16), Triple(verse.book, verse.chapter, verse.verse))
        assertEquals(HelperAction.AddSongToSchedule("amazing grace"), action("add amazing grace to the schedule"))
        assertEquals(HelperAction.AddSongToSchedule("245"), action("add song 245 to schedule"))
    }

    @Test
    fun `the schedule is stepped and searched`() {
        assertEquals(HelperAction.ScheduleStep(forward = true), action("next item"))
        assertEquals(HelperAction.ScheduleStep(forward = false), action("previous item in the schedule"))
        assertEquals(HelperAction.ScheduleGoTo("sermon"), action("go to the sermon in the schedule"))
    }

    @Test
    fun `questions about the app are answered`() {
        assertEquals(HelperAction.WhatsLive, action("what's live"))
        assertEquals(HelperAction.CheckForUpdates, action("check for updates"))
        assertEquals(HelperAction.OpenStatistics, action("ccli report"))
    }

    @Test
    fun `help lists the commands, and hello greets`() {
        assertEquals(HelperAction.ShowCommands, action("help"))
        assertEquals(HelperAction.ShowCommands, action("what can I type"))
        assertEquals(HelperAction.Greet, action("hello"))
        // "Help" with a task is the task, not the list.
        assertIs<HelperAction.StartDisplaySetup>(action("help me set up the screens"))
    }

    @Test
    fun `feature questions are tours of their controls`() {
        assertTrue(GuideTargets.SONG_SEARCH in tourTargets("how do I search for a song"))
        assertTrue(GuideTargets.BIBLE_VERSES in tourTargets("how do I show several verses"))
        assertTrue(GuideTargets.SONG_FAVORITES in tourTargets("how do I favorite a song"))
        assertTrue(GuideTargets.SONG_BACKGROUND in tourTargets("give this song its own background"))
        assertTrue(GuideTargets.PLANNING_CENTER_IMPORT in tourTargets("import from planning center"))
        assertTrue(GuideTargets.WEB_URL in tourTargets("show a website"))
        assertTrue(GuideTargets.BIBLE_HISTORY in tourTargets("what did we just show"))
        assertTrue(GuideTargets.BIBLE_CROSS_REFS in tourTargets("related verses"))
        assertTrue(GuideTargets.SERVER_QR in tourTargets("control it from my phone"))
    }

    @Test
    fun `look questions click the right part, then ring the right row`() {
        assertEquals(
            listOf(GuideTargets.lookElement("SONG_LYRICS"), GuideTargets.PROFILE_TEXT_STYLE),
            tourTargets("how do I make the lyrics bold").takeLast(2),
        )
        assertEquals(
            listOf(GuideTargets.lookElement("SONG_NUMBER"), GuideTargets.PROFILE_TEXT_FONT),
            tourTargets("how do I make the song number font").takeLast(2),
        )
        assertEquals(
            listOf(GuideTargets.lookElement("SONG_TITLE"), GuideTargets.PROFILE_TEXT_SIZE),
            tourTargets("how do I make the song title bigger").takeLast(2),
        )
        assertEquals(GuideTargets.PROFILE_MARGINS, tourTargets("how do I change the margins").last())
        assertEquals(GuideTargets.PROFILE_END_MARKER, tourTargets("how do I change the end of song marker").last())
        assertEquals(GuideTargets.SONG_LYRICS, tourTargets("how do I change song lyrics").last())
        assertTrue(GuideTargets.lookElement("BIBLE_REFERENCE") in tourTargets("how do I style the bible reference"))
        assertTrue(GuideTargets.PROFILE_BIBLE_PAGE in tourTargets("how do I change how bible verses look"))
    }

    @Test
    fun `making the lyrics bigger, said outright, changes the size rather than showing where`() {
        assertEquals(HelperAction.ChangeFontSize(ContentScope.SONG, 1), action("make the song text bigger"))
    }

    @Test
    fun `other languages reach the same actions`() {
        val russian = assertIs<HelperAction.SetBackgroundColor>(action("сделай фон песен синим", "ru"))
        assertEquals(ContentScope.SONG, russian.scope)
        assertEquals(HelperAction.ShowCommands, action("помощь", "ru"))
        assertEquals(HelperAction.StartCountdown(5), action("countdown ۵ minutes"))
        assertEquals(HelperAction.ShowAnnouncement("кофе после служения"), action("объяви: кофе после служения", "ru"))
    }

    @Test
    fun `nothing understood is unknown`() {
        assertIs<Resolution.Unknown>(resolve("qwerty zxcvb"))
    }
}
