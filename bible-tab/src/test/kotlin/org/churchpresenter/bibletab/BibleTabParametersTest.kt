@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.testing.showsExactly
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleTabParametersTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel
    private lateinit var settings: AppSettings

    private class Engine(up: Boolean) : BibleEngineStatus {
        override val connected: State<Boolean> = mutableStateOf(up)
        override val startFailed: State<Boolean> = mutableStateOf(!up)
        override val engineSttConnected: State<Boolean?> = mutableStateOf(up)
    }

    @BeforeTest
    fun load() {
        dir = Files.createTempDirectory("cp-bible-tab-params").toFile()
        SpbFixture.spbFile(dir, content = bibleFixture)
        settings = AppSettings(
            bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
        )
        vm = BibleViewModel(settings, dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun clean() {
        vm.dispose()
        dir.deleteRecursively()
    }

    @Test
    fun `the tab draws with only what it requires`() = runComposeUiTest {
        setContent {
            MaterialTheme { BibleTab(viewModel = vm, appSettings = settings, crossReferences = noCrossReferences()) }
        }
        waitForIdle()

        assertTrue(showsExactly("1. In the beginning God created the heaven and the earth."))
    }

    @Test
    fun `every input can be given and swapped between compositions`() = runComposeUiTest {
        var round by mutableStateOf(0)
        val selected = mutableListOf<List<SelectedVerse>>()
        val item = ScheduleItem.BibleVerseItem(
            id = "row", bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world.",
        )
        setContent {
            val r = round
            MaterialTheme {
                BibleTab(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = vm,
                    appSettings = settings,
                    onSettingsChange = { r },
                    onAddToSchedule = { _, _, _, _, _, _ -> r },
                    selectedVerseItem = item,
                    selectedVerseItemVersion = r,
                    onVerseSelected = { selected += it },
                    onInstanceLinkSendVerse = { _, _, _, _, _ -> r },
                    onInstanceLinkSendBibleHold = { r },
                    onPresenting = { r },
                    bibleOutput = FakeBibleOutput(),
                    verseStatistics = BibleVerseStatistics { _, _, _, _ -> r },
                    onVerseWentLive = { r },
                    crossReferences = noCrossReferences(),
                    engineStatus = Engine(up = r % 2 == 0),
                    dialogDismissSignal = r,
                )
            }
        }
        waitForIdle()
        round = 1
        waitForIdle()

        assertEquals("John", selected.last().first().bookName, "the schedule item went out")
        assertEquals(16, selected.last().first().verseNumber)
    }
}
