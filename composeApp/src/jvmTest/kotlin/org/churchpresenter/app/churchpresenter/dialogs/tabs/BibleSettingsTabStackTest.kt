package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
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
     * Which node that is cannot be "the first one reading [label]": the typography panel carries a
     * text-transform option reading "None", which is on screen before the menu opens and traverses
     * ahead of it. So the nodes are counted first and the one the click *added* is the one taken.
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

    /** Points the styling panel at translation [index] of the stack, one-based, via its chip. */
    private fun ComposeUiTest.selectTranslation(index: Int) {
        onAllNodesWithText("$index · ", substring = true).onFirst().performClick()
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

    @Test
    fun `only the selected translation's styling is on screen`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(
                        BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 41),
                        BibleTranslationSettings(fileName = "syn.spb", textFontSize = 52),
                    ),
                ),
            ),
        )

        assertEquals(
            1,
            onAllNodesWithText("41").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "the first translation is selected on arrival, so its font size is the one shown",
        )
        assertEquals(
            0,
            onAllNodesWithText("52").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "one set of controls stands for whichever translation is selected, so the other's is absent",
        )
    }

    @Test
    fun `picking a later translation points the panel at it`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(
                        BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 41),
                        BibleTranslationSettings(fileName = "syn.spb", textFontSize = 52),
                    ),
                ),
            ),
        )

        selectTranslation(2)

        assertEquals(
            1,
            onAllNodesWithText("52").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
        )
        assertEquals(
            0,
            onAllNodesWithText("41").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
        )
    }

    @Test
    fun `editing the second translation writes the second one, not the first`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(
                        BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 41),
                        BibleTranslationSettings(fileName = "syn.spb", textFontSize = 52),
                    ),
                ),
            ),
        )

        selectTranslation(2)

        onNodeWithText("52").performTextReplacement("63")
        waitForIdle()

        val stack = harness.current.bibleSettings.translationList()
        assertEquals(63, stack[1].textFontSize, "the panel was pointed at the second translation")
        assertEquals(41, stack[0].textFontSize, "and the first must be untouched")
    }

    @Test
    fun `editing the first translation leaves the second alone`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(
                        BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 41),
                        BibleTranslationSettings(fileName = "syn.spb", textFontSize = 52),
                    ),
                ),
            ),
        )

        onNodeWithText("41").performTextReplacement("38")
        waitForIdle()

        val stack = harness.current.bibleSettings.translationList()
        assertEquals(38, stack[0].textFontSize)
        assertEquals(52, stack[1].textFontSize)
    }

    // ── Renaming ────────────────────────────────────────────────────────────────────────────────

    /** The rename box standing empty, found by the module value it is showing as its placeholder. */
    private fun ComposeUiTest.emptyFieldOffering(placeholder: String) =
        onNode(hasSetTextAction() and hasText(placeholder))

    /** The abbreviation box, which carries no placeholder for a test to find an empty one by. */
    private fun ComposeUiTest.abbreviationField() =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(BIBLE_ABBREVIATION_FIELD_TAG)))

    @Test
    fun `the name boxes start empty, offering what the module calls itself`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        // Blank means "use what the module says", so both boxes show that as a placeholder rather
        // than typing it in -- including the abbreviation, which is derived and written nowhere.
        emptyFieldOffering("King James Version").assertExists()
        emptyFieldOffering("KJV").assertExists()
        val translation = harness.current.bibleSettings.translationList().single()
        assertEquals("" to "", translation.customName to translation.customAbbreviation)
    }

    @Test
    fun `typing a name renames that translation`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        emptyFieldOffering("King James Version").performTextReplacement("Authorised Version")
        waitForIdle()

        assertEquals("Authorised Version", harness.current.bibleSettings.translationList().single().customName)
    }

    @Test
    fun `a name keeps the spaces it was typed with`() = runComposeUiTest {
        // Trimming on the way in deleted the space as it was pressed: "King James" stuck at "King".
        val dir = bibleFolder("kjv.spb" to "King James Version")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        emptyFieldOffering("King James Version").performTextReplacement("King ")
        waitForIdle()

        assertEquals("King ", harness.current.bibleSettings.translationList().single().customName)
    }

    @Test
    fun `typing an abbreviation renames only the abbreviation`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        abbreviationField().performTextReplacement("AV")
        waitForIdle()

        val translation = harness.current.bibleSettings.translationList().single()
        assertEquals("AV" to "", translation.customAbbreviation to translation.customName)
    }

    @Test
    fun `renaming the second translation writes the second one`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        selectTranslation(2)
        emptyFieldOffering("Synodal").performTextReplacement("Pew Bible")
        waitForIdle()

        val stack = harness.current.bibleSettings.translationList()
        assertEquals("Pew Bible", stack[1].customName)
        assertEquals("", stack[0].customName)
    }

    @Test
    fun `a renamed translation is offered under its new name in the picker`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James Version", "syn.spb" to "Synodal")
        showTab(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(BibleTranslationSettings(fileName = "kjv.spb", customName = "Authorised")),
                ),
            ),
        )

        onNode(hasText("TRANSLATION 1") and hasText("Authorised")).assertExists()
    }

    @Test
    fun `the show-abbreviation switch appears only on the Reference tab`() = runComposeUiTest {
        // The header it lives in is shared by both element tabs, but the label only ever lands on
        // the reference -- so on Verse Text the box was present and completely ineffective.
        val dir = bibleFolder("kjv.spb" to "King James Version")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        onNodeWithText("Show Bible abbreviation").assertDoesNotExist()

        onNodeWithText("Reference").performClick()
        onNodeWithText("Show Bible abbreviation").performClick()
        waitForIdle()

        assertEquals(true, harness.current.bibleSettings.translationList().single().showAbbreviation)
    }
}
