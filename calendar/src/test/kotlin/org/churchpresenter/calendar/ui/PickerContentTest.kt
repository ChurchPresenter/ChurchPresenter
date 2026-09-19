@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The picker with something to pick: a song folder on disk and presets beside the calendar.
 *
 * Without them half the sheet is an empty state, and the half that matters — a search that
 * narrows, a preset that previews itself, a pick that lands in the run of show — is never drawn.
 */
class PickerContentTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun songs() = songFolderWith(
        libraSong("1", "Amazing Grace"),
        libraSong("42", "Be Thou My Vision"),
        libraSong("7", "Silent Night", songbook = "Carols"),
    )

    private fun timerPreset() = ItemPreset(
        id = "p1",
        name = "Five minute countdown",
        item = ScheduleItem.AnnouncementItem(
            id = "t", text = "", isTimer = true, timerMode = TimerModes.DURATION, timerMinutes = 5,
        ),
    )

    private fun scenePreset() = ItemPreset(
        id = "p2",
        name = "Bible with Background",
        item = ScheduleItem.SceneItem(id = "s", sceneId = "scene-1", sceneName = "Scene"),
    )

    private fun ComposeUiTest.openPicker() {
        awaitText("Sunday Morning")
        clickFirst("Add song, verse or section")
        awaitText("Songs")
    }

    @Test
    fun `the songs tab lists what the folder holds`() {
        val folder = songs()
        try {
            withCalendar(documentWith(service(items = emptyList(), planned = emptyMap())), songFolder = folder) {
                openPicker()
                awaitText("Amazing Grace")

                assertTrue(shows("Be Thou My Vision"))
                assertTrue(shows("Silent Night"))
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `typing narrows the songs to what matches`() {
        val folder = songs()
        try {
            withCalendar(documentWith(service(items = emptyList(), planned = emptyMap())), songFolder = folder) {
                openPicker()
                awaitText("Amazing Grace")

                typeIntoFirstField("Silent")
                waitForIdle()

                assertTrue(shows("Silent Night"))
                assertTrue(!shows("Be Thou My Vision"), "and drops what does not")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `picking a song puts it in the run of show`() {
        val folder = songs()
        try {
            withCalendar(
                documentWith(service(items = emptyList(), planned = emptyMap())),
                songFolder = folder,
            ) { store ->
                openPicker()
                awaitText("Amazing Grace")

                clickFirst("Amazing Grace")
                waitForIdle()

                assertTrue(
                    stored(store).services.single().items.any { it is ScheduleItem.SongItem },
                    "the pick landed in the service behind the sheet",
                )
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `the presets tab lists what has been saved, by kind`() =
        withCalendar(documentWith(service())) { folder ->
            seedPresets(folder, timerPreset(), scenePreset())
            // Presets are re-read whenever something that offers them opens.
            clickFirst("Add song, verse or section")
            awaitText("Presets")
            clickFirst("Presets")

            awaitText("Five minute countdown")
            assertTrue(shows("Bible with Background"))
        }

    @Test
    fun `a preset shows what it will put on screen`() = withCalendar(documentWith(service())) { folder ->
        seedPresets(folder, timerPreset())
        clickFirst("Add song, verse or section")
        awaitText("Presets")
        clickFirst("Presets")
        awaitText("Five minute countdown")

        clickIcon("Preview")
        awaitText("5:00")

        assertTrue(shows("Counts down"), "a timer previews as the readout it starts on")
    }

    @Test
    fun `a preset keeps the name it was saved under when it becomes a row`() =
        withCalendar(documentWith(service(items = emptyList(), planned = emptyMap()))) { folder ->
            seedPresets(folder, scenePreset())
            clickFirst("Add song, verse or section")
            awaitText("Presets")
            clickFirst("Presets")
            awaitText("Bible with Background")

            clickFirst("Bible with Background")
            waitForIdle()

            val row = stored(folder).services.single().items.single()
            assertTrue(
                row.displayText == "Bible with Background",
                "a scene left on its default name would otherwise read `Scene: Scene`",
            )
        }
}
