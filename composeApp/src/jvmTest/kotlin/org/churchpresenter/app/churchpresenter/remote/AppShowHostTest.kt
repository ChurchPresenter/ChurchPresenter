package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.messageOnAir
import org.churchpresenter.app.churchpresenter.viewmodel.propsOnAir
import org.churchpresenter.app.churchpresenter.viewmodel.setPropOn
import org.churchpresenter.app.churchpresenter.viewmodel.showMessage
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.ActionRunner
import org.churchpresenter.showcontrol.MediaCommand
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What each show-control action does to the app, through the host. */
class AppShowHostTest {

    private val pm = PresenterManager(showPresenterWindowInitially = false)
    private val done = mutableListOf<String>()
    private val atemBlocks = mutableListOf<suspend (AtemClient) -> Unit>()

    private val song = ScheduleItem.SongItem("s1", 1, "Amazing", "Hymns", "Hymns::1")
    private val heading = ScheduleItem.LabelItem("h", "Word", "#FFFFFF", "#000000")
    private val verse = ScheduleItem.SongItem("s2", 2, "Grace", "Hymns", "Hymns::2")

    private var settings = AppSettings(
        clearGroups = listOf(ClearGroup("clear1", "Clear text", listOf("SLIDE", "MESSAGES"))),
        messageTemplates = listOf(MessageTemplate("msg1", "Nursery", "Parent of {number}", durationSeconds = 30)),
        props = listOf(PropDefinition("prop1", "Logo")),
    )
    private var current: String? = null

    private val host = AppShowHost(
        pm,
        { settings },
        ShowOutlets(
            rows = { listOf(song, heading, verse) },
            currentRowId = { current },
            goLive = { item, plays -> done += "live:${item.id}x$plays" },
            rowActions = { item, depth -> done += "actions:${item.id}@$depth" },
            toPreview = { done += "preview:${it.id}" },
            media = { done += "media:$it" },
            obsScene = { done += "obs:$it" },
            companion = { it.connection == "conn1" },
            atem = { atemBlocks += it },
            macro = { name -> listOf(Action.ObsScene(name)).takeIf { name == "walk in" } },
            log = { done += "log:$it" },
        ),
    )

    @AfterTest
    fun stopTickers() {
        pm.pauseAnnouncementTimer()
    }

    private fun run(action: Action) = runBlocking { ActionRunner(host, this).perform(listOf(action)) }

    private fun refused(action: Action): String {
        done.clear()
        run(action)
        return done.single().substringAfter(": ")
    }

    @Test
    fun `a row goes live by its id, or an item carried in the action`() {
        run(Action.GoLive(rowId = "s2", plays = 3))
        run(Action.GoLive(item = song))
        assertEquals(listOf("live:s2x3", "live:s1x1"), done)
        assertEquals("No schedule row nope", refused(Action.GoLive(rowId = "nope")))
    }

    @Test
    fun `a row is cued on Preview only while preview mode is on`() {
        assertEquals("Preview mode is off", refused(Action.ToPreview("s1")))
        settings = settings.copy(projectionSettings = ProjectionSettings(previewModeEnabled = true))
        done.clear()
        run(Action.ToPreview("s1"))
        assertEquals(listOf("preview:s1"), done)
    }

    @Test
    fun `take puts what is cued on air, and one layer alone is refused`() {
        pm.previewBus.setEnabled(true)
        pm.previewBus.present(Presenting.BIBLE)
        run(Action.Take())
        assertTrue(pm.isLive(Presenting.BIBLE))
        assertEquals("Taking one layer at a time is not supported yet", refused(Action.Take("SLIDE")))
    }

    @Test
    fun `a layer is cleared by its API name or its own, a group by name, and everything at once`() {
        pm.setPropOn("prop1", true)
        run(Action.Clear("props"))
        assertEquals(emptySet(), pm.propsOnAir)
        pm.showMessage(Cue.Message("Hi"))
        run(Action.Clear("Messages"))
        assertEquals(null, pm.messageOnAir)
        assertEquals("No layer called stage", refused(Action.Clear("stage")))

        pm.setPresentingMode(Presenting.LYRICS)
        run(Action.ClearGroup("clear text"))
        assertFalse(pm.isLive(Presenting.LYRICS))
        assertEquals("No clear group called x", refused(Action.ClearGroup("x")))

        pm.setPresentingMode(Presenting.BIBLE)
        run(Action.ClearAll)
        assertTrue(pm.clearDisplayRequested.value, "the outputs fade out, as the Clear button does")
    }

