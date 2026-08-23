@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.ui

import androidx.compose.ui.test.performClick
import org.churchpresenter.dictionary.DictionaryFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsContainingText

class DictionaryTabHistoryTest {

    @Test
    fun `there is nothing to go back to on a freshly opened tab`() = dictionaryTab { vm, _ ->
        assertFalse(vm.canGoBack)
        assertFalse(vm.canGoForward)
    }

    @Test
    fun `a single selected entry is still the start of history`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)

        assertFalse(vm.canGoBack, "the first word looked up is where history begins")
        assertFalse(vm.canGoForward)
    }

    @Test
    fun `looking up a second entry offers a way back to the first`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.charis)

        assertTrue(vm.canGoBack)
        assertTrue(hasDictButton(DictionaryLabel.BACK), "the button appears once there is somewhere to go")
    }

    @Test
    fun `going back returns the previous entry to the detail pane`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.charis)

        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()

        assertEquals(DictionaryFixture.agape.number, vm.selectedEntry?.number)
        assertTrue(showsContainingText(DictionaryFixture.agape.pronunciation))
    }

    @Test
    fun `going back offers a way forward again`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.charis)
        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()

        assertTrue(vm.canGoForward)
        assertTrue(hasDictButton(DictionaryLabel.FORWARD))
    }

    @Test
    fun `going forward returns to the entry that was left`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.charis)
        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()

        dictButton(DictionaryLabel.FORWARD).performClick()
        waitForIdle()

        assertEquals(DictionaryFixture.charis.number, vm.selectedEntry?.number)
        assertFalse(vm.canGoForward, "there is nothing past the newest entry")
    }

    @Test
    fun `walking all the way back lands on the first entry looked up`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.charis)

        while (vm.canGoBack) {
            dictButton(DictionaryLabel.BACK).performClick()
            waitForIdle()
        }

        assertEquals(DictionaryFixture.agape.number, vm.selectedEntry?.number)
    }

    @Test
    fun `three entries walk back one at a time`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.elohim)
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.charis)

        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()
        assertEquals(DictionaryFixture.agape.number, vm.selectedEntry?.number)

        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()
        assertEquals(DictionaryFixture.elohim.number, vm.selectedEntry?.number)
    }

    @Test
    fun `looking up a new entry after going back drops what was ahead`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.elohim)
        selectEntry(DictionaryFixture.agape)
        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()

        selectEntry(DictionaryFixture.charis)

        assertFalse(vm.canGoForward, "the branch that was abandoned must not still be offered")
        assertEquals(DictionaryFixture.charis.number, vm.selectedEntry?.number)
    }

    @Test
    fun `looking up the same entry again is recorded as its own step`() = dictionaryTab { vm, _ ->
        selectEntry(DictionaryFixture.agape)
        selectEntry(DictionaryFixture.agape)

        assertTrue(vm.canGoBack, "every lookup is a step, so back retraces the operator's path exactly")
        assertEquals(DictionaryFixture.agape.number, vm.selectedEntry?.number)
    }
}
