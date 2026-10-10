@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BulkRenameTabTest {

    private fun song(dir: File, name: String, firstLine: String = "A line"): File = File(dir, name).apply {
        parentFile.mkdirs()
        writeText("---\nauthor: x\n---\n[Primary]\ntitle: T\n\n[Verse 1]\n$firstLine\n\n[Chorus]\nchorus\n")
    }

    private fun names(dir: File): Set<String> = dir.listFiles()!!.map { it.name }.toSet()

    @Test
    fun `numbers are stripped, clashes are flagged, and renaming skips what would overwrite`() =
        withTempDir("rename-strip") { dir ->
            song(dir, "01 - Grace.song")
            song(dir, "02 - Grace.song")
            song(dir, "03 - Peace.song")
            song(dir, "Peace.song")
            runComposeUiTest {
                setConverterContent(FakePickers(directory = dir)) { BulkRenameTab() }

                click(Strings.selectFolder)
                assertTrue(isShowing(dir.absolutePath))
                click(Strings.preview)
                assertTrue(isShowing(Strings.renameSummary(3, 1) + ", " + Strings.conflictsSummary(2)))
                assertTrue(isShowing("01 - Grace.song"))
                assertTrue(isShowing(Strings.conflict))

                click(Strings.renameNFiles(3))
                awaitShowing(Strings.doneRenamed(1, 2, 0))
                assertTrue(isShowing("SKIP: 03 - Peace.song → Peace.song (target exists)"))
                assertEquals(setOf("Grace.song", "02 - Grace.song", "03 - Peace.song", "Peace.song"), names(dir))

                click(Strings.startOver)
                assertTrue(isShowing(Strings.preview))
            }
        }

    @Test
    fun `the first verse line and a letter case rename the file, a case-only change included`() =
        withTempDir("rename-verse") { dir ->
            song(dir, "07 - untitled.song", firstLine = "amazing grace: how sweet")
            song(dir, "hymn.song")
            runComposeUiTest {
                setConverterContent(FakePickers(directory = dir)) { BulkRenameTab() }

                click(Strings.selectFolder)
                click(Strings.renameFirstVerse)
                click(Strings.caseUpper)
                assertTrue(isShowing(Strings.renameExampleFirstLine.uppercase() + ".song"))
                click(Strings.preview)
                assertTrue(isShowing("AMAZING GRACE HOW SWEET.song"))
                assertTrue(isShowing("A LINE.song"))

                click(Strings.renameFirstVerse)
                click(Strings.caseSentence)
                waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isShowing("Hymn.song") }
                click(Strings.caseTitle)
                click(Strings.caseLower)
                click(Strings.caseNone)
                click(Strings.caseSentence)
                waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isShowing("Hymn.song") }

                click(Strings.renameNFiles(2))
                awaitShowing(Strings.doneRenamed(2, 0, 0))
                assertEquals(setOf("Untitled.song", "Hymn.song"), names(dir))
            }
        }

    @Test
    fun `going back from a preview, and turning numbers off, leaves nothing to rename`() = withTempDir("rename-back") { dir ->
        song(dir, "05 - Joy.song")
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { BulkRenameTab() }
            click(Strings.selectFolder)
            click(Strings.preview)
            click(Strings.back)
            click(Strings.stripNumbers)
            click(Strings.preview)
            assertTrue(isShowing(Strings.renameSummary(0, 1)))
            assertEquals(setOf("05 - Joy.song"), names(dir))
        }
    }

    @Test
    fun `an empty folder previews nothing`() = withTempDir("rename-empty") { dir ->
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { BulkRenameTab() }
            click(Strings.selectFolder)
            click(Strings.preview)
            assertTrue(isShowing(Strings.renameSummary(0, 0)))
        }
    }
}
