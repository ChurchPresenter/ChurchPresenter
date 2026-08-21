package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.PlanningCenterClient
import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Matching a Planning Center plan item against the local song library.
 *
 * This decides whether an import reuses the song the church already has or creates a second copy
 * of it, so each tier is driven directly rather than through a mocked plan load: CCLI number
 * first, then a leading four-digit song number, then the title. A tier that matches nothing has
 * to fall through to the next one rather than stopping the search.
 */
class PlanningCenterSongMatchTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-match").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val viewModel by lazy {
        PlanningCenterImportViewModel(
            initialAccessToken = "valid-token",
            initialRefreshToken = "refresh-token",
            initialExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
            initialServiceTypeId = "st-1",
            importSongbookName = "Planning Center",
            onTokensRefreshed = { _, _, _ -> },
        )
    }

    private fun song(number: String, title: String, ccli: String = "") =
        SongItem(number = number, title = title, songbook = "Hymnal", ccliNumber = ccli)

    private fun item(
        title: String,
        itemType: String = "song",
        songTitle: String? = null,
        ccli: String? = null,
    ) = PlanningCenterClient.PlanItem(
        id = "i-1", title = title, itemType = itemType, sequence = 0,
        songTitle = songTitle, songCcliNumber = ccli,
    )

    private val catalog = listOf(
        song(number = "0042", title = "Amazing Grace", ccli = "22025"),
        song(number = "0100", title = "How Great Thou Art"),
    )

    @Test
    fun `a plan item that is not a song is never matched`() {
        // A header row carrying a song's title must not pull that song into the schedule.
        assertNull(viewModel.matchLocalSong(item("Amazing Grace", itemType = "header"), catalog))
    }

    @Test
    fun `a ccli number matches whatever the title says`() {
        val matched = viewModel.matchLocalSong(item("Renamed In Planning Center", ccli = "22025"), catalog)
        assertEquals("0042", matched?.number)
    }

    @Test
    fun `a ccli number nothing in the library carries falls through to the title`() {
        val matched = viewModel.matchLocalSong(item("How Great Thou Art", ccli = "99999"), catalog)
        assertEquals("0100", matched?.number)
    }

    @Test
    fun `a song with no ccli number of its own is still reachable by title`() {
        // The catalogue entry's blank ccliNumber must not be treated as matching a blank query.
        val matched = viewModel.matchLocalSong(item("how great thou art"), catalog)
        assertEquals("0100", matched?.number)
    }

    @Test
    fun `a leading four-digit number matches the song bearing it`() {
        val matched = viewModel.matchLocalSong(item("0100 Something Else Entirely"), catalog)
        assertEquals("0100", matched?.number)
    }

    @Test
    fun `a leading number the library does not have leaves the item unmatched`() {
        // The title tier compares the whole title, number and all, so "0777 Amazing Grace" does
        // not quietly become the library's "Amazing Grace" under a different number.
        assertNull(viewModel.matchLocalSong(item("0777 Amazing Grace"), catalog))
    }

    @Test
    fun `the song title field is preferred over the item title`() {
        val matched = viewModel.matchLocalSong(item("Opening", songTitle = "Amazing Grace"), catalog)
        assertEquals("0042", matched?.number)
    }

    @Test
    fun `an item matching nothing at all is left unmatched`() {
        assertNull(viewModel.matchLocalSong(item("A Song Nobody Here Has"), catalog))
    }

    @Test
    fun `an empty library matches nothing`() {
        assertNull(viewModel.matchLocalSong(item("Amazing Grace", ccli = "22025"), emptyList()))
    }
}
