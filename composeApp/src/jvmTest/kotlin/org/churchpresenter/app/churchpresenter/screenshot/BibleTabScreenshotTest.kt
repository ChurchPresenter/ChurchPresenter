@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.tabs.BibleLabel
import org.churchpresenter.app.churchpresenter.tabs.actionButton
import org.churchpresenter.app.churchpresenter.tabs.bibleSearch
import org.churchpresenter.app.churchpresenter.tabs.bibleTab
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test

class BibleTabScreenshotTest {

    private val managers = mutableListOf<STTManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
    }

    private fun connectedStt() = STTManager().also {
        managers.add(it)
        it.applyConnected()
    }

    private fun stack(app: AppSettings, vararg fileNames: String) = app.copy(
        bibleSettings = app.bibleSettings.copy(
            translations = fileNames.map { BibleTranslationSettings(fileName = it) },
        ),
    )

    private fun ComposeUiTest.runSearch(query: String) {
        bibleSearch(query)
        actionButton(BibleLabel.SEARCH).performClick()
        waitForIdle()
    }

    private fun shoot(
        name: String,
        settings: (AppSettings) -> AppSettings = { it },
        width: Dp? = null,
        stt: STTManager? = null,
        rootIndex: Int = 0,
        crossReferences: CrossReferenceRepository? = null,
        drive: ComposeUiTest.(BibleViewModel) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        bibleTab(
            settings = settings, width = width, stt = stt, themeMode = mode,
            crossReferences = crossReferences,
        ) { vm, _ ->
            drive(vm)
            captureTo(file, rootIndex)
        }
    }

    /**
     * Cross-references for Genesis 1:1, as a fixture.
     *
     * The shipped dataset would work, but then the image would change whenever that file did, and
     * a reviewer could not tell a layout change from a data change.
     */
    private fun crossReferenceFixture() = CrossReferenceRepository {
        """{"v":1,"r":{"001001001":"043003016 019023001 045005008 019023001-003"}}""".toByteArray()
    }

    @Test
    fun browsing() = shoot("browsing")

    @Test
    fun `another book and chapter`() = shoot("book_john_3") { vm ->
        vm.selectBook(2)
        vm.selectChapter(3)
        waitForIdle()
    }

    @Test
    fun `a verse selected`() = shoot("verse_selected") { vm ->
        vm.selectVerse(2)
        waitForIdle()
    }

    @Test
    fun `several verses selected`() = shoot("multi_verse_selected") { vm ->
        vm.ctrlClickVerse(1)
        vm.ctrlClickVerse(2)
        waitForIdle()
    }

    @Test
    fun `a range of verses shift-selected`() = shoot("multi_verse_range") { vm ->
        vm.selectVerse(0)
        vm.shiftClickVerse(1)
        waitForIdle()
    }

    @Test
    fun `search mode reference`() = shoot("search_mode_reference") { vm ->
        vm.cycleSearchMode()
        waitForIdle()
        bibleSearch("John 3:16")
    }

    @Test
    fun `search mode text`() = shoot("search_mode_text") { vm ->
        vm.cycleSearchMode()
        vm.cycleSearchMode()
        waitForIdle()
        runSearch("shepherd")
    }

    @Test
    fun `search results`() = shoot("search_results") { runSearch("shepherd") }

    @Test
    fun `search with no matches`() = shoot("search_no_matches") { runSearch("zzzznotinthisbible") }

    @Test
    fun `a reference in auto mode`() = shoot("search_mode_auto") { bibleSearch("John 3:16") }

    @Test
    fun `narrow window`() = shoot("narrow_window", width = 420.dp)

    @Test
    fun `a second translation adds the swap control`() =
        shoot("translation_swap_available", settings = { stack(it, "test.spb", "second.spb") })

    @Test
    fun `the translation order panel`() = shoot(
        "translation_order_panel",
        settings = { stack(it, "test.spb", "second.spb", "third.spb") },
        rootIndex = 1,
    ) {
        onNodeWithText("TRANSLATION ORDER").performClick()
        waitForIdle()
    }

    @Test
    fun `split browse mode`() = shoot(
        "split_browse",
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(splitBrowseMode = true)) },
    ) { vm ->
        vm.selectVerse(1)
        waitForIdle()
    }

    @Test
    fun `cross references`() = shoot(
        "cross_references",
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true)) },
        crossReferences = crossReferenceFixture(),
    ) { vm ->
        vm.selectVerse(0)
        waitForIdle()
    }

    @Test
    fun `cross references beside the split panel`() = shoot(
        "cross_references_split",
        settings = {
            it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true, splitBrowseMode = true))
        },
        crossReferences = crossReferenceFixture(),
    ) { vm ->
        vm.selectVerse(0)
        waitForIdle()
    }

    @Test
    fun `auto-follow panel`() = shoot("auto_follow_panel", stt = connectedStt())

    @Test
    fun `no bible configured`() = shoot(
        "no_bible_configured",
        settings = { it.copy(bibleSettings = BibleSettings(storageDirectory = it.bibleSettings.storageDirectory)) },
    )

    private companion object {
        const val SECTION = "bibleTab"
    }
}
