@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.sharedui.composables.SCANNING_ROW_TAG
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleSettingsTabBookNamesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-book-names-tab").toFile()
        SpbFixture.spbFile(
            dir,
            name = "named.spb",
            content = SpbFixture.buildContent(
                title = "Named Bible",
                books = (1..BOOK_COUNT).map { SpbFixture.Book(it, "Real $it", 1) },
                verses = emptyList(),
            ),
        )
        SpbFixture.spbFile(
            dir,
            name = "bare.spb",
            content = SpbFixture.buildContent("Bare Bible", emptyList(), emptyList()),
        )
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private class Harness {
        var current by mutableStateOf(AppSettings())
    }

    private fun ComposeUiTest.showTab(fileName: String, overrides: Map<Int, String> = emptyMap()): Harness {
        val harness = Harness().apply {
            current = AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
                    listOf(BibleTranslationSettings(fileName = fileName, bookNameOverrides = overrides)),
                ),
            )
        }
        setContent {
            MaterialTheme {
                BibleSettingsTab(
                    settings = harness.current,
                    onSettingsChange = { transform -> harness.current = transform(harness.current) },
                )
            }
        }
        waitUntil {
            onAllNodesWithTag(SCANNING_ROW_TAG).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        return harness
    }

    private fun Harness.overrides() = current.bibleSettings.translationList().single().bookNameOverrides

    private fun ComposeUiTest.chooseInSlot(label: String) {
        val before = onAllNodesWithText(label)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).map { it.id }.toSet()
        onAllNodesWithText("TRANSLATION 1").onFirst().performClick()
        waitForIdle()
        val added = onAllNodesWithText(label).fetchSemanticsNodes().indexOfFirst { it.id !in before }
        assertTrue(added >= 0, "the menu must offer \"$label\"")
        onAllNodesWithText(label)[added].performClick()
        waitForIdle()
    }

    @Test
    fun `the hint says where names come from`() = runComposeUiTest {
        showTab("named.spb")
        onNodeWithText(
            "Book names come from the Bible file when it has them. " +
                "If it doesn't, English names are used until you set your own.",
        ).assertExists()
    }

    @Test
    fun `a file with names reads as from file`() = runComposeUiTest {
        showTab("named.spb")
        onNodeWithText("From file").assertExists()
    }

    @Test
    fun `saved overrides are counted`() = runComposeUiTest {
        showTab("named.spb", mapOf(1 to "Bereshit", 2 to "Shemot"))
        onNodeWithText("2 edited").assertExists()
    }

    @Test
    fun `a file with no names reads as not set`() = runComposeUiTest {
        showTab("bare.spb")
        onNodeWithText("Not set").assertExists()
    }

    @Test
    fun `the dialog opens on the file's names and says when there are none`() = runComposeUiTest {
        showTab("bare.spb")

        onNodeWithText("BOOK NAMES").performClick()
        waitForIdle()

        onNodeWithText("Book names could not be extracted from this file.").assertExists()
    }

    @Test
    fun `saving in the dialog writes the translation and closes it`() = runComposeUiTest {
        val harness = showTab("named.spb")

        onNodeWithText("BOOK NAMES").performClick()
        waitForIdle()
        onNodeWithContentDescription("Real 1").performTextReplacement("Bereshit")
        waitForIdle()
        onNodeWithText("Save").performClick()
        waitForIdle()

        assertEquals(mapOf(1 to "Bereshit"), harness.overrides())
        onNodeWithText("1 edited").assertExists()
        onNodeWithText("Using names from the Bible file.").assertDoesNotExist()
    }

    @Test
    fun `cancelling in the dialog writes nothing`() = runComposeUiTest {
        val harness = showTab("named.spb")

        onNodeWithText("BOOK NAMES").performClick()
        waitForIdle()
        onNodeWithContentDescription("Real 1").performTextReplacement("Bereshit")
        waitForIdle()
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertEquals(emptyMap(), harness.overrides())
    }

    @Test
    fun `putting another Bible in the slot drops the names set for the old one`() = runComposeUiTest {
        val harness = showTab("named.spb", mapOf(1 to "Bereshit"))

        chooseInSlot("Bare Bible")

        assertEquals("bare.spb", harness.current.bibleSettings.translationList().single().fileName)
        assertEquals(emptyMap(), harness.overrides())
    }

    @Test
    fun `picking the Bible already in the slot keeps its names`() = runComposeUiTest {
        val harness = showTab("named.spb", mapOf(1 to "Bereshit"))

        chooseInSlot("Named Bible")

        assertEquals(mapOf(1 to "Bereshit"), harness.overrides())
    }
}
