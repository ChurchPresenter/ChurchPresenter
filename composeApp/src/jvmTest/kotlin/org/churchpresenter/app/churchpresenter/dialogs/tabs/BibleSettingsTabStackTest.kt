package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.SCANNING_ROW_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Swapping, clearing and promoting slots in the Bible tab's translation stack.
 *
 * ## Reduced when styling moved to the Profiles tab
 *
 * This suite used to also cover a per-translation styling panel pointed by a chip below the
 * stack -- font size, renaming, the abbreviation field. That panel moved to the Profiles tab
 * entirely and no longer exists here, so those tests were deleted along with it. What survives is
 * the stack mutation itself: picking another bible into a slot, and setting a slot to none.
 */
@OptIn(ExperimentalTestApi::class)
class BibleSettingsTabStackTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanup() = temps.forEach { it.deleteRecursively() }

    private fun tempDir(): File = Files.createTempDirectory("cp-bible-stack").toFile().also { temps.add(it) }

    private fun bibleFolder(vararg files: Pair<String, String>): File = tempDir().also { dir ->
        files.forEach { (name, title) -> File(dir, name).writeText("##Title: $title\n") }
    }

    private class Harness {
        var current by mutableStateOf(AppSettings())
    }

    private fun ComposeUiTest.showTab(initial: AppSettings): Harness {
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

    private fun ComposeUiTest.awaitFolderScan() {
        waitUntil {
            onAllNodesWithTag(SCANNING_ROW_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    private fun stackOf(dir: File, vararg fileNames: String) = AppSettings(
        bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
            fileNames.map { BibleTranslationSettings(fileName = it) },
        ),
    )

    private fun ComposeUiTest.openSlot(index: Int) {
        onAllNodesWithText("TRANSLATION $index").onFirst().performClick()
        waitForIdle()
    }

    /**
     * Opens slot [index] and picks [label] out of the menu it drops.
     *
     * Which node that is cannot be "the first one reading [label]": another node elsewhere on the
     * tab can already read the same text before the menu opens. So the nodes are counted first and
     * the one the click *added* is the one taken.
     */
    private fun ComposeUiTest.openSlotAndChoose(index: Int, label: String) {
        val before = onAllNodesWithText(label)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).map { it.id }.toSet()
        openSlot(index)
        val opened = onAllNodesWithText(label).fetchSemanticsNodes().indexOfFirst { it.id !in before }
        assertTrue(opened >= 0, "opening slot $index must offer \"$label\"")
        onAllNodesWithText(label)[opened].performClick()
        waitForIdle()
    }

    private fun files(harness: Harness) = harness.current.bibleSettings.translationList().map { it.fileName }

    @Test
    fun `picking another bible in a slot swaps it rather than adding one`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "niv.spb" to "New International")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        openSlotAndChoose(1, "New International")

        assertEquals(listOf("niv.spb"), files(harness), "the stack keeps its size when a slot is repointed")
    }

    @Test
    fun `picking another bible leaves the other slots alone`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal", "niv.spb" to "New International")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        openSlotAndChoose(1, "New International")

        assertEquals(listOf("niv.spb", "syn.spb"), files(harness))
    }

    @Test
    fun `setting the second slot to none takes that translation out of the stack`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        openSlotAndChoose(2, "None")

        assertEquals(listOf("kjv.spb"), files(harness), "None removes the slot, it does not blank it")
    }

    @Test
    fun `setting the first slot to none promotes the one behind it`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        openSlotAndChoose(1, "None")

        assertEquals(listOf("syn.spb"), files(harness))
    }

    // ── Adding, reordering and removing ─────────────────────────────────────────────────────────

    /**
     * The picker below the stack, which appends rather than repointing a slot.
     *
     * It is the only control that grows the stack, and it is deliberately absent in two states
     * -- nothing left in the folder to add, and the stack already at its cap -- because
     * `addTranslation` refuses past the cap and a picker that answers a choice by doing nothing is
     * worse than no picker at all.
     */
    @Test
    fun `the Add translation picker appends to the stack`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "niv.spb" to "New International")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        chooseFromMenu("Add translation", "New International")

        assertEquals(listOf("kjv.spb", "niv.spb"), files(harness), "it adds a slot rather than repointing one")
    }

    @Test
    fun `there is nothing to add once the folder is exhausted`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James")
        showTab(stackOf(dir, "kjv.spb"))

        onAllNodesWithText("Add translation").assertCountEquals(0)
    }

    @Test
    fun `a translation can be moved up the stack`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        // The first row has no "up", so the one button carrying that description is the second
        // row's -- which is the one that moves Synodal above King James.
        onNodeWithContentDescription("Move translation up").performClick()
        waitForIdle()

        assertEquals(listOf("syn.spb", "kjv.spb"), files(harness))
    }

    @Test
    fun `a translation can be moved down the stack`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        // Likewise the last row has no "down", so this is the first row's.
        onNodeWithContentDescription("Move translation down").performClick()
        waitForIdle()

        assertEquals(listOf("syn.spb", "kjv.spb"), files(harness))
    }

    @Test
    fun `a single translation is offered neither reorder button`() {
        // Nothing to swap with, and a button that cannot do anything reads as one that is broken.
        runComposeUiTest {
            val dir = bibleFolder("kjv.spb" to "King James")
            showTab(stackOf(dir, "kjv.spb"))

            onAllNodesWithContentDescription("Move translation up").assertCountEquals(0)
            onAllNodesWithContentDescription("Move translation down").assertCountEquals(0)
        }
    }

    @Test
    fun `Remove takes a translation out of the stack`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        onAllNodesWithContentDescription("Remove")[1].performClick()
        waitForIdle()

        assertEquals(listOf("kjv.spb"), files(harness))
    }

    // ── What this church calls a translation ────────────────────────────────────────────────────

    /**
     * The name and abbreviation a church overrides its modules' own with.
     *
     * Both are per translation and both default to blank, which means "use what the module says" --
     * so a blank field is not an empty setting, it is the fallback still being in force. The two are
     * stored apart because a church can want one without the other: "Synodal" relabelled for the
     * congregation while the abbreviation beside each verse stays the module's.
     */
    @Test
    fun `a custom name is stored against its own translation`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(named(dir))

        // The fields carry no label semantics of their own -- the caption above each is a separate
        // Text node -- so each is addressed by the value it is showing, which is why the fixture
        // seeds four distinct ones.
        editableShowing("seed-name-2").performTextReplacement("Синодальный")
        waitForIdle()

        val stack = harness.current.bibleSettings.translationList()
        assertEquals("Синодальный", stack[1].customName)
        assertEquals("seed-name-1", stack[0].customName, "the translation above it must not have been renamed")
    }

    @Test
    fun `an abbreviation is stored apart from the name`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(named(dir))

        editableShowing("seed-abbr-1").performTextReplacement("AV")
        waitForIdle()

        val first = harness.current.bibleSettings.translationList()[0]
        assertEquals("AV", first.customAbbreviation)
        assertEquals("seed-name-1", first.customName, "the name beside it is a separate choice")
    }

    /**
     * The one editable field showing [text].
     *
     * The slot dropdown above each row displays the same custom name, so plain text alone matches
     * two nodes -- the read-only button and the field.
     */
    private fun ComposeUiTest.editableShowing(text: String) =
        onNode(hasSetTextAction() and hasText(text))

    /** Two translations whose four identity fields each show a value of their own. */
    private fun named(dir: File) = AppSettings(
        bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
            listOf(
                BibleTranslationSettings(
                    fileName = "kjv.spb",
                    customName = "seed-name-1",
                    customAbbreviation = "seed-abbr-1",
                ),
                BibleTranslationSettings(
                    fileName = "syn.spb",
                    customName = "seed-name-2",
                    customAbbreviation = "seed-abbr-2",
                ),
            ),
        ),
    )

    /** Opens the dropdown labelled [control] and picks [option] out of the menu it drops. */
    private fun ComposeUiTest.chooseFromMenu(control: String, option: String) {
        val before = onAllNodesWithText(option)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).map { it.id }.toSet()
        onAllNodesWithText(control).onFirst().performClick()
        waitForIdle()
        val opened = onAllNodesWithText(option).fetchSemanticsNodes().indexOfFirst { it.id !in before }
        assertTrue(opened >= 0, "\"$control\" must offer \"$option\"")
        onAllNodesWithText(option)[opened].performClick()
        waitForIdle()
    }
}
