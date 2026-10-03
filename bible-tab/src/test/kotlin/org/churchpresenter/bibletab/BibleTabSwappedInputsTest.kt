@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.stt.STTManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BibleTabSwappedInputsTest {

    private lateinit var dir: File
    private val models = mutableListOf<BibleViewModel>()
    private val managers = mutableListOf<STTManager>()

    private class Engine(up: Boolean) : BibleEngineStatus {
        override val connected: State<Boolean> = mutableStateOf(up)
        override val startFailed: State<Boolean> = mutableStateOf(false)
        override val engineSttConnected: State<Boolean?> = mutableStateOf(up)
    }

    private class Inputs(
        val vm: BibleViewModel,
        val settings: AppSettings,
        val stt: STTManager,
        val engine: BibleEngineStatus,
        val log: VerseSequenceLog,
        val output: FakeBibleOutput = FakeBibleOutput(),
        val refs: CrossReferenceRepository = noCrossReferences(),
    )

    @BeforeTest
    fun load() {
        dir = Files.createTempDirectory("cp-bible-tab-swap").toFile()
        SpbFixture.spbFile(dir, content = bibleFixture)
    }

    @AfterTest
    fun clean() {
        models.forEach { it.dispose() }
        managers.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun settings(primary: String = "test.spb", devMode: Boolean = true) = AppSettings(
        bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = primary),
        bibleEngineSettings = BibleEngineSettings(enabled = true, helpDevMode = devMode),
    )

    private fun model(settings: AppSettings) =
        BibleViewModel(settings, dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
            .also { models += it }

    private fun stt() = STTManager().also { managers += it; it.applyConnected() }

    private fun inputs(settings: AppSettings, up: Boolean) = Inputs(
        vm = model(settings),
        settings = settings,
        stt = stt(),
        engine = Engine(up),
        log = VerseSequenceLog(File(dir, "seq-${models.size}.json")),
    )

    private fun ComposeUiTest.tab(state: () -> Inputs) {
        setContent {
            val i = state()
            MaterialTheme {
                BibleTab(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = i.vm,
                    appSettings = i.settings,
                    onSettingsChange = { i.settings },
                    onAddToSchedule = { _, _, _, _, _, _ -> i.vm },
                    onVerseSelected = { i.vm },
                    onPresenting = { i.vm },
                    bibleOutput = i.output,
                    verseStatistics = BibleVerseStatistics { _, _, _, _ -> i.vm },
                    verseSequenceLog = i.log,
                    crossReferences = i.refs,
                    sttManager = i.stt,
                    engineStatus = i.engine,
                )
            }
        }
        waitForIdle()
    }

    private fun live(vararg verse: Int) = verse.map {
        SelectedVerse(bookName = "Genesis", chapter = 1, verseNumber = it, verseText = "v$it", bookId = 1)
    }

    @Test
    fun `swapping the view model and every collaborator redraws from the new ones`() = runComposeUiTest {
        val first = inputs(settings(), up = true)
        val second = inputs(settings(devMode = false), up = false)
        first.vm.addToHistory("Genesis", 1, 1, "In the beginning")
        first.vm.onEngineScripture(EngineScripture(1, 1, 2, null, "", "explicit"))
        first.output.setDisplayedVerses(live(1, 2))
        second.vm.selectBook(1)
        var current by mutableStateOf(first)
        tab { current }

        assertTrue(showsContainingText("Genesis"), renderedText().toString())

        current = second
        waitForIdle()
        assertTrue(showsContainingText("Blessed is the man"), renderedText().toString())

        current = first
        waitForIdle()
        assertTrue(showsContainingText("In the beginning"), renderedText().toString())
    }

    @Test
    fun `a search on one model and none on the next shows each one's state`() = runComposeUiTest {
        val searching = inputs(settings(), up = true)
        val miss = inputs(settings(), up = true)
        searching.vm.cycleSearchMode()
        searching.vm.onSmartQueryChanged("God")
        searching.vm.performSearch()
        miss.vm.cycleSearchMode()
        miss.vm.onSmartQueryChanged("zzzz")
        miss.vm.performSearch()
        var current by mutableStateOf(searching)
        tab { current }

        current = miss
        waitForIdle()
        current = searching
        waitForIdle()

        assertTrue(renderedText().isNotEmpty())
    }

    @Test
    fun `a model with no primary Bible and one with a Bible take turns`() = runComposeUiTest {
        val none = inputs(settings(primary = ""), up = false)
        val loaded = inputs(settings(), up = true)
        var current by mutableStateOf(none)
        tab { current }

        current = loaded
        waitForIdle()
        assertTrue(showsContainingText("In the beginning"), renderedText().toString())

        current = none
        waitForIdle()
        assertTrue(renderedText().isNotEmpty())
    }
}
