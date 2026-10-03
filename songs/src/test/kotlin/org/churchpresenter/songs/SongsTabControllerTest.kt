package org.churchpresenter.songs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongsTabControllerTest {

    private lateinit var dir: File
    private val vms = mutableListOf<SongsViewModel>()
    private val sent = mutableListOf<LyricSection>()
    private val projects = mutableListOf<ScheduleItem>()
    private val sections = mutableListOf<Triple<String, Int, Int>>()
    private val wentLive = mutableListOf<String>()
    private var lastSettings: AppSettings? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-controller").toFile()
    }

    @AfterTest
    fun tearDown() {
        vms.forEach { it.dispose() }
        dir.deleteRecursively()
    }

    private fun write(number: String, title: String) {
        SongFileParser().writeSongFile(
            SongItem(number = number, title = title, songbook = "Hymnal", lyrics = listOf("[Verse 1]", "a", "b")),
            File(File(dir, "Hymnal").apply { mkdirs() }, "$number - $title.song").absolutePath,
        )
    }

    private fun controller(
        songs: SongSettings = SongSettings(),
        widths: Map<String, Float> = emptyMap(),
    ): SongsTabController {
        val settings = AppSettings().withSongsEverywhere(songs.copy(storageDirectory = dir.absolutePath))
        val vm = SongsViewModel(
            settings,
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also { vms += it }
        return SongsTabController(vm, SongDialogRequests(), SongLiveState()).apply {
            appSettings = settings
            titleSlideFor = ::fakeTitleSlide
            onSongItemSelected = { sent += it }
            onSongWentLive = { wentLive += it.title }
            onInstanceLinkSendProject = { projects += it }
            onInstanceLinkSendSongSection = { n, s, l -> sections += Triple(n, s, l) }
            onSettingsChange = { transform -> lastSettings = transform(lastSettings ?: settings) }
            density = Density(1f)
            columns = SongTableColumns(Density(1f), widths, listOf(SongColumnId.NUMBER), emptySet())
            favPanelHeight = mutableStateOf(0f)
            lyricsPanel = mutableStateOf(240f)
        }
    }

    @Test
    fun `with no songs a push sends nothing and records no song`() {
        val c = controller()
        c.sendToPresenter(goLive = true)

        assertTrue(sent.isEmpty())
        assertNull(c.live.songId)
        assertTrue(projects.isEmpty())
    }

    @Test
    fun `going live with a new song asks the primary for the project, then only the section`() {
        write("1", "One")
        val c = controller()
        c.viewModel.selectSong(0)

        c.sendToPresenter(goLive = true)
        c.sendToPresenter(goLive = true)

        assertEquals(1, projects.size)
        assertEquals(listOf("One"), wentLive)
        assertEquals("1", sections.single().first)
    }

    @Test
    fun `a push while presenting counts the song without asking the primary`() {
        write("1", "One")
        val c = controller()
        c.viewModel.selectSong(0)
        c.isPresenting = true

        c.sendToPresenter()

        assertEquals(listOf("One"), wentLive)
        assertTrue(projects.isEmpty())
    }

    @Test
    fun `a song with a non-numeric number goes out as number zero`() {
        write("12b", "Odd")
        val c = controller()
        c.viewModel.selectSong(0)

        c.sendToPresenter(goLive = true)

        assertEquals(0, (projects.single() as ScheduleItem.SongItem).songNumber)
    }

    @Test
    fun `the title slide goes out first while it is selected and enabled`() {
        write("1", "One")
        val c = controller(SongSettings(titleSlideEnabled = true))
        c.viewModel.selectSong(0)
        c.live.titleSlideSelected = true

        c.sendToPresenter()

        assertEquals(Constants.SECTION_TYPE_TITLE_SLIDE, sent.last().type)
        assertEquals(-1, c.live.sectionIndex)
    }

    @Test
    fun `a selected title slide is ignored when the slide is turned off`() {
        write("1", "One")
        val c = controller(SongSettings(titleSlideEnabled = false))
        c.viewModel.selectSong(0)
        c.viewModel.selectSection(0)
        c.live.titleSlideSelected = true

        c.sendToPresenter()

        assertFalse(sent.last().type == Constants.SECTION_TYPE_TITLE_SLIDE)
    }

    @Test
    fun `stepping back onto the title slide needs it enabled, a song, and not already there`() {
        write("1", "One")
        val off = controller(SongSettings(titleSlideEnabled = false))
        assertFalse(off.backToTitleSlide())

        val on = controller(SongSettings(titleSlideEnabled = true))
        assertTrue(on.backToTitleSlide())
        assertFalse(on.backToTitleSlide(), "already on it")
    }

    @Test
    fun `stepping back with no song selected is refused`() {
        val c = controller(SongSettings(titleSlideEnabled = true))
        assertFalse(c.backToTitleSlide())
    }

    @Test
    fun `leaving the title slide lands on the first section`() {
        write("1", "One")
        val c = controller(SongSettings(titleSlideEnabled = true))
        assertFalse(c.leaveTitleSlide())

        c.live.titleSlideSelected = true
        assertTrue(c.leaveTitleSlide())
        assertEquals(0, c.viewModel.selectedSectionIndex.value)
    }

    @Test
    fun `an edited live song is pushed at its previous position`() {
        write("1", "One")
        val c = controller()
        c.live.sectionIndex = 0
        c.live.lineIndex = 5
        val edited = c.viewModel.filteredSongItems.value[0]

        c.sendEditedSongToPresenter(edited, SongTuning(bpm = 80))

        assertEquals(80, sent.last().bpm)
        assertEquals(0, c.live.sectionIndex)
    }

    @Test
    fun `the lyrics panel width is saved to the maximized or the windowed layout`() {
        val c = controller()
        c.isMaximized = true
        c.saveLyricsPanelWidth()
        assertEquals(240, lastSettings?.maximizedLayout?.lyricsPanelWidthDp)

        c.isMaximized = false
        c.lyricsPanelPx = 300f
        c.saveLyricsPanelWidth()
        assertEquals(300, lastSettings?.windowedLayout?.lyricsPanelWidthDp)
    }

    @Test
    fun `column widths the table has none for keep their saved values`() {
        val c = controller()
        c.saveColWidths()
        val defaults = SongSettings()
        assertEquals(defaults.colWidthTitle, lastSettings?.songSettings?.colWidthTitle)
        assertEquals(defaults.colWidthComposer, lastSettings?.songSettings?.colWidthComposer)
    }

    @Test
    fun `column widths the table has are written as dp`() {
        val all = listOf(
            SongColumnId.NUMBER, SongColumnId.TITLE, SongColumnId.SONGBOOK, SongColumnId.TUNE,
            SongColumnId.PLAY_COUNT, SongColumnId.AUTHOR, SongColumnId.COMPOSER,
        ).associateWith { 77f }
        val c = controller(widths = all)
        c.saveColWidths()
        assertEquals(77, lastSettings?.songSettings?.colWidthNumber)
        assertEquals(77, lastSettings?.songSettings?.colWidthComposer)
    }

    @Test
    fun `reading the columns or the focus rescue before the tab sets them fails loudly`() {
        val c = SongsTabController(controller().viewModel, SongDialogRequests(), SongLiveState())
        assertFailsWith<IllegalStateException> { c.columns }
        assertFailsWith<IllegalStateException> { c.focusRescue }
    }
}
