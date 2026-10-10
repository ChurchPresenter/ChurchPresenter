@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.ui.ColorPickerRequest
import org.churchpresenter.calendar.ui.SongEditRequest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.lottiegen.GuidedControl
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.utils.ScreenArea
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.songlibrary.ui.SongEditorRequest
import org.churchpresenter.theme.ThemeMode
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class ToolWindowSlotsTest {

    private val song = SongItem(number = "1", title = "Amazing Grace", songbook = "Hymnal")
    private val other = SongItem(number = "2", title = "How Great Thou Art", songbook = "Hymnal")

    private class Shown {
        var theme: ThemeMode? = null
        var editing: SongEditorRequest? = null
        var typicalSeconds: Int? = null
        val dialog: SongEditorDialog = { theme, editing, typicalSeconds ->
            this.theme = theme
            this.editing = editing
            this.typicalSeconds = typicalSeconds
        }
    }

    @Test
    fun `the calendar opens over the usable screen area, or at its own size when there is none`() {
        assertNull(calendarWindowBounds(null))
        assertEquals(
            Rectangle(10, 20, 1900, 1040),
            calendarWindowBounds(ScreenArea(10.dp, 20.dp, 1900.dp, 1040.dp)),
        )
    }

    @Test
    fun `on Linux the calendar keeps the host's own file choosers`() {
        val host = CalendarHost()
        assertSame(host, calendarOwnedHost(host, "Linux", CalendarFileChoosers({ _, _, _ -> null }, { _, _ -> null })))
    }

    @Test
    fun `on macOS and Windows the calendar's file choosers are the window's own`() = runBlocking {
        val folder = Files.createTempDirectory("calendar-choosers").toFile()
        try {
            val saved = File(folder, "plan.pdf")
            val logo = File(folder, "logo.png")
            val asked = mutableListOf<String>()
            var savedAs: Pair<String, File?>? = null
            var extensions: Set<String>? = null
            val choosers = CalendarFileChoosers(
                save = { title, suggested, into ->
                    asked += title
                    savedAs = suggested to into
                    saved
                },
                open = { title, exts ->
                    asked += title
                    extensions = exts
                    logo
                },
            )
            val host = CalendarHost()
            listOf("Mac OS X", "Windows 11").forEach { os ->
                assertNotSame(host, calendarOwnedHost(host, os, choosers))
            }
            val owned = calendarOwnedHost(host, "Mac OS X", choosers)

            assertEquals(saved, owned.chooseExportFile("plan.pdf", folder))
            assertEquals(logo, owned.chooseImageFile())
            assertEquals("plan.pdf" to folder, savedAs)
            assertEquals(setOf("png", "jpg", "jpeg"), extensions)
            assertEquals(listOf("Export Run of Show", "Choose a church logo"), asked)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `each guided control of the lower-third generator is tagged with its own target`() {
        mapOf(
            GuidedControl.NAME to GuideTargets.LOWER_THIRD_NAME,
            GuidedControl.INFO to GuideTargets.LOWER_THIRD_INFO,
            GuidedControl.SAVE to GuideTargets.LOWER_THIRD_SAVE,
        ).forEach { (control, target) ->
            assertEquals(Modifier.guideTarget(target), lowerThirdControlTag(control))
        }
    }

    @Test
    fun `the calendar's color picker reports the color chosen`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme {
                CalendarColorPicker(
                    ColorPickerRequest(initialHex = "#123456", onPicked = { picked = it }, onDismiss = {}),
                )
            }
        }
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals("#123456", picked)
    }

    @Test
    fun `the calendar's color picker can be dismissed`() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MaterialTheme {
                CalendarColorPicker(
                    ColorPickerRequest(initialHex = "#123456", onPicked = {}, onDismiss = { dismissed++ }),
                )
            }
        }
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `the song library hands its request to the app's editor with the song's measured length`() = runComposeUiTest {
        val shown = Shown()
        val request = SongEditorRequest(song, listOf("Hymnal"), listOf(song, other), onSave = {}, onDismiss = {})
        setContent {
            songLibrarySongEditor(ThemeMode.DARK, { if (it == song) 245 else null }, shown.dialog)(request)
        }
        waitForIdle()
        assertEquals(ThemeMode.DARK, shown.theme)
        assertSame(request, shown.editing)
        assertEquals(245, shown.typicalSeconds)
    }

    @Test
    fun `a song saved from the calendar is saved there and counted as edited`() = runComposeUiTest {
        val shown = Shown()
        var saved: SongItem? = null
        var dismissed = 0
        val request = SongEditRequest(
            song = song,
            songbooks = listOf("Hymnal"),
            allSongs = listOf(song, other),
            onSave = { saved = it },
            onDismiss = { dismissed++ },
        )
        setContent {
            calendarSongEditor(ThemeMode.LIGHT, { null }, shown.dialog)(request)
        }
        waitForIdle()
        val editing = requireNotNull(shown.editing)
        assertEquals(ThemeMode.LIGHT, shown.theme)
        assertNull(shown.typicalSeconds)
        assertEquals(song, editing.song)
        assertEquals(listOf("Hymnal"), editing.songbooks)
        assertEquals(listOf(song, other), editing.allSongs)

        val before = UsageEvents.unreported()[UsageEvent.SONG_EDITED] ?: 0
        val edited = song.copy(title = "Amazing Grace (My Chains Are Gone)")
        editing.onSave(edited)
        assertEquals(edited, saved)
        assertEquals(before + 1, UsageEvents.unreported()[UsageEvent.SONG_EDITED] ?: 0)

        editing.onDismiss()
        assertEquals(1, dismissed)
    }
}
