package ui

import java.util.Locale
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The translated bundles against the English one.
 *
 * `ResourceBundle` falls back to English for a key a locale lacks, so a missing translation shows up
 * as a stray English word inside an otherwise translated screen rather than as a crash — invisible
 * to anyone not running that locale. A *stale* key is the opposite problem: harmless at runtime, but
 * it hides the fact that the English string it once translated is gone.
 *
 * The placeholder check is the one that would actually throw: `String.format` raises
 * `MissingFormatArgumentException` if a translation introduces a `%s` the call site does not supply,
 * and silently drops text if it omits one.
 */
class StringBundleTest {

    private val locales = listOf("be", "cs", "de", "kk", "pl", "ru", "uk")

    private fun load(name: String): Properties {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing $name" }
        return stream.use { input -> Properties().apply { load(input.reader(Charsets.UTF_8)) } }
    }

    private fun english() = load("converter_strings.properties")

    private fun placeholders(value: String): List<String> =
        Regex("%[sd]").findAll(value).map { it.value }.sorted().toList()

    @Test
    fun `every locale defines exactly the English keys`() {
        val expected = english().stringPropertyNames()
        for (locale in locales) {
            val actual = load("converter_strings_$locale.properties").stringPropertyNames()
            assertEquals(emptySet(), expected - actual, "$locale is missing keys")
            assertEquals(emptySet(), actual - expected, "$locale has keys English no longer defines")
        }
    }

    @Test
    fun `every translation uses the same format placeholders as the English string`() {
        val english = english()
        for (locale in locales) {
            val translated = load("converter_strings_$locale.properties")
            for (key in english.stringPropertyNames()) {
                val source = english.getProperty(key)
                val target = translated.getProperty(key) ?: continue
                assertEquals(
                    placeholders(source),
                    placeholders(target),
                    "$locale/$key: \"$source\" vs \"$target\""
                )
            }
        }
    }

    @Test
    fun `no translation is blank`() {
        for (locale in locales) {
            val translated = load("converter_strings_$locale.properties")
            for (key in translated.stringPropertyNames()) {
                assertTrue(translated.getProperty(key).isNotBlank(), "$locale/$key is blank")
            }
        }
    }

    @Test
    fun `every string the UI asks for by name resolves in English`() {
        // These are built at runtime from a format id, so a renamed id would otherwise surface as a
        // MissingResourceException the moment someone clicked that rail entry.
        val english = english()
        for (source in SongSources.all) {
            assertTrue(english.containsKey("source_desc_${source.id}"), source.id)
            assertTrue(english.containsKey("source_accepts_${source.id}"), source.id)
        }
    }

    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
        Strings.setLocale(systemLocale)
    }

    @Test
    fun `the chosen language is used, not the machine's`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        Strings.setLocale(Locale.forLanguageTag("de"))
        assertEquals(load("converter_strings_de.properties").getProperty("tab_bibles"), Strings.tabBibles)
    }

    @Test
    fun `a language with no bundle falls back to English, not to the machine's`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        Strings.setLocale(Locale.forLanguageTag("fr"))
        assertEquals(english().getProperty("tab_bibles"), Strings.tabBibles)
    }
}
