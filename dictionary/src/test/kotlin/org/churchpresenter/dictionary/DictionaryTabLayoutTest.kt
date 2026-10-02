@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.WindowLayoutSettings
import org.churchpresenter.sharedui.testing.showsContainingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tab's own frame: the divider that sizes the entry list, the list's loading notice, and
 * bringing a chosen entry into view.
 */
class DictionaryTabLayoutTest {

    private companion object {
        const val DRAG_PX = 60f
        const val LOADING = "Loading"
    }

    private fun ComposeUiTest.dragDivider(byPx: Float) {
        onNodeWithTag(DICTIONARY_LIST_DIVIDER_TAG).performTouchInput {
            down(center)
            moveBy(Offset(byPx, 0f))
            up()
        }
        waitForIdle()
    }

    @Test
    fun `dragging the divider widens the list and remembers the width`() =
        dictionaryTab(
            appSettings = AppSettings(windowedLayout = WindowLayoutSettings(dictionaryListWidthDp = 250)),
            width = 900.dp,
        ) { _, reports ->
            dragDivider(DRAG_PX)

            val saved = reports.settings.single().windowedLayout.dictionaryListWidthDp
            assertTrue(saved > 250, "the list grew from the width it was given, to $saved")
        }

    @Test
    fun `the divider stops the list from shrinking past its minimum`() =
        dictionaryTab(
            appSettings = AppSettings(windowedLayout = WindowLayoutSettings(dictionaryListWidthDp = 200)),
            width = 900.dp,
        ) { _, reports ->
            dragDivider(-400f)

            assertEquals(180, reports.settings.single().windowedLayout.dictionaryListWidthDp)
        }

    @Test
    fun `the list says it is loading while the entries are read`() {
        val files = DictionaryFixture.files()
        dictionaryTab(files = files) { vm, _ ->
            assertFalse(showsContainingText(LOADING))

            files.gate = CompletableDeferred()
            vm.toggleDictLanguage()
            waitForIdle()
            assertTrue(showsContainingText(LOADING))

            files.gate?.complete(Unit)
            waitUntil("the Russian entries to load") { vm.entries.isNotEmpty() }
            assertFalse(showsContainingText(LOADING))
        }
    }

    @Test
    fun `an entry chosen off screen is scrolled into view`() {
        val many = (1..120).map { n ->
            StrongsEntry("G${9000 + n}", "w$n", "t$n", "p$n", "definition $n")
        }
        dictionaryTab(extraEntries = many) { vm, _ ->
            val last = many.last()
            assertFalse(listShows(last), "the last of 120 is off screen to begin with")

            vm.onEntrySelected(last)
            waitForIdle()

            onAllNodes(rowOf(last))[0].assertIsDisplayed()
        }
    }

    @Test
    fun `the tab draws with only a view model, as a preview composes it`() = runComposeUiTest {
        val vm = DictionaryViewModel(DictionaryFixture.files())
        try {
            setContent { DictionaryTab(viewModel = vm) }
            waitUntil("the dictionary to load") { vm.entries.isNotEmpty() }

            assertTrue(listShows(DictionaryFixture.agape))
            assertFalse(hasDictButton(DictionaryLabel.GO_LIVE), "nothing to go live to")
        } finally {
            vm.dispose()
        }
    }
}
