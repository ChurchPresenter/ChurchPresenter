@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cross-reference column beside the verse list.
 *
 * It holds two kinds of row in one list — what the passage points at (the bundled TSK data) and
 * what this operator usually shows next (their own go-lives) — and the interaction is the History
 * panel's: click to go there, double-click to put it on screen.
 *
 * The repository is a fixture rather than the real 3 MB dataset, so these assert on the panel
 * rather than on what TSK happens to say about Genesis 1. The fixture points Genesis 1:1 at John
 * 3:16 and Psalm 23:1, both of which the shared Bible fixture contains, so a click has somewhere
 * real to land.
 *
 * See `BibleTabTestSupport.kt` for the harness.
 */
class BibleTabCrossReferenceTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("cp-cross-refs").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Genesis 1:1 → John 3:16 and Psalm 23:1; Genesis 1:2 → a range; Genesis 1:3 → nothing. */
    private fun references() = CrossReferenceRepository {
        """{"v":1,"r":{
             "001001001":"043003016 019023001",
             "001001002":"019023001-003"
           }}""".toByteArray()
    }

    /**
     * The shared fixture, but with John 3 running 15-17 instead of holding verse 16 alone.
     *
     * `initialPassCombinedClickable` keeps its double-click timer per node, and a `LazyColumn`
     * recycles a node when the list it holds is replaced — as it is when navigating from Genesis 1
     * to John 3. With John 3:16 as the only verse it lands on the node Genesis 1:1 was clicked on
     * moments earlier, and the test's own clicks are well inside the 300ms window, so the click
     * registers as a double-click and goes live instead of selecting. Three verses put it on a
     * different row index and a different node. Real use is nowhere near that fast.
     */
    private val johnChapterOfThree = SpbFixture.buildContent(
        title = "Test Bible",
        books = listOf(
            SpbFixture.Book(1, "Genesis", 2),
            SpbFixture.Book(19, "Psalms", 23),
            SpbFixture.Book(43, "John", 3),
        ),
        verses = listOf(
            SpbFixture.Verse(1, 1, 1, "In the beginning God created the heaven and the earth."),
            SpbFixture.Verse(1, 1, 2, "And the earth was without form, and void."),
            SpbFixture.Verse(1, 1, 3, "And God said, Let there be light."),
            SpbFixture.Verse(19, 23, 1, "The LORD is my shepherd; I shall not want."),
            SpbFixture.Verse(43, 3, 15, "That whosoever believeth in him should not perish."),
            SpbFixture.Verse(43, 3, 16, "For God so loved the world."),
            SpbFixture.Verse(43, 3, 17, "For God sent not his Son to condemn the world."),
        ),
    )

    private fun sequenceLog(now: Long = FIXED_NOW) =
        VerseSequenceLog(File(tempDir, "verse_sequences.json")) { now }

    private fun withPanel(settings: AppSettings) =
        settings.copy(bibleSettings = settings.bibleSettings.copy(crossReferencesPanel = true))

    // ── Showing and hiding ────────────────────────────────────────────────────

    @Test
    fun `the column is absent until the setting is on`() =
        bibleTab(crossReferences = references()) { _, _ ->
            assertFalse(showsExactly(BibleLabel.CROSS_REFS), "off by default")
        }

    @Test
    fun `the column lists what the selected verse points at`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.CROSS_REFS), "the column header is drawn")
            assertTrue(showsExactly("John 3:16"))
            assertTrue(showsExactly("Ps 23:1"), "book names are abbreviated to fit the column")
        }

    @Test
    fun `a reference to a run of verses is labelled as a range`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            onNodeWithText("2. And the earth was without form, and void.").performClick()
            waitForIdle()

            assertTrue(showsExactly("Ps 23:1-3"))
        }

    @Test
    fun `a verse with no references says so rather than collapsing`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            onNodeWithText("3. And God said, Let there be light.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.CROSS_REFS_EMPTY))
            assertTrue(showsExactly(BibleLabel.CROSS_REFS), "the column keeps its place in the layout")
        }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Test
    fun `clicking a reference goes there without putting it on screen`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { vm, reports ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            onNodeWithText("John 3:16").performClick()
            waitForIdle()

            assertEquals(2, vm.selectedBookIndex.value, "John is the third book of the fixture")
            assertEquals(3, vm.selectedChapter.value)
            assertTrue(vm.history.isEmpty(), "a single click is navigation, not a go-live")
            assertFalse(reports.presenting.contains(Presenting.BIBLE))
        }

    @Test
    fun `double-clicking a reference puts it on screen`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { vm, reports ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            // Psalms rather than John: going live adds a history row reading "Psalms 23:1", which
            // does not collide with this column's abbreviated "Ps 23:1" the way "John 3:16" would.
            val reference = onNodeWithText("Ps 23:1")
            reference.performClick()
            reference.performClick()
            waitForIdle()

            assertEquals(listOf("Psalms 23:1"), vm.history.map { it.displayText })
            assertEquals("The LORD is my shepherd; I shall not want.", reports.live?.single()?.verseText)
            assertTrue(reports.presenting.contains(Presenting.BIBLE))
        }

    @Test
    fun `the column stays put while its references are explored`() =
        bibleTab(content = johnChapterOfThree, settings = ::withPanel, crossReferences = references()) { vm, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            onNodeWithText("John 3:16").performClick()
            waitForIdle()

            assertEquals(2, vm.selectedBookIndex.value, "it navigated")
            assertTrue(
                showsExactly("Ps 23:1"),
                "the column still describes Genesis 1:1, so the other reference is still reachable",
            )

            // Selecting in the verse list is a new starting point, so the column follows again —
            // even though this is the very verse the column just sent us to.
            onNodeWithText("16. For God so loved the world.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.CROSS_REFS_EMPTY), "John 3:16 has no references here")
        }

    // ── Learned suggestions ───────────────────────────────────────────────────

    @Test
    fun `what the operator usually shows next is listed above the bundled references`() {
        val log = sequenceLog()
        // Two services in which Genesis 1:1 was followed by Psalm 23:1.
        repeat(2) {
            log.recordGoLive(1, 1, 1)
            log.recordGoLive(19, 23, 1)
        }

        bibleTab(settings = ::withPanel, crossReferences = references(), sequenceLog = log) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.OFTEN_NEXT), "the learned rows are labelled")
            // Psalm 23:1 is both learned and a bundled reference; it appears once, as the habit.
            assertEquals(1, renderedText().count { it == "Ps 23:1" }, "no duplicate row")
        }
    }

    @Test
    fun `nothing learned means no label and no gap`() =
        bibleTab(settings = ::withPanel, crossReferences = references(), sequenceLog = sequenceLog()) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            assertFalse(showsExactly(BibleLabel.OFTEN_NEXT), "a fresh install shows only references")
            assertTrue(showsExactly("John 3:16"))
        }

    @Test
    fun `going live from the column is learned like any other go-live`() {
        val log = sequenceLog()

        bibleTab(settings = ::withPanel, crossReferences = references(), sequenceLog = log) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()

            val reference = onNodeWithText("Ps 23:1")
            reference.performClick()
            reference.performClick()
            waitForIdle()
        }

        assertEquals(
            mapOf("019023001" to 1),
            log.snapshot().pairs["001001001"],
            "a cross-reference go-live feeds the same log as every other route",
        )
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
