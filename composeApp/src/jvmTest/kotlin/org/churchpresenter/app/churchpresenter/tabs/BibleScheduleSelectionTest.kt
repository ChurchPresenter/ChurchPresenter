@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Bible item clicked in the schedule, while the Bible is still loading.
 *
 * This is the cold-start report: open the app, open a saved service, click its Bible item — the
 * output says Bible is live and shows nothing, and clicking the item again does nothing either.
 * Selecting any verse by hand first "initialises" it, after which the item works.
 *
 * Two separate faults produced that, and both are asserted here:
 *
 * - The reference was resolved against a Bible that was not loaded yet. `loadBibles` publishes a
 *   books-only module first and the full text second, so a lookup landing in that window finds no
 *   book (or a book whose chapters are empty) and quietly resolves to nothing.
 * - Clicking the same item twice changed nothing the effect was keyed on, so the second click could
 *   not recover from the first.
 *
 * **Why the shared `bibleTab` harness cannot cover this.** It builds the view model on
 * `Dispatchers.Unconfined`, which loads the Bible synchronously — before the tab is ever composed,
 * so the window this test is about does not exist there. The load is held shut here instead, with a
 * dispatcher that queues rather than runs, and opened once the tab is up and waiting.
 */
class BibleScheduleSelectionTest {

    /**
     * A dispatcher that runs nothing until [release], then runs everything.
     *
     * Standing in for a slow disk: the view model's load is queued at construction and stays queued
     * while the tab composes and asks for its verse. [release] drains in a loop because the work
     * re-dispatches into this same queue every time it comes back from the IO dispatcher.
     */
    private class GatedDispatcher : CoroutineDispatcher() {
        private val queued = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(queued) { queued.addLast(block) }
        }

        fun release() {
            while (true) {
                val next = synchronized(queued) { queued.removeFirstOrNull() } ?: return
                next.run()
            }
        }
    }

    private lateinit var dir: File

    @BeforeTest
    fun createModule() {
        dir = Files.createTempDirectory("cp-bible-schedule").toFile()
        SpbFixture.spbFile(dir, content = bibleFixture)
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun settings() = AppSettings(
        bibleSettings = BibleSettings(
            storageDirectory = dir.absolutePath,
            primaryBible = "test.spb",
        )
    )

    private val johnThreeSixteen = ScheduleItem.BibleVerseItem(
        id = "item-1",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "For God so loved the world.",
        bookId = 43,
    )

    /**
     * Composes the tab with [item] already selected, holding the Bible load shut until the test
     * calls the returned release. [block] gets what the tab pushed to the presenter.
     */
    private fun scheduleClick(
        item: ScheduleItem.BibleVerseItem,
        block: ComposeUiTest.(
            vm: BibleViewModel,
            pushed: List<List<SelectedVerse>>,
            release: () -> Unit,
            clickAgain: () -> Unit,
        ) -> Unit,
    ) {
        val gate = GatedDispatcher()
        val vm = BibleViewModel(settings(), dispatcher = gate, ioDispatcher = Dispatchers.Unconfined)
        val pushed = mutableListOf<List<SelectedVerse>>()
        val version = mutableStateOf(0)
        runComposeUiTest {
            setContent {
                val currentVersion by version
                MaterialTheme {
                    BibleTab(
                        viewModel = vm,
                        appSettings = settings(),
                        selectedVerseItem = item,
                        selectedVerseItemVersion = currentVersion,
                        onVerseSelected = { pushed += it },
                        crossReferences = noCrossReferences(),
                    )
                }
            }
            block(vm, pushed, { gate.release() }, { version.value++ })
        }
    }

    @Test
    fun `a verse clicked while the Bible is still loading reaches the output once it loads`() {
        scheduleClick(johnThreeSixteen) { _, pushed, release, _ ->
            waitForIdle()
            assertTrue(pushed.isEmpty(), "nothing can be shown before the Bible is read: $pushed")

            release()
            waitUntil("the verse reached the output") { pushed.isNotEmpty() }

            val verse = pushed.last().single()
            assertEquals("John", verse.bookName)
            assertEquals(3, verse.chapter)
            assertEquals(16, verse.verseNumber)
            assertTrue(verse.verseText.contains("loved the world"), "the text came from the module")
        }
    }

    @Test
    fun `clicking the same item again puts it back on the output`() {
        scheduleClick(johnThreeSixteen) { _, pushed, release, clickAgain ->
            release()
            waitUntil("the first click reached the output") { pushed.isNotEmpty() }
            val first = pushed.size

            clickAgain()
            waitUntil("the second click reached the output too") { pushed.size > first }

            assertEquals(16, pushed.last().single().verseNumber)
        }
    }

    @Test
    fun `a reference this Bible does not have puts nothing of its own on the output`() {
        val notInModule = johnThreeSixteen.copy(bookName = "Habakkuk", bookId = 35, chapter = 3, verseNumber = 2)

        scheduleClick(notInModule) { vm, pushed, release, _ ->
            release()
            // The positive signal is the load finishing — the reference is resolved the moment it
            // does, so there is no window left in which the item could still land on the output.
            waitUntil("the Bible finished loading") { vm.isFullyLoaded }
            waitForIdle()

            // The tab still offers its default browse selection while nothing is live, which is
            // its own behaviour and not this item; what must not happen is the item resolving to
            // some other book's verse and going out under the reference the operator clicked.
            assertTrue(
                pushed.flatten().none { it.bookName == "Habakkuk" },
                "the module has no Habakkuk: $pushed",
            )
            assertEquals(0, vm.selectedBookIndex.value, "the browse selection was left where it was")
            assertEquals(1, vm.selectedChapter.value, "and not moved to the chapter that was asked for")
        }
    }
}
