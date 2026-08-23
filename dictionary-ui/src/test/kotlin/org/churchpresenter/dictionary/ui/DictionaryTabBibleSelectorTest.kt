@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.bible.SpbFixture
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The dictionary's Bible-translation selector — which translation the "In Scripture" verses are read
 * from, as opposed to the app's primary Bible.
 *
 * It is drawn only once interlinear data has loaded **and** at least one `.spb` has been found, so
 * every other suite here runs with it absent.
 *
 * **Both halves are reached through the real API rather than by writing the view model's state.**
 * `isInterlinearDataLoaded` is set by the pre-load the tab kicks off itself (the harness already
 * stubs `InterlinearRepository`, so it resolves immediately), and `availableDictBibles` by
 * `loadAvailableBibles` over a directory of real `.spb` files built with [SpbFixture] — which also
 * exercises the title being read out of the file, since that is the name the operator picks by.
 * Every one of these properties has a `private set`; widening four of them to make a test simpler
 * would have meant testing a state the app cannot actually reach.
 *
 * **The label is uppercased by `DropdownSelector`** (`label.uppercase()`), so it is matched as
 * `SELECT BIBLE TRANSLATION`. Matching the resource's own casing would find nothing, and every
 * absence assertion below would pass while asserting about a control that was never there.
 *
 * Not covered here, and stated so the next reader does not assume otherwise:
 *
 *  * **The spinner** shown while a chosen translation loads. `isDictBibleLoading` is only true
 *    between the click and the file being read, which for a fixture-sized `.spb` is too short to
 *    observe without racing it — and it cannot be set directly.
 *  * **The `isInterlinearDataLoaded` gate on its own.** The tests below pin the inner
 *    `availableDictBibles.isNotEmpty()` gate, but not the outer one: the pre-load resolves against
 *    the stubbed repository as soon as the harness has waited for the entries, so the state that
 *    would isolate it — translations found, interlinear data still loading — cannot be held open
 *    without racing that coroutine. Mutating the outer gate away therefore fails nothing here.
 */
class DictionaryTabBibleSelectorTest {

    private companion object {
        const val LABEL = "SELECT BIBLE TRANSLATION"
        const val PRIMARY = "Primary Bible"
    }

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    /** A folder holding one real `.spb` per (file name, translation title) pair given. */
    private fun bibleFolder(vararg bibles: Pair<String, String>): String {
        val dir = Files.createTempDirectory("cp-dict-bibles").toFile().also { tempDirs += it }
        bibles.forEach { (fileName, title) ->
            File(dir, fileName).writeText(SpbFixture.sampleContent(title))
        }
        return dir.absolutePath
    }

    /** Waits for the interlinear pre-load the tab starts on its own. */
    private fun ComposeUiTest.awaitInterlinear(vm: DictionaryViewModel) {
        waitUntil("interlinear data to load") { vm.isInterlinearDataLoaded }
    }

    /** Puts the tab in the state that draws the selector, with two translations to choose from. */
    private fun ComposeUiTest.withTranslations(vm: DictionaryViewModel) {
        awaitInterlinear(vm)
        vm.loadAvailableBibles(bibleFolder("kjv.spb" to "King James", "rst.spb" to "Synodal"))
        waitUntil("the translations to be listed") { vm.availableDictBibles.size == 2 }
        waitForIdle()
    }

    /** Opens the selector's menu. The label sits inside the clickable box, not beside it. */
    private fun ComposeUiTest.openSelector() {
        onNodeWithText(LABEL).performClick()
        waitForIdle()
    }

    @Test
    fun `the selector appears once interlinear data and translations are both present`() =
        dictionaryTab { vm, _ ->
            onAllNodesWithText(LABEL).assertCountEquals(0)

            withTranslations(vm)

            onNodeWithText(LABEL).assertIsDisplayed()
            onNodeWithText(PRIMARY).assertIsDisplayed()
        }

    @Test
    fun `loaded data with no translations installed draws nothing`() = dictionaryTab { vm, _ ->
        // An empty folder is the ordinary case for anyone who has not put a Bible beside the app.
        awaitInterlinear(vm)
        vm.loadAvailableBibles(bibleFolder())
        waitForIdle()

        onAllNodesWithText(LABEL).assertCountEquals(0)

        // Positive twin: the same matcher finds it the moment a translation exists, so the absence
        // above is about the gate and not about the selector being unmatchable.
        vm.loadAvailableBibles(bibleFolder("kjv.spb" to "King James"))
        waitUntil("the translation to be listed") { vm.availableDictBibles.size == 1 }
        waitForIdle()
        onNodeWithText(LABEL).assertIsDisplayed()
    }

    @Test
    fun `the translations are named by the title inside the file`() = dictionaryTab { vm, _ ->
        // The file name is not the name — an operator recognises "Synodal", not "rst.spb".
        withTranslations(vm)

        openSelector()

        onNodeWithText("King James").assertIsDisplayed()
        onNodeWithText("Synodal").assertIsDisplayed()
    }

    @Test
    fun `the primary bible is what it starts on`() = dictionaryTab { vm, _ ->
        withTranslations(vm)

        onNodeWithText(PRIMARY).assertIsDisplayed()
        assertEquals("", vm.dictBibleFile, "an unset translation means the app's primary Bible")
    }

    @Test
    fun `choosing a translation reaches the view model as its path`() = dictionaryTab { vm, _ ->
        // The title is what the operator picks, but the *path* is what has to be stored — two
        // translations can share a title, and only the path can be loaded from.
        withTranslations(vm)

        openSelector()
        onNodeWithText("Synodal").performClick()
        waitForIdle()

        assertEquals("rst.spb", File(vm.dictBibleFile).name)
    }

    @Test
    fun `the primary bible stays on the menu as the way back`() = dictionaryTab { vm, _ ->
        // Without it, an operator who picked a translation could never return to the app's own —
        // no other control clears this.
        withTranslations(vm)
        openSelector()
        onNodeWithText("King James").performClick()
        waitForIdle()
        assertEquals("kjv.spb", File(vm.dictBibleFile).name)

        openSelector()
        onNodeWithText(PRIMARY).performClick()
        waitForIdle()

        assertEquals("", vm.dictBibleFile, "choosing Primary Bible has to clear the override")
    }

    @Test
    fun `the chosen translation is what the selector then shows`() = dictionaryTab { vm, _ ->
        // The control reads back from the view model rather than from its own memory: one wired to
        // write correctly but read from elsewhere would keep showing "Primary Bible" after a choice.
        withTranslations(vm)

        openSelector()
        onNodeWithText("King James").performClick()
        waitForIdle()

        onNodeWithText("King James").assertIsDisplayed()
        onAllNodesWithText(PRIMARY).assertCountEquals(0)
    }
}
