package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.LiveHistoryEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class LiveStateBroadcastVerseCodeTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        dir = Files.createTempDirectory("cp-live-broadcast").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun ComposeUiTest.wire(pm: PresenterManager, server: CompanionServer, bible: Bible?) {
        setContent {
            LiveStateBroadcastWiring(
                appSettings = { AppSettings() },
                primaryBible = { bible },
                presenterManager = pm,
                companionServer = server,
                screenCountForUsage = 1,
                deckLinkCountForUsage = 0,
            )
        }
        waitForIdle()
    }

    private fun john316() = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God")

    @Test
    fun `a live verse is resolved through the primary bible into a code for a bible change`() = runComposeUiTest {
        val pm = PresenterManager()
        val server = CompanionServer()
        wire(pm, server, SpbFixture.loadedBible(dir))
        pm.setSelectedVerses(listOf(john316()))
        pm.setPresentingMode(Presenting.BIBLE)

        pm.onLiveStateChanged?.invoke(pm, Presenting.BIBLE)

        val state = assertNotNull(server.liveState.value)
        assertEquals(43, state.verseCodeBook)
        assertEquals(3, state.verseCodeChapter)
        assertEquals(16, state.verseCodeVerse)
    }

    @Test
    fun `a verse up behind another change carries no code`() = runComposeUiTest {
        val pm = PresenterManager()
        val server = CompanionServer()
        wire(pm, server, SpbFixture.loadedBible(dir))
        pm.setSelectedVerses(listOf(john316()))
        pm.setPresentingMode(Presenting.LYRICS)

        pm.onLiveStateChanged?.invoke(pm, Presenting.LYRICS)

        assertNull(assertNotNull(server.liveState.value).verseCodeBook)
    }

    @Test
    fun `a book the bible does not have resolves to no code`() = runComposeUiTest {
        val pm = PresenterManager()
        val server = CompanionServer()
        wire(pm, server, SpbFixture.loadedBible(dir))
        pm.setSelectedVerses(listOf(SelectedVerse(bookName = "Nowhere", chapter = 1, verseNumber = 1)))
        pm.setPresentingMode(Presenting.BIBLE)

        pm.onLiveStateChanged?.invoke(pm, Presenting.BIBLE)

        assertNull(assertNotNull(server.liveState.value).verseCodeBook)
    }

    @Test
    fun `scene, question and dictionary entry reach the broadcast`() = runComposeUiTest {
        val pm = PresenterManager()
        val server = CompanionServer()
        wire(pm, server, null)
        pm.setActiveScene(Scene(id = "s1", name = "Welcome"))
        pm.setDisplayedQuestion(Question(id = "q1", text = "Why?", timestamp = 1L))
        pm.setDisplayedDictionaryEntry(StrongsEntry("G26", "agape", "agape", "ag-ah'-pay", "love"))
        pm.setPresentingMode(Presenting.CANVAS)

        pm.onLiveStateChanged?.invoke(pm, Presenting.CANVAS)

        assertNotNull(server.liveState.value)
    }

    @Test
    fun `a section with lines but no title is still logged, with no section type`() {
        val pm = PresenterManager()
        pm.setLyricSection(LyricSection(title = "", lines = listOf("words")))
        pm.setPresentingMode(Presenting.LYRICS)

        val entry = liveHistoryEntryOf(pm, null)

        assertEquals("LYRICS", entry.contentType)
        assertNull(entry.sectionType)
    }

    @Test
    fun `a titled section with no lines is still logged`() {
        val pm = PresenterManager()
        pm.setLyricSection(LyricSection(title = "Grace", lines = emptyList()))
        pm.setPresentingMode(Presenting.LYRICS)

        assertEquals("Grace", liveHistoryEntryOf(pm, null).songTitle)
    }

    @Test
    fun `a presentation with no live slide is logged without a file`() {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.PRESENTATION)

        assertEquals(LiveHistoryEntry("PRESENTATION"), liveHistoryEntryOf(pm, null))
    }

    @Test
    fun `a presentation's live slide is logged by file and index`() {
        val pm = PresenterManager()
        pm.setLiveSlide("deck.pptx", 4)
        pm.setPresentingMode(Presenting.PRESENTATION)

        assertEquals(
            LiveHistoryEntry("PRESENTATION", fileName = "deck.pptx", slideIndex = 4),
            liveHistoryEntryOf(pm, null),
        )
    }

    @Test
    fun `a blank picture path is logged as nothing live`() {
        val pm = PresenterManager()
        pm.setSelectedImagePath("  ")
        pm.setPresentingMode(Presenting.PICTURES)

        assertEquals(LiveHistoryEntry("NONE"), liveHistoryEntryOf(pm, null))
    }

    @Test
    fun `media with no url is logged as nothing live`() {
        val pm = PresenterManager()
        pm.setCurrentMedia("", "")
        pm.setPresentingMode(Presenting.MEDIA)

        assertEquals(LiveHistoryEntry("NONE"), liveHistoryEntryOf(pm, null))
    }
}
