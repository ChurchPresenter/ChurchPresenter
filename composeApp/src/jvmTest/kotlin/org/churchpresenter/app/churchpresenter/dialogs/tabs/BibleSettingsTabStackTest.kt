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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.ui.SCANNING_ROW_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun ComposeUiTest.chooseFromMenu(label: String) {
        onAllNodesWithText(label).onFirst().performClick()
        waitForIdle()
    }

    private fun files(harness: Harness) = harness.current.bibleSettings.translationList().map { it.fileName }

    private fun ComposeUiTest.expandSection(title: String) {
        onNodeWithText(title, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun `picking another bible in a slot swaps it rather than adding one`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "niv.spb" to "New International")
        val harness = showTab(stackOf(dir, "kjv.spb"))

        openSlot(1)
        chooseFromMenu("New International")

        assertEquals(listOf("niv.spb"), files(harness), "the stack keeps its size when a slot is repointed")
    }

    @Test
    fun `picking another bible leaves the other slots alone`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal", "niv.spb" to "New International")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        openSlot(1)
        chooseFromMenu("New International")

        assertEquals(listOf("niv.spb", "syn.spb"), files(harness))
    }

    @Test
    fun `setting the second slot to none takes that translation out of the stack`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        openSlot(2)
        chooseFromMenu("None")

        assertEquals(listOf("kjv.spb"), files(harness), "None removes the slot, it does not blank it")
    }

    @Test
    fun `setting the first slot to none promotes the one behind it`() = runComposeUiTest {
        val dir = bibleFolder("kjv.spb" to "King James", "syn.spb" to "Synodal")
        val harness = showTab(stackOf(dir, "kjv.spb", "syn.spb"))

        openSlot(1)
        chooseFromMenu("None")

        assertEquals(listOf("syn.spb"), files(harness))
    }

    @Test
    fun `only the first translation section is open to begin with`() = runComposeUiTest {
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
            "the open section shows its font size",
        )
        assertEquals(
            0,
            onAllNodesWithText("52").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "four full appearance profiles open at once would be unreadable",
        )
    }

    @Test
    fun `opening a later section closes the one before it`() = runComposeUiTest {
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

        expandSection("Translation 2 —")

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
    fun `editing the second section writes the second translation, not the first`() = runComposeUiTest {
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

        expandSection("Translation 2 —")

        onNodeWithText("52").performTextReplacement("63")
        waitForIdle()

        val stack = harness.current.bibleSettings.translationList()
        assertEquals(63, stack[1].textFontSize, "the edited section is the second one")
        assertEquals(41, stack[0].textFontSize, "and the first must be untouched")
    }

    @Test
    fun `editing the first section leaves the second alone`() = runComposeUiTest {
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
}
