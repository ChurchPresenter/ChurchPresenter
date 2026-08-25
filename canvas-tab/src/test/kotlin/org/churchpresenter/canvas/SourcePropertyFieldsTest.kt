@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three numeric controls every properties panel is built from.
 *
 * They are shared, so a fault in one shows up in a dozen unrelated places — and the fault they are
 * written to avoid is the same one in all three: a half-typed value must not be committed. Typing
 * `1` on the way to `180`, or clearing a field before retyping it, reaches `onValueChange` on every
 * keystroke unless the control holds its own text until the value is confirmed. The panel tests
 * reach these through whichever editor happens to use them; this reaches them directly, including
 * the ranges and suffixes no editor currently asks for.
 */
class SourcePropertyFieldsTest {

    private fun ComposeUiTest.commit(to: String) {
        onAllNodes(hasSetTextAction())[0].performTextReplacement(to)
        onAllNodes(hasSetTextAction())[0].performImeAction()
        waitForIdle()
    }

    private fun ComposeUiTest.shownText(): List<String> =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text }

    // ── PropertySlider's trailing label ────────────────────────────────────────

    @Test
    fun `a nought-to-one slider is labelled as a percentage`() = runComposeUiTest {
        setContent { MaterialTheme { PropertySlider("Opacity", 0.42f, 0f, 1f) { } } }

        onNodeWithText("42%").assertExists()
    }

    @Test
    fun `a slider with any other range is labelled with the value itself`() = runComposeUiTest {
        // A "4200%" opacity would be nonsense; outside 0..1 the number is the number.
        setContent { MaterialTheme { PropertySlider("Width", 42f, 0f, 100f) { } } }

        onNodeWithText("42.00").assertExists()
    }

    @Test
    fun `a slider starting above nought is not treated as a percentage`() = runComposeUiTest {
        setContent { MaterialTheme { PropertySlider("Scale", 1.5f, 1f, 4f) { } } }

        onNodeWithText("1.50").assertExists()
    }

    @Test
    fun `a slider ending above one is not treated as a percentage`() = runComposeUiTest {
        setContent { MaterialTheme { PropertySlider("Zoom", 0.5f, 0f, 2f) { } } }

        onNodeWithText("0.50").assertExists()
    }

    // ── PropertySliderWithInput's typed value ──────────────────────────────────

    @Test
    fun `a number typed into the input is committed when it is confirmed`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertySliderWithInput("Rotation", 0f, -180f, 180f) { committed = it } } }

        commit("90")

        assertEquals(90f, committed)
    }

    @Test
    fun `a number past the end of the range is pulled back to it`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertySliderWithInput("Rotation", 0f, -180f, 180f) { committed = it } } }

        commit("9999")

        assertEquals(180f, committed, "a value outside the range would put the source somewhere it cannot be")
    }

    @Test
    fun `a number before the start of the range is pulled up to it`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertySliderWithInput("Rotation", 0f, -180f, 180f) { committed = it } } }

        commit("-9999")

        assertEquals(-180f, committed)
    }

    @Test
    fun `text that is not a number is put back rather than committed`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertySliderWithInput("Rotation", 45f, -180f, 180f) { committed = it } } }

        commit("sideways")

        assertEquals(null, committed, "nothing may be committed for text that is not a number")
        assertTrue(shownText().contains("45"), "and the field must show the value again: ${shownText()}")
    }

    @Test
    fun `an emptied field is put back rather than committed as nought`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertySliderWithInput("Rotation", 45f, -180f, 180f) { committed = it } } }

        commit("")

        assertEquals(null, committed)
        assertTrue(shownText().contains("45"))
    }

    @Test
    fun `a suffix is shown beside the input when there is one`() = runComposeUiTest {
        setContent { MaterialTheme { PropertySliderWithInput("Stroke", 3f, 1f, 50f, "px") { } } }

        onNodeWithText("px").assertExists()
    }

    @Test
    fun `a control with no suffix draws none`() = runComposeUiTest {
        setContent { MaterialTheme { PropertySliderWithInput("Steps", 3f, 1f, 50f) { } } }

        assertEquals(0, countOf("px"))
    }

    // ── PropertyFloatField ─────────────────────────────────────────────────────

    @Test
    fun `a float field shows its value to three places`() = runComposeUiTest {
        setContent { MaterialTheme { PropertyFloatField("X", 0.25f) { } } }

        assertTrue(shownText().contains("0.250"), "was ${shownText()}")
    }

    @Test
    fun `a float field commits what was typed when it is confirmed`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertyFloatField("X", 0.25f) { committed = it } } }

        commit("0.5")

        assertEquals(0.5f, committed)
    }

    @Test
    fun `a float field puts back text that is not a number`() = runComposeUiTest {
        var committed: Float? = null
        setContent { MaterialTheme { PropertyFloatField("X", 0.25f) { committed = it } } }

        commit("over there")

        assertEquals(null, committed)
        assertTrue(shownText().contains("0.250"), "was ${shownText()}")
    }
}
