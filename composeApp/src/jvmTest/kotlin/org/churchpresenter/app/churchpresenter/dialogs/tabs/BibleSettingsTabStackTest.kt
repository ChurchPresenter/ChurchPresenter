package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
}
