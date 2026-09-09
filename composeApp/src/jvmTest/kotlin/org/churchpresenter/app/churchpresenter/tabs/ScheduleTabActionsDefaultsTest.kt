package org.churchpresenter.app.churchpresenter.tabs

import kotlin.test.Test
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * `MainDesktop` holds a `ScheduleTabActions()` built from every default before the tab's own
 * `LaunchedEffect` publishes the real one — a placeholder state, live only for a moment, but real
 * production code all the same. Its every field is a no-op, so this asserts only that constructing
 * it and invoking each one does not throw.
 */
class ScheduleTabActionsDefaultsTest {

    @Test
    fun `every default action is a safe no-op`() {
        val actions = ScheduleTabActions()

        actions.newSchedule()
        actions.openSchedule()
        actions.saveSchedule()
        actions.saveScheduleAs()
        actions.removeSelected()
        actions.removeById("id")
        actions.clearSchedule()
        actions.moveSelectedToTop()
        actions.moveSelectedUp()
        actions.moveSelectedDown()
        actions.moveSelectedToBottom()
        actions.addLabel("text", "#FFFFFF", "#000000")
        actions.updateLabel("id", "text", "#FFFFFF", "#000000")
        actions.addBibleVerse("John", 3, 16, "text", "16-17", 43)
        actions.addSong(1, "title", "songbook", "songId")
        actions.addPicture("path", "name", 1)
        actions.addPresentation("path", "name", 1, "pptx")
        actions.addMedia("url", "title", "local")
        actions.addLowerThird("presetId", "label", true, 1_000L)
        actions.addAnnouncement(
            "text", "#FFFFFF", "#000000", 48, "Arial",
            false, false, false, false, "#000000", 100, 78,
            "center", "center", "SLIDE_FROM_BOTTOM", 500, 0,
            false, 0, 0, 0, "#FFFFFF", "", "duration",
            0, 0, 0, "HH:mm:ss", TextBackdrop(),
        )
        actions.addWebsite("url", "title")
        actions.updateWebsiteTitle("url", "title")
        actions.addScene("sceneId", "sceneName")
        actions.addDictionary("number", "word", "transliteration", "definition")
    }
}
