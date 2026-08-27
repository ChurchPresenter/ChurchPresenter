package org.churchpresenter.bible

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A module renamed to what the church calls it, over what its `##Title:` says.
 *
 * The override is kept apart from the parsed title rather than written over it, because clearing a
 * rename has to give the module's own name back — a blank field is "use what the module says", not
 * a rename to nothing.
 */
class BibleNameOverrideTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-rename-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun module(name: String, content: String): File =
        File(dir, name).apply { parentFile.mkdirs(); writeText(content) }

    private fun loaded(title: String = "King James Version", fileName: String = "kjv.spb"): Bible {
        val file = module(fileName, SpbFixture.sampleContent(title))
        return Bible().also { it.loadFromSpb(file.absolutePath) }
    }

    // ── The loaded module ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a renamed module reports the name it was given`() {
        val bible = loaded().also { it.applyNameOverride("Authorised Version", "AV") }

        assertEquals("Authorised Version", bible.getBibleTitle())
        assertEquals("AV", bible.getBibleAbbreviation())
    }

    @Test
    fun `the abbreviation can be renamed on its own`() {
        val bible = loaded().also { it.applyNameOverride(null, "AV") }

        assertEquals("King James Version", bible.getBibleTitle())
        assertEquals("AV", bible.getBibleAbbreviation())
    }

    @Test
    fun `the name can be renamed on its own`() {
        val bible = loaded().also { it.applyNameOverride("Authorised Version", null) }

        assertEquals("Authorised Version", bible.getBibleTitle())
        assertEquals("KJV", bible.getBibleAbbreviation())
    }

    @Test
    fun `clearing a rename gives the module its own name back`() {
        // The bug this exists for: a cleared abbreviation stayed on the presentation screen.
        val bible = loaded().also { it.applyNameOverride("Authorised Version", "AV") }

        bible.applyNameOverride("", "")

        assertEquals("King James Version", bible.getBibleTitle())
        assertEquals("KJV", bible.getBibleAbbreviation())
    }

    @Test
    fun `a rename of whitespace is not a rename`() {
        val bible = loaded().also { it.applyNameOverride("   ", "  ") }

        assertEquals("King James Version", bible.getBibleTitle())
        assertEquals("KJV", bible.getBibleAbbreviation())
    }

    @Test
    fun `a rename is trimmed`() {
        val bible = loaded().also { it.applyNameOverride("  Authorised  ", " AV ") }

        assertEquals("Authorised", bible.getBibleTitle())
        assertEquals("AV", bible.getBibleAbbreviation())
    }

    @Test
    fun `the module's own title is still readable under a rename`() {
        val bible = loaded().also { it.applyNameOverride("Authorised Version", "AV") }

        assertEquals("King James Version", bible.getModuleTitle())
    }

    @Test
    fun `a rename does not touch the file it was loaded from`() {
        val file = module("kjv.spb", SpbFixture.sampleContent("King James Version"))
        Bible().also { it.loadFromSpb(file.absolutePath) }.applyNameOverride("Authorised", "AV")

        assertEquals("King James Version", readTranslationTitle(file))
    }

    // ── Naming a folder for a picker ────────────────────────────────────────────────────────────

    @Test
    fun `a rename wins over the module's own header`() {
        val file = module("kjv.spb", "##Title: King James Version\n")

        assertEquals("Authorised", readTranslationTitle(file, "Authorised"))
    }

    @Test
    fun `a blank rename leaves the header title in place`() {
        val file = module("kjv.spb", "##Title: King James Version\n")

        assertEquals("King James Version", readTranslationTitle(file, "  "))
    }

    @Test
    fun `only the renamed entries change in a folder listing`() {
        module("kjv.spb", "##Title: King James Version\n")
        module("rst.spb", "##Title: Synodal\n")

        assertEquals(
            mapOf("kjv.spb" to "Authorised", "rst.spb" to "Synodal"),
            bibleDisplayNames(dir.absolutePath, listOf("kjv.spb", "rst.spb"), mapOf("kjv.spb" to "Authorised")),
        )
    }

    @Test
    fun `two modules renamed to the same thing are still told apart`() {
        // A picker reverse-maps the chosen name back to its file, so typing one name twice has to
        // be qualified exactly as two matching headers are.
        module("a/kjv.spb", "##Title: King James Version\n")
        module("b/kjv.spb", "##Title: Synodal\n")

        val names = bibleDisplayNames(
            dir.absolutePath,
            listOf("a/kjv.spb", "b/kjv.spb"),
            mapOf("a/kjv.spb" to "Pew Bible", "b/kjv.spb" to "Pew Bible"),
        )

        assertEquals(listOf("Pew Bible  (a)", "Pew Bible  (b)"), names.values.toList())
    }

    @Test
    fun `titles are read without applying any rename`() {
        module("kjv.spb", "##Title: King James Version\n")

        assertEquals(
            mapOf("kjv.spb" to "King James Version"),
            bibleTitles(dir.absolutePath, listOf("kjv.spb")),
        )
    }

    @Test
    fun `already-read titles can be renamed without touching the disk`() {
        assertEquals(
            mapOf("kjv.spb" to "Authorised", "rst.spb" to "Synodal"),
            displayNamesFor(
                mapOf("kjv.spb" to "King James Version", "rst.spb" to "Synodal"),
                mapOf("kjv.spb" to "Authorised"),
            ),
        )
    }

    // ── The abbreviation a rename overrules ─────────────────────────────────────────────────────

    @Test
    fun `the default abbreviation is the one the loader would derive`() {
        assertEquals(loaded().getBibleAbbreviation(), defaultTranslationAbbreviation("King James Version", "kjv.spb"))
    }

    @Test
    fun `a module with no title falls back to its file name`() {
        assertEquals("ru_RST77", defaultTranslationAbbreviation("", "ru_RST77.spb"))
    }
}
