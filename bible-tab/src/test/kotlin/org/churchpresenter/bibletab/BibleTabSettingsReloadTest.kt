@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleTabSettingsReloadTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    private class Host(settings: AppSettings) {
        var settings by mutableStateOf(settings)
        var item by mutableStateOf<ScheduleItem.BibleVerseItem?>(null)
        var itemVersion by mutableStateOf(0)
        var dismiss by mutableStateOf(0)
        val selected = mutableListOf<List<SelectedVerse>>()
    }

    private fun bible(file: String = "test.spb", name: String = "", split: Boolean = false, words: Int = 45) =
        BibleSettings(
            storageDirectory = dir.absolutePath,
            primaryBible = file,
            translations = listOf(BibleTranslationSettings(fileName = file, customName = name)),
            splitLongVerses = split,
            longVerseWordCount = words,
        )

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-settings-reload").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = bibleFixture)
        SpbFixture.spbFile(dir, name = SECOND_MODULE, content = bibleFixture)
        vm = BibleViewModel(
            AppSettings().withBibleEverywhere(bible()),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    private fun item(id: String, book: String, chapter: Int, verse: Int) =
        ScheduleItem.BibleVerseItem(id = id, bookName = book, chapter = chapter, verseNumber = verse, verseText = "")

    private fun ComposeUiTest.host(): Host {
        val host = Host(AppSettings().withBibleEverywhere(bible()))
        setContent {
            MaterialTheme {
                BibleTab(
                    viewModel = vm,
                    appSettings = host.settings,
                    crossReferences = noCrossReferences(),
                    selectedVerseItem = host.item,
                    selectedVerseItemVersion = host.itemVersion,
                    onVerseSelected = { host.selected += it },
                    dialogDismissSignal = host.dismiss,
                )
            }
        }
        waitForIdle()
        return host
    }

    @Test
    fun `renaming the translation reaches the view model`() = runComposeUiTest {
        val host = host()

        host.settings = AppSettings().withBibleEverywhere(bible(name = "Church Version"))
        waitUntil { vm.primaryBible.value?.getBibleTitle() == "Church Version" }
    }

    @Test
    fun `turning on long-verse splitting and moving its threshold reach the view model`() = runComposeUiTest {
        val host = host()

        host.settings = AppSettings().withBibleEverywhere(bible(split = true))
        waitUntil { vm.splitLongVersesEnabled }
        host.settings = AppSettings().withBibleEverywhere(bible(split = true, words = 30))
        waitUntil { vm.longVerseWordCount == 30 }
    }

    @Test
    fun `choosing another module reloads the tab from it`() = runComposeUiTest {
        val host = host()

        host.settings = AppSettings().withBibleEverywhere(bible(file = SECOND_MODULE))
        waitUntil { vm.loadedTranslations.value.map { it.fileName } == listOf(SECOND_MODULE) }
    }

    @Test
    fun `a schedule item that names no verse sends nothing, and the next one goes out`() = runComposeUiTest {
        val host = host()
        val before = host.selected.size

        host.item = item("a", "Nowhere", 9, 9)
        waitForIdle()
        assertEquals(before, host.selected.size)

        host.item = item("b", "John", 3, 16)
        host.itemVersion++
        waitUntil { host.selected.lastOrNull()?.firstOrNull()?.verseNumber == 16 }
    }

    @Test
    fun `a dismissed dialog hands the keys back without moving the selection`() = runComposeUiTest {
        val host = host()
        val index = vm.selectedVerseIndex.value

        host.dismiss++
        waitForIdle()

        assertEquals(index, vm.selectedVerseIndex.value)
        assertTrue(vm.verses.value.isNotEmpty())
    }
}
