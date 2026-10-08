@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.statistics

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher

/*
 * Reading what a report body rendered, for the `CCLIReportDialog` test classes. The harness, the
 * fixtures and the labels are in `CCLIReportDialogTestSupport.kt`.
 */

/** The left-hand chart column is 300.dp wide; everything right of it is the table. */
private const val CHART_PANEL_WIDTH = 300f

// ── Reading what was rendered ───────────────────────────────────────────────────────────────────

private fun ComposeUiTest.textNodes() =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map {
            it.boundsInRoot to (it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } ?: "")
        }

/** Every string on screen, in traversal order. */
fun ComposeUiTest.renderedText(): List<String> = textNodes().map { it.second }

/**
 * The ranked labels in the left-hand chart, top to bottom.
 *
 * The chart and the table both render the same titles, so they are told apart by position: the chart
 * occupies a fixed-width column on the left. Each chart row publishes its label and its value as two
 * nodes side by side; only the label — the leftmost of the pair — is returned.
 */
fun ComposeUiTest.chartLabels(): List<String> =
    textNodes()
        .filter { it.first.left < CHART_PANEL_WIDTH }
        .groupBy { it.first.top }
        .toSortedMap()
        .values
        .mapNotNull { band -> band.minByOrNull { it.first.left }?.second }

/**
 * The chart's ranked rows as `label to value`, top to bottom.
 *
 * A row publishes its label and its total as two nodes on the same horizontal band, so a band
 * carrying exactly two is a row; the heading and its subtitle sit alone on theirs and drop out.
 */
fun ComposeUiTest.chartRows(): List<Pair<String, String>> =
    textNodes()
        .filter { it.first.left < CHART_PANEL_WIDTH }
        .groupBy { it.first.top }
        .toSortedMap()
        .values
        .mapNotNull { band ->
            val cells = band.sortedBy { it.first.left }
            if (cells.size == 2) cells[0].second to cells[1].second else null
        }

/** The strings rendered in the table, right of the chart column. */
fun ComposeUiTest.tableText(): List<String> =
    textNodes().filter { it.first.left >= CHART_PANEL_WIDTH }.map { it.second }
