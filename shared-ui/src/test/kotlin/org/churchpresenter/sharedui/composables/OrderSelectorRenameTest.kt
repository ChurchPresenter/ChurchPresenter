package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [OrderSelector] given an [OrderRename]: its rows become name fields that write back once, when
 * the field is left or the panel goes away, and an Add button can add a row to name.
 */
@OptIn(ExperimentalTestApi::class)
class OrderSelectorRenameTest {

    private class Renames {
        val calls = mutableListOf<Pair<String, String>>()
        var adds = 0
    }

    private val entries = listOf(
        OrderEntry("0", "English", placeholder = "Language 1"),
        OrderEntry("1", "", placeholder = "Language 2"),
    )

    /** The selector, shown while [shown] says so -- turning it off disposes the open panel. */
    private fun ComposeUiTest.selector(
        renames: Renames,
        rows: List<OrderEntry> = entries,
        canAdd: Boolean = true,
        shown: () -> Boolean = { true },
    ) {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                if (shown()) {
                    OrderSelector(
                        label = "Languages",
                        entries = rows,
                        subtitle = "Which language leads",
                        moveUpLabel = "Move up",
                        moveDownLabel = "Move down",
                        onMove = { _, _ -> },
                        rename = OrderRename(
                            label = "Language name",
                            hint = "Names this language everywhere",
                            onRename = { key, name -> renames.calls += key to name },
                            addLabel = "Add a language",
                            onAdd = { renames.adds += 1; Unit }.takeIf { canAdd },
                        ),
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.open(header: String = "English") {
        onAllNodes(hasText(header))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.field(index: Int) = onAllNodes(hasSetTextAction() and hasContentDescription("Language name"))[index]

    @Test
    fun `each row is a name field, an unnamed one showing what it is called until named`() = runComposeUiTest {
        selector(Renames())
        open()

        assertEquals(2, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertTrue(has("Language 2"), "the blank row shows its placeholder")
        assertTrue(has("Names this language everywhere"), "the hint says what a rename changes")
    }

    @Test
    fun `a name is written once, when the field is left`() = runComposeUiTest {
        val renames = Renames()
        selector(renames)
        open()

        field(1).performClick()
        field(1).performTextReplacement(" Yoruba ")
        waitForIdle()
        assertEquals(emptyList(), renames.calls, "nothing is written while typing")

        field(0).performClick()
        waitForIdle()
        assertEquals(listOf("1" to "Yoruba"), renames.calls)
    }

    @Test
    fun `a name typed and left as it was writes nothing`() = runComposeUiTest {
        val renames = Renames()
        selector(renames)
        open()

        field(0).performClick()
        field(0).performTextReplacement("English ")
        field(1).performClick()
        waitForIdle()

        assertEquals(emptyList(), renames.calls)
    }

    @Test
    fun `clearing a name writes it blank, so the placeholder comes back`() = runComposeUiTest {
        val renames = Renames()
        selector(renames)
        open()

        field(0).performClick()
        field(0).performTextReplacement("")
        field(1).performClick()
        waitForIdle()

        assertEquals(listOf("0" to ""), renames.calls)
        assertTrue(has("Language 1"))
    }

    @Test
    fun `closing with a name still being typed writes it`() = runComposeUiTest {
        val renames = Renames()
        var shown by mutableStateOf(true)
        selector(renames, shown = { shown })
        open()

        field(1).performClick()
        field(1).performTextReplacement("Deutsch")
        waitForIdle()
        shown = false
        waitForIdle()

        assertEquals(listOf("1" to "Deutsch"), renames.calls)
    }

    @Test
    fun `the button reads the placeholder while the first entry is unnamed`() = runComposeUiTest {
        selector(Renames(), rows = listOf(OrderEntry("0", "", placeholder = "Language 1")))

        assertTrue(has("Language 1"))
    }

    @Test
    fun `Add asks for another entry`() = runComposeUiTest {
        val renames = Renames()
        selector(renames)
        open()

        onAllNodes(hasText("Add a language"))[0].performClick()
        waitForIdle()

        assertEquals(1, renames.adds)
    }

    @Test
    fun `with nothing to add there is no Add button`() = runComposeUiTest {
        selector(Renames(), canAdd = false)
        open()

        assertTrue(!has("Add a language"))
    }
}
