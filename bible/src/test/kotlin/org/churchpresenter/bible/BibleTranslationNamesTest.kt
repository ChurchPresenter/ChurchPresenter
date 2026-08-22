package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one reader that names an installed `.spb` translation (issue #98).
 *
 * Four callers used to hand-roll this scan with four copies of the fallback rules. The rules are
 * peculiar enough to be worth pinning in one place: the separator after `##Title:` is a tab from the
 * converter and a space from plenty of hand-made modules, and a header that is present but empty is
 * NOT the same as no header — the first says the module's name is nothing, the second says it never
 * gave one, and only the second falls back to the file name.
 */
class BibleTranslationNamesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-names-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun module(name: String, content: String): File =
        File(dir, name).apply { parentFile.mkdirs(); writeText(content) }

    // ── Reading one title ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a tab after the colon is the converter's own output`() {
        assertEquals(
            "King James Version",
            readTranslationTitle(module("ENG_KJV.spb", "##spDataVersion:\t1\n##Title:\tKing James Version\n")),
        )
    }

    @Test
    fun `a space after the colon reads the same`() {
        assertEquals("Synodal", readTranslationTitle(module("RUS_SYN.spb", "##Title: Synodal\n")))
    }

    @Test
    fun `no separator at all still reads the title`() {
        assertEquals("Luther", readTranslationTitle(module("DEU_LUT.spb", "##Title:Luther\n")))
    }

    @Test
    fun `a module with no title header is named by its file stem`() {
        assertEquals("ENG_ACV", readTranslationTitle(module("ENG_ACV.spb", "1 Genesis 2\n-----\n")))
    }

    @Test
    fun `a file that is not there is named by its file stem`() {
        assertEquals("gone", readTranslationTitle(File(dir, "gone.spb")))
    }

    @Test
    fun `an empty title header stays empty rather than falling back`() {
        // What the full loader does with the same file: a module that says its name is nothing is not
        // a module that never said.
        assertEquals("", readTranslationTitle(module("blank.spb", "##Title: \n1 Genesis 1\n-----\n")))
    }

    @Test
    fun `a title buried past the header block is not looked for`() {
        // These callers scan a whole folder, so each file gets a bounded read: a directory of
        // multi-megabyte modules would otherwise stall the picker listing it.
        val buried = buildString {
            repeat(12) { appendLine("filler line $it") }
            appendLine("##Title:\tBuried Title")
        }
        assertEquals("deep", readTranslationTitle(module("deep.spb", buried)))
    }

    @Test
    fun `the title is found past the headers that precede it`() {
        assertEquals(
            "Berean",
            readTranslationTitle(
                module("bsb.spb", "##spDataVersion:\t1\n##Copyright:\tpublic domain\n##Title:\tBerean\n"),
            ),
        )
    }

    // ── Naming a whole folder ───────────────────────────────────────────────────────────────────

    @Test
    fun `every file in the folder is named`() {
        module("a.spb", "##Title:\tFirst\n")
        module("b.spb", "##Title:\tSecond\n")

        assertEquals(
            mapOf("a.spb" to "First", "b.spb" to "Second"),
            bibleDisplayNames(dir.absolutePath, listOf("a.spb", "b.spb")),
        )
    }

    @Test
    fun `with no folder configured there is nothing to name`() {
        assertEquals(emptyMap(), bibleDisplayNames("", listOf("a.spb")))
    }

    // ── Keeping the names unique ────────────────────────────────────────────────────────────────

    @Test
    fun `two files sharing a title are told apart by their folders`() {
        // The shape a real collection has: one folder per language, several holding an edition that
        // calls itself the same thing.
        assertEquals(
            mapOf(
                "ENG/a.spb" to "Holy Bible  (ENG)",
                "RUS/b.spb" to "Holy Bible  (RUS)",
                "unique.spb" to "Only One",
            ),
            uniqueDisplayNames(
                mapOf("ENG/a.spb" to "Holy Bible", "RUS/b.spb" to "Holy Bible", "unique.spb" to "Only One"),
            ),
        )
    }

    @Test
    fun `two files sharing a title and a folder are told apart by their names`() {
        assertEquals(
            mapOf("a.spb" to "Holy Bible  (a.spb)", "b.spb" to "Holy Bible  (b.spb)"),
            uniqueDisplayNames(mapOf("a.spb" to "Holy Bible", "b.spb" to "Holy Bible")),
        )
    }

    @Test
    fun `a folder of titled modules with a repeat stays reverse-mappable`() {
        module("ENG/a.spb", "##Title:\tHoly Bible\n")
        module("RUS/b.spb", "##Title:\tHoly Bible\n")
        module("unique.spb", "##Title:\tOnly One\n")

        val names = bibleDisplayNames(dir.absolutePath, listOf("ENG/a.spb", "RUS/b.spb", "unique.spb"))

        // The property the pickers actually depend on: a display name identifies one file.
        assertEquals(names.size, names.values.toSet().size, "display names must be unique: $names")
    }
}
