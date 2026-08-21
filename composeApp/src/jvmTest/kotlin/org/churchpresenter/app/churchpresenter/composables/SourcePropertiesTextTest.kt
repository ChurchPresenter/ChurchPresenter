@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.Utils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Text source: the text itself, its font, size, colour, alignment, line spacing and background.
 *
 * Two things here are not like the rest of the panel. The **transparent-background checkbox** is not
 * a boolean in the model at all — it is derived from whether `backgroundColor` reads `#00000000`, and
 * ticking it writes that sentinel while unticking writes opaque black. So both the flag's effect on
 * the model *and* the colour field it hides are asserted together; neither alone would catch the
 * sentinel being written in the wrong case. The **alignment buttons** carry no text, no content
 * description and no selected-state semantics — the panel signals the current choice with colour
 * only — so what each one stores is asserted from the model, and the button is located by its ordinal
 * in the group (right, centre, left; bottom, middle, top — each group is laid out in that order).
 *
 * Known gap: the "Edit in larger window" link opens a `DialogWindow`, a real AWT window, which throws
 * `HeadlessException` under the suite's headless JVM. The link is asserted to be on screen; it is
 * never clicked, and the dialog's own copy of the text field is not reached.
 */
class SourcePropertiesTextTest {

    /** Ordinals of the text panel's fields — the header owns the first six. */
    private object Field {
        const val TEXT = 6
        const val FONT_SIZE = 7
    }

    /** Ordinals within the panel's six alignment buttons, in the order the group lays them out. */
    private object Align {
        const val RIGHT = 0
        const val CENTER = 1
        const val LEFT = 2
        const val BOTTOM = 3
        const val MIDDLE = 4
        const val TOP = 5
        const val COUNT = 6
    }

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and every control captioned`() = sourcePanel(Fixture.text()) { _ ->
        listOf(
            "TEXT", "Edit in larger window", "Line Spacing", "FONT", "FONT SIZE",
            "Horizontal", "Vertical", "FONT COLOR", "Transparent background",
        ).forEach { caption ->
            onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the text panel")
        }
        // The section heading and this source's own default content are both the word "Text".
        assertEquals(2, countOf(Label.TEXT), "the section must be headed, above a box holding \"Text\"")
    }

    @Test
    fun `the text panel adds two fields and one checkbox to the header`() = sourcePanel(Fixture.text()) { _ ->
        // A transparent background — the default — hides the background colour field, so this is the
        // panel at its smallest: the text box and the font size.
        textFields().assertCountEquals(8)
        checkboxes().assertCountEquals(1)
        roleButtons().assertCountEquals(Align.COUNT)
    }

    @Test
    fun `every stored value is shown by the control that owns it`() {
        val styled = Fixture.text().copy(
            text = "Welcome home", fontFamily = "Verdana", fontSize = 72,
            fontColor = "#FFCC00", lineSpacing = 140,
        )
        sourcePanel(styled) { _ ->
            assertFieldShows("Welcome home", "the text box")
            assertFieldShows("72", "the font size field")
            onNodeWithText("Verdana").assertExists("the font dropdown names the stored family")
            onNodeWithText("#FFCC00").assertExists("the colour field reads out its stored hex")
            onNodeWithText("140%").assertExists("the line spacing slider reads out percent")
        }
    }

    @Test
    fun `the edit-in-a-larger-window link is on screen`() = sourcePanel(Fixture.text()) { _ ->
        // Never clicked: it opens a DialogWindow, which is a real AWT window and throws when headless.
        onNodeWithText("Edit in larger window").assertIsDisplayed()
    }

    // ── The text itself ───────────────────────────────────────────────────────

    @Test
    fun `typing text stores it and shows it`() = sourcePanel(Fixture.text()) { get ->
        typeField(Field.TEXT, "Grace and peace")

        assertEquals("Grace and peace", (get() as SceneSource.TextSource).text)
        assertFieldShows("Grace and peace", "the text box after typing")
    }

    @Test
    fun `text with line breaks is stored whole`() = sourcePanel(Fixture.text()) { get ->
        typeField(Field.TEXT, "First line\nSecond line")

        assertEquals(
            "First line\nSecond line", (get() as SceneSource.TextSource).text,
            "the box is multiline — a newline is content, not a commit",
        )
    }

    @Test
    fun `the text can be cleared`() = sourcePanel(Fixture.text()) { get ->
        typeField(Field.TEXT, "")

        assertEquals("", (get() as SceneSource.TextSource).text)
    }

    // ── Font ──────────────────────────────────────────────────────────────────

    @Test
    fun `the font dropdown offers the system's own families and stores the one chosen`() =
        sourcePanel(Fixture.text()) { get ->
            // Which families exist is the machine's business, so the family to pick is taken from the
            // same list the panel builds its menu from rather than hard-coded here.
            val families = Utils.getAvailableSystemFonts()
            assertTrue(families.size > 1, "the machine must report more than one font family")
            val chosen = families.first { it != Fixture.text().fontFamily }

            chooseFromDropdown(showing = Fixture.text().fontFamily, option = chosen)

            assertEquals(chosen, (get() as SceneSource.TextSource).fontFamily, "the menu writes the family")
            assertEquals(
                Fixture.text().copy(fontFamily = chosen), get(),
                "and changes nothing else about the source",
            )
        }

