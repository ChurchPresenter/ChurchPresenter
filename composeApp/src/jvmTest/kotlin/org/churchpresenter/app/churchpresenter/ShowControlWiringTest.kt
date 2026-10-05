package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
}