    @Test
    fun `a message is the words given, or a saved one filled in and timed`() {
        run(Action.Message(text = "Hello"))
        assertEquals(Cue.Message("Hello"), pm.messageOnAir)
        run(Action.Message(template = "nursery", tokens = mapOf("number" to "12")))
        assertEquals(Cue.Message("Parent of 12", "Nursery", 30), pm.messageOnAir)
        run(Action.Message(template = "msg1", tokens = mapOf("number" to "4"), durationSeconds = 5))
        assertEquals(Cue.Message("Parent of 4", "Nursery", 5), pm.messageOnAir)
        assertEquals("No saved message called Choir", refused(Action.Message(template = "Choir")))
        assertEquals("A message needs words", refused(Action.Message(text = " ")))
    }

    @Test
    fun `a prop is switched by id or name, or flipped`() {
        run(Action.Prop("logo"))
        assertEquals(setOf("prop1"), pm.propsOnAir)
        run(Action.Prop("prop1", on = true))
        assertEquals(setOf("prop1"), pm.propsOnAir)
        run(Action.Prop("Logo", on = false))
        assertEquals(emptySet(), pm.propsOnAir)
        assertEquals("No prop called Clock", refused(Action.Prop("Clock")))
    }

    @Test
    fun `a lower third runs the preset of that name from the folder`() {
        val folder = Files.createTempDirectory("cp-show-lt").toFile()
        try {
            folder.resolve("Pastor.json").writeText("""{"v":"5.7"}""")
            settings = settings.copy(streamingSettings = StreamingSettings(lowerThirdFolder = folder.path))
            run(Action.LowerThird(" Pastor "))
            assertTrue(pm.isLive(Presenting.LOWER_THIRD))
            assertEquals("""{"v":"5.7"}""", pm.lottieJsonContent.value)
            assertEquals("No lower third called Guest", refused(Action.LowerThird("Guest")))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a timer counts down, to a time, up, or shows the clock`() {
        run(Action.Timer(TimerModes.DURATION, seconds = 90))
        assertEquals(90, pm.timerRemainingSeconds.value)
        assertTrue(pm.isLive(Presenting.ANNOUNCEMENTS))
        listOf(
            Action.Timer(TimerModes.CLOCK, until = "23:59"),
            Action.Timer(TimerModes.COUNT_UP),
            Action.Timer(TimerModes.CLOCK_DISPLAY),
        ).forEach {
            run(it)
            assertTrue(pm.announcementTickerLive.value, it.mode)
        }
        assertEquals("A clock timer needs a time, HH:mm", refused(Action.Timer(TimerModes.CLOCK, until = "soon")))
        assertEquals("No timer mode called lap", refused(Action.Timer("lap")))
    }

    @Test
    fun `media, OBS, the ATEM and Companion go to their outlets`() {
        run(Action.Media(MediaCommand.PLAY))
        run(Action.ObsScene("Wide"))
        run(Action.CompanionPress("conn1", 3))
        run(Action.AtemKey(downstream = true, keyer = 1))
        run(Action.AtemMacro(2))
        assertEquals(listOf("media:PLAY", "obs:Wide"), done)
        assertEquals(2, atemBlocks.size)
        assertEquals("No Companion surface for conn2", refused(Action.CompanionPress("conn2", 3)))
    }

    @Test
    fun `next and previous step through the content rows from the current one, with their own actions`() {
        run(Action.PreviousItem)
        run(Action.NextItem)
        current = "s1"
        run(Action.NextItem)
        run(Action.PreviousItem)
        current = "s2"
        run(Action.NextItem)
        run(Action.PreviousItem)
        assertEquals(
            listOf("live:s1x1", "actions:s1@0", "live:s2x1", "actions:s2@0", "live:s1x1", "actions:s1@0"),
            done,
        )
    }

    @Test
    fun `a step from a row's own actions carries how deep the chain already is`() {
        current = "s1"
        runBlocking { ActionRunner(host, this).run(listOf(Action.NextItem), key = "s1", chainDepth = 3).join() }
        assertEquals(listOf("live:s2x1", "actions:s2@3"), done)
    }

    @Test
    fun `a macro comes from the outlet, and a failure is logged`() {
        run(Action.RunMacro("walk in"))
        assertEquals(listOf("obs:walk in"), done)
        assertTrue(refused(Action.RunMacro("nope")).startsWith("No macro called nope"))
        assertTrue(done.single().startsWith("log:Show control could not run RunMacro(name=nope)"))
    }
}
