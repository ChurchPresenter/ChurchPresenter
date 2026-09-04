@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.app.churchpresenter.dialogs.tabs.uniquelyNamedFont
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.Utils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Text source: the text itself, its font, size, colour, alignment, spacing, curve and background.
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
        const val LETTER_SPACING = 7
        const val CURVE = 8
        const val FONT_SIZE = 9
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
            "TEXT", "Edit in larger window", "Letter Spacing", "Curve", "FONT", "FONT SIZE",
            "Horizontal", "Vertical", "FONT COLOR", "Transparent background",
        ).forEach { caption ->
            onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the text panel")
        }
        // The section heading and this source's own default content are both the word "Text".
        assertEquals(2, countOf(Label.TEXT), "the section must be headed, above a box holding \"Text\"")
    }

    @Test
    fun `the text panel adds four fields and one checkbox to the header`() = sourcePanel(Fixture.text()) { _ ->
        // A transparent background — the default — hides the background colour field, so this is the
        // panel at its smallest: the text box, the two sliders' inputs and the font size.
        textFields().assertCountEquals(10)
        checkboxes().assertCountEquals(1)
        // The four style buttons publish no role of their own, so this counts the alignment ones.
        roleButtons().assertCountEquals(Align.COUNT)
        listOf("B", "I", "U", "S").forEach {
            assertEquals(1, countOf(it), "\"$it\" must be the text panel's own style button")
        }
    }

    @Test
    fun `every stored value is shown by the control that owns it`() {
        val styled = Fixture.text().copy(
            text = "Welcome home", fontFamily = "Verdana", fontSize = 72,
            fontColor = "#FFCC00", letterSpacing = 25f, curve = 40f,
        )
        sourcePanel(styled) { _ ->
            assertFieldShows("Welcome home", "the text box")
            assertFieldShows("72", "the font size field")
            assertFieldShows("25", "the letter spacing input")
            assertFieldShows("40", "the curve input")
            onNodeWithText("Verdana").assertExists("the font dropdown names the stored family")
            onNodeWithText("#FFCC00").assertExists("the colour field reads out its stored hex")
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
            // Which families exist is the machine's business, so the family to pick is taken from
            // the same list the panel builds its menu from rather than hard-coded here — and from
            // the part of it the panel actually offers. The picker hides the system's own internal
            // faces, and on a Mac the first family the JDK reports is one of them.
            assertTrue(
                Utils.getAvailableSystemFonts().size > 1,
                "the machine must report more than one font family",
            )
            val chosen = uniquelyNamedFont()
            assertTrue(chosen != Fixture.text().fontFamily, "the family picked must be a change")

            chooseFont(showing = Fixture.text().fontFamily, option = chosen)

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

    // ── Letter spacing and curve ──────────────────────────────────────────────

    @Test
    fun `dragging letter spacing to its far end tracks the text out`() = sourcePanel(Fixture.text()) { get ->
        tapSliderUnder("Letter Spacing", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(100f, (get() as SceneSource.TextSource).letterSpacing, "the range tops out at 100%")
        assertFieldShows("100", "the letter spacing input follows the track")
    }

    @Test
    fun `dragging letter spacing to its near end tightens it past zero`() = sourcePanel(Fixture.text()) { get ->
        tapSliderUnder("Letter Spacing", fraction = 0f, gapDp = Gap.INPUT)

        assertEquals(-20f, (get() as SceneSource.TextSource).letterSpacing, "the range starts at -20%")
    }

    @Test
    fun `typing a letter spacing stores it`() = sourcePanel(Fixture.text()) { get ->
        // The slider's own input holds what is typed until Done, like every other numeric field.
        commitField(Field.LETTER_SPACING, "35")

        assertEquals(35f, (get() as SceneSource.TextSource).letterSpacing)
    }

    @Test
    fun `dragging the curve to its far end arches the line`() = sourcePanel(Fixture.text()) { get ->
        tapSliderUnder("Curve", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(200f, (get() as SceneSource.TextSource).curve, "the curve runs to two full turns")
    }

    @Test
    fun `dragging the curve to its near end cups it`() = sourcePanel(Fixture.text()) { get ->
        tapSliderUnder("Curve", fraction = 0f, gapDp = Gap.INPUT)

        assertEquals(-200f, (get() as SceneSource.TextSource).curve, "and as far the other way")
    }

    @Test
    fun `a mid-track tap on the curve lands between the ends`() = sourcePanel(Fixture.text()) { get ->
        tapSliderUnder("Curve", fraction = 0.5f, gapDp = Gap.INPUT)

        val curve = (get() as SceneSource.TextSource).curve
        assertTrue(curve > -200f && curve < 200f, "a mid-track tap lands inside the range, was $curve")
    }

    @Test
    fun `the text is straight out of the box`() = sourcePanel(Fixture.text()) { get ->
        assertEquals(0f, (get() as SceneSource.TextSource).curve)
        assertEquals(0f, (get() as SceneSource.TextSource).letterSpacing)
    }

    // ── The four style buttons ────────────────────────────────────────────────

    @Test
    fun `every face is off out of the box`() = sourcePanel(Fixture.text()) { get ->
        val source = get() as SceneSource.TextSource
        assertEquals(false, source.bold)
        assertEquals(false, source.italic)
        assertEquals(false, source.underline)
        assertEquals(false, source.strikethrough)
    }

    @Test
    fun `Bold stores bold and nothing else`() = sourcePanel(Fixture.text()) { get ->
        clickStyleButton("B")

        assertEquals(Fixture.text().copy(bold = true), get(), "B may write only the bold flag")
    }

    @Test
    fun `Italic stores italic and nothing else`() = sourcePanel(Fixture.text()) { get ->
        clickStyleButton("I")

        assertEquals(Fixture.text().copy(italic = true), get())
    }

    @Test
    fun `Underline stores underline and nothing else`() = sourcePanel(Fixture.text()) { get ->
        clickStyleButton("U")

        assertEquals(Fixture.text().copy(underline = true), get())
    }

    @Test
    fun `Strikethrough stores strikethrough and nothing else`() = sourcePanel(Fixture.text()) { get ->
        clickStyleButton("S")

        assertEquals(Fixture.text().copy(strikethrough = true), get())
    }

    @Test
    fun `a face stored on is turned back off by its own button`() {
        sourcePanel(Fixture.text().copy(bold = true, italic = true)) { get ->
            clickStyleButton("B")

            val source = get() as SceneSource.TextSource
            assertEquals(false, source.bold)
            assertEquals(true, source.italic, "the other faces are untouched")
        }
    }

    @Test
    fun `the style row carries no text-backing control`() = sourcePanel(Fixture.text()) { _ ->
        onAllNodesWithText("B").assertCountEquals(1)
        onAllNodesWithText("A").assertCountEquals(0)
    }
}
