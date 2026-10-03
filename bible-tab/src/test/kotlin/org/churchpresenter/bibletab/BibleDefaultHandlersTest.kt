@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleDefaultHandlersTest {

    private val verses = listOf("1. In the beginning", "2. And the earth", "3. Let there be light")

    private fun ComposeUiTest.column(selected: MutableList<Int>, refCount: Int = 0) {
        setContent {
            MaterialTheme {
                Box(Modifier.size(600.dp)) {
                    BibleVerseColumn(
                        verses = verses,
                        selectedIndex = 0,
                        onItemSelected = { selected += it },
                        refCountFor = { refCount },
                    )
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `a column given only a select handler ignores double clicks`() = runComposeUiTest {
        val selected = mutableListOf<Int>()
        column(selected)

        onNodeWithText("In the beginning", substring = true).performMouseInput { click(); click() }
        waitForIdle()

        assertEquals(listOf(0), selected, "the second click is a double, which nothing here handles")
    }

    @Test
    fun `a column given only a select handler ignores right clicks`() = runComposeUiTest {
        val selected = mutableListOf<Int>()
        column(selected)

        onNodeWithText("And the earth", substring = true).performMouseInput { rightClick() }
        waitForIdle()

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `a column given only a select handler ignores ctrl and shift clicks`() = runComposeUiTest {
        val selected = mutableListOf<Int>()
        column(selected)

        onNodeWithText("Let there be light", substring = true).performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { click() }
            key { keyUp(Key.CtrlLeft) }
            key { keyDown(Key.ShiftLeft) }
            mouse { click() }
            key { keyUp(Key.ShiftLeft) }
        }
        waitForIdle()

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `a refs chip with no handler can be clicked and shows its default tooltip`() = runComposeUiTest {
        val selected = mutableListOf<Int>()
        column(selected, refCount = 4)

        onAllNodesWithText("4")[0].performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
        onAllNodesWithText("4")[0].performClick()
        waitForIdle()

        assertTrue(selected.isEmpty(), "the chip is not the row")
    }

    @Test
    fun `a tab given only its required arguments goes live and docks its panel`() {
        val dir = Files.createTempDirectory("cp-bible-defaults").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        val settings = AppSettings(
            bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
        )
        val vm = BibleViewModel(settings, dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        BibleTab(viewModel = vm, appSettings = settings, crossReferences = noCrossReferences())
                    }
                }
                waitUntil(timeoutMillis = TIMEOUT_MS) {
                    onAllNodesWithText("In the beginning", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                actionButton(BibleLabel.CROSS_REFS_TOGGLE).performClick()
                onNode(hasText("In the beginning", substring = true)).performMouseInput { click(); click() }
                waitForIdle()

                assertEquals("Genesis 1:1", vm.history.first().displayText)
            }
        } finally {
            vm.dispose()
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val TOOLTIP_DELAY_MS = 1_000L
        const val TIMEOUT_MS = 5_000L
    }
}
