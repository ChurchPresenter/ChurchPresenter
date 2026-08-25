@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.tabs.Tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText

/**
 * The tab bar itself — the row every other tab hangs off, and the one control in the app that is
 * always on screen.
 *
 * Two things here are easy to get wrong and would be felt immediately. `onTabSelected` reports the
 * index **within the visible list**, not the enum ordinal, so any instance of the bar showing a
 * subset has to renumber — treat one as the other and hiding a tab silently switches every tab after
 * it to the wrong screen. And [getStringName] is an exhaustive `when` over [Tabs]: a new entry
 * without a label fails to compile, but an entry wired to the *wrong* string resource does not, so
 * the labels are read back rather than assumed.
 *
 * The overflow arrows are the other half. They exist because fourteen tabs do not fit on a laptop
 * screen, and they are conditional on the scroll state rather than on a flag — so they only appear
 * once layout has decided the row overflows, which a fixed-width harness reproduces.
 */
class TabSectionTest {

    private fun ComposeUiTest.tabBar(
        visibleTabs: List<Tabs> = Tabs.entries,
        selectedTabIndex: Int = 0,
        width: Int = 2_000,
        onTabSelected: (Int) -> Unit = {},
    ) {
        setContent {
            Box(Modifier.width(width.dp)) {
                TabSection(
                    visibleTabs = visibleTabs,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = onTabSelected,
                )
            }
        }
        waitForIdle()
    }

    /**
     * The overflow arrows: clickable, and the only clickable nodes here carrying no text (every tab
     * is addressed by its label). Selecting them positionally would break the moment the row scrolls.
     * In row order the back arrow comes first, so with both present index 0 is back and 1 is forward.
     */
    private fun ComposeUiTest.arrows() =
        onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))

    private fun ComposeUiTest.arrowCount(): Int =
        arrows().fetchSemanticsNodes(atLeastOneRootRequired = false).size

    // ── Labels ──────────────────────────────────────────────────────────────────

    @Test
    fun `every tab is shown with its own label`() {
        runComposeUiTest {
            tabBar()

            val shown = renderedText()
            assertEquals(
                Tabs.entries.size, shown.size,
                "one label per tab, no more and no fewer — saw $shown"
            )
            assertEquals(
                shown.size, shown.toSet().size,
                "every tab needs a distinct label or two of them are indistinguishable: $shown"
            )
            assertTrue(shown.none { it.isBlank() }, "a tab with no label is unclickable in practice: $shown")
        }
    }

    @Test
    fun `hiding tabs shows only the ones that are left`() {
        runComposeUiTest {
            tabBar(visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS))

            assertEquals(2, renderedText().size, "hidden tabs must not be rendered at all")
        }
    }

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `the selected tab is the one marked selected`() {
        runComposeUiTest {
            val tabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA)
            tabBar(visibleTabs = tabs, selectedTabIndex = 1)

            val labels = renderedText()
            onNodeWithText(labels[1]).assertIsSelected()
            onNodeWithText(labels[0]).assertIsNotSelected()
            onNodeWithText(labels[2]).assertIsNotSelected()
        }
    }

    @Test
    fun `clicking a tab reports its position`() {
        runComposeUiTest {
            val picked = mutableListOf<Int>()
            val tabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA)
            tabBar(visibleTabs = tabs, onTabSelected = { picked.add(it) })

            val labels = renderedText()
            onNodeWithText(labels[2]).performClick()
            onNodeWithText(labels[0]).performClick()

            assertEquals(listOf(2, 0), picked)
        }
    }

    @Test
    fun `a hidden tab renumbers the ones after it`() {
        // The index is a position in the visible list, not an enum ordinal. Songs is ordinal 1 with
        // every tab shown; with Bible hidden it is position 0, and reporting 1 here would open the
        // wrong screen for every operator who has hidden a tab.
        runComposeUiTest {
            val picked = mutableListOf<Int>()
            tabBar(visibleTabs = listOf(Tabs.SONGS, Tabs.MEDIA), onTabSelected = { picked.add(it) })

            val labels = renderedText()
            onNodeWithText(labels[0]).performClick()

            assertEquals(listOf(0), picked, "the first visible tab is index 0 whatever its ordinal")
        }
    }

    // ── Overflow arrows ─────────────────────────────────────────────────────────

    @Test
    fun `a row with room for its tabs shows no scroll arrows`() {
        // Deliberately a short tab list rather than a very wide row: the full fourteen overflow even
        // a 2000dp window, so with every tab enabled the arrows are always there. Which is the point
        // of them, but it means "wide enough" has to come from having fewer tabs.
        runComposeUiTest {
            tabBar(visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS), width = 2_000)

            assertEquals(0, arrowCount(), "nothing to scroll to, nothing to press")
        }
    }

    @Test
    fun `a row too narrow for its tabs offers a way to scroll`() {
        runComposeUiTest {
            tabBar(width = 300)

            assertEquals(
                1, arrowCount(),
                "only the forward arrow: the row starts at the left edge, so there is nothing behind it yet"
            )
        }
    }

    @Test
    fun `scrolling forward makes the way back appear`() {
        // The two arrows are conditional on opposite ends of the same scroll state, so the second one
        // appearing is the observable proof that the first one actually scrolled.
        runComposeUiTest {
            tabBar(width = 300)

            arrows().onFirst().performClick() // the only arrow at the left edge is the forward one

            waitUntil("the back arrow to appear once the row has scrolled") { arrowCount() == 2 }
        }
    }
}
