@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The panel the font field opens, driven with a catalog of its own rather than the machine's.
 *
 * Measured through the installed fonts, every assertion here would say something different on
 * another machine — CI has almost no families, and none of the ones a church would pick.
 */
class FontPickerPanelTest {

    private val arial = FontFace("Arial", FontCategory.SANS, cyrillic = true, hebrew = true, recommended = true)
    private val georgia = FontFace("Georgia", FontCategory.SERIF, cyrillic = true, hebrew = false, recommended = true)
    private val papyrus =
        FontFace("Papyrus", FontCategory.DISPLAY, cyrillic = false, hebrew = false, recommended = false)
    private val menlo = FontFace("Menlo", FontCategory.MONO, cyrillic = true, hebrew = false, recommended = false)

    private fun catalog(
        faces: List<FontFace> = listOf(arial, georgia, papyrus, menlo),
        hidden: Int = 0,
        measured: Boolean = true,
    ) = FontCatalogSnapshot(faces, hidden, measured)

    /** Renders the panel and returns readers for what it picked and whether it asked to close. */
    private fun ComposeUiTest.panel(
        value: String = "Georgia",
        catalog: FontCatalogSnapshot = catalog(),
        previewLines: List<String> = listOf("In the beginning"),
    ): Pair<() -> String?, () -> Int> {
        var picked: String? = null
        var dismissed = 0
        setContent {
            MaterialTheme {
                FontPickerPanel(
                    value = value,
                    catalog = catalog,
                    previewLines = previewLines,
                    onDismiss = { dismissed++ },
                    onPick = { picked = it },
                )
            }
        }
        waitForIdle()
        return { picked } to { dismissed }
    }

    /** A family's row, as opposed to the same name appearing in the preview caption. */
    private fun row(name: String) = hasText(name) and hasClickAction()

    @BeforeTest
    fun clearRecents() = RecentFonts.clear()

    @AfterTest
    fun forgetRecents() = RecentFonts.clear()

    // --- the list ---

    @Test
    fun `the panel opens on every family, grouped`() = runComposeUiTest {
        panel()

        onNodeWithText("GOOD FOR PROJECTION").assertExists()
        onNodeWithText("ALL FONTS").assertExists()
        listOf("Arial", "Georgia", "Papyrus", "Menlo").forEach { onNode(row(it)).assertExists() }
    }

    @Test
    fun `a family used this session gets its own heading`() = runComposeUiTest {
        RecentFonts.record("Menlo")
        panel()

        onNodeWithText("RECENTLY USED").assertExists()
    }

    @Test
    fun `each family says what it is shaped like`() = runComposeUiTest {
        panel()

        onNodeWithText("Serif").assertExists()
        onNodeWithText("Mono").assertExists()
        onNodeWithText("Display").assertExists()
    }

    @Test
    fun `the footer counts what is shown against what is installed`() = runComposeUiTest {
        panel()

        onNodeWithText("Showing 4 of 4").assertExists()
    }

    @Test
    fun `families left out are accounted for rather than silently missing`() = runComposeUiTest {
        panel(catalog = catalog(hidden = 12))

        onNodeWithText("12 symbol and system fonts hidden · showing 4 of 4").assertExists()
    }

    // --- searching ---

    @Test
    fun `typing narrows the list and collapses the headings`() = runComposeUiTest {
        panel()
        onNode(isEditable()).performTextInput("geo")
        waitForIdle()

        onNodeWithText("MATCHES").assertExists()
        onNode(row("Georgia")).assertExists()
        onNode(row("Papyrus")).assertDoesNotExist()
        onNodeWithText("GOOD FOR PROJECTION").assertDoesNotExist()
    }

    @Test
    fun `a search matching nothing says so and offers a way back`() = runComposeUiTest {
        panel()
        onNode(isEditable()).performTextInput("kiwi")
        waitForIdle()

        onNodeWithText("No results found for \"kiwi\"").assertExists()

        onNodeWithText("Clear search").performClick()
        waitForIdle()

        onNode(row("Papyrus")).assertExists()
    }

    @Test
    fun `the search box counts down as it narrows`() = runComposeUiTest {
        panel()
        onNode(isEditable()).performTextInput("a")
        waitForIdle()

        // Arial, Georgia, Papyrus — only Menlo has no "a" in it.
        onNodeWithText("Showing 3 of 4").assertExists()
    }

    // --- picking ---

