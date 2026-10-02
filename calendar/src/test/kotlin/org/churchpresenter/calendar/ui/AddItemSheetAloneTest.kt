@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The picker as a component of its own, given only what it cannot do without -- the way any
 * caller but the Calendar Manager would use it -- so its defaults are what it runs on: no timing,
 * no planned length, nothing measured, no previews.
 */
class AddItemSheetAloneTest {

    private class Added(val items: List<ScheduleItem>, val planned: Int?, val timing: RowTiming)

    private val songs = listOf(SongItem(number = "7", title = "Be Thou My Vision", songbook = "Hymns"))
    private val sections = listOf(SectionStyle("Pre-Service", "#4FD3E8"), SectionStyle("Worship", "#5B9DF5"))

    private fun ComposeUiTest.picker(replacing: ScheduleItem? = null, added: MutableList<Added>) {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                AddItemSheet(
                    songs = songs,
                    songsLoaded = true,
                    presets = emptyList(),
                    sections = sections,
                    bibleBooks = emptyList(),
                    serviceName = "Sunday Morning",
                    serviceStartTime = "10:00",
                    replacing = replacing,
                    songbooks = listOf("Hymns"),
                    songEditor = null,
                    onSaveSong = { _, _ -> },
                    onAdd = { items, planned, timing -> added += Added(items, planned, timing) },
                    onDismiss = {},
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun `a pick with nothing changed goes on with no length and the default timing`() = runComposeUiTest {
        val added = mutableListOf<Added>()
        picker(added = added)
        awaitText("Be Thou My Vision")

        clickFirst("Be Thou My Vision")

        val pick = added.single()
        assertEquals("Be Thou My Vision", assertIs<ScheduleItem.SongItem>(pick.items.single()).title)
        assertEquals(null, pick.planned)
        assertEquals(RowTiming.DEFAULT, pick.timing)
    }

    @Test
    fun `editing a heading opens on the sections, and its replacement is a heading`() = runComposeUiTest {
        val added = mutableListOf<Added>()
        picker(replacing = heading("h", "Pre-Service"), added = added)
        awaitText("Worship")

        // Last: the search box's own hint names a section too.
        clickLast("Worship")

        assertEquals("Worship", assertIs<ScheduleItem.LabelItem>(added.single().items.single()).text)
        assertTrue(!shows("Be Thou My Vision"), "not the songs")
    }
}
