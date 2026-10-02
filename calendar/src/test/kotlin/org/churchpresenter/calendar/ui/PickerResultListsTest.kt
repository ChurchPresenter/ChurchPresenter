@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The picker's result lists on their own, fed exactly what each case needs: the sections a query
 * matches or would create, the ministry row typed in and added with Enter, and the presets a
 * query and a kind leave showing. Through the whole window each of these is several sheets deep.
 */
class PickerResultListsTest {

    private val sections = listOf(SectionStyle("Pre-Service", "#4FD3E8"), SectionStyle("Worship", "#5B9DF5"))

    private fun ComposeUiTest.show(content: @Composable () -> Unit) {
        setContent { AppThemeWrapper(theme = ThemeMode.LIGHT) { content() } }
        waitForIdle()
    }

    // ── Songs ───────────────────────────────────────────────────────────────────

    private val library = listOf(
        SongItem(number = "1", title = "Amazing Grace", songbook = "Hymns"),
        SongItem(number = "", title = "Amazing Love", songbook = "Choruses"),
    )

    @Test
    fun `a reference typed into the song search is offered as a passage, even with no song matching`() =
        runComposeUiTest {
            val added = mutableListOf<ScheduleItem>()
            show {
                SongResults(
                    library, songsLoaded = true, songBook = null, query = "John 3:16",
                    onAdd = { added += it }, onEditSong = null,
                )
            }
            assertTrue(!shows("Nothing matches that"))
            clickFirst("John 3:16")
            assertEquals("John", assertIs<ScheduleItem.BibleVerseItem>(added.single()).bookName)
        }

    @Test
    fun `a book scope leaves only that book's songs, and a song with no number is its title`() = runComposeUiTest {
        var book by mutableStateOf<String?>("Hymns")
        show {
            SongResults(library, songsLoaded = true, songBook = book, query = "amazing", onAdd = {}, onEditSong = null)
        }
        assertTrue(shows("1 - Amazing Grace"))
        assertTrue(!shows("Amazing Love"))

        book = null
        waitForIdle()
        assertTrue(shows("Amazing Love"))
        assertTrue(!shows("- Amazing Love"), "no number, no dash")
    }

    // ── Sections ────────────────────────────────────────────────────────────────

    @Test
    fun `a section that already exists is offered, matched whatever the case`() = runComposeUiTest {
        val added = mutableListOf<ScheduleItem>()
        show { SectionResults(sections, query = "WORSHIP", onAdd = { added += it }) }
        assertTrue(shows("Worship"))
        assertTrue(!shows("New section"), "typing a name that exists offers no new one")
        clickFirst("Worship")
        assertEquals("Worship", assertIs<ScheduleItem.LabelItem>(added.single()).text)
    }

    @Test
    fun `a name that matches nothing is offered as a new section`() = runComposeUiTest {
        val added = mutableListOf<ScheduleItem>()
        show { SectionResults(sections, query = "  Baptism ", onAdd = { added += it }) }
        assertTrue(shows("New section"))
        clickFirst("Baptism")
        assertEquals("Baptism", assertIs<ScheduleItem.LabelItem>(added.single()).text, "trimmed")
    }

    @Test
    fun `with no sections kept and nothing typed there is nothing to offer`() = runComposeUiTest {
        show { SectionResults(emptyList(), query = "", onAdd = {}) }
        assertTrue(shows("Nothing matches that"))
    }

    // ── Ministry ────────────────────────────────────────────────────────────────

    @Composable
    private fun ministry(title: String, duration: String, added: MutableList<ScheduleItem>) = MinistryResults(
        title = title, onTitle = {}, detail = " the choir ", onDetail = {},
        duration = duration, onDuration = {}, onAdd = { added += it },
    )

    @Test
    fun `Enter adds the ministry row once it has a name`() = runComposeUiTest {
        val added = mutableListOf<ScheduleItem>()
        show { ministry(title = "Anthem", duration = "4:30", added) }
        val field = onAllNodes(hasSetTextAction()).onLast()
        field.performClick()
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        val row = assertIs<ScheduleItem.MinistryItem>(added.single())
        assertEquals("Anthem", row.title)
        assertEquals("the choir", row.detail)
    }

    @Test
    fun `Enter with no name adds nothing, and a duration that is not a time is still kept typed`() =
        runComposeUiTest {
            val added = mutableListOf<ScheduleItem>()
            show { ministry(title = "  ", duration = "later", added) }
            val field = onAllNodes(hasSetTextAction()).onLast()
            field.performClick()
            field.performKeyInput { pressKey(Key.NumPadEnter) }
            waitForIdle()
            assertTrue(added.isEmpty())
            assertTrue(shows("later"), "an unreadable duration is shown, flagged, not thrown away")
        }

    // ── Presets ─────────────────────────────────────────────────────────────────

    private val presets = listOf(
        ItemPreset("p1", "Opener", ScheduleItem.SceneItem("s", "scene-1", "Welcome Scene")),
        ItemPreset("p2", "Welcome loop", ScheduleItem.PictureItem("pic", "/x/welcome", "Lobby pictures", 4)),
    )

    @Test
    fun `a preset is found by its name or by what it shows`() = runComposeUiTest {
        var query by mutableStateOf("opener")
        show { PresetResults(presets, kind = null, query = query, previewSources = PreviewSources(), onAdd = {}) }
        assertTrue(shows("Opener"))
        assertTrue(!shows("Welcome loop"))

        query = "lobby"
        waitForIdle()
        assertTrue(shows("Welcome loop"), "matched on the item's own text")

        query = "nothing like it"
        waitForIdle()
        assertTrue(shows("Nothing matches that"))
    }

    @Test
    fun `a kind leaves only its presets`() = runComposeUiTest {
        show {
            PresetResults(presets, kind = PresetKind.SCENES, query = "", previewSources = PreviewSources(), onAdd = {})
        }
        assertTrue(shows("Opener"))
        assertTrue(!shows("Welcome loop"))
    }
}
