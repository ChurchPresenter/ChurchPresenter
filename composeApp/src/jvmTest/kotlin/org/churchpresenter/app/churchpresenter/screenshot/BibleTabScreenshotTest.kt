@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.diagnostics.CrashReportSweep
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.tabs.BibleLabel
import org.churchpresenter.app.churchpresenter.tabs.actionButton
import org.churchpresenter.app.churchpresenter.tabs.bibleSearch
import org.churchpresenter.app.churchpresenter.tabs.bibleTab
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.stt.STTManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

class BibleTabScreenshotTest {

    private val managers = mutableListOf<STTManager>()

    /** The load-error state reports itself; shooting it must not leave the report behind. */
    private val sweep = CrashReportSweep()

    @BeforeTest
    fun mark() = sweep.mark()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
        sweep.sweep()
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
        /** Modules to write alongside the primary — see `bibleTab`'s parameter of the same name. */
        extraModules: List<String> = emptyList(),
        drive: ComposeUiTest.(BibleViewModel) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        bibleTab(
            settings = settings, width = width, stt = stt, themeMode = mode,
            crossReferences = crossReferences, extraModules = extraModules,
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
    fun `a second translation adds the swap control`() = shoot(
        "translation_swap_available",
        settings = { stack(it, "test.spb", "second.spb") },
        extraModules = listOf("second.spb"),
    )

    @Test
    fun `the translation order panel`() = shoot(
        "translation_order_panel",
        settings = { stack(it, "test.spb", "second.spb", "third.spb") },
        extraModules = listOf("second.spb", "third.spb"),
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

    /**
     * Waits for the column to have resolved its rows.
     *
     * The references are read through a repository that loads off the main thread, so `waitForIdle`
     * — which waits for composition, not for that — can return with the column still showing "No
     * cross references". Shooting then photographs the empty state, and whether it does is a matter
     * of which side of a race the capture lands on.
     */
    private fun ComposeUiTest.awaitCrossReferences() =
        waitUntilAtLeastOneExists(hasText("Rom 5:8", substring = true))

    @Test
    fun `cross references`() = shoot(
        "cross_references",
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true)) },
        crossReferences = crossReferenceFixture(),
    ) { vm ->
        vm.selectVerse(0)
        awaitCrossReferences()
    }

    @Test
    fun `cross references pooled over a passage`() = shoot(
        "cross_references_passage",
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true)) },
        crossReferences = CrossReferenceRepository {
            """{"v":1,"r":{
                 "001001001":"019023001 043003016",
                 "001001002":"019023001 045005008",
                 "001001003":"019023001"
               }}""".toByteArray()
        },
    ) { _ ->
        // Read three verses in sequence, which is what turns the column into a passage view.
        for (line in listOf(
            "1. In the beginning God created the heaven and the earth.",
            "2. And the earth was without form, and void.",
            "3. And God said, Let there be light.",
        )) {
            onNodeWithText(line).performClick()
            waitForIdle()
            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()
        }
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
        awaitCrossReferences()
    }

    @Test
    fun `cross reference chips`() = shoot(
        "cross_reference_chips",
        crossReferences = crossReferenceFixture(),
    ) { _ ->
        waitUntilAtLeastOneExists(hasContentDescription("Cross references: 4"))
    }

    @Test
    fun `the cross reference popover`() = shoot(
        "cross_reference_popover",
        crossReferences = crossReferenceFixture(),
        rootIndex = 1,
    ) { _ ->
        waitUntilAtLeastOneExists(hasContentDescription("Cross references: 4"))
        onNodeWithContentDescription("Cross references: 4").performClick()
        waitForIdle()
        waitUntilAtLeastOneExists(hasText("Rom 5:8", substring = true))
    }

    @Test
    fun `a cross reference this module does not carry`() = shoot(
        "cross_references_unavailable",
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true)) },
        crossReferences = CrossReferenceRepository {
            """{"v":1,"r":{"001001001":"035003002 043003016"}}""".toByteArray()
        },
    ) { vm ->
        vm.selectVerse(0)
        waitUntilAtLeastOneExists(hasText("Hab 3:2"))
    }

    @Test
    fun `the cross reference panel with nothing to show`() = shoot(
        "cross_references_empty",
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true)) },
        crossReferences = crossReferenceFixture(),
    ) { vm ->
        vm.selectVerse(2)
        waitUntilAtLeastOneExists(hasText(BibleLabel.CROSS_REFS_EMPTY))
    }

    @Test
    fun `auto-follow panel`() = shoot("auto_follow_panel", stt = connectedStt()) { _ ->
        // The mic button is the point of this shot and appears a recomposition after the manager
        // reports connected, so waiting for it is the difference between photographing the panel
        // and photographing the moment before it.
        waitUntilAtLeastOneExists(hasContentDescription(BibleLabel.STT_DISCONNECT))
    }

    @Test
    fun `no bible configured`() = shoot(
        "no_bible_configured",
        settings = { it.copy(bibleSettings = BibleSettings(storageDirectory = it.bibleSettings.storageDirectory)) },
    )

    /**
     * The state directly above: an empty tab, but with the reason for it named.
     *
     * Worth its own image precisely because `no_bible_configured` looks so similar — the two are
     * the same layout and a reviewer should be able to see that the difference is stated on screen.
     * `deleted.spb` is configured and never written, which is the one failure a screenshot can set
     * up without a byte-level fixture.
     */
    @Test
    fun `a translation that could not be read`() = shoot(
        "load_error",
        settings = { stack(it, "test.spb", "deleted.spb") },
    )

    private companion object {
        const val SECTION = "bibleTab"
    }
}
