@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
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

    /**
     * The column's row for [reference].
     *
     * A row is one text node holding the reference *and* the start of its verse, so it cannot be
     * matched exactly — but a plain substring match is too loose: the search field's placeholder
     * reads "Reference or text — e.g. John 3:16, mat 1, or a word" and would match too. So this
     * anchors on the row's own shape: the reference alone, or the reference followed by the
     * two-space separator the preview sits behind.
     */
    private fun ComposeUiTest.crossRefRow(reference: String) = onNode(
        SemanticsMatcher("cross-reference row for $reference") { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            text == reference || text?.startsWith("$reference  ") == true
        }
    )

    // ── Showing and hiding ────────────────────────────────────────────────────

    @Test
    fun `the column is absent until the setting is on`() =
        bibleTab(crossReferences = references()) { _, _ ->
            assertFalse(showsExactly(BibleLabel.CROSS_REFS), "off by default")
        }

    /**
     * The tab opens on Genesis 1:1 and the column should already describe it, with nothing clicked.
     *
     * This passes either way under `Dispatchers.Unconfined`, where the module is loaded before
     * anything composes — it is a guard on the opening state rather than a reproduction. The bug it
     * was written for turned out to share a cause with the swap above (the column watching the book
     * list, which does not change when the fully parsed module replaces the books-only one), and
     * `swapping translations re-resolves the labels and previews` is the test with teeth for it.
     */
    @Test
    fun `the column is filled in for the verse the tab opens on`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            assertTrue(showsContainingText("John 3:16  For God so loved the world."))
        }

    @Test
    fun `the column lists what the selected verse points at`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.CROSS_REFS), "the column header is drawn")
            // "John"/"Psa" are what the module's own book names shorten to — not the app's
            // abbreviation resources, which would follow the UI language instead of the module's.
            assertTrue(showsContainingText("John 3:16  For God so loved the world."))
            assertTrue(showsContainingText("Psa 23:1  The LORD is my shepherd"))
        }

    @Test
    fun `a reference to a run of verses is labelled as a range`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            onNodeWithText("2. And the earth was without form, and void.").performClick()
            waitForIdle()

            // The preview is the range's first verse — the one the label starts at.
            assertTrue(showsContainingText("Psa 23:1-3  The LORD is my shepherd"))
        }

    @Test
    fun `a verse with no references says so rather than collapsing`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { _, _ ->
            onNodeWithText("3. And God said, Let there be light.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.CROSS_REFS_EMPTY))
            assertTrue(showsExactly(BibleLabel.CROSS_REFS), "the column keeps its place in the layout")
        }

    // ── Following the loaded module ───────────────────────────────────────────

    /** The same books and verses as the shared fixture, worded differently. */
    private val otherTranslation = SpbFixture.buildContent(
        title = "Second Bible",
        books = listOf(
            SpbFixture.Book(1, "Genesis", 2),
            SpbFixture.Book(19, "Psalms", 23),
            SpbFixture.Book(43, "John", 3),
        ),
        verses = listOf(
            SpbFixture.Verse(1, 1, 1, "At the first God made the heaven and the earth."),
            SpbFixture.Verse(1, 1, 2, "And the earth was waste and without form."),
            SpbFixture.Verse(19, 23, 1, "The Lord takes care of me as his sheep."),
            SpbFixture.Verse(43, 3, 16, "For God had such love for the world."),
        ),
    )

    @Test
    fun `swapping translations re-resolves the labels and previews`() =
        bibleTab(
            secondContent = otherTranslation,
            settings = ::withPanel,
            crossReferences = references(),
        ) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            assertTrue(showsContainingText("John 3:16  For God so loved the world."))

            actionButton(BibleLabel.SWAP).performClick()
            waitForIdle()

            // The column resolves against whatever module is loaded, so the preview has to be the
            // new one's wording. This is where it used to stop: loadBibles publishes a books-only
            // Bible first, every reference resolves to null against it, and the effect watched the
            // book list — which is equal across both phases — so the half-resolved rows stayed up
            // until something else happened to re-key it.
            assertTrue(
                showsContainingText("John 3:16  For God had such love for the world."),
                "the preview follows the module that is now loaded",
            )
        }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Test
    fun `clicking a reference goes there without putting it on screen`() =
        bibleTab(settings = ::withPanel, crossReferences = references()) { vm, reports ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            crossRefRow("John 3:16").performClick()
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
            // this column's abbreviated "Psa 23:1" is not a substring of. "John 3:16" would match
            // both the column's row and the history row it creates.
            val reference = crossRefRow("Psa 23:1")
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
            crossRefRow("John 3:16").performClick()
            waitForIdle()

            assertEquals(2, vm.selectedBookIndex.value, "it navigated")
            assertTrue(
                showsContainingText("Psa 23:1  "),
                "the column still describes Genesis 1:1, so the other reference is still reachable",
            )

            // Selecting in the verse list is a new starting point, so the column follows again —
            // even though this is the very verse the column just sent us to.
            onNodeWithText("16. For God so loved the world.").performClick()
            waitForIdle()

            assertTrue(showsExactly(BibleLabel.CROSS_REFS_EMPTY), "John 3:16 has no references here")
        }

    @Test
    fun `a reference this module does not carry is shown but inert`() {
        // Habakkuk (canonical 35) is not in the fixture, as it is not in an NT-only module.
        val references = CrossReferenceRepository {
            """{"v":1,"r":{"001001001":"035003002 043003016"}}""".toByteArray()
        }

        bibleTab(settings = ::withPanel, crossReferences = references) { vm, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            // Labelled from the app's own abbreviations, since the module cannot name it, and with
            // no preview — there is no verse text to preview.
            assertTrue(showsExactly("Hab 3:2"), "the reference is still listed")

            crossRefRow("Hab 3:2").performClick()
            waitForIdle()
            assertEquals(0, vm.selectedBookIndex.value, "clicking it must not move the selection")
            assertEquals(1, vm.selectedChapter.value)

            // The reference beside it, which the module does have, still works.
            crossRefRow("John 3:16").performClick()
            waitForIdle()
            assertEquals(2, vm.selectedBookIndex.value)
        }
    }

    // ── Reading a passage ─────────────────────────────────────────────────────

    /**
     * Genesis 1:1, 1:2 and 1:3 all point into Psalm 23; only 1:1 points into John 3.
     *
     * So reading the three should rank Psalms above John, which is the whole point of aggregating:
     * what the passage keeps returning to beats what one verse mentioned once.
     */
    private fun passageReferences() = CrossReferenceRepository {
        """{"v":1,"r":{
             "001001001":"019023001 043003016",
             "001001002":"019023002",
             "001001003":"019023003"
           }}""".toByteArray()
    }

    /** A module containing every verse those references point at, so none render as unavailable. */
    private val passageModule = SpbFixture.buildContent(
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
            SpbFixture.Verse(19, 23, 2, "He maketh me to lie down in green pastures."),
            SpbFixture.Verse(19, 23, 3, "He restoreth my soul."),
            SpbFixture.Verse(43, 3, 16, "For God so loved the world."),
        ),
    )

    /**
     * Selects [verseLine] in the verse list and takes it live.
     *
     * The browser's copy specifically: in split browse mode the same line is also in the live
     * panel, and `livePanelVerse` exists for that one. Leftmost is the browser.
     */
    private fun ComposeUiTest.goLiveOn(verseLine: String) {
        val nodes = onAllNodesWithText(verseLine).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val leftmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.left }
            ?: error("no verse line reading \"$verseLine\" is on screen")
        onAllNodesWithText(verseLine)[leftmost].performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
    }

    @Test
    fun `reading a run of verses pools their references`() =
        bibleTab(content = passageModule, settings = ::withPanel, crossReferences = passageReferences()) { _, _ ->
            goLiveOn("1. In the beginning God created the heaven and the earth.")
            // One verse read: still that verse's own references.
            assertFalse(showsContainingText("Passage"), "one verse is not yet a passage")

            goLiveOn("2. And the earth was without form, and void.")
            goLiveOn("3. And God said, Let there be light.")

            assertTrue(showsExactly("Passage 1:1-3"), "the header names what was read")
            // Psalm 23 is pointed at by all three; John 3 by only the first.
            assertTrue(showsContainingText("Psa 23:1-3"), "scattered targets collapse into a span")
            assertEquals(listOf("×3", "×1"), renderedText().filter { it.startsWith("×") })
        }

    @Test
    fun `moving to another book starts a new passage`() =
        bibleTab(content = passageModule, settings = ::withPanel, crossReferences = passageReferences()) { _, _ ->
            goLiveOn("1. In the beginning God created the heaven and the earth.")
            goLiveOn("2. And the earth was without form, and void.")
            assertTrue(showsExactly("Passage 1:1-2"))

            // Jump to John: the reading has moved on, so the pooled list goes with it.
            onNodeWithText("John").performClick()
            waitForIdle()
            onNodeWithText("3").performClick()
            waitForIdle()
            goLiveOn("16. For God so loved the world.")

            assertFalse(showsContainingText("Passage"), "a new reading is one verse long")
        }

    @Test
    fun `browsing ahead shows that verse without discarding the reading`() =
        bibleTab(content = passageModule, settings = ::withPanel, crossReferences = passageReferences()) { _, _ ->
            goLiveOn("1. In the beginning God created the heaven and the earth.")
            goLiveOn("2. And the earth was without form, and void.")
            assertTrue(showsExactly("Passage 1:1-2"))

            // Looking ahead is not reading: the column describes that verse alone...
            onNodeWithText("3. And God said, Let there be light.").performClick()
            waitForIdle()
            assertFalse(showsContainingText("Passage"))
            assertTrue(showsContainingText("Psa 23:3  He restoreth my soul."), "verse 3's own reference")

            // ...and taking it live continues the passage rather than starting over.
            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()
            assertTrue(showsExactly("Passage 1:1-3"))
        }

    // ── Split browse mode ─────────────────────────────────────────────────────

    private fun withSplitPanel(settings: AppSettings) = settings.copy(
        bibleSettings = settings.bibleSettings.copy(
            crossReferencesPanel = true, splitBrowseMode = true,
        ),
    )

    @Test
    fun `the column follows a click in the live panel, not the browser`() =
        bibleTab(content = passageModule, settings = ::withSplitPanel, crossReferences = passageReferences()) { vm, _ ->
            // Move the browser to John while the live panel still holds Genesis. This is the state
            // where reading the browse selection names the wrong book entirely.
            goLiveOn("1. In the beginning God created the heaven and the earth.")
            onNodeWithText("John").performClick()
            waitForIdle()
            assertEquals(2, vm.selectedBookIndex.value, "the browser moved to John")

            livePanelVerse("3. And God said, Let there be light.").performClick()
            waitForIdle()

            // Genesis 1:1 and 1:3 pooled — the live panel's book. Had it followed the browser it
            // would be describing John 1:1, which this fixture gives no references at all.
            assertTrue(showsExactly("Passage 1:1-3"))
            assertTrue(showsContainingText("Psa 23:1-3"))
            assertFalse(showsExactly(BibleLabel.CROSS_REFS_EMPTY))
        }

    @Test
    fun `a live panel click continues the passage being read`() =
        bibleTab(content = passageModule, settings = ::withSplitPanel, crossReferences = passageReferences()) { _, _ ->
            goLiveOn("1. In the beginning God created the heaven and the earth.")
            livePanelVerse("2. And the earth was without form, and void.").performClick()
            waitForIdle()

            assertTrue(showsExactly("Passage 1:1-2"), "clicking in the live panel is reading on")
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
            assertEquals(1, renderedText().count { it.startsWith("Psa 23:1  ") }, "no duplicate row")
        }
    }

    @Test
    fun `nothing learned means no label and no gap`() =
        bibleTab(settings = ::withPanel, crossReferences = references(), sequenceLog = sequenceLog()) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()

            assertFalse(showsExactly(BibleLabel.OFTEN_NEXT), "a fresh install shows only references")
            assertTrue(showsContainingText("John 3:16  "))
        }

    @Test
    fun `going live from the column is learned like any other go-live`() {
        val log = sequenceLog()

        bibleTab(settings = ::withPanel, crossReferences = references(), sequenceLog = log) { _, _ ->
            onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
            waitForIdle()
            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()

            val reference = crossRefRow("Psa 23:1")
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
