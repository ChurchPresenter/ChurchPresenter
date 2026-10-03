@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.sharedui.testing.showsExactly
import org.churchpresenter.stt.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BibleTabDetectionRowTest {

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

    private fun engine(helpDev: Boolean = false) = BibleEngineSettings(enabled = true, helpDevMode = helpDev)

    private fun BibleViewModel.detectJohn316() = onEngineScripture(
        EngineScripture(43, 3, 16, null, "For God so loved the world.", "explicit"),
    )

    private fun verse(n: Int, text: String) =
        SelectedVerse(bookName = "John", chapter = 3, verseNumber = n, verseText = text, bookId = 43)

    @Test
    fun `clicking a detection opens its verse without putting it on screen`() =
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, reports ->
            vm.detectJohn316()
            waitForIdle()

            onAllNodesWithTag(DETECTION_ROW_TAG)[0].performClick()
            waitForIdle()

            assertEquals(2, vm.selectedBookIndex.value, "John is the third book")
            assertEquals(3, vm.selectedChapter.value)
            assertTrue(vm.history.isEmpty(), "a single click only browses")
            assertTrue(reports.presenting.isEmpty())
        }

    @Test
    fun `double-clicking a detection puts its verse on screen`() =
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, reports ->
            vm.detectJohn316()
            waitForIdle()

            onAllNodesWithTag(DETECTION_ROW_TAG)[0].performMouseInput { doubleClick() }
            waitForIdle()

            val live = assertNotNull(reports.live)
            assertEquals(16, live.first().verseNumber)
            assertEquals("John", live.first().bookName)
            assertEquals(listOf("John 3:16"), vm.history.map { it.displayText })
        }

    @Test
    fun `flagging a live passage of several verses is accepted`() {
        val presenter = FakeBibleOutput()
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(helpDev = true)) },
            stt = connectedStt(),
            presenter = presenter,
        ) { _, _ ->
            runOnIdle { presenter.setDisplayedVerses(listOf(verse(16, "a"), verse(17, "b"))) }
            waitForIdle()

            onNode(hasText("Wrong passage") and hasClickAction()).performClick()
            waitForIdle()
            onNode(hasText("Premature") and hasClickAction()).performClick()
            waitForIdle()

            assertTrue(showsExactly("Wrong passage"))
            assertTrue(showsExactly("Premature"))
        }
    }

    @Test
    fun `flagging a single live verse is accepted`() {
        val presenter = FakeBibleOutput()
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(helpDev = true)) },
            stt = connectedStt(),
            presenter = presenter,
        ) { _, _ ->
            runOnIdle { presenter.setDisplayedVerses(listOf(verse(16, "a"))) }
            waitForIdle()

            onNode(hasText("Premature") and hasClickAction()).performClick()
            waitForIdle()

            assertTrue(showsExactly("Premature"))
        }
    }
}
