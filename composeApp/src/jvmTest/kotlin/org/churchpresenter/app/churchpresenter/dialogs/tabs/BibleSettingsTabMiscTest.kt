@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import org.churchpresenter.app.churchpresenter.composables.SCANNING_ROW_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three controls in the Bible tab's Miscellaneous section that only mean anything with two
 * translations on screen: the gap between them, how they sit, and the rule drawn between them.
 * `BibleSettingsTabTest` covers the rest of that section.
 */
class BibleSettingsTabMiscTest {

    private class Harness {
        var current by mutableStateOf(AppSettings())
    }

    private fun settings(
        spacing: Int = 24,
        divider: Boolean = false,
        fullScreen: String = Constants.BILINGUAL_TOP_BOTTOM,
        lowerThird: String = Constants.BILINGUAL_SIDE_BY_SIDE,
    ) = AppSettings(
        bibleSettings = BibleSettings(
            multiTranslationSpacing = spacing,
            multiTranslationDivider = divider,
            bilingualLayout = fullScreen,
            bilingualLayoutLowerThird = lowerThird,
        ).withTranslations(listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
    )

    private fun ComposeUiTest.showTab(initial: AppSettings = settings()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            MaterialTheme {
                BibleSettingsTab(
                    settings = harness.current,
                    onSettingsChange = { transform -> harness.current = transform(harness.current) },
                )
            }
        }
        // The tab reads the Bible folder on IO; waitForIdle does not cover that hop.
        waitUntil {
            onAllNodesWithTag(SCANNING_ROW_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        return harness
    }

    /** Row 0 is the full screen's layout, row 1 the band's; both offer the same two segments. */
    private fun ComposeUiTest.layoutSegment(row: Int, label: String) {
        onAllNodesWithText(label)[row].performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.retypeSpacing(showing: Int, to: Int) {
        onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))[0]
            .performScrollTo()
            .performTextReplacement(to.toString())
        waitForIdle()
    }

    // ── The gap between translations ──────────────────────────────────────────

    @Test
    fun `the spacing field shows what is stored`() = runComposeUiTest {
        showTab(settings(spacing = 37))
        onNodeWithText("Space between translations").assertExists()
        onAllNodes(hasSetTextAction() and hasText("37")).assertCountAtLeastOne()
    }

    @Test
    fun `retyping the spacing writes it`() = runComposeUiTest {
        val harness = showTab(settings(spacing = 37))
        retypeSpacing(37, 60)
        assertEquals(60, harness.current.bibleSettings.multiTranslationSpacing)
    }

    @Test
    fun `a spacing of zero is a value, not an absence`() = runComposeUiTest {
        val harness = showTab(settings(spacing = 37))
        retypeSpacing(37, 0)
        assertEquals(0, harness.current.bibleSettings.multiTranslationSpacing)
    }

    @Test
    fun `a spacing past the range is not written`() = runComposeUiTest {
        val harness = showTab(settings(spacing = 37))
        retypeSpacing(37, 9999)
        assertEquals(37, harness.current.bibleSettings.multiTranslationSpacing)
    }

    @Test
    fun `the spacing field writes nothing else`() = runComposeUiTest {
        val harness = showTab(settings(spacing = 37))
        val before = harness.current.bibleSettings
        retypeSpacing(37, 60)
        assertEquals(before.copy(multiTranslationSpacing = 60), harness.current.bibleSettings)
    }

    // ── How two translations sit ──────────────────────────────────────────────

    @Test
    fun `both output shapes get a layout row of their own`() = runComposeUiTest {
        showTab()
        assertEquals(
            2,
            onAllNodesWithText("Left / Right").fetchSemanticsNodes().size,
            "one row for the full screen and one for the band",
        )
        assertEquals(2, onAllNodesWithText("Top / Bottom").fetchSemanticsNodes().size)
    }

    @Test
    fun `the full screen's row writes the full screen's layout`() = runComposeUiTest {
        val harness = showTab()
        layoutSegment(row = 0, label = "Left / Right")

        val bible = harness.current.bibleSettings
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, bible.bilingualLayout)
        assertEquals(
            Constants.BILINGUAL_SIDE_BY_SIDE,
            bible.bilingualLayoutLowerThird,
            "the band's own layout must be untouched",
        )
    }

    @Test
    fun `the band's row writes the band's layout`() = runComposeUiTest {
        val harness = showTab()
        layoutSegment(row = 1, label = "Top / Bottom")

        val bible = harness.current.bibleSettings
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, bible.bilingualLayoutLowerThird)
        assertEquals(
            Constants.BILINGUAL_TOP_BOTTOM,
            bible.bilingualLayout,
            "the full screen's own layout must be untouched",
        )
    }

    @Test
    fun `the two shapes ship disagreeing, which is the point of having two rows`() = runComposeUiTest {
        val harness = showTab()
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, harness.current.bibleSettings.bilingualLayout)
        assertEquals(Constants.BILINGUAL_SIDE_BY_SIDE, harness.current.bibleSettings.bilingualLayoutLowerThird)
    }

    @Test
    fun `re-picking the layout already in force leaves it there`() = runComposeUiTest {
        val harness = showTab()
        layoutSegment(row = 0, label = "Top / Bottom")
        assertEquals(Constants.BILINGUAL_TOP_BOTTOM, harness.current.bibleSettings.bilingualLayout)
    }

    @Test
    fun `each row is captioned with the shape it is for`() = runComposeUiTest {
        showTab()
        onNodeWithText("FULL SCREEN").assertExists()
        onNodeWithText("LOWER THIRD").assertExists()
    }

    // ── The rule between them ─────────────────────────────────────────────────

    @Test
    fun `the divider checkbox toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        onNode(isToggleable() and hasText("Show divider between translations"))
            .performScrollTo()
            .performClick()
        waitForIdle()

        assertTrue(harness.current.bibleSettings.multiTranslationDivider)
        assertEquals(
            before.copy(multiTranslationDivider = true),
            harness.current.bibleSettings,
            "nothing else in the section may move with it",
        )
    }

    @Test
    fun `the divider checkbox turns back off`() = runComposeUiTest {
        val harness = showTab(settings(divider = true))
        onNode(isToggleable() and hasText("Show divider between translations"))
            .performScrollTo()
            .performClick()
        waitForIdle()
        assertFalse(harness.current.bibleSettings.multiTranslationDivider)
    }
}

/** Asserts the collection holds at least one node, which `assertCountEquals` cannot say. */
private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountAtLeastOne() {
    check(fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) { "expected at least one node" }
}
