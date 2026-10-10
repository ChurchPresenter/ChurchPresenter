@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongsTabTest {

    @Test
    fun `picking files, previewing and converting a SongBeamer batch writes songs beside them`() = withTempDir("songs-batch") { dir ->
        val first = sng(dir, "first.sng", "First Song", "Amazing grace")
        val second = sng(dir, "second.sng", "Second Song", "How sweet the sound")
        val pickers = FakePickers(files = listOf(first, second))
        val converted = mutableListOf<String>()
        runComposeUiTest {
            setConverterContent(pickers) { SongsTab(onConverted = { converted += it }) }

            assertTrue(isShowing("SongBeamer"))
            click(Strings.selectFiles)
            assertTrue(isShowing(Strings.filesSelected(2)))
            assertTrue(isShowing("first.sng"))
            assertEquals(listOf(SongSources.SONGBEAMER), pickers.requestedSources)

            click(Strings.preview)
            assertTrue(isShowing(Strings.previewLabel))
            assertTrue(isShowing("\"First Song\"", substring = true))

            click(Strings.convertNFiles(2))
            awaitShowing(Strings.doneConverted(2, 0))
            assertTrue(isShowing(Strings.nConverted(2)))
            assertTrue(isShowing("OK: first.sng -> first.song"))
            assertEquals(listOf(SongSources.SONGBEAMER), converted)
            assertTrue(File(dir, "second.song").readText().contains("How sweet the sound"))

            click(Strings.startOver)
            assertTrue(isShowing(Strings.selectFiles))
        }
    }

    @Test
    fun `a folder is searched for inputs, a destination is chosen, and the selection can be cleared`() =
        withTempDir("songs-folder") { dir ->
            val input = File(dir, "in").apply { mkdirs() }
            sng(input, "nested/one.sng", "One", "line")
            val out = File(dir, "out").apply { mkdirs() }
            val pickers = FakePickers(directory = input)
            runComposeUiTest {
                setConverterContent(pickers) { SongsTab() }

                click(Strings.selectFolder)
                assertTrue(isShowing(Strings.folderSelected(input.absolutePath, 1)))

                pickers.directory = out
                click(Strings.browse)
                assertTrue(isShowing(out.absolutePath))

                click(Strings.convert)
                awaitShowing(Strings.doneConverted(1, 0))
                assertTrue(File(out, "one.song").isFile)

                click(Strings.startOver)
                pickers.directory = input
                click(Strings.selectFolder)
                pickers.files = emptyList()
                click(Strings.change)
                assertTrue(isShowing(Strings.folderSelected(input.absolutePath, 1)))
                click(Strings.clear)
                assertTrue(isShowing(Strings.selectFiles))
            }
        }

    @Test
    fun `a file that fails to convert is logged as an error`() = withTempDir("songs-error") { dir ->
        val broken = File(dir, "broken.sng").apply { mkdirs() }
        runComposeUiTest {
            setConverterContent(FakePickers(files = listOf(broken))) { SongsTab() }
            click(Strings.selectFiles)
            click(Strings.convert)
            awaitShowing(Strings.doneConverted(0, 1))
            assertTrue(isShowing("ERROR: broken.sng", substring = true))
        }
    }

    @Test
    fun `the rail filters by name, says when nothing matches, and switches the panel`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) { SongsTab() }

            onAllNodes(hasSetTextAction()).onFirst().performTextInput("zzz")
            waitForIdle()
            assertTrue(isShowing(Strings.noFormatMatches("zzz")))

            onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("open")
            waitForIdle()
            assertTrue(isShowing("OpenSong"))
            assertFalse(isShowing("EasySlides"))

            click("OpenLP")
            assertTrue(isShowing(Strings.sourceDescription(SongSources.OPENLP)))
        }
    }

    @Test
    fun `a hidden source asked for opens the default instead`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) { SongsTab(initialSourceId = SongSources.EASYWORSHIP) }
            assertTrue(isShowing(Strings.sourceDescription(SongSources.SONGBEAMER)))
        }
    }

    @Test
    fun `a format that writes many files asks for a folder before it converts`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) { SongsTab(initialSourceId = SongSources.OPENLP) }
            assertTrue(isShowing(Strings.chooseOutputFolder))
            assertTrue(isShowing(Strings.outputManyFilesWarning))
        }
    }

    @Test
    fun `SoftProjector song books preview their songs and convert into a folder each`() = withTempDir("songs-sps") { dir ->
        val book = File(dir, "hymns.sps").apply {
            writeText("##SoftProjector\n##Hymns\n1#\$#Amazing Grace#\$#x#\$##\$##\$##\$#Verse 1@%Amazing grace\n")
        }
        val out = File(dir, "out").apply { mkdirs() }
        File(out, "Hymns").mkdirs()
        val pickers = FakePickers(files = listOf(book))
        val converted = mutableListOf<String>()
        runComposeUiTest {
            setConverterContent(pickers) {
                SongsTab(onConverted = { converted += it }, initialSourceId = SongSources.SOFTPROJECTOR)
            }

            click(Strings.selectFiles)
            onNodeWithText(Strings.preview).assertIsNotEnabled()
            pickers.directory = out
            click(Strings.browse)

            click(Strings.preview)
            assertTrue(isShowing(Strings.songbookPrefix("Hymns")))
            assertTrue(isShowing(Strings.songsFound(1)))
            assertTrue(isShowing("0001 - Amazing Grace"))
            assertTrue(isShowing(Strings.outputFolderOverwrite))

            click(Strings.convertNSongs(1))
            awaitShowing(Strings.doneLabel)
            assertEquals(listOf(SongSources.SOFTPROJECTOR), converted)
            assertTrue(File(out, "Hymns/0001 - Amazing Grace.song").isFile)

            click(Strings.startOver)
            pickers.directory = dir
            click(Strings.selectFolder)
            assertTrue(isShowing(Strings.folderSelected(dir.absolutePath, 1)))
            click(Strings.change)
            click(Strings.clear)
            assertTrue(isShowing(Strings.selectFiles))
        }
    }

    @Test
    fun `a SoftProjector book that cannot be read blocks the conversion`() = withTempDir("songs-sps-bad") { dir ->
        val good = File(dir, "a.sps").apply { writeText("##SoftProjector\n##A\n1#\$#T#\$#x#\$##\$##\$##\$#l\n") }
        val missing = File(dir, "missing.sps")
        val pickers = FakePickers(files = listOf(good, missing), directory = dir)
        runComposeUiTest {
            setConverterContent(pickers) { SongsTab(initialSourceId = SongSources.SOFTPROJECTOR) }
            click(Strings.selectFiles)
            click(Strings.browse)
            click(Strings.preview)
            assertTrue(isShowing(Strings.errorPrefix(""), substring = true))
            onNodeWithText(Strings.convertNSongs(1)).assertIsNotEnabled()
            assertTrue(isShowing(Strings.songsFound(1)))
        }
    }

    @Test
    fun `documents are extracted, previewed as songs or text, and converted`() = withTempDir("songs-docs") { dir ->
        val doc = File(dir, "hymn.docx")
        XWPFDocument().use { word ->
            listOf("Amazing Grace", "Author: John Newton", "", "Verse 1", "Amazing grace how sweet", "", "Chorus", "Praise him")
                .forEach { word.createParagraph().createRun().setText(it) }
            doc.outputStream().use { word.write(it) }
        }
        val out = File(dir, "out").apply { mkdirs() }
        val pickers = FakePickers(files = listOf(doc), directory = out)
        val converted = mutableListOf<String>()
        runComposeUiTest {
            setConverterContent(pickers) {
                SongsTab(onConverted = { converted += it }, initialSourceId = SongSources.DOCUMENTS)
            }

            click(Strings.selectFiles)
            assertTrue(isShowing("hymn.docx"))
            click(Strings.browse)
            click(Strings.preview)
            awaitShowing(Strings.songsExtracted(1))
            assertTrue(isShowing("Amazing Grace"))
            assertTrue(isShowing("Verse 1, Chorus"))
            assertTrue(isShowing("John Newton", substring = true))

            click(Strings.docPreviewMarkdown)
            assertTrue(isShowing("── hymn.docx ──", substring = true))
            click(Strings.docPreviewSong)

            click(Strings.convertNSongs(1))
            awaitShowing(Strings.doneConverted(1, 0))
            assertEquals(listOf(SongSources.DOCUMENTS), converted)
            assertTrue(File(out, "Amazing Grace.song").isFile)

            click(Strings.startOver)
            pickers.directory = dir
            click(Strings.selectFolder)
            assertTrue(isShowing("hymn.docx"))
            click(Strings.change)
            click(Strings.clear)
            assertTrue(isShowing(Strings.selectFiles))
        }
    }

    @Test
    fun `a document that cannot be read is logged as an error`() = withTempDir("songs-docs-bad") { dir ->
        val corrupt = File(dir, "bad.docx").apply { writeText("not a zip") }
        runComposeUiTest {
            setConverterContent(FakePickers(files = listOf(corrupt), directory = dir)) {
                SongsTab(initialSourceId = SongSources.DOCUMENTS)
            }
            click(Strings.selectFiles)
            click(Strings.browse)
            click(Strings.convert)
            awaitShowing(Strings.doneConverted(0, 1))
            assertTrue(isShowing("ERROR: bad.docx", substring = true))
        }
    }
}
