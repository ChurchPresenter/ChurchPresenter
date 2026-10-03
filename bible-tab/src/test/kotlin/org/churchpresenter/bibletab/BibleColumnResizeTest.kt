@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BibleColumnResizeTest {

    private val defaults = AppSettings().maximizedLayout
    private val genesisOne = "1. In the beginning God created the heaven and the earth."

    private fun ComposeUiTest.bounds(text: String, index: Int = 0): Rect =
        onAllNodesWithText(text).fetchSemanticsNodes()[index].boundsInRoot

    private fun ComposeUiTest.dragAt(x: Float, y: Float, dx: Float) {
        onRoot().performTouchInput {
            down(Offset(x, y))
            moveBy(Offset(dx / 2, 0f))
            moveBy(Offset(dx / 2, 0f))
            up()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.px(dp: Int) = with(density) { dp.dp.toPx() }

    @Test
    fun `dragging the handle after the book column widens it and saves the width`() =
        bibleTab { _, reports ->
            val label = bounds(BibleLabel.BOOK.uppercase())
            val cardLeft = label.left - px(16)
            val x = cardLeft + px(defaults.bibleColWidthBook) + px(4)

            dragAt(x, label.center.y + px(80), px(40))

            val saved = assertNotNull(reports.settingsAfterChange).maximizedLayout.bibleColWidthBook
            assertTrue(saved > defaults.bibleColWidthBook, "saved $saved, was ${defaults.bibleColWidthBook}")
        }

    @Test
    fun `dragging the handle after the chapter column widens it and saves the width`() =
        bibleTab { _, reports ->
            val label = bounds(BibleLabel.CHAPTER.uppercase())
            val x = label.center.x + px(defaults.bibleColWidthChapter) / 2 + px(4)

            dragAt(x, label.center.y + px(80), px(30))

            val saved = assertNotNull(reports.settingsAfterChange).maximizedLayout.bibleColWidthChapter
            assertTrue(saved > defaults.bibleColWidthChapter, "saved $saved, was ${defaults.bibleColWidthChapter}")
        }

    @Test
    fun `dragging the live panel's handle left widens the panel and saves it`() = bibleTab(
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(splitBrowseMode = true)) },
    ) { _, reports ->
        val live = bounds(genesisOne, index = 1)
        val x = live.left - px(8) - px(4)

        dragAt(x, live.center.y, -px(30))

        val saved = assertNotNull(reports.settingsAfterChange).maximizedLayout.splitLivePanelWidth
        assertTrue(saved != defaults.splitLivePanelWidth, "saved $saved, was ${defaults.splitLivePanelWidth}")
    }

    @Test
    fun `dragging the docked references' handle left widens the panel and saves it`() = bibleTab(
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(crossReferencesPanel = true)) },
    ) { _, reports ->
        val title = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .filter { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
                text == "Refs" || text.startsWith("Passage ")
            }
            .maxBy { it.boundsInRoot.left }
            .boundsInRoot
        val x = title.left - px(30) - px(4)

        dragAt(x, title.center.y + px(60), -px(30))

        val saved = assertNotNull(reports.settingsAfterChange).maximizedLayout.bibleColWidthCrossRef
        assertTrue(saved > defaults.bibleColWidthCrossRef, "saved $saved, was ${defaults.bibleColWidthCrossRef}")
    }
}
