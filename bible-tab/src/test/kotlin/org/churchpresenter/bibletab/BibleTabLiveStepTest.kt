@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BibleTabLiveStepTest {

    private val recorded = mutableListOf<String>()
    private val statistics = BibleVerseStatistics { _, book, chapter, verse -> recorded += "$book $chapter:$verse" }
    private val sent = mutableListOf<SelectedVerse>()
    private val holds = mutableListOf<Boolean>()

    private fun split(app: AppSettings) = app.copy(bibleSettings = app.bibleSettings.copy(splitBrowseMode = true))

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private val longVerse = (1..80).joinToString(" ") { "word$it" }

    private val esther = SpbFixture.buildContent(
        title = "Split Bible",
        books = listOf(SpbFixture.Book(17, "Esther", 1)),
        verses = listOf(
            SpbFixture.Verse(17, 1, 1, "Short verse one"),
            SpbFixture.Verse(17, 1, 2, longVerse),
            SpbFixture.Verse(17, 1, 3, "Short verse three"),
        ),
    )

    @Test
    fun `stepping down in the live panel counts, mirrors and releases hold`() {
        val output = FakeBibleOutput().apply { setBibleHold(true) }
        bibleTab(
            settings = ::split,
            presenter = output,
            statistics = statistics,
            onInstanceLinkSendVerse = { sent += it },
            onInstanceLinkSendBibleHold = { holds += it },
        ) { _, reports ->
            press(Key.DirectionDown)

            assertEquals(listOf("Genesis 1:2"), recorded)
            assertEquals(2, sent.single().verseNumber)
            assertFalse(output.bibleHold.value, "a verse sent from the live panel lifts hold")
            assertEquals(listOf(false), holds)
            assertEquals(listOf(Presenting.BIBLE), reports.presenting)
        }
    }

    @Test
    fun `stepping onto the second half of a long verse does not count the verse again`() =
        bibleTab(
            content = esther,
            settings = { app ->
                app.withBibleEverywhere(app.bibleSettings.copy(splitBrowseMode = true, splitLongVerses = true))
            },
            statistics = statistics,
        ) { _, reports ->
            press(Key.DirectionDown)
            press(Key.DirectionDown)

            assertEquals(listOf("Esther 1:2"), recorded, "both halves are one verse in the report")
            assertEquals(listOf(2, 2), reports.selectedVerses.takeLast(2).map { it.first().verseNumber })
        }

    @Test
    fun `clicking the live panel counts, mirrors and releases hold`() {
        val output = FakeBibleOutput().apply { setBibleHold(true) }
        bibleTab(
            settings = ::split,
            presenter = output,
            statistics = statistics,
            onInstanceLinkSendVerse = { sent += it },
            onInstanceLinkSendBibleHold = { holds += it },
        ) { _, _ ->
            livePanelVerse("3. And God said, Let there be light.").performClick()
            waitForIdle()

            assertEquals(listOf("Genesis 1:3"), recorded)
            assertEquals(3, sent.single().verseNumber)
            assertFalse(output.bibleHold.value)
            assertEquals(listOf(false), holds)
        }
    }
}
