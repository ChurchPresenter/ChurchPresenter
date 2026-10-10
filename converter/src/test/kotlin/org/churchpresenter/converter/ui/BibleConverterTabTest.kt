@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleConverterTabTest {

    private fun zefania(dir: File, name: String): File = File(dir, name).apply {
        parentFile.mkdirs()
        writeText(
            """<?xml version="1.0" encoding="UTF-8"?><XMLBIBLE biblename="Test Bible">""" +
                """<INFORMATION><language>ENG</language></INFORMATION>""" +
                """<BIBLEBOOK bnumber="1" bname="Genesis"><CHAPTER cnumber="1">""" +
                """<VERS vnumber="1">In the beginning</VERS><VERS vnumber="2">And the earth</VERS>""" +
                """</CHAPTER></BIBLEBOOK></XMLBIBLE>""",
            Charsets.UTF_8,
        )
    }

    @Test
    fun `XML Bibles are previewed and converted to spb files beside them`() = withTempDir("bible-tab") { dir ->
        val bible = zefania(dir, "kjv.xml")
        val pickers = FakePickers(files = listOf(bible))
        val converted = mutableListOf<String>()
        runComposeUiTest {
            setConverterContent(pickers) { BibleConverterTab(onConverted = { converted += it }) }

            click(Strings.selectXmlFiles)
            assertEquals(listOf("xml"), pickers.requestedExtensions)
            assertTrue(isShowing(Strings.fileCount(1)))

            click(Strings.preview)
            assertTrue(isShowing(Strings.previewLabel))
            assertTrue(isShowing("\"Test Bible\" | 1 book(s) | 2 verses", substring = true))
            click(Strings.back)
            click(Strings.preview)

            click(Strings.convertNFiles(1))
            awaitShowing(Strings.doneConverted(1, 0))
            assertTrue(isShowing("OK: kjv.xml -> kjv.spb"))
            assertEquals(listOf(BIBLE_CONVERSION), converted)
            assertTrue(File(dir, "kjv.spb").isFile)

            click(Strings.startOver)
            assertTrue(isShowing(Strings.selectXmlFiles))
        }
    }

    @Test
    fun `a folder of Bibles converts straight into the chosen destination, logging failures`() =
        withTempDir("bible-tab-folder") { dir ->
            val input = File(dir, "in")
            zefania(input, "nested/good.xml")
            File(input, "bad.xml").writeText("this is not xml")
            val out = File(dir, "out").apply { mkdirs() }
            val pickers = FakePickers(directory = input)
            val converted = mutableListOf<String>()
            runComposeUiTest {
                setConverterContent(pickers) { BibleConverterTab(onConverted = { converted += it }) }

                click(Strings.selectFolder)
                assertTrue(isShowing(Strings.fileCount(2)))
                pickers.directory = out
                click(Strings.browse)
                assertTrue(isShowing(out.absolutePath))

                click(Strings.preview)
                assertTrue(isShowing("Parse error", substring = true))
                click(Strings.back)

                click(Strings.convert)
                awaitShowing(Strings.doneConverted(1, 1))
                assertTrue(isShowing("ERROR: bad.xml", substring = true))
                assertTrue(File(out, "good.spb").isFile)
                assertEquals(listOf(BIBLE_CONVERSION), converted)
            }
        }

    @Test
    fun `spb files are checked for verse fixes and the result is reported`() = withTempDir("bible-tab-fix") { dir ->
        val spb = File(dir, "clean.spb").apply { writeText("##spDataVersion:1\n-----\n") }
        val missing = File(dir, "missing.spb")
        val bible = zefania(dir, "bible.xml")
        val pickers = FakePickers(files = listOf(bible))
        runComposeUiTest {
            setConverterContent(pickers) { BibleConverterTab() }
            click(Strings.selectXmlFiles)
            click(Strings.convert)
            awaitShowing(Strings.doneConverted(1, 0))
            pickers.files = listOf(spb, missing)

            click(Strings.selectSpbFiles)
            assertEquals(listOf("xml", "spb"), pickers.requestedExtensions)
            assertTrue(isShowing(Strings.fileCount(2)))

            click(Strings.fixVerses)
            awaitShowing(Strings.doneFixed(0, 1))
            assertTrue(isShowing("OK: clean.spb — no patches needed"))
            assertTrue(isShowing("ERROR: missing.spb", substring = true))

            onAllNodesWithText(Strings.startOver).onLast().performScrollTo().performClick()
            waitForIdle()
            assertTrue(isShowing(Strings.fixVerses))
            click(Strings.startOver)
            assertTrue(isShowing(Strings.convert))
        }
    }

    @Test
    fun `nothing chosen leaves the selection as it was`() {
        runComposeUiTest {
            setConverterContent(FakePickers()) { BibleConverterTab() }
            click(Strings.selectXmlFiles)
            click(Strings.selectFolder)
            click(Strings.selectSpbFiles)
            click(Strings.browse)
            assertTrue(isShowing(Strings.sameAsInput))
        }
    }
}
