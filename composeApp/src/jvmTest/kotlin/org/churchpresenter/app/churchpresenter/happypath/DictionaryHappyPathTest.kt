@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test

/** Strong's H2617, as the bundled dictionary spells its transliteration. */
private const val CHESED = "chêçêd"

class DictionaryHappyPathTest : HappyPathHarness() {

    private fun ComposeUiTest.findChesed() {
        onAllNodes(hasSetTextAction())[0].performTextReplacement("H2617")
        waitForIdle()
        await("H2617 is listed") {
            onAllNodes(hasText(CHESED, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        onAllNodes(hasText(CHESED, substring = true))[0].performClick()
        waitForIdle()
    }

    @Test
    fun `a search finds an entry and shows it`() = happyPath(settings(Tabs.DICTIONARY)) { _ ->
        findChesed()

        await("the entry's details are shown") {
            onAllNodes(hasText("Definition")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test
    fun `the selected entry goes live`() = happyPath(settings(Tabs.DICTIONARY)) { app ->
        findChesed()
        goLive()

        await("H2617 is on air") {
            app.presenter.isLive(Presenting.DICTIONARY) &&
                app.presenter.displayedDictionaryEntry.value?.number == "H2617"
        }
        awaitOnOutput(app, CHESED)
    }
}
