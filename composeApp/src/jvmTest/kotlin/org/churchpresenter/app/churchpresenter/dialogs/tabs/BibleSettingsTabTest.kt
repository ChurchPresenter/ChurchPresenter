package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.SCANNING_ROW_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Bible settings tab, content only: which Bibles are in the translation stack and in what
 * order, and the two flags ([BibleSettings.splitBrowseMode]/[BibleSettings.crossReferencesEnabled])
 * that decide what the live Bible tab itself offers.
 *
 * ## Reduced when styling moved to the Profiles tab
 *
 * Every styling control this suite used to drive -- margins, transitions, per-element typography
 * and colour, bilingual layout, text transforms, alignment, shadow, and the Auto-fit button --
 * moved off this tab entirely, onto the Profiles tab's editing surface
 * ([CustomizeCategoryStrip]/[CustomizeBiblePane] and friends), which has its own coverage. The
 * tests that drove those controls here were deleted rather than repaired, because the controls
 * themselves no longer exist on this composable -- not to make a red build green.
 *
 * What survives is everything about the translation stack (add, remove, reorder, swap, the name a
 * missing file reads back as, the cap) plus the two content flags.
 */
@OptIn(ExperimentalTestApi::class)
class BibleSettingsTabTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanup() = temps.forEach { it.deleteRecursively() }

    private fun tempDir(): File = Files.createTempDirectory("cp-bible-tab").toFile().also { temps.add(it) }

    /** A bible folder holding [titles] keyed by file name; a null title leaves the file untitled. */
    private fun bibleFolder(vararg files: Pair<String, String?>): File = tempDir().also { dir ->
        files.forEach { (name, title) ->
            File(dir, name).writeText(if (title != null) "##Title: $title\n" else "no header here\n")
        }
    }

    private class Harness {
        var current by mutableStateOf(AppSettings())
    }

    private fun ComposeUiTest.showTab(initial: AppSettings = AppSettings()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            MaterialTheme {
                BibleSettingsTab(
                    settings = harness.current,
                    onSettingsChange = { transform -> harness.current = transform(harness.current) },
                )
            }
        }
        awaitFolderScan()
        return harness
    }

    /**
     * Waits for the Bible folder scan to land.
     *
     * The tab reads the folder on `Dispatchers.IO` — walking it and reading a header out of every
     * module is what used to freeze the settings dialog on each open — and `waitForIdle` does not
     * cover that hop, so every assertion about what the pickers offer would otherwise race it. The
     * scanning row is on screen until the listing arrives and gone afterwards, so this ends on the
     * scan finishing rather than on a clock; with no folder configured the scan returns at once.
     */
    private fun ComposeUiTest.awaitFolderScan() {
        waitUntil {
            onAllNodesWithTag(SCANNING_ROW_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    private fun ComposeUiTest.showBibleTab(bible: BibleSettings): Harness =
        showTab(AppSettings(bibleSettings = bible))

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    fun `a pre-list settings file shows its two bibles as the translation stack`() = runComposeUiTest {
        // There is no mode to choose any more: a file written before the list existed presents its
        // primary/secondary pair as the first two translations, with no toggle offered.
        showBibleTab(BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb"))

        onAllNodesWithText("Translation 1", substring = true, ignoreCase = true).onFirst().assertExists()
        onNodeWithText("Translation mode").assertDoesNotExist()
        onNodeWithText("Dual translation").assertDoesNotExist()
    }

    // ── Bible selection ───────────────────────────────────────────────────────

    @Test
    fun `a bible the folder no longer holds is still shown by its stored name`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        showBibleTab(BibleSettings(storageDirectory = dir.path, primaryBible = "deleted.spb"))

        onAllNodesWithText("deleted.spb").onFirst()
            .assertExists("a missing bible reads back as its file name rather than vanishing")
    }

    @Test
    fun `a translation is named by the title inside its file once the folder has been read`() = runComposeUiTest {
        // The title comes from a header read per module, which now happens off the composition
        // thread — so the picker starts out labelled by file name and sharpens when the scan lands.
        val dir = bibleFolder("kjv.spb" to "King James Version", "asv.spb" to "American Standard")
        showBibleTab(BibleSettings(storageDirectory = dir.path, primaryBible = "kjv.spb"))

        onAllNodesWithText("King James Version").onFirst()
            .assertExists("the configured translation is named by its ##Title:")
        onNodeWithText("Add translation")
            .assertExists("and the other module in the folder is offered to add")
    }

    @Test
    fun `the swap button is offered only once a secondary bible is set`() = runComposeUiTest {
        showTab()

        onAllNodesWithContentDescription("Swap").assertCountEquals(0)
    }

    @Test
    fun `a secondary bible the folder no longer holds is still shown by its stored name`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        showBibleTab(BibleSettings(storageDirectory = dir.path, secondaryBible = "gone.spb"))

        onAllNodesWithText("gone.spb").onFirst()
            .assertExists("a missing secondary bible reads back as its file name")
    }

    // ── Checkboxes ────────────────────────────────────────────────────────────
    //
    // In composition order: split browse, then cross references — the only two content flags left
    // on this tab.

    private object Box {
        const val SPLIT_BROWSE = 0
        const val CROSS_REFERENCES = 1
    }

    /** Clicks checkbox [index] and returns the bible settings that produced. */
    private fun ComposeUiTest.toggle(index: Int, harness: Harness): BibleSettings {
        onAllNodes(isToggleable())[index].performScrollTo().performClick()
        waitForIdle()
        return harness.current.bibleSettings
    }

    @Test
    fun `split browse mode toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.SPLIT_BROWSE, harness)
        if (after.splitBrowseMode) onAllNodes(isToggleable())[Box.SPLIT_BROWSE].assertIsOn()
        else onAllNodes(isToggleable())[Box.SPLIT_BROWSE].assertIsOff()

        assertEquals(true, after.splitBrowseMode, "split browse starts off and turns on")
        assertEquals(before.copy(splitBrowseMode = true), after)
    }

    @Test
    fun `cross references toggles only its own flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.bibleSettings

        val after = toggle(Box.CROSS_REFERENCES, harness)
        if (after.crossReferencesEnabled) onAllNodes(isToggleable())[Box.CROSS_REFERENCES].assertIsOn()
        else onAllNodes(isToggleable())[Box.CROSS_REFERENCES].assertIsOff()

        assertEquals(false, after.crossReferencesEnabled, "cross references start on and turn off")
        assertEquals(before.copy(crossReferencesEnabled = false), after)
    }

    // ── A slot cannot be pointed at a bible another slot already holds ─────────

    @Test
    fun `a slot picker does not offer a bible another slot already holds`() = runComposeUiTest {
        // The stack is keyed by file name, so choosing a duplicate collapsed two slots into one and
        // took the other's ~50 appearance values with it — silently, with no undo. The only safe
        // answer is not to offer it, which is what the "add" picker already did.
        val dir = bibleFolder("kjv.spb" to "King James", "rst.spb" to "Synodal", "niv.spb" to "New International")
        val harness = showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(
                        BibleTranslationSettings(fileName = "kjv.spb"),
                        BibleTranslationSettings(fileName = "rst.spb"),
                    ),
                ),
            ),
        )
        waitForIdle()

        // Both names are already on the tab — each slot's own picker shows what it holds — so the
        // question is not whether they appear but whether opening this menu adds one. Counted before
        // and after for exactly that reason.
        fun shown(text: String) =
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size

        val synodalBefore = shown("Synodal")
        val unusedBefore = shown("New International")

        // The picker, not any other label: a DropdownSettingsField uppercases its label, so this
        // exact text belongs only to the picker.
        onAllNodesWithText("TRANSLATION 1").onFirst().performClick()
        waitForIdle()

        assertEquals(
            unusedBefore + 1, shown("New International"),
            "the menu must offer the bible no slot holds yet",
        )
        assertEquals(
            synodalBefore, shown("Synodal"),
            "slot 2 already holds Synodal, so slot 1 must not be offered it too",
        )
        assertEquals(
            2, harness.current.bibleSettings.translationList().size,
            "and nothing was changed by looking",
        )
    }

    // ── The stack is bounded, and its rows line up ─────────────────────────────

    /** A folder of [count] bibles, and a tab showing the first [stacked] of them as the stack. */
    private fun ComposeUiTest.showStackOf(count: Int, stacked: Int): Harness {
        val files = (1..count).map { "bible$it.spb" to "Bible $it" }
        val dir = bibleFolder(*files.toTypedArray())
        return showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    files.take(stacked).map { BibleTranslationSettings(fileName = it.first) },
                ),
            ),
        )
    }

    @Test
    fun `the add picker is withdrawn once the stack is full`() = runComposeUiTest {
        // `addTranslation` refuses past the cap, so offering the add anyway would answer a selection
        // by doing nothing at all.
        showStackOf(count = Constants.MAX_BIBLE_TRANSLATIONS + 2, stacked = Constants.MAX_BIBLE_TRANSLATIONS)
        waitForIdle()

        onAllNodesWithText("Add translation", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun `the add picker is offered while the stack has room`() = runComposeUiTest {
        showStackOf(count = Constants.MAX_BIBLE_TRANSLATIONS + 2, stacked = Constants.MAX_BIBLE_TRANSLATIONS - 1)
        waitForIdle()

        onAllNodesWithText("Add translation", substring = true).onFirst()
            .assertExists("one short of the cap there is still room, so the add must be offered")
    }

    /** The left edge of every row's delete button, top row first. */
    private fun ComposeUiTest.deleteButtonEdges(): List<Float> =
        onAllNodesWithContentDescription("Remove").fetchSemanticsNodes().map { it.boundsInRoot.left }

    @Test
    fun `every row's delete button sits at the same edge`() = runComposeUiTest {
        // The first row has no "up" and the last no "down". Without a gap standing in for the missing
        // button, delete stepped left on those two rows and the column of buttons came out ragged.
        showStackOf(count = 3, stacked = 3)
        waitForIdle()

        val lefts = deleteButtonEdges()

        assertEquals(3, lefts.size, "one delete button per translation")
        lefts.forEach { left ->
            assertEquals(
                lefts.first(), left, 0.5f,
                "every delete button must share one left edge, got $lefts",
            )
        }
    }

    @Test
    fun `two rows line up without a placeholder holding space open`() = runComposeUiTest {
        // Two rows are short of one reorder button each -- the first of "up", the second of "down" --
        // so they align on their own. Padding them anyway would only open a gap nothing can ever
        // fill, which is why the placeholder starts at three rows.
        showStackOf(count = 3, stacked = 2)
        waitForIdle()

        val lefts = deleteButtonEdges()
        assertEquals(2, lefts.size, "one delete button per translation")
        assertEquals(lefts.first(), lefts.last(), 0.5f, "two rows must already share one edge: $lefts")

        // Alignment alone cannot see a placeholder — padding both rows keeps them aligned, just
        // wider. What it does change is the second row, where the missing button is the last one:
        // its delete would sit a whole button further from "up" instead of the row's 6dp spacing.
        val up = onNodeWithContentDescription("Move translation up").fetchSemanticsNode().boundsInRoot
        val gap = lefts.last() - up.right
        assertEquals(
            true, gap < 34f,
            "delete must follow the reorder button directly, not across a button-sized gap: $gap",
        )
    }
}
