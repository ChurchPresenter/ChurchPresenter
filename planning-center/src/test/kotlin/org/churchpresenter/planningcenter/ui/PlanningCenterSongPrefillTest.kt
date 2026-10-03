package org.churchpresenter.planningcenter.ui

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A plan song the library does not have: the lyrics the add-song editor opens with, and the song
 * file written once the operator confirms it.
 *
 * The prefill prefers the song's arrangement and falls back to the item's description, then its
 * rich details; the file is written straight into the song library's songbook folder.
 */
class PlanningCenterSongPrefillTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun setUp() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-prefill-home").toFile()
        System.setProperty("user.home", home.absolutePath)
        val songDir = File(home, "songs").also { it.mkdirs() }
        val manager = SettingsManager()
        manager.saveSettings(
            manager.loadSettings().let { s ->
                s.copy(songSettings = s.songSettings.copy(storageDirectory = songDir.absolutePath))
            },
        )
        mockkObject(PlanningCenterClient)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PlanningCenterClient)
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun viewModel() = PlanningCenterImportViewModel(
        initialAccessToken = "valid-token",
        initialRefreshToken = "refresh",
        initialExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
        initialServiceTypeId = "st-1",
        importSongbookName = "Planning Center",
        onTokensRefreshed = { _, _, _ -> },
    )

    // ── Lyrics prefill ──────────────────────────────────────────────────────────

    private fun songItem(
        id: String = "s1",
        description: String = "",
        htmlDetails: String = "",
        songId: String? = null,
        arrangementId: String? = null,
    ) = PlanningCenterClient.PlanItem(
        id = id, title = "A Song", description = description, htmlDetails = htmlDetails,
        itemType = "song", sequence = 0, songId = songId, arrangementId = arrangementId,
    )

    @Test
    fun `an arrangement's lyrics win over the description`() = runBlocking {
        coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any(), any()) } returns
            PlanningCenterClient.ArrangementOutcome.Success(
                PlanningCenterClient.ArrangementDetail(chordChart = "G C D", lyrics = "Arrangement lyrics"),
            )
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(description = "Description lyrics", songId = "song-1", arrangementId = "arr-1"),
        )
        assertEquals("Arrangement lyrics", assertNotNull(detail).lyrics)
        assertEquals("G C D", detail.chordChart)
    }

    @Test
    fun `a blank arrangement falls back to the description`() {
        // PCO returns an arrangement record with no lyrics when nobody filled it in.
        runBlocking {
            coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any(), any()) } returns
                PlanningCenterClient.ArrangementOutcome.Success(
                    PlanningCenterClient.ArrangementDetail(chordChart = "", lyrics = "   "),
                )
            val detail = viewModel().fetchArrangementForAddSong(
                songItem(description = "Description lyrics", songId = "song-1", arrangementId = "arr-1"),
            )
            assertEquals("Description lyrics", assertNotNull(detail).lyrics)
        }
    }

    @Test
    fun `a failed arrangement request falls back to the description`() = runBlocking {
        coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any(), any()) } returns
            PlanningCenterClient.ArrangementOutcome.NetworkError
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(description = "Description lyrics", songId = "song-1", arrangementId = "arr-1"),
        )
        assertEquals("Description lyrics", assertNotNull(detail).lyrics)
    }

    @Test
    fun `html details are used when there is no description`() = runBlocking {
        // Worship leaders paste lyrics into PCO's rich "Details" box as often as the plain field.
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(htmlDetails = "<p>First line</p><p>Second line</p>"),
        )
        val lyrics = assertNotNull(detail).lyrics
        assertTrue(lyrics.contains("First line"))
        assertTrue(lyrics.contains("Second line"))
        assertFalse(lyrics.contains("<p>"), "the HTML must be converted to plain text")
    }

    @Test
    fun `the description wins over html details`() = runBlocking {
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(description = "Plain description", htmlDetails = "<p>Rich details</p>"),
        )
        assertEquals("Plain description", assertNotNull(detail).lyrics)
    }

    @Test
    fun `html details that render to nothing yield no prefill`() = runBlocking {
        assertNull(viewModel().fetchArrangementForAddSong(songItem(htmlDetails = "<p></p>")))
    }

    @Test
    fun `a song with no arrangement ids never calls the api`() = runBlocking {
        // No songId/arrangementId means there is nothing to request.
        val detail = viewModel().fetchArrangementForAddSong(songItem(description = "Description only"))
        assertEquals("Description only", assertNotNull(detail).lyrics)
    }

    // ── Creating local songs ────────────────────────────────────────────────────

    @Test
    fun `a created song is returned with its source file populated`() {
        val created = assertNotNull(
            createLocalSong(
                SongItem(number = "0500", title = "Imported", songbook = "Hymnal", lyrics = listOf("line")),
            ),
        )
        assertTrue(created.sourceFile.endsWith("0500 - Imported.song"))
        assertTrue(File(created.sourceFile).exists())
        assertTrue(created.songId.isNotBlank(), "the caller adds this straight to the schedule")
    }

    @Test
    fun `a song with no number is filed under its title`() {
        val created = assertNotNull(
            createLocalSong(SongItem(
                number = "",
                title = "No Number",
                songbook = "Hymnal",
                lyrics = listOf("l"),
            )),
        )
        assertTrue(created.sourceFile.endsWith("No Number.song"))
    }

    @Test
    fun `a song with no songbook is refused`() {
        assertNull(
            createLocalSong(SongItem(number = "1", title = "Orphan", songbook = "", lyrics = listOf("l"))),
        )
    }
}
