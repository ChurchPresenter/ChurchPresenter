@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictionaryEmptyStateTest {

    private fun chip(number: String) = hasText(number) and hasAnyAncestor(hasTestTag(EXAMPLE_ENTRIES_TAG))

    @Test
    fun `with nothing chosen the details side says how to find a word`() = dictionaryTab { _, _ ->
        assertTrue(showsExactly("No word selected"), renderedText().toString())
        assertTrue(showsExactly("Pick an entry from the list, or search by Strong’s number, word or definition."))
    }

    @Test
    fun `an example entry opens when clicked`() = dictionaryTab { vm, _ ->
        onNode(chip(DictionaryFixture.agape.number)).performClick()
        waitUntil(timeoutMillis = 5_000) { vm.selectedEntry == DictionaryFixture.agape }
        waitForIdle()

        assertEquals(DictionaryFixture.agape, vm.selectedEntry)
        assertTrue(onAllNodes(hasTestTag(EXAMPLE_ENTRIES_TAG)).fetchSemanticsNodes(false).isEmpty(), "the examples go")
    }

    @Test
    fun `only the examples this dictionary has are offered`() = dictionaryTab { _, _ ->
        // The fixture carries agape and charis, but not H157.
        val offered = listOf("H157", "G26", "G5485").filter {
            onAllNodes(chip(it)).fetchSemanticsNodes(false).isNotEmpty()
        }
        assertEquals(listOf("G26", "G5485"), offered)
    }
}
