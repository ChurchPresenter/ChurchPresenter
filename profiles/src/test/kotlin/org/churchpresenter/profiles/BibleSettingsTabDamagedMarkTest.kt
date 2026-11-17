package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.sharedui.composables.SCANNING_ROW_TAG
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The warning mark on a stacked Bible whose file has verse rows with an unreadable id. */
@OptIn(ExperimentalTestApi::class)
class BibleSettingsTabDamagedMarkTest {

    private val dir: File = Files.createTempDirectory("cp-bible-damaged").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun bible(name: String, vararg rows: String) =
        File(dir, name).writeText("##Title:\t$name\n-----\n" + rows.joinToString("\n") + "\n")

    private fun ComposeUiTest.showStack(vararg fileNames: String) {
        val settings = AppSettings(
            bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                fileNames.map { BibleTranslationSettings(fileName = it) },
            ),
        )
        setContent { MaterialTheme { BibleSettingsTab(settings = settings, onSettingsChange = {}) } }
        waitUntil {
            onAllNodesWithTag(SCANNING_ROW_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
    }

    private fun ComposeUiTest.marks() =
        onAllNodesWithContentDescription("verse(s) in this Bible have a damaged ID", substring = true)
            .fetchSemanticsNodes().size

    @Test
    fun `a stacked Bible with an unreadable id carries the mark, and its tooltip says how many`() = runComposeUiTest {
        bible("bad.spb", "B047C010V004\t47\t10\t4\tFour", "B047C010V0050\t47\t10\t5\tFive")
        showStack("bad.spb")
        waitUntil { marks() == 1 }
        onAllNodesWithContentDescription("1 verse(s) in this Bible have a damaged ID", substring = true)
            .fetchSemanticsNodes().let { assertEquals(1, it.size) }
    }

    @Test
    fun `only the damaged Bible of two is marked`() = runComposeUiTest {
        bible("bad.spb", "B047C010V0050\t47\t10\t5\tFive")
        bible("good.spb", "B047C010V005\t47\t10\t5\tFive")
        showStack("good.spb", "bad.spb")
        waitUntil { marks() == 1 }
        assertEquals(1, marks())
    }

    @Test
    fun `a clean stack shows no mark`() = runComposeUiTest {
        bible("good.spb", "B047C010V005\t47\t10\t5\tFive")
        showStack("good.spb")
        waitForIdle()
        assertEquals(0, marks())
    }
}
