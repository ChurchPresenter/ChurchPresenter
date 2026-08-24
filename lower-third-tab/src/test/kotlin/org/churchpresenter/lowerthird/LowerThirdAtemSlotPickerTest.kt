@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText

/**
 * Choosing *which* media-pool slot a lower third lands in.
 *
 * The switcher has a fixed number of slots and uploading overwrites whatever is in the one you pick,
 * so this is the control that decides what gets destroyed. Two shapes: a dropdown listing the pool
 * the switcher reported, and — when the query came back with nothing — a number field to type a slot
 * in by hand, displayed 1-based the way ATEM Software Control shows it.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdAtemSlotPickerTest {

    private fun state(
        stills: List<AtemMediaSlot> = listOf(
            AtemMediaSlot(index = 0, name = "Welcome", isUsed = true),
            AtemMediaSlot(index = 1, name = "", isUsed = false),
            AtemMediaSlot(index = 2, name = "Sermon", isUsed = true),
        ),
    ) = AtemState(
        fps = 25.0,
        videoMode = "1080p25",
        stillSlots = stills,
        clipSlots = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames = listOf(600),
    )

    // ── The dropdown, when the switcher reported a pool ──────────────────────────

    @Test
    fun `the slot dropdown lists every slot the switcher reported`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()
            openSlotDropdown()

            val shown = renderedText()
            assertTrue(shown.any { it.contains("Welcome") }, shown.toString())
            assertTrue(shown.any { it.contains("Sermon") }, shown.toString())
        }

    @Test
    fun `picking a slot from the dropdown closes it and shows the choice`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()
            openSlotDropdown()

            // Slot 3 is the one no other row shares a name with, so clicking it is unambiguous.
            onNodeWithText("Sermon", substring = true).performClick()
            waitForIdle()

            assertTrue(fieldShows("Sermon"), "the chosen slot is what the field now reads")
        }

    @Test
    fun `an empty pool falls back to typing the slot number by hand`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state(stills = emptyList()) }) { _ ->
            openAtemDialog()

            // Displayed 1-based, like ATEM Software Control — the default slot 0 reads as "1".
            assertTrue(fieldShows("1"), "the manual entry starts at the first slot")
            slotNumberField().performTextReplacement("4")
            waitForIdle()

            assertTrue(fieldShows("4"), "what was typed is what the field holds")
        }

    @Test
    fun `a slot number below one is held at the first slot`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state(stills = emptyList()) }) { _ ->
            openAtemDialog()

            // 0 and anything below it would address slot -1; the pool starts at 1 as displayed.
            slotNumberField().performTextReplacement("0")
            waitForIdle()

            assertTrue(fieldShows("1"), "a slot below the first is held there")
        }

    @Test
    fun `letters typed into the slot number are ignored rather than clearing it`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state(stills = emptyList()) }) { _ ->
            openAtemDialog()
            slotNumberField().performTextReplacement("3")
            waitForIdle()

            slotNumberField().performTextReplacement("three")
            waitForIdle()

            assertTrue(fieldShows("3"), "an unparseable slot leaves the last one alone")
        }

    /**
     * Whether any field on screen reads [text].
     *
     * `renderedText()` collects `Text` nodes only, and both slot controls are `OutlinedTextField`s —
     * their contents live in `EditableText`, which `hasText` matches and `renderedText` does not.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.fieldShows(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    /** The dialog's only editable field is the manual slot entry. */
    private fun androidx.compose.ui.test.ComposeUiTest.slotNumberField() =
        onAllNodes(hasSetTextAction())[0]

    /**
     * Opens the slot dropdown — the read-only field showing the currently chosen slot's label.
     *
     * Matched on "Slot 1", which `atemSlotLabel` builds for the default slot whatever the pool says
     * about it ("Slot 1 – Welcome", "Slot 1 (empty)", …).
     */
    private fun androidx.compose.ui.test.ComposeUiTest.openSlotDropdown() {
        onAllNodes(androidx.compose.ui.test.hasText("Slot 1", substring = true))[0].performClick()
        waitForIdle()
    }
}
