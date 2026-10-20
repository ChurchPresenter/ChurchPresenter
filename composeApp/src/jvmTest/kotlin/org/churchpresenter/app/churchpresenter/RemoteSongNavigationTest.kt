package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.server.ScheduleSongDto
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * A remote's `select_song`: the schedule moves to the song's row and the Songs tab opens the song
 * ready for Go Live. It used to be acknowledged and then dropped, so a tablet's song list did nothing.
 */
class RemoteSongNavigationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )
    private val selected = mutableListOf<String>()
    private val added = mutableListOf<String>()
    private val opened = mutableListOf<RemoteSongSelection>()
    private val listener: Job = CoroutineScope(Dispatchers.Unconfined).launch {
        root.remoteSelectSongFlow.collect { opened += it }
    }

    private val row = ScheduleItem.SongItem(
        id = "row-1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal", songId = "Hymnal::42",
    )

    init {
        root.scheduleActions = ScheduleActions(
            selectItem = { selected += it },
            addSong = { number, title, _, _ -> added += "$number $title" },
        )
        root.currentScheduleItems = listOf(row)
    }

    @AfterTest
    fun tearDown() {
        listener.cancel()
        scope.cancel()
    }

    @Test
    fun `a song in the schedule has its row selected and is opened ready for Go Live`() {
        root.readyRemoteSong(ScheduleSongDto(id = "", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal"))

        assertEquals(listOf("row-1"), selected)
        assertEquals(row, opened.single().item, "the row itself, songId and all")
        assertFalse(opened.single().goLive, "nothing goes on screen")
        assertEquals(emptyList(), added, "and nothing is added to the schedule")
    }

    @Test
    fun `a song not in the schedule is opened on its own and the schedule is left alone`() {
        root.readyRemoteSong(ScheduleSongDto(id = "", songNumber = 7, title = "It Is Well", songbook = "Hymnal"))

        assertEquals(emptyList(), selected)
        assertEquals(emptyList(), added)
        val item = opened.single().item
        assertEquals(7 to "It Is Well", item.songNumber to item.title)
        assertNotEquals("", item.id)
        assertFalse(opened.single().goLive)
    }
}
