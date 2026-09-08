@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import org.churchpresenter.core.models.text.TextBackdrop
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one dialog behind the text-backing button: the four-way Style row, the presets, and the
 * fill/border fields the chosen style brings with it.
 *
 * The presets row mixes the operator's own saved looks with the built-ins, so every test here
 * starts from an empty [SavedTextBackdrops] — the same reset [ColorPickerDialogTest] does for
 * [RecentColors], and for the same reason: the list is a process-wide singleton backed by a file
 * under the fake home.
 */
class TextBackdropDialogTest {

    private val savedFile = File(System.getProperty("user.home"), ".churchpresenter/saved_backdrops.json")

    @BeforeTest
    fun freshPresets() {
        savedFile.delete()
        SavedTextBackdrops.looks.clear()
    }

    @AfterTest
    fun cleanupPresets() {
        savedFile.delete()
        SavedTextBackdrops.looks.clear()
    }

    /** Opens the dialog on [initial] and hands the block back whatever it last reported. */
    private fun dialog(
        initial: TextBackdrop = TextBackdrop(),
        block: ComposeUiTest.(get: () -> TextBackdrop, dismissed: () -> Int) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        var dismissals = 0
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                TextBackdropDialog(
                    backdrop = state,
                    onChange = { state = it; current = it },
                    onDismiss = { dismissals++ },
                )
            }
        }
        block({ current }, { dismissals })
    }

    /**
     * Retypes the number field currently showing [showing]. Values are distinct per fixture.
     *
     * Field captions are matched in upper case throughout: `NumberSettingsTextField` and
     * `ColorPickerField` both draw `label.uppercase()` inside the box, as does the dialog's own
     * `SectionLabel`.
     */
    private fun ComposeUiTest.retype(showing: Int, to: Int) {
        onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))[0]
            .performTextReplacement(to.toString())
        waitForIdle()
    }

    /** A look with every measurement distinct, so a field can be found by the number it shows. */
    private fun tuned(mode: String) = TextBackdrop(
        lineBackground = mode == "fill" || mode == "both",
        lineBackgroundOpacity = 71,
        lineBackgroundHeight = 32,
        lineBackgroundOffset = 13,
        border = mode == "border" || mode == "both",
        borderOpacity = 84,
        borderWidth = 7,
        borderPadding = 26,
        borderRadius = 19,
    )

    // ── The Style row ─────────────────────────────────────────────────────────

    @Test
    fun `the style row offers all four states`() = dialog { _, _ ->
        for (label in listOf("Off", "Fill", "Border", "Both")) {
            onNodeWithText(label).assertExists("the Style row must offer $label")
        }
    }

    @Test
    fun `choosing Fill turns the line background on and leaves the border off`() = dialog { get, _ ->
        onNodeWithText("Fill").performClick()
        waitForIdle()
        assertTrue(get().lineBackground, "Fill must turn the line background on")
        assertFalse(get().border, "Fill must not turn the border on")
    }

    @Test
    fun `choosing Border turns the border on and leaves the fill off`() = dialog { get, _ ->
        onNodeWithText("Border").performClick()
        waitForIdle()
        assertTrue(get().border, "Border must turn the border on")
        assertFalse(get().lineBackground, "Border must not turn the fill on")
    }

    @Test
    fun `choosing Both turns on the fill and the border together`() = dialog { get, _ ->
        onNodeWithText("Both").performClick()
        waitForIdle()
        assertTrue(get().lineBackground && get().border, "Both must turn on both halves")
    }

    @Test
    fun `choosing Off from Both turns both halves off`() = dialog(tuned("both")) { get, _ ->
        onNodeWithText("Off").performClick()
        waitForIdle()
        assertFalse(get().lineBackground || get().border, "Off must turn both halves off")
    }

    @Test
    fun `turning a look off keeps every colour and measurement it had`() = dialog(tuned("both")) { get, _ ->
        onNodeWithText("Off").performClick()
        waitForIdle()
        val off = get()
        assertEquals(71, off.lineBackgroundOpacity, "the fill's opacity must survive being turned off")
        assertEquals(32, off.lineBackgroundHeight)
        assertEquals(13, off.lineBackgroundOffset)
        assertEquals(7, off.borderWidth, "and the border's thickness with it")
        assertEquals(26, off.borderPadding)
        assertEquals(19, off.borderRadius)
    }

    @Test
    fun `switching from Fill to Border keeps the fill's own settings`() = dialog(tuned("fill")) { get, _ ->
        onNodeWithText("Border").performClick()
        waitForIdle()
        assertEquals(71, get().lineBackgroundOpacity, "the fill it left behind keeps its opacity")
        assertEquals(32, get().lineBackgroundHeight)
    }

    // ── What each state shows ─────────────────────────────────────────────────

    @Test
    fun `Off shows the hint and neither set of fields`() = dialog { _, _ ->
        onNodeWithText("PRESETS").assertDoesNotExist()
        onNodeWithText("FILL COLOR").assertDoesNotExist()
        onNodeWithText("BORDER COLOR").assertDoesNotExist()
    }

    @Test
    fun `Off explains how to start rather than showing an empty panel`() = dialog { _, _ ->
        onNode(hasText("Pick Fill, Border or Both to start", substring = true))
            .assertExists("Off must say what to do next")
    }

    @Test
    fun `Fill shows the fill fields and no border fields`() = dialog(tuned("fill")) { _, _ ->
        onNodeWithText("FILL COLOR").assertExists()
        onNodeWithText("HEIGHT OFFSET").assertExists()
        onNodeWithText("VERTICAL OFFSET").assertExists()
        onNodeWithText("BORDER COLOR").assertDoesNotExist()
        onNodeWithText("THICKNESS").assertDoesNotExist()
    }

    @Test
    fun `Border shows the border fields and no fill fields`() = dialog(tuned("border")) { _, _ ->
        onNodeWithText("BORDER COLOR").assertExists()
        onNodeWithText("THICKNESS").assertExists()
        onNodeWithText("PADDING").assertExists()
        onNodeWithText("CORNER RADIUS").assertExists()
        onNodeWithText("FILL COLOR").assertDoesNotExist()
        onNodeWithText("HEIGHT OFFSET").assertDoesNotExist()
    }

    @Test
    fun `Both shows every field of both halves`() = dialog(tuned("both")) { _, _ ->
        onNodeWithText("FILL COLOR").assertExists()
        onNodeWithText("BORDER COLOR").assertExists()
        onNodeWithText("CORNER RADIUS").assertExists()
        assertEquals(
            2,
            onAllNodesWithText("OPACITY %").fetchSemanticsNodes().size,
            "each half carries its own opacity",
        )
    }

    @Test
    fun `the presets row appears as soon as a look is chosen`() = dialog(tuned("fill")) { _, _ ->
        onNodeWithText("PRESETS").assertExists()
        onNodeWithText("Save as Preset").assertExists()
    }

    // ── The fill fields ───────────────────────────────────────────────────────

    @Test
    fun `retyping the fill opacity reports it`() = dialog(tuned("fill")) { get, _ ->
        retype(71, 40)
        assertEquals(40, get().lineBackgroundOpacity)
    }

    @Test
    fun `retyping the height offset reports it`() = dialog(tuned("fill")) { get, _ ->
        retype(32, 55)
        assertEquals(55, get().lineBackgroundHeight)
    }

    @Test
    fun `a negative height offset is inside the range and reported`() = dialog(tuned("fill")) { get, _ ->
        retype(32, -20)
        assertEquals(-20, get().lineBackgroundHeight, "the band may be pulled in as well as grown")
    }

    @Test
    fun `retyping the vertical offset reports it`() = dialog(tuned("fill")) { get, _ ->
        retype(13, -45)
        assertEquals(-45, get().lineBackgroundOffset, "a band may be nudged up as well as down")
    }

    @Test
    fun `an opacity past 100 is not reported`() = dialog(tuned("fill")) { get, _ ->
        retype(71, 140)
        assertEquals(71, get().lineBackgroundOpacity, "the field withholds a value outside its range")
    }

    // ── The border fields ─────────────────────────────────────────────────────

    @Test
    fun `retyping the border opacity reports it`() = dialog(tuned("border")) { get, _ ->
        retype(84, 33)
        assertEquals(33, get().borderOpacity)
    }

    @Test
    fun `retyping the thickness reports it`() = dialog(tuned("border")) { get, _ ->
        retype(7, 22)
        assertEquals(22, get().borderWidth)
    }

    @Test
    fun `retyping the padding reports it`() = dialog(tuned("border")) { get, _ ->
        retype(26, 120)
        assertEquals(120, get().borderPadding)
    }

    @Test
    fun `retyping the corner radius reports it`() = dialog(tuned("border")) { get, _ ->
        retype(19, 0)
        assertEquals(0, get().borderRadius, "a square corner is a setting, not an absence of one")
    }

    @Test
    fun `a thickness past the range is not reported`() = dialog(tuned("border")) { get, _ ->
        retype(7, 90)
        assertEquals(7, get().borderWidth, "40 is as thick as the box goes")
    }

    @Test
    fun `the two halves' fields write their own settings in Both`() = dialog(tuned("both")) { get, _ ->
        retype(32, 60)
        retype(7, 12)
        assertEquals(60, get().lineBackgroundHeight, "the fill's height")
        assertEquals(12, get().borderWidth, "and the border's thickness, independently")
    }

    // ── Presets ───────────────────────────────────────────────────────────────

    @Test
    fun `picking the first built-in applies the black bar whole`() = dialog(tuned("fill")) { get, _ ->
        onAllNodesWithText("Aa")[0].performClick()
        waitForIdle()
        val bar = get()
        assertEquals("#000000", bar.lineBackgroundColor)
        assertEquals(100, bar.lineBackgroundOpacity)
        assertEquals(24, bar.lineBackgroundHeight)
        assertEquals(2, bar.lineBackgroundOffset)
    }

    @Test
    fun `picking the outline preset leaves the fill it found alone`() = dialog(tuned("both")) { get, _ ->
        // Built-ins in order: black bar, soft shade, thin outline, rounded plate.
        onAllNodesWithText("Aa")[2].performClick()
        waitForIdle()
        val outlined = get()
        assertEquals(71, outlined.lineBackgroundOpacity, "a border preset must not restyle the fill")
        assertEquals(3, outlined.borderWidth, "and must set its own half")
        assertEquals("#FFFFFF", outlined.borderColor)
    }

    @Test
    fun `the rounded plate preset sets both halves at once`() = dialog(tuned("fill")) { get, _ ->
        onAllNodesWithText("Aa")[3].performClick()
        waitForIdle()
        val plate = get()
        assertTrue(plate.lineBackground && plate.border, "the plate is a fill and a box together")
        assertEquals("#7A1246", plate.lineBackgroundColor)
        assertEquals(14, plate.borderRadius)
    }

    @Test
    fun `saving the current look puts it at the front of the row`() = dialog(tuned("fill")) { _, _ ->
        onNodeWithText("Save as Preset").performClick()
        waitForIdle()
        assertEquals(1, SavedTextBackdrops.looks.size, "the look must be stored")
        assertEquals(71, SavedTextBackdrops.looks[0].lineBackgroundOpacity)
    }

    @Test
    fun `the save button says so once the look is already stored`() = dialog(tuned("fill")) { _, _ ->
        onNodeWithText("Save as Preset").performClick()
        waitForIdle()
        onNodeWithText("This look is already saved").assertExists()
        onNodeWithText("Save as Preset").assertDoesNotExist()
    }

    @Test
    fun `a saved look is offered ahead of the built-ins and applies whole`() {
        val mine = TextBackdrop(lineBackground = true, lineBackgroundColor = "#123456", lineBackgroundOpacity = 42)
        SavedTextBackdrops.looks.add(0, mine)
        dialog(tuned("border")) { get, _ ->
            onAllNodesWithText("Aa")[0].performClick()
            waitForIdle()
            assertEquals(mine, get(), "a saved look is the operator's finished thing, applied entire")
        }
    }

    @Test
    fun `a saved look is not also drawn as a built-in`() {
        val blackBar = TEXT_BACKDROP_PRESETS[0].preview
        SavedTextBackdrops.looks.add(0, blackBar)
        dialog(tuned("fill")) { _, _ ->
            assertEquals(
                4,
                onAllNodesWithText("Aa").fetchSemanticsNodes().size,
                "the saved copy replaces the built-in rather than joining it",
            )
        }
    }

    // ── Dismissal ─────────────────────────────────────────────────────────────

    @Test
    fun `the close button dismisses`() = dialog(tuned("fill")) { _, dismissed ->
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(1, dismissed(), "the close button must dismiss exactly once")
    }

    @Test
    fun `the close button is there in every state`() = dialog { _, _ ->
        onNodeWithContentDescription("Close").assertExists("Off must still be closable")
    }
}