    @Test
    fun `clicking a family picks it`() = runComposeUiTest {
        val (picked, _) = panel()
        onNode(row("Papyrus")).performClick()
        waitForIdle()

        assertEquals("Papyrus", picked())
    }

    @Test
    fun `enter picks whatever is highlighted`() = runComposeUiTest {
        val (picked, _) = panel(value = "Menlo")
        onNode(isEditable()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        // The panel opens with the current family highlighted, so Enter alone re-picks it.
        assertEquals("Menlo", picked())
    }

    @Test
    fun `the arrow keys walk the list and enter takes what they land on`() = runComposeUiTest {
        val (picked, _) = panel(value = "Arial")
        onNode(isEditable()).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNode(isEditable()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        // Arial then Georgia lead the recommended group; down moves one row.
        assertEquals("Georgia", picked())
    }

    @Test
    fun `up at the top of the list stays there`() = runComposeUiTest {
        val (picked, _) = panel(value = "Arial")
        repeat(3) { onNode(isEditable()).performKeyInput { pressKey(Key.DirectionUp) } }
        waitForIdle()
        onNode(isEditable()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals("Arial", picked())
    }

    @Test
    fun `escape closes without picking anything`() = runComposeUiTest {
        val (picked, dismissed) = panel()
        onNode(isEditable()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(1, dismissed())
        assertNull(picked())
    }

    @Test
    fun `a key the panel has no use for is left alone`() = runComposeUiTest {
        val (picked, dismissed) = panel()
        onNode(isEditable()).performKeyInput { pressKey(Key.Tab) }
        waitForIdle()

        assertEquals(0, dismissed())
        assertNull(picked())
    }

    @Test
    fun `enter with nothing left to pick does nothing`() = runComposeUiTest {
        val (picked, _) = panel()
        onNode(isEditable()).performTextInput("kiwi")
        waitForIdle()
        onNode(isEditable()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertNull(picked())
    }

    // --- the preview ---

    @Test
    fun `the preview quotes the verses it was given`() = runComposeUiTest {
        panel(previewLines = listOf("In the beginning", "В начале"))

        onNodeWithText("In the beginning").assertIsDisplayed()
        onNodeWithText("В начале").assertIsDisplayed()
    }

    @Test
    fun `a family that cannot draw the verse says so`() = runComposeUiTest {
        panel(value = "Papyrus", previewLines = listOf("В начале сотворил Бог"))

        onNodeWithText(
            "Papyrus has no Cyrillic glyphs — Cyrillic verses fall back to another font on screen.",
        ).assertExists()
    }

    @Test
    fun `a family that can draw the verse says nothing`() = runComposeUiTest {
        panel(value = "Arial", previewLines = listOf("В начале сотворил Бог"))

        onNodeWithText("has no Cyrillic glyphs", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no warning is claimed before the fonts have been measured`() = runComposeUiTest {
        // Unmeasured, every family reads as covering nothing — warning on all of them would be a lie.
        panel(value = "Arial", catalog = catalog(measured = false), previewLines = listOf("В начале"))

        onNodeWithText("has no Cyrillic glyphs", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the preview follows the highlight rather than the selection`() = runComposeUiTest {
        panel(value = "Arial")

        // The caption names the row the keys are on, not the one that is ticked: with Arial
        // highlighted, "Georgia" is on screen once — its row — and Arial twice.
        assertEquals(1, onAllNodesWithText("Georgia").fetchSemanticsNodes().size)
        assertEquals(2, onAllNodesWithText("Arial").fetchSemanticsNodes().size)

        onNode(isEditable()).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()

        assertEquals(2, onAllNodesWithText("Georgia").fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithText("Arial").fetchSemanticsNodes().size)
    }

    @Test
    fun `every heading the list can raise is drawn`() = runComposeUiTest {
        // The four headings are the only thing a group's kind decides, and three of them can only
        // appear together with recents in play.
        RecentFonts.record("Menlo")
        panel()

        listOf("RECENTLY USED", "GOOD FOR PROJECTION", "ALL FONTS").forEach {
            onNodeWithText(it).assertExists()
        }

        onNode(isEditable()).performTextInput("a")
        waitForIdle()

        onNodeWithText("MATCHES").assertExists()
    }

    @Test
    fun `an empty catalog leaves the panel standing`() = runComposeUiTest {
        val (picked, _) = panel(catalog = catalog(faces = emptyList()))

        onNodeWithText("Showing 0 of 0").assertExists()
        assertNull(picked())
    }
}
