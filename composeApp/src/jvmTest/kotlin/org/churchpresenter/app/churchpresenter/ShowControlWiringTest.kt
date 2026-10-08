package org.churchpresenter.app.churchpresenter

import kotlin.test.assertTrue
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.showcontrol.ActionRunner
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.liveoutput.clearFromOperator
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.showcontrol.Action
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** A row's actions reaching the air with it, and the lower-third presets the editor offers. */
class ShowControlWiringTest {

    private val pictures = ScheduleItem.PictureItem("p1", "/pics", "Gallery", 3)
    private val announcement = ScheduleItem.AnnouncementItem("a1", "Welcome")
    private val obs = listOf(Action.ObsScene("Wide"))

    @Test
    fun `a row's actions run now when its content goes straight on air, keyed by the row`() {
        val bus = PresenterManager(showPresenterWindowInitially = false).previewBus
        val ran = mutableListOf<Pair<List<Action>, String>>()
        bus.runOnAir(announcement, obs) { list, key -> ran += list to key }
        bus.runOnAir(announcement, emptyList()) { list, key -> ran += list to key }
        assertEquals(listOf<Pair<List<Action>, String>>(obs to "a1"), ran)
    }

    @Test
    fun `with preview mode on they wait for the Take that puts the row on air`() {
        val bus = PresenterManager(showPresenterWindowInitially = false).previewBus
        bus.setEnabled(true)
        bus.present(Presenting.PICTURES)
        val ran = mutableListOf<String>()
        bus.runOnAir(pictures, obs) { _, key -> ran += key }
        assertEquals(emptyList(), ran, "cued, not on air yet")
        bus.take()
        assertEquals(listOf("p1"), ran)
    }

    @Test
    fun `only an operator's clear is counted, so only it stops what a list still has to do`() {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)

        pm.requestClearDisplay()
        assertEquals(0, pm.operatorClears.intValue, "a media end or an action's own clear")
        assertTrue(pm.clearDisplayRequested.value)

        pm.clearFromOperator()
        pm.clearFromOperator()
        assertEquals(2, pm.operatorClears.intValue, "counted even with nothing left to fade")
    }

    @Test
    fun `the presets are the json files in the folder, by name, in order`() {
        val folder = Files.createTempDirectory("cp-lower-thirds").toFile()
        try {
            folder.resolve("Pastor.json").writeText("{}")
            folder.resolve("band.JSON").writeText("{}")
            folder.resolve("notes.txt").writeText("x")
            folder.resolve("nested.json").mkdir()
            assertEquals(listOf("Pastor", "band"), lowerThirdPresetNames(folder))
            assertEquals(emptyList(), lowerThirdPresetNames(folder.resolve("missing")))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a media action plays, pauses or stops the player`() {
        val calls = mutableListOf<String>()
        val outlet = mediaOutlet({ calls += "play" }, { calls += "pause" }, { calls += "stop" })
        MediaCommand.entries.forEach(outlet)
        assertEquals(listOf("play", "pause", "stop"), calls)
    }

    @Test
    fun `no ATEM action runs until a switcher is set up`() = kotlinx.coroutines.runBlocking {
        var ran = false
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            runOnAtem(AtemSettings(host = " ")) { ran = true }
        }
        assertEquals("No ATEM switcher is set up", error.message)
        assertTrue(!ran)
    }

    @Test
    fun `a row's actions chain one level down, and stop at the depth limit`() {
        val seen = mutableListOf<Triple<List<Action>, String, Int>>()
        val run = { list: List<Action>, key: String, depth: Int -> seen += Triple(list, key, depth); Unit }
        runRowActionsChained("r1", emptyList(), -1, run)
        runRowActionsChained("r1", obs, -1, run)
        runRowActionsChained("r2", obs, 3, run)
        runRowActionsChained("r3", obs, ActionRunner.MAX_CHAIN_DEPTH - 1, run)
        assertEquals<List<Triple<List<Action>, String, Int>>>(listOf(Triple(obs, "r1", 0), Triple(obs, "r2", 4)), seen)
    }

    @Test
    fun `the editor is offered what settings hold, plus the lower thirds and scenes found`() {
        var opened = 0
        val settings = AppSettings(
            clearGroups = listOf(ClearGroup("g1", "Everything")),
            messageTemplates = listOf(MessageTemplate("m1", "Greeting", "Hello {name}")),
            props = listOf(PropDefinition("p1", "Logo")),
            macros = listOf(Macro("x1", "Walk in", obs)),
        )
        val choices = actionChoices(settings, listOf("Pastor"), listOf("Wide"), { opened++ })
        assertEquals(listOf("Everything"), choices.clearGroups)
        assertEquals(listOf("Greeting"), choices.messages.map { it.name })
        assertEquals(listOf("Logo"), choices.props)
        assertEquals(listOf("Walk in"), choices.macros)
        assertEquals(listOf("Pastor"), choices.lowerThirds)
        assertEquals(listOf("Wide"), choices.obsScenes)
        choices.onOpen()
        assertEquals(1, opened)
    }

    @Test
    fun `a companion press tries the named placement, or each one when none is named`() {
        val asked = mutableListOf<CompanionSurfaceSlot>()
        val last = CompanionSurfacePlacement.entries.last()
        val answers = { slot: CompanionSurfaceSlot, _: Int ->
            asked += slot
            if (slot.placement == last) true else null
        }
        assertTrue(pressOnSurface(Action.CompanionPress("c1", 3), answers))
        assertEquals(CompanionSurfacePlacement.entries.size, asked.size)

        asked.clear()
        val first = CompanionSurfacePlacement.entries.first()
        assertTrue(!pressOnSurface(Action.CompanionPress("c1", 3, first.name.lowercase()), answers))
        assertEquals(listOf(CompanionSurfaceSlot("c1", first)), asked)
    }
}
