@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookNamesDialogTest {

    private val titles = BookTitles(
        english = (1..BOOK_COUNT).map { "English $it" }.toMutableList().apply { this[2] = "Same" },
        localized = (1..BOOK_COUNT).map { "Local $it" }.toMutableList().apply { this[2] = "Same" },
    )
    private val allNames = ModuleBookNames((1..BOOK_COUNT).associateWith { "File $it" }, BookNamesRead.ALL)
    private val noNames = ModuleBookNames(emptyMap(), BookNamesRead.NONE)

    private class Outcome {
        var saved: Map<Int, String>? = null
        var dismissed = false
    }

    private fun ComposeUiTest.show(
        module: ModuleBookNames = allNames,
        overrides: Map<Int, String> = emptyMap(),
    ): Outcome {
        val outcome = Outcome()
        setContent {
            MaterialTheme {
                BookNamesContent(
                    title = "Russian · RSV",
                    module = module,
                    titles = titles,
                    overrides = overrides,
                    onSave = { outcome.saved = it },
                    onDismiss = { outcome.dismissed = true },
                )
            }
        }
        waitForIdle()
        return outcome
    }

    private fun ComposeUiTest.type(placeholder: String, text: String) {
        onNodeWithContentDescription(placeholder).performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.assertShown(text: String) = onNodeWithText(text).assertExists()
    private fun ComposeUiTest.assertGone(text: String) = onNodeWithText(text).assertDoesNotExist()

    @Test
    fun `all names found says they come from the file`() = runComposeUiTest {
        show()
        assertShown("Using names from the Bible file.")
    }

    @Test
    fun `no names found says they could not be extracted`() = runComposeUiTest {
        show(noNames)
        assertShown("Book names could not be extracted from this file.")
        assertShown("No names set")
    }

    @Test
    fun `some names found says how many`() = runComposeUiTest {
        show(ModuleBookNames((1..3).associateWith { "File $it" }, BookNamesRead.SOME))
        assertShown("Some book names could not be extracted from this file.")
        assertShown("Found names for 3 of 66 books. The rest show English names until you set your own.")
    }

    @Test
    fun `an unreadable file says so`() = runComposeUiTest {
        show(ModuleBookNames(emptyMap(), BookNamesRead.FAILED))
        assertShown("This Bible file could not be read.")
    }

    @Test
    fun `the first column is the localized name with English under it`() = runComposeUiTest {
        show()
        assertShown("BOOK")
        assertShown("Local 1")
        assertShown("English 1")
        assertGone("IN FILE")
    }

    @Test
    fun `English is not repeated when the localized name is the same`() = runComposeUiTest {
        show()
        assertEquals(1, onAllNodesWithText("Same").fetchSemanticsNodes().size)
    }

    @Test
    fun `a blank field falls back to the file's name, or English when the file has none`() = runComposeUiTest {
        show(ModuleBookNames(mapOf(1 to "File 1"), BookNamesRead.SOME))
        onNodeWithContentDescription("File 1").assertExists()
        onNodeWithContentDescription("English 2").assertExists()
    }

    @Test
    fun `typing a name counts it as an override`() = runComposeUiTest {
        show()
        assertShown("No overrides")

        type("File 1", "Bereshit")
        assertShown("1 override")
        assertShown("Overridden  1")

        type("File 2", "Shemot")
        assertShown("2 overrides")
    }

    @Test
    fun `emptying a field removes its override`() = runComposeUiTest {
        show(overrides = mapOf(1 to "Bereshit"))
        assertShown("1 override")

        type("File 1", "")
        assertShown("No overrides")
    }

    @Test
    fun `the row's reset gives the file's name back`() = runComposeUiTest {
        show(overrides = mapOf(1 to "Bereshit"))

        onNodeWithContentDescription("Use name from file").performClick()
        waitForIdle()

        assertShown("No overrides")
        onNodeWithContentDescription("Use name from file").assertDoesNotExist()
    }

    @Test
    fun `with no name in the file the reset says it goes back to the default`() = runComposeUiTest {
        show(noNames, overrides = mapOf(1 to "Bereshit"))

        onNodeWithContentDescription("Use default name").performClick()
        waitForIdle()

        assertShown("No names set")
    }

    @Test
    fun `clear overrides empties every row`() = runComposeUiTest {
        show(overrides = mapOf(1 to "Bereshit", 2 to "Shemot"))

        onNodeWithText("Clear overrides").performClick()
        waitForIdle()

        assertShown("No overrides")
        assertGone("Clear overrides")
    }

    @Test
    fun `the overridden filter lists only those books`() = runComposeUiTest {
        show(overrides = mapOf(2 to "Shemot"))

        onNodeWithText("Overridden  1").performClick()
        waitForIdle()

        assertShown("Local 2")
        assertGone("Local 1")
    }

    @Test
    fun `the testament filters split the list and head it`() = runComposeUiTest {
        show()

        onNodeWithText("New Testament  27").performClick()
        waitForIdle()
        assertShown("NEW TESTAMENT")
        assertShown("Local 40")
        assertGone("Local 1")

        onNodeWithText("Old Testament  39").performClick()
        waitForIdle()
        assertShown("OLD TESTAMENT")
        assertShown("Local 1")

        onNodeWithText("All  66").performClick()
        waitForIdle()
        assertShown("Local 1")
    }

    @Test
    fun `a search narrows the list and says when nothing matches`() = runComposeUiTest {
        show()

        type("Find a book", "Local 40")
        assertShown("English 40")
        assertGone("Local 1")

        type("Find a book", "nowhere")
        assertShown("No books match.")
    }

    @Test
    fun `save hands over the cleaned names`() = runComposeUiTest {
        val outcome = show()

        type("File 1", "  Bereshit ")
        type("File 2", "File 2")
        onNodeWithText("Save").performClick()

        assertEquals(mapOf(1 to "Bereshit"), outcome.saved)
    }

    @Test
    fun `cancel leaves without saving`() = runComposeUiTest {
        val outcome = show()

        type("File 1", "Bereshit")
        onNodeWithText("Cancel").performClick()

        assertTrue(outcome.dismissed)
        assertNull(outcome.saved)
    }

    @Test
    fun `the close key leaves without saving`() = runComposeUiTest {
        val outcome = show()

        onNodeWithContentDescription("Close").performClick()

        assertTrue(outcome.dismissed)
        assertNull(outcome.saved)
    }

    @Test
    fun `escape leaves without saving`() = runComposeUiTest {
        val outcome = show()

        onNodeWithContentDescription("Find a book").performClick()
        waitForIdle()
        onNodeWithContentDescription("Find a book").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertTrue(outcome.dismissed)
        assertNull(outcome.saved)
    }

    @Test
    fun `another key does not close it`() = runComposeUiTest {
        val outcome = show()

        onNodeWithContentDescription("Find a book").performClick()
        waitForIdle()
        onNodeWithContentDescription("Find a book").performKeyInput { pressKey(Key.A) }
        waitForIdle()

        assertTrue(!outcome.dismissed)
    }

    @Test
    fun `the key shows every status and opens only once it has one`() = runComposeUiTest {
        var clicks = 0
        var status by mutableStateOf<BookNamesStatus?>(null)
        setContent { MaterialTheme { BookNamesKey(status, onClick = { clicks++ }) } }
        waitForIdle()

        onNodeWithText("BOOK NAMES").performClick()
        waitForIdle()
        assertEquals(0, clicks, "nothing to open while the file is being read")

        listOf(
            BookNamesStatus.FromFile to "From file",
            BookNamesStatus.Edited(3) to "3 edited",
            BookNamesStatus.NotSet to "Not set",
            BookNamesStatus.PartlySet(3) to "3 of 66 set",
            BookNamesStatus.Custom to "Custom",
        ).forEach { (value, label) ->
            status = value
            waitForIdle()
            assertShown(label)
        }

        onNodeWithText("BOOK NAMES").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }
}
