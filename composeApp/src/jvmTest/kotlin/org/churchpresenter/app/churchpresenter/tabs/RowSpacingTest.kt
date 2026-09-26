package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.factor
import org.churchpresenter.settings.ListRowSpacing
import org.churchpresenter.theme.LocalThemeCustomization
import org.churchpresenter.theme.ThemeCustomization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [rowPad] and [rowSpan], through which every list row takes the Margin chosen in Customize Theme.
 *
 * The one promise that matters to the congregation's operator is the second: however thin the
 * Margin, a one-line row keeps room for its line -- only the space around the text gives.
 */
@OptIn(ExperimentalTestApi::class)
class RowSpacingTest {

    /** What [rowPad] and [rowSpan] give for [base] under each Margin, in Normal, Thin, Thinner order. */
    private fun measured(base: Dp): List<Pair<Dp, Dp>> {
        val results = mutableListOf<Pair<Dp, Dp>>()
        runComposeUiTest {
            setContent {
                ListRowSpacing.entries.forEach { spacing ->
                    val customization = ThemeCustomization(rowSpacing = spacing.factor)
                    CompositionLocalProvider(LocalThemeCustomization provides customization) {
                        results += rowPad(base) to rowSpan(base)
                    }
                }
            }
            waitForIdle()
        }
        return results.take(ListRowSpacing.entries.size)
    }

    @Test
    fun `padding shrinks by the margin's factor`() {
        val pads = measured(10.dp).map { it.first }

        assertEquals(ListRowSpacing.entries.map { 10.dp * it.factor }, pads)
    }

    @Test
    fun `a row's height gives way to the margin but never below one line of text`() {
        val spans = measured(32.dp).map { it.second }

        assertEquals(spans.sortedDescending(), spans, "each step is no taller than the last: $spans")
        assertTrue(spans.first() < 32.dp, "Normal is already tighter than the height the row is written with")
        assertTrue(spans.all { it >= 20.dp }, "every step keeps room for a line: $spans")
    }
}
