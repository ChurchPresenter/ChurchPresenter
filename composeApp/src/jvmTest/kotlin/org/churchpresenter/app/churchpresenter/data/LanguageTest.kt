package org.churchpresenter.app.churchpresenter.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The languages the interface offers, checked against the translations that actually ship.
 *
 * [Language] is what fills the language picker, and the string resources live in `values-<code>`
 * folders that nothing in the code references by name — so the two halves can drift apart silently
 * in either direction. A language added to the enum without a folder puts an option in the picker
 * that falls back to English; a folder with no enum entry is translation work nobody can select.
 * Neither shows up as a compile error, and neither is obvious to anyone who does not read that
 * language.
 *
 * English is the exception on purpose: it lives in the default `values` folder, which is also the
 * fallback for every other language.
 */
class LanguageTest {

    /**
     * The `composeResources` folder, found by walking up from the working directory — Gradle may
     * run tests from either the repository root or the module, and this should not depend on which.
     */
    private val resourcesDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(6) {
            val here = dir ?: return@repeat
            listOf("composeApp/src/jvmMain/composeResources", "src/jvmMain/composeResources")
                .map { File(here, it) }
                .firstOrNull { it.isDirectory }
                ?.let { return@lazy it }
            dir = here.parentFile
        }
        error("could not find composeResources from ${System.getProperty("user.dir")}")
    }

    private val translationFolders: Set<String> by lazy {
        resourcesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.map { it.name.removePrefix("values-") }
            ?.toSet()
            .orEmpty()
    }

    // ── The enum itself ─────────────────────────────────────────────────────────

    @Test
    fun `every language has its own code`() {
        val codes = Language.entries.map { it.code }

        assertEquals(codes.size, codes.toSet().size, "two languages sharing a code would load each other's strings")
    }

    @Test
    fun `every language has its own name`() {
        val names = Language.entries.map { it.nativeName }

        assertEquals(names.size, names.toSet().size, "the picker shows these; duplicates cannot be told apart")
    }

    @Test
    fun `every name is written in its own language`() {
        // Not a rule a machine can check in general, but the two-character check catches an entry
        // left blank or filled in with the code by mistake.
        Language.entries.forEach {
            assertTrue(it.nativeName.length >= 2, "${it.name} has no readable name: \"${it.nativeName}\"")
            assertTrue(it.nativeName != it.code, "${it.name} is showing its code rather than its name")
        }
    }

    @Test
    fun `codes are the two-letter form the resource folders use`() {
        Language.entries.forEach {
            assertTrue(
                Regex("[a-z]{2}").matches(it.code),
                "${it.name} has code \"${it.code}\", which would not match any values- folder",
            )
        }
    }

    @Test
    fun `english is one of the languages`() {
        assertNotNull(Language.entries.firstOrNull { it.code == "en" }, "English is the fallback for every other one")
    }

    // ── The enum against what ships ─────────────────────────────────────────────

    @Test
    fun `every language offered has translations to show`() {
        val missing = Language.entries
            .filter { it != Language.ENGLISH }
            .filter { it.code !in translationFolders }

        assertTrue(
            missing.isEmpty(),
            "these are in the picker with no values- folder, so they would quietly display English: " +
                missing.map { "${it.name} (${it.code})" },
        )
    }

    @Test
    fun `english is served by the default folder`() {
        assertTrue(File(resourcesDir, "values").isDirectory, "the default folder is the English one")
        assertTrue(
            "en" !in translationFolders,
            "a values-en folder would duplicate the default one and the two could drift apart",
        )
    }

    @Test
    fun `every translation that ships can be selected`() {
        val codes = Language.entries.map { it.code }.toSet()
        val orphans = translationFolders - codes

        assertTrue(
            orphans.isEmpty(),
            "these values- folders have no entry in Language, so nobody can pick them: $orphans",
        )
    }

    @Test
    fun `every language folder holds a strings file`() {
        val empty = translationFolders.filterNot { File(resourcesDir, "values-$it/strings.xml").isFile }

        assertTrue(empty.isEmpty(), "a values- folder with no strings.xml translates nothing: $empty")
    }
}
