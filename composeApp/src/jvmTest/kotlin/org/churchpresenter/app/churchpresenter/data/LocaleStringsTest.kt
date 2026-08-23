package org.churchpresenter.app.churchpresenter.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every `strings.xml` that ships, checked for the damage a hand- or bulk-edited translation causes.
 *
 * These files are not compiled: Compose Resources reads them at build time and a broken one fails
 * far from its cause, or — worse — loads with a key silently missing. The three faults below are
 * the ones that actually happen when 30-odd locales are edited in bulk: a bare `&` or `<` in a
 * translation, a key pasted twice, and a value left blank so the interface shows nothing at all
 * rather than falling back to English.
 */
class LocaleStringsTest {

    private val resourcesDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(6) {
            val here = dir ?: return@repeat
            listOf("resources/src/main/composeResources", "src/main/composeResources")
                .map { File(here, it) }
                .firstOrNull { it.isDirectory }
                ?.let { return@lazy it }
            dir = here.parentFile
        }
        error("could not find composeResources from ${System.getProperty("user.dir")}")
    }

    /** The default folder and every `values-<code>` one, each of which must hold a strings file. */
    private val stringFiles: List<File> by lazy {
        val folders = resourcesDir.listFiles()
            ?.filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
            .orEmpty()
            .sortedBy { it.name }
        assertTrue(folders.size > 1, "found no locale folders under $resourcesDir")
        folders.map { File(it, "strings.xml") }
    }

    private fun keysOf(file: File): List<String> =
        Regex("""<string name="([^"]+)">""").findAll(file.readText()).map { it.groupValues[1] }.toList()

    @Test
    fun `every strings file is well-formed xml`() {
        val factory = DocumentBuilderFactory.newInstance()
        val broken = stringFiles.mapNotNull { file ->
            runCatching { factory.newDocumentBuilder().parse(file) }
                .exceptionOrNull()
                ?.let { "${file.parentFile.name}: ${it.message}" }
        }

        assertTrue(
            broken.isEmpty(),
            "an unescaped & or < in a translation makes the whole locale unreadable: $broken",
        )
    }

    @Test
    fun `no strings file names the same key twice`() {
        val duplicated = stringFiles.mapNotNull { file ->
            val keys = keysOf(file)
            val dups = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (dups.isEmpty()) null else "${file.parentFile.name}: $dups"
        }

        assertTrue(duplicated.isEmpty(), "the later value silently wins: $duplicated")
    }

    @Test
    fun `no translation is blank`() {
        val blank = stringFiles.flatMap { file ->
            Regex("""<string name="([^"]+)"></string>""").findAll(file.readText())
                .map { "${file.parentFile.name}/${it.groupValues[1]}" }
        }

        assertTrue(
            blank.isEmpty(),
            "a blank value shows as nothing, where a missing key would have shown English: $blank",
        )
    }

    @Test
    fun `every key is one the default folder defines`() {
        val english = keysOf(File(resourcesDir, "values/strings.xml")).toSet()
        assertTrue(english.size > 1000, "the default folder looks truncated: ${english.size} keys")

        // A key no longer in the default folder is translated text nothing can ever read. Locales
        // that predate this test carry some; the assertion is that a locale adds no new ones.
        val stale = mapOf(
            "values-be" to 37, "values-cs" to 37, "values-de" to 37, "values-es" to 37,
            "values-et" to 37, "values-fr" to 37, "values-kk" to 37, "values-nl" to 37,
            "values-pl" to 37, "values-pt" to 37, "values-ro" to 37, "values-ru" to 37,
            "values-sk" to 37, "values-uk" to 37,
        )
        val grown = stringFiles.mapNotNull { file ->
            val folder = file.parentFile.name
            if (folder == "values") return@mapNotNull null
            val orphans = keysOf(file).filterNot { it in english }
            val allowed = stale[folder] ?: 0
            if (orphans.size <= allowed) null else "$folder: ${orphans.size} (allowed $allowed) ${orphans.take(5)}"
        }

        assertTrue(grown.isEmpty(), "these locales define keys the default folder does not: $grown")
    }
}
