@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleTabPassageStatisticsTest {

    private val recorded = mutableListOf<String>()
    private val statistics = BibleVerseStatistics { _, book, chapter, verse -> recorded += "$book $chapter:$verse" }

    @Test
    fun `a passage going live counts every verse in it, and the hook fires once`() {
        var wentLive = 0
        bibleTab(statistics = statistics, onVerseWentLive = { wentLive++ }) { vm, _ ->
            runOnIdle { vm.ctrlClickVerse(1) }
            waitForIdle()

            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(listOf("Genesis 1:1", "Genesis 1:2"), recorded)
            assertEquals(1, wentLive)
        }
    }

    @Test
    fun `a single verse going live is counted once`() = bibleTab(statistics = statistics) { _, _ ->
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf("Genesis 1:1"), recorded)
    }
}
