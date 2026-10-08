package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.guide.SettingsPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DoRulesTest {

    private val resolver = RuleIntentResolver()

    private fun resolve(text: String, language: String = "en") =
        resolver.resolveNow(text, ResolveContext(language = language))

    private fun actionOf(text: String, language: String = "en"): HelperAction? =
        (resolve(text, language) as? Resolution.Act)?.action

    @Test
    fun `a countdown needs a sensible number of minutes, and clearing one is a clear`() {
        assertEquals(HelperAction.StartCountdown(3), actionOf("countdown for 3 minutes"))
        listOf("countdown", "countdown for 0 minutes", "countdown for 900 minutes").forEach {
            assertIs<HelperAction.Highlight>(actionOf(it), it)
        }
        assertEquals(HelperAction.ClearOutput, actionOf("clear the 5 minute countdown"))
    }

    @Test
    fun `an announcement takes the quoted, labelled or typed words`() {
        assertEquals(HelperAction.ShowAnnouncement("Hello all"), actionOf("show \"Hello all\""))
        assertEquals(HelperAction.ShowAnnouncement("Doors open"), actionOf("announcement \"Doors open\""))
        assertEquals(HelperAction.ShowAnnouncement("Doors open"), actionOf("display a notice: Doors open"))
        assertEquals(
            HelperAction.ShowAnnouncement("Parents of Ann, please wait."),
            actionOf("please ask parents of Ann to wait!"),
        )
        assertEquals(HelperAction.ShowAnnouncement("кофе"), actionOf("объяви кофе", "ru"))
        assertIs<HelperAction.Highlight>(actionOf("объяви", "ru"))
        assertIs<HelperAction.Highlight>(actionOf("announce \"\""))
        assertEquals(HelperAction.FindSong("hello all"), actionOf("sing \"Hello all\""))
    }

    @Test
    fun `a reference is read however it is typed, and a song otherwise`() {
        listOf(
            "add john 3 16 to the schedule",
            "add john chapter 3 verse 16 to the schedule",
            "john 3:16 to the schedule add",
        ).forEach {
            val verse = assertIs<HelperAction.AddVerseToSchedule>(actionOf(it), it)
            assertEquals("John 3:16", verse.display)
        }
        val range = assertIs<HelperAction.AddVerseToSchedule>(actionOf("add john 3:16-18 to the schedule"))
        assertEquals(18, range.lastVerse)
        assertEquals(
            HelperAction.AddSongToSchedule("holy holy"),
            actionOf("add the song called holy holy to the schedule"),
        )
        listOf("add it to the schedule", "add this song to the schedule", "schedule", "play it", "show me", "song a")
            .forEach { assertEquals(Resolution.Unknown, resolve(it), it) }
    }

    @Test
    fun `the schedule is stepped or searched with the verb first or last`() {
        assertEquals(HelperAction.ScheduleStep(forward = true), actionOf("go to next item in the schedule"))
        assertEquals(HelperAction.ScheduleStep(forward = false), actionOf("last item"))
        assertEquals(HelperAction.ScheduleStep(forward = false), actionOf("previous item"))
        assertEquals(HelperAction.ScheduleGoTo("sermon"), actionOf("the sermon in the schedule go to"))
        assertEquals(HelperAction.ScheduleGoTo("sermon"), actionOf("in the schedule the sermon go to"))
    }

    @Test
    fun `a song is found by number, by a name before or after the word, or loosely`() {
        assertEquals(HelperAction.FindSong("12"), actionOf("song no 12"))
        assertEquals(HelperAction.FindSong("12"), actionOf("#song 12"))
        assertEquals(HelperAction.FindSong("amazing grace"), actionOf("play amazing grace hymn"))
        assertEquals(HelperAction.FindSong("how great thou art"), actionOf("pull up how great thou art"))
        assertEquals(HelperAction.OpenSettings(SettingsPage.PROJECTION), actionOf("show the projection settings"))
    }
}
