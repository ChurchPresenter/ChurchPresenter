@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibletab.SECOND_MODULE
import org.churchpresenter.bibletab.bibleFixture
import org.churchpresenter.bibletab.bibleSearch
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals

private const val VERSE_ONE = "1. In the beginning God created the heaven and the earth."
private const val VERSE_TWO = "2. And the earth was without form, and void."
private const val VERSE_THREE = "3. And God said, Let there be light."

class BibleHappyPathTest : HappyPathHarness() {

    private val secondTranslation = SpbFixture.buildContent(
        title = "Second Bible",
        books = listOf(SpbFixture.Book(1, "Genesis", 2)),
        verses = listOf(SpbFixture.Verse(1, 1, 1, "At the first God made the heaven and the earth.")),
    )

    private fun library(bibleSettings: BibleSettings = BibleSettings()): AppSettings {
        bible("test.spb", bibleFixture)
        return settings(Tabs.BIBLE, bibleFile = "test.spb", bibleSettings = bibleSettings)
    }

    private fun ComposeUiTest.awaitVerses(app: HappyApp, vararg numbers: Int) {
        await("verses ${numbers.toList()} are on air") {
            app.presenter.slideContent.value == Presenting.BIBLE &&
                app.presenter.selectedVerses.value.map { it.verseNumber } == numbers.toList()
        }
    }

    private fun ComposeUiTest.goLiveWith(line: String) {
        clickText(line)
        goLive()
    }

    @Test
    fun `a verse picked in the browser goes live`() = happyPath(library()) { app ->
        goLiveWith(VERSE_ONE)

        awaitVerses(app, 1)
        awaitOnOutput(app, "In the beginning God created the heaven and the earth.")
    }

    @Test
    fun `a typed reference goes live`() = happyPath(library()) { app ->
        bibleSearch("John 3:16")
        goLiveWith("16. For God so loved the world.")

        await("John 3:16 is on air") {
            val verse = app.presenter.selectedVerses.value.singleOrNull()
            verse?.bookName == "John" && verse.verseNumber == 16
        }
        awaitOnOutput(app, "For God so loved the world.")
    }

    @Test
    fun `the live verse steps forward and back`() = happyPath(library()) { app ->
        goLiveWith(VERSE_ONE)
        awaitVerses(app, 1)

        press(Key.DirectionDown)
        awaitVerses(app, 2)
        awaitOnOutput(app, "And the earth was without form, and void.")

        press(Key.DirectionUp)
        awaitVerses(app, 1)
        awaitOnOutput(app, "In the beginning God created the heaven and the earth.")
    }

    @Test
    fun `a verse found by a word search goes live`() = happyPath(library()) { app ->
        bibleSearch("shepherd")
        clickTabControl("Search")
        clickText("Psalms 23:1 The LORD is my shepherd; I shall not want.")
        goLiveWith("1. The LORD is my shepherd; I shall not want.")

        await("Psalm 23:1 is on air") {
            app.presenter.selectedVerses.value.singleOrNull()?.bookName == "Psalms"
        }
        awaitOnOutput(app, "The LORD is my shepherd; I shall not want.")
    }

    @Test
    fun `a verse from history goes back on air`() = happyPath(library()) { app ->
        goLiveWith(VERSE_ONE)
        awaitVerses(app, 1)
        goLiveWith(VERSE_THREE)
        awaitVerses(app, 3)

        onAllNodes(hasText("Genesis 1:1  ", substring = true))[0].performMouseInput { doubleClick() }
        waitForIdle()

        awaitVerses(app, 1)
        awaitOnOutput(app, "In the beginning God created the heaven and the earth.")
    }

    @Test
    fun `a cross reference opens its passage`() =
        happyPath(library(BibleSettings(crossReferencesPanel = true))) { _ ->
            clickText(VERSE_ONE)
            await("the panel lists John 1:1-3") {
                onAllNodes(hasText("John 1:1-3")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }

            clickText("John 1:1-3")

            await("the browser is on John 1") {
                onAllNodes(hasText("1. In the beginning was the Word."))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        }

    @Test
    fun `a verse added to the schedule becomes a row`() = happyPath(library()) { app ->
        clickText(VERSE_TWO)
        clickTabControl("Add to Schedule")

        await("the schedule holds Genesis 1:2") { scheduleShows("Genesis 1:2") }
        assertEquals(1, app.schedule().size)
    }

    @Test
    fun `two translations go live together`() {
        bible("test.spb", bibleFixture)
        bible(SECOND_MODULE, secondTranslation)
        happyPath(settings(Tabs.BIBLE, bibleFile = "test.spb", secondaryBibleFile = SECOND_MODULE)) { app ->
            goLiveWith(VERSE_ONE)

            awaitOnOutput(app, "In the beginning God created the heaven and the earth.")
            awaitOnOutput(app, "At the first God made the heaven and the earth.")
        }
    }
}
