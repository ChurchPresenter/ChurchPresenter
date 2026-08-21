package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a module's language comes from when the module itself does not say.
 *
 * Plenty of the archive's XML declares no `<language>` at all. Rather than give up and write book
 * names through untranslated, the converter reads the code off the path — the archive files modules
 * as `<LANG>/<something>/<something>/file.xml`, so the code sits four components up. Getting this
 * wrong is quiet: the module still converts and still opens, it just shows English book names to a
 * Russian congregation, which nobody reports as a bug.
 *
 * Only codes the app actually has a book-name table for are taken, so a stray folder name cannot
 * invent a language. The one special case is `RUS`, which the archive also uses for Ukrainian
 * conversions — those are told apart by the path naming the language in full.
 */
class ZefaniaPathLanguageTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-zefania-path-language-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** No `<language>` element at all — the case the path fallback exists for. */
    private fun moduleAt(vararg folders: String): File {
        val xml = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<XMLBIBLE biblename="Untitled Module">""")
            append("<INFORMATION><title>Untitled Module</title><identifier>UM</identifier></INFORMATION>")
            append("""<BIBLEBOOK bnumber="1">""")
            append("""<CHAPTER cnumber="1"><VERS vnumber="1">In the beginning</VERS></CHAPTER>""")
            append("</BIBLEBOOK>")
            append("</XMLBIBLE>")
        }
        val parent = folders.fold(dir) { acc, name -> File(acc, name) }.apply { mkdirs() }
        return File(parent, "module.xml").apply { writeText(xml) }
    }

    private fun bookNameOf(source: File): String {
        val out = File(dir, "out.spb")
        XmlToSpbConverter.convert(source, out)
        return out.readText().lineSequence().first { it.startsWith("1\t") }.split("\t")[1]
    }

    @Test
    fun `a module filed under a language folder is converted with that language's book names`() {
        val russian = bookNameOf(moduleAt("RUS", "conversion", "v1"))

        assertEquals(BookNames.LANGUAGE_LOOKUPS.getValue("RUS").getValue(1), russian)
    }

    @Test
    fun `a module filed under ENG gets English book names`() {
        assertEquals("Genesis", bookNameOf(moduleAt("ENG", "conversion", "v1")))
    }

    /**
     * The archive files its Ukrainian conversions under `RUS` and distinguishes them by naming the
     * language in the path. Read as Russian, a Ukrainian module would show the wrong book names
     * throughout while looking perfectly healthy.
     */
    @Test
    fun `a Ukrainian conversion filed under RUS is not mistaken for Russian`() {
        val ukrainian = bookNameOf(moduleAt("RUS", "Ukrainian Bible", "v1"))

        assertEquals(BookNames.LANGUAGE_LOOKUPS.getValue("UKR").getValue(1), ukrainian)
        assertEquals(bookNameOf(moduleAt("RUS", "українська", "v1")), ukrainian, "either spelling")
    }

    /**
     * A folder that is not a language the app knows must not become one: the module keeps whatever
     * names it carries rather than being relabelled from a directory that happened to be there.
     */
    @Test
    fun `a folder that is not a known language code is ignored`() {
        val unknown = bookNameOf(moduleAt("ZZZ", "conversion", "v1"))

        assertEquals("Genesis", unknown, "no table applies, so the default naming stands")
    }

    @Test
    fun `a module sitting too shallow to have a language folder still converts`() {
        assertEquals("Genesis", bookNameOf(moduleAt()))
    }
}