    @Test
    fun `committing a font size stores it`() = sourcePanel(Fixture.text()) { get ->
        typeField(Field.FONT_SIZE, "96")

        assertEquals(96, (get() as SceneSource.TextSource).fontSize)
        assertFieldShows("96", "the font size field")
    }

    @Test
    fun `text that is not a number leaves the font size alone`() = sourcePanel(Fixture.text()) { get ->
        typeField(Field.FONT_SIZE, "huge")

        assertEquals(48, (get() as SceneSource.TextSource).fontSize, "the stored size is untouched")
    }

    // ── Colour ────────────────────────────────────────────────────────────────

    @Test
    fun `recolouring the font stores the new hex`() = sourcePanel(Fixture.text()) { get ->
        recolor(fromHex = "#FFFFFF", toHex = "#112233")

        assertEquals("#112233", (get() as SceneSource.TextSource).fontColor)
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Test
    fun `a transparent background is what the box shows out of the box`() = sourcePanel(Fixture.text()) { _ ->
        checkboxes()[0].assertIsOn()
        assertEquals(0, countOf("BG COLOR"), "and no background colour field is offered")
    }

    @Test
    fun `unticking transparency writes opaque black and reveals the colour field`() =
        sourcePanel(Fixture.text()) { get ->
            toggleCheckbox(0)

            assertEquals(
                "#000000", (get() as SceneSource.TextSource).backgroundColor,
                "unticking the box replaces the transparent sentinel with opaque black",
            )
            checkboxes()[0].assertIsOff()
            onNodeWithText("BG COLOR").assertExists("and the background colour field appears with it")
        }

    @Test
    fun `ticking transparency writes the sentinel and hides the colour field again`() {
        sourcePanel(Fixture.text().copy(backgroundColor = "#123456")) { get ->
            checkboxes()[0].assertIsOff()
            toggleCheckbox(0)

            assertEquals("#00000000", (get() as SceneSource.TextSource).backgroundColor)
            checkboxes()[0].assertIsOn()
            assertEquals(0, countOf("BG COLOR"), "the background colour field goes with it")
        }
    }

    @Test
    fun `the sentinel is recognised whatever case it was stored in`() {
        sourcePanel(Fixture.text().copy(backgroundColor = "#00000000".lowercase())) { _ ->
            checkboxes()[0].assertIsOn()
        }
    }

    @Test
    fun `recolouring an opaque background stores the new hex`() {
        sourcePanel(Fixture.text().copy(backgroundColor = "#123456")) { get ->
            recolor(fromHex = "#123456", toHex = "#654321")

            val source = get() as SceneSource.TextSource
            assertEquals("#654321", source.backgroundColor)
            assertEquals("#FFFFFF", source.fontColor, "and the font colour is untouched")
        }
    }

    // ── Alignment ─────────────────────────────────────────────────────────────

    @Test
    fun `the panel offers three horizontal and three vertical alignment buttons`() =
        sourcePanel(Fixture.text()) { _ ->
            roleButtons().assertCountEquals(Align.COUNT)
        }

    @Test
    fun `aligning left stores left`() = sourcePanel(Fixture.text()) { get ->
        roleButtons()[Align.LEFT].performScrollTo().performClick()
        waitForIdle()

        assertEquals("left", (get() as SceneSource.TextSource).horizontalAlignment)
        assertEquals("center", (get() as SceneSource.TextSource).verticalAlignment, "vertical is untouched")
    }

    @Test
    fun `aligning right stores right`() = sourcePanel(Fixture.text()) { get ->
        roleButtons()[Align.RIGHT].performScrollTo().performClick()
        waitForIdle()

        assertEquals("right", (get() as SceneSource.TextSource).horizontalAlignment)
    }

    @Test
    fun `aligning centre stores center`() {
        sourcePanel(Fixture.text().copy(horizontalAlignment = "left")) { get ->
            roleButtons()[Align.CENTER].performScrollTo().performClick()
            waitForIdle()

            assertEquals("center", (get() as SceneSource.TextSource).horizontalAlignment)
        }
    }

    @Test
    fun `aligning to the top stores top`() = sourcePanel(Fixture.text()) { get ->
        roleButtons()[Align.TOP].performScrollTo().performClick()
        waitForIdle()

        assertEquals("top", (get() as SceneSource.TextSource).verticalAlignment)
        assertEquals("center", (get() as SceneSource.TextSource).horizontalAlignment, "horizontal is untouched")
    }

    @Test
    fun `aligning to the bottom stores bottom`() = sourcePanel(Fixture.text()) { get ->
        roleButtons()[Align.BOTTOM].performScrollTo().performClick()
        waitForIdle()

        assertEquals("bottom", (get() as SceneSource.TextSource).verticalAlignment)
    }

    @Test
    fun `aligning to the middle stores center`() {
        sourcePanel(Fixture.text().copy(verticalAlignment = "top")) { get ->
            roleButtons()[Align.MIDDLE].performScrollTo().performClick()
            waitForIdle()

            assertEquals("center", (get() as SceneSource.TextSource).verticalAlignment)
        }
    }

    // ── Line spacing ──────────────────────────────────────────────────────────

    @Test
    fun `dragging line spacing to its near end is the tightest setting`() = sourcePanel(Fixture.text()) { get ->
        tapSliderBeside("Line Spacing", fraction = 0f, gapDp = Gap.READOUT)

        assertEquals(50, (get() as SceneSource.TextSource).lineSpacing, "the range starts at 50%")
        onNodeWithText("50%").assertExists("and the read-out follows immediately")
    }

    @Test
    fun `dragging line spacing to its far end is the loosest setting`() = sourcePanel(Fixture.text()) { get ->
        tapSliderBeside("Line Spacing", fraction = 1f, gapDp = Gap.READOUT)

        assertEquals(300, (get() as SceneSource.TextSource).lineSpacing, "the range tops out at 300%")
        onNodeWithText("300%").assertExists()
    }

    @Test
    fun `a mid-track tap on line spacing lands between the ends`() = sourcePanel(Fixture.text()) { get ->
        tapSliderBeside("Line Spacing", fraction = 0.5f, gapDp = Gap.READOUT)

        val spacing = (get() as SceneSource.TextSource).lineSpacing
        assertTrue(spacing in 51..299, "a mid-track tap lands inside the range, was $spacing")
        onNodeWithText("$spacing%").assertExists("the read-out shows exactly what was stored")
    }
}
