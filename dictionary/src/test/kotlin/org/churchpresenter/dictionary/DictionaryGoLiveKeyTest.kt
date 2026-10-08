@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary

import androidx.compose.ui.test.performClick
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings

/**
 * The Go Live key on the dictionary tab: Enter on the tab root sends the entry on show, as the Go
 * Live button does, and Enter typed into the search box stays the search box's. Enter on the entry
 * already on air does nothing.
 */
class DictionaryGoLiveKeyTest {

    private fun ComposeUiTest.pressEnter() {
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    /** Opens [entry] without a click, so the keyboard stays where the tab put it on opening. */
    private fun ComposeUiTest.open(vm: DictionaryViewModel, entry: StrongsEntry) {
        vm.selectByNumber(entry.number)
        waitUntil("${entry.number} to open") { vm.selectedEntry == entry }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root sends the entry on show live`() = dictionaryTab { vm, reports ->
        open(vm, DictionaryFixture.agape)

        pressEnter()

        assertEquals(listOf(DictionaryFixture.agape), reports.live)
    }

    @Test
    fun `enter does nothing for the entry already on air`() =
        dictionaryTab(liveEntryNumber = DictionaryFixture.agape.number) { vm, reports ->
            open(vm, DictionaryFixture.agape)

            pressEnter()

            assertTrue(reports.live.isEmpty(), "already on air: ${reports.live}")
        }

    @Test
    fun `enter sends the entry on show while another is on air`() =
        dictionaryTab(liveEntryNumber = DictionaryFixture.charis.number) { vm, reports ->
            open(vm, DictionaryFixture.agape)

            pressEnter()

            assertEquals(listOf(DictionaryFixture.agape), reports.live)
        }

    @Test
    fun `with nothing selected enter sends nothing`() = dictionaryTab { _, reports ->
        pressEnter()

        assertTrue(reports.live.isEmpty(), "nothing to go live with: ${reports.live}")
    }

    @Test
    fun `enter typed in the search box does not go live`() = dictionaryTab { vm, reports ->
        open(vm, DictionaryFixture.agape)
        dictSearchField().requestFocus()

        dictSearchField().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(reports.live.isEmpty(), "the field kept the key: ${reports.live}")
    }

    @Test
    fun `a tab that opens on its search box leaves enter to the field`() = dictionaryTab(
        appSettings = AppSettings(keyboardShortcutSettings = KeyboardShortcutSettings(focusSearchOnTabOpen = true)),
    ) { vm, reports ->
        open(vm, DictionaryFixture.agape)

        pressEnter()

        assertTrue(reports.live.isEmpty(), "the caret was in the search box: ${reports.live}")
    }

    @Test
    fun `clicking an entry and pressing enter sends that entry live`() = dictionaryTab { vm, reports ->
        onAllNodes(rowOf(DictionaryFixture.agape))[0].performClick()
        waitUntil("the clicked entry to open") { vm.selectedEntry == DictionaryFixture.agape }
        waitForIdle()

        pressEnter()

        assertEquals(listOf(DictionaryFixture.agape), reports.live, "a clicked row must not keep the keyboard")
    }
}
