package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The arrow keys once the search has narrowed the list to nothing.
 *
 * `enter with nothing left to pick does nothing` covers Enter in that state; the arrows have their
 * own guard, and walking a list with no rows in it is what that guard is for — without it the move
 * indexes an empty list.
 */
@OptIn(ExperimentalTestApi::class)
class FontPickerEmptyListKeysTest {

    private fun face(name: String, category: FontCategory = FontCategory.SANS) =
        FontFace(name, category, cyrillic = true, hebrew = false, recommended = false)

    private fun catalog() = FontCatalogSnapshot(
        faces = listOf(face("Arial"), face("Georgia", FontCategory.SERIF)),
        hiddenCount = 0,
        measured = true,
    )

    @Test
    fun `the arrows do nothing once the search has emptied the list`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme {
                FontPickerPanel(
                    value = "Georgia",
                    catalog = catalog(),
                    previewLines = listOf("In the beginning"),
                    onDismiss = {},
                    onPick = { picked = it },
                )
            }
        }

        onNode(isEditable()).performTextInput("kiwi")
        waitForIdle()

        // Down, up, down again: with nothing to walk, each one has to be a no-op rather than an
        // index into an empty list.
        onNode(isEditable()).performKeyInput { pressKey(Key.DirectionDown) }
        onNode(isEditable()).performKeyInput { pressKey(Key.DirectionUp) }
        onNode(isEditable()).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()

        onNode(isEditable()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertNull(picked, "the arrows must not leave a highlight behind for enter to commit")
    }
}
