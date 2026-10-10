@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.converter.song.SongFormatConverters
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppTest {

    @Test
    fun `the window opens on the Bibles tab and switches between all four`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) { App() }
            assertTrue(isShowing(Strings.bibleTitle))
            click(Strings.tabSongs)
            assertTrue(isShowing(Strings.sourceDescription(SongSources.SONGBEAMER)))
            click(Strings.tabDuplicates)
            assertTrue(isShowing(Strings.dupesTitle))
            click(Strings.tabRename)
            assertTrue(isShowing(Strings.renameTitle))
            click(Strings.tabBibles)
            assertTrue(isShowing(Strings.fixVersesTitle))
        }
    }

    @Test
    fun `a caller can open the Songs tab on a chosen source`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) { App(initialTab = ConverterTab.SONGS, initialSongSource = SongSources.QUELEA) }
            assertTrue(isShowing(Strings.sourceDescription(SongSources.QUELEA)))
        }
    }

    @Test
    fun `preview rows show their details and warn about an overwrite`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) {
                PreviewRow(PreviewItem("in.sng", "/in.sng", "in.song", "/out/in.song", "\"Title\"", willOverwrite = true))
                LogLine("ERROR: broken")
            }
            assertTrue(isShowing("\"Title\""))
            assertTrue(isShowing(Strings.outputOverwrite))
            assertTrue(isShowing("/out/in.song"))
            assertTrue(isShowing("ERROR: broken"))
        }
    }

    @Test
    fun `file sizes are shown in the largest unit that fits`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("2 KB", formatFileSize(2048))
        assertEquals("1.5 MB", formatFileSize(1536L * 1024))
    }

    @Test
    fun `a run counts as converted only when one line says OK`() {
        assertTrue(listOf("ERROR: a", "OK: b").anyConverted())
        assertFalse(listOf("ERROR: a").anyConverted())
    }

    @Test
    fun `the side by side diff aligns matches and pairs changed lines`() {
        val rows = computeSideBySide(listOf("a", "b", "c", "x"), listOf("a", "B", "c", "y", "z"))
        assertEquals(DiffType.SAME, rows[0].leftType)
        assertEquals(Pair("b", "B"), rows[1].leftText to rows[1].rightText)
        assertEquals(DiffType.DEL, rows[1].leftType)
        assertEquals(DiffType.ADD, rows[1].rightType)
        assertEquals("c", rows[2].rightText)
        assertEquals(Pair("x", "y"), rows[3].leftText to rows[3].rightText)
        assertEquals(null, rows[4].leftText)
        assertEquals("z", rows[4].rightText)
        assertEquals(DiffType.SAME, rows[4].leftType)

        val onlyAdded = computeSideBySide(emptyList(), listOf("new"))
        assertEquals(listOf(SideBySideRow(rightNum = 1, rightText = "new", rightType = DiffType.ADD)), onlyAdded)
        val onlyRemoved = computeSideBySide(listOf("old", "older"), listOf("old"))
        assertEquals(DiffType.DEL, onlyRemoved[1].leftType)
        assertEquals(null, onlyRemoved[1].rightText)
    }

    @Test
    fun `format previews describe each file, and say when it cannot be read`() = withTempDir("app-preview") { dir ->
        val good = sng(dir, "good.sng", "Good Song", "line")
        val bad = File(dir, "bad.sng").apply { mkdirs() }
        File(dir, "good.song").writeText("exists")
        val format = SongFormatConverters.byId(SongSources.SONGBEAMER)

        val items = buildFormatPreview(format, listOf(good, bad), null)
        assertTrue(items[0].details.startsWith("\"Good Song\""), items[0].details)
        assertTrue(items[0].willOverwrite)
        assertTrue(items[1].details.startsWith(Strings.parseError("").trimEnd()), items[1].details)

        val elsewhere = buildFormatPreview(format, listOf(good), File(dir, "out"))
        assertFalse(elsewhere.single().willOverwrite)
    }

    @Test
    fun `a song book preview lists its songs, and an unreadable one carries the error`() = withTempDir("app-sps") { dir ->
        val book = File(dir, "book.sps").apply { writeText("##SoftProjector\n##Psalms\n7#\$#Psalm#\$#x#\$##\$##\$##\$#line\n") }
        File(dir, "Psalms").mkdirs()
        val preview = buildSpsPreview(book, dir)
        assertEquals("Psalms", preview.songbookName)
        assertEquals(listOf("0007 - Psalm"), preview.sampleTitles)
        assertTrue(preview.folderExists)

        val failed = buildSpsPreview(File(dir, "absent.sps"), dir)
        assertTrue(failed.error != null)
        assertEquals(0, failed.songCount)
    }

    @Test
    fun `Bible previews name the language when the file gives one, and xml files are found in subfolders`() =
        withTempDir("app-bible") { dir ->
            val bible = File(dir, "sub/b.xml").apply {
                parentFile.mkdirs()
                writeText(
                    """<?xml version="1.0"?><XMLBIBLE biblename="B"><INFORMATION><language>DEU</language></INFORMATION>""" +
                        """<BIBLEBOOK bnumber="1" bname="Genesis"><CHAPTER cnumber="1"><VERS vnumber="1">Am Anfang</VERS>""" +
                        """</CHAPTER></BIBLEBOOK></XMLBIBLE>""",
                )
            }
            File(dir, "notes.txt").writeText("x")
            assertEquals(listOf(bible), findXmlFilesRecursive(dir))
            val details = buildBiblePreview(listOf(bible), null).single().details
            assertTrue(details.contains("lang:"), details)
        }
}
