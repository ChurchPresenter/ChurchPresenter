package org.churchpresenter.dictionary.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.io.FileNotFoundException

/**
 * The files this module ships, as the app reads them: every name [DictionaryFiles] defines is on the
 * classpath, and a name it does not is an error rather than an empty dictionary.
 */
class DictionaryFilesTest {

    @Test
    fun `every bundled file is shipped`() = runBlocking {
        listOf(
            DictionaryFiles.STRONGS_HEBREW, DictionaryFiles.STRONGS_GREEK,
            DictionaryFiles.STRONGS_HEBREW_RU, DictionaryFiles.STRONGS_GREEK_RU,
            DictionaryFiles.INTERLINEAR_GREEK, DictionaryFiles.INTERLINEAR_HEBREW,
        ).forEach { name ->
            assertTrue(DictionaryFiles.Bundled.read(name).isNotEmpty(), "$name is empty or missing")
        }
    }

    @Test
    fun `the bundled Greek dictionary reads as Strong's entries`() = runBlocking {
        val bytes = DictionaryFiles.Bundled.read(DictionaryFiles.STRONGS_GREEK)
        val entries = Json { ignoreUnknownKeys = true }.decodeFromString<List<StrongsEntry>>(bytes.decodeToString())

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.isGreek }, "the Greek file holds only G numbers")
    }

    @Test
    fun `a file the module does not ship is an error`() {
        assertFailsWith<FileNotFoundException> { runBlocking { DictionaryFiles.Bundled.read("nope.json") } }
    }

    @Test
    fun `Russian has its own files and everything else reads English`() {
        assertEquals(
            DictionaryFiles.STRONGS_HEBREW_RU to DictionaryFiles.STRONGS_GREEK_RU,
            DictionaryFiles.strongsFor("ru"),
        )
        assertEquals(DictionaryFiles.STRONGS_HEBREW to DictionaryFiles.STRONGS_GREEK, DictionaryFiles.strongsFor("en"))
        assertEquals(DictionaryFiles.STRONGS_HEBREW to DictionaryFiles.STRONGS_GREEK, DictionaryFiles.strongsFor("de"))
    }
}
