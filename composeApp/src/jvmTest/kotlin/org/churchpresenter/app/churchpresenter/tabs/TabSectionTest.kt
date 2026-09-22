@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasText
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.app.churchpresenter.composables.LABELED_TAB_MIN_WIDTH
import org.churchpresenter.app.churchpresenter.composables.labeledTabMinWidth
import org.churchpresenter.settings.TabLabelStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        labelStyle: TabLabelStyle = TabLabelStyle.TEXT,
        labelMargin: TabLabelMargin = TabLabelMargin.NORMAL,
        onTabSelected: (Int) -> Unit = {},
    ) {
        setContent {
            Box(Modifier.width(width.dp)) {
                TabSection(
                    visibleTabs = visibleTabs,
                    selectedTabIndex = selectedTabIndex,
                    labelStyle = labelStyle,
                    labelMargin = labelMargin,
                    onTabSelected = onTabSelected,
                )
            }
        }
        waitForIdle()
    }

    /** The icon-only tabs, in row order: selectable (the arrows are not) and named by their icon. */
    private fun ComposeUiTest.iconTabs() =
        onAllNodes(isSelectable() and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))

    private fun ComposeUiTest.iconTabNames(): List<String> =
        iconTabs().fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("") }

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

    // ── Label styles ────────────────────────────────────────────────────────────

    @Test
    fun `icons and text keeps every label`() {
        runComposeUiTest {
            tabBar(labelStyle = TabLabelStyle.ICONS_AND_TEXT)

            assertEquals(Tabs.entries.size, renderedText().size)
            assertEquals(0, iconTabNames().size, "the name is drawn, so the icon must not repeat it")
        }
    }

    @Test
    fun `icons only draws no text and names every tab by its icon`() {
        runComposeUiTest {
            tabBar(labelStyle = TabLabelStyle.ICONS)

            assertEquals(emptyList(), renderedText())
            val names = iconTabNames()
            assertEquals(Tabs.entries.size, names.size, "one named icon per tab — saw $names")
            assertEquals(names.size, names.toSet().size, "every icon needs a distinct name: $names")
        }
    }

    @Test
    fun `an icon-only tab is named the same as its text label`() {
        runComposeUiTest {
            setContent {
                Column(Modifier.width(2_000.dp)) {
                    TabSection(visibleTabs = Tabs.entries, onTabSelected = {})
                    TabSection(visibleTabs = Tabs.entries, labelStyle = TabLabelStyle.ICONS, onTabSelected = {})
                }
            }
            waitForIdle()

            assertEquals(renderedText(), iconTabNames())
        }
    }

    @Test
    fun `clicking an icon-only tab reports its position`() {
        runComposeUiTest {
            val picked = mutableListOf<Int>()
            tabBar(
                visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA),
                labelStyle = TabLabelStyle.ICONS,
                onTabSelected = { picked.add(it) },
            )

            iconTabs()[2].performClick()
            iconTabs()[0].performClick()

            assertEquals(listOf(2, 0), picked)
        }
    }

    @Test
    fun `the selected icon-only tab is the one marked selected`() {
        runComposeUiTest {
            tabBar(
                visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS),
                selectedTabIndex = 1,
                labelStyle = TabLabelStyle.ICONS,
            )

            iconTabs()[1].assertIsSelected()
            iconTabs()[0].assertIsNotSelected()
        }
    }

    @Test
    fun `hovering an icon-only tab shows its name`() {
        runComposeUiTest {
            tabBar(visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS), labelStyle = TabLabelStyle.ICONS)
            assertEquals(emptyList(), renderedText(), "nothing is written until the pointer rests on a tab")

            iconTabs()[1].performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
            waitForIdle()

            assertEquals(listOf(iconTabNames()[1]), renderedText())
        }
    }

    @Test
    fun `every tab has an icon of its own`() {
        val icons = Tabs.entries.map { tabIcon(it) }
        assertEquals(icons.size, icons.toSet().size, "two tabs sharing an icon are indistinguishable as icons")
    }

    // ── Spacing ─────────────────────────────────────────────────────────────────

    /**
     * How wide the Bible tab is drawn at each spacing, smallest first -- one composition, the spacing
     * switched through state, so five sizes cost one test's setup rather than five.
     */
    private fun bibleTabWidths(style: TabLabelStyle): List<Float> {
        val widths = mutableListOf<Float>()
        runComposeUiTest {
            var margin by mutableStateOf(TabLabelMargin.SMALL)
            setContent {
                Box(Modifier.width(2_000.dp)) {
                    TabSection(
                        visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS),
                        labelStyle = style,
                        labelMargin = margin,
                        onTabSelected = {},
                    )
                }
            }
            for (next in TabLabelMargin.entries) {
                margin = next
                waitForIdle()
                val tab =
                    if (style == TabLabelStyle.ICONS) iconTabs()[0] else onNode(isSelectable() and hasText("Bible"))
                widths += tab.fetchSemanticsNode().boundsInRoot.width
            }
        }
        return widths
    }

    @Test
    fun `each step of spacing gives a named tab more room than the one before`() {
        for (style in listOf(TabLabelStyle.TEXT, TabLabelStyle.ICONS_AND_TEXT)) {
            val widths = bibleTabWidths(style)
            assertEquals(widths.sorted(), widths, "$style: $widths")
            assertEquals(widths.size, widths.toSet().size, "$style: every step must change something -- $widths")
        }
    }

    @Test
    fun `each step of spacing widens an icon-only tab too`() {
        val widths = bibleTabWidths(TabLabelStyle.ICONS)

        assertEquals(widths.sorted(), widths, "$widths")
        assertEquals(widths.size, widths.toSet().size, "every step must change something -- $widths")
    }

    @Test
    fun `only icon-only tabs are held to a minimum width`() {
        for (margin in TabLabelMargin.entries) {
            assertEquals(0.dp, labeledTabMinWidth(TabLabelStyle.TEXT, margin), "$margin")
            assertEquals(0.dp, labeledTabMinWidth(TabLabelStyle.ICONS_AND_TEXT, margin), "$margin")
        }
        assertEquals(LABELED_TAB_MIN_WIDTH, labeledTabMinWidth(TabLabelStyle.ICONS, TabLabelMargin.NORMAL))
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

    private companion object {
        /** Past `TooltipArea`'s 500ms rest before it shows. */
        const val TOOLTIP_DELAY_MS = 1_000L
    }
}
