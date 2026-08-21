package org.churchpresenter.lottiegen

import org.churchpresenter.lottiegen.model.CANVAS_PRESETS
import org.churchpresenter.lottiegen.model.TIMING_PRESETS
import org.churchpresenter.lottiegen.model.defaultColorThemes
import org.churchpresenter.lottiegen.persistence.StyleSpecStorage
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.StyleSpec
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.lottiegen.model.TimingPreset

/**
 * Saving and loading Style Editor projects, plus the built-in preset tables.
 *
 * Storage failures are handled by returning null/false rather than throwing, because these run
 * from UI callbacks where an exception would take the editor down and lose unsaved work. The
 * slug rules matter for the same reason: a project name is free text and becomes a file name.
 */
class StyleSpecStorageTest {

    private val temp: File = Files.createTempDirectory("lottiegen-storage-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun spec(name: String = "Project") =
        StyleSpec(name = name, elements = listOf(RectElement(id = "rect1", name = "Banner")))

    // ── Round trip ────────────────────────────────────────────────────────────

    @Test
    fun `a saved spec loads back identical`() {
        val file = File(temp, "project.json")
        assertTrue(StyleSpecStorage.save(spec("My Lower Third"), file))
        assertEquals(spec("My Lower Third"), StyleSpecStorage.load(file))
    }

    @Test
    fun `the saved file is readable JSON rather than an opaque blob`() {
        val file = File(temp, "project.json")
        StyleSpecStorage.save(spec("Readable"), file)
        val text = file.readText()
        assertTrue(text.contains("Readable"), "the project name is legible in the file")
        assertTrue(text.trimStart().startsWith("{"))
    }

    @Test
    fun `an element's identity and ordering survive the round trip`() {
        val original = StyleSpec(
            name = "Ordered",
            elements = listOf(RectElement(id = "a"), RectElement(id = "b"), RectElement(id = "c")),
        )
        val file = File(temp, "ordered.json")
        StyleSpecStorage.save(original, file)
        // Element order is z-order, so a reordering save would silently restack the design.
        assertEquals(listOf("a", "b", "c"), StyleSpecStorage.load(file)!!.elements.map { it.id })
    }

    // ── Failure handling ──────────────────────────────────────────────────────

    @Test
    fun `loading a corrupt file returns null rather than throwing`() {
        val file = File(temp, "corrupt.json").apply { writeText("{ not valid json") }
        assertNull(StyleSpecStorage.load(file), "the editor stays up and reports the failure itself")
    }

    @Test
    fun `loading a missing file returns null`() {
        assertNull(StyleSpecStorage.load(File(temp, "does-not-exist.json")))
    }

    @Test
    fun `saving into a directory that cannot be written reports false rather than throwing`() {
        // A directory in place of the file is the simplest portable way to make the write fail.
        val blocked = File(temp, "blocked.json").apply { mkdirs() }
        assertTrue(!StyleSpecStorage.save(spec(), blocked))
    }

    @Test
    fun `deleting reports whether the file went away`() {
        val file = File(temp, "gone.json").apply { writeText("{}") }
        assertTrue(StyleSpecStorage.delete(file))
        assertTrue(!file.exists())
        assertTrue(!StyleSpecStorage.delete(file), "deleting again reports false")
    }

    // ── Slugs ─────────────────────────────────────────────────────────────────

    @Test
    fun `a name becomes a lower-case hyphenated slug`() {
        assertEquals("my-lower-third", StyleSpecStorage.slugify("My Lower Third"))
    }

    @Test
    fun `punctuation and repeated separators collapse`() {
        assertEquals("sunday-service-2024", StyleSpecStorage.slugify("Sunday / Service — 2024!"))
    }

    @Test
    fun `leading and trailing separators are trimmed off`() {
        assertEquals("welcome", StyleSpecStorage.slugify("  ...Welcome...  "))
    }

    @Test
    fun `a name with nothing usable falls back rather than producing an empty file name`() {
        assertEquals("untitled", StyleSpecStorage.slugify("!!!"))
        assertEquals("untitled", StyleSpecStorage.slugify(""))
        assertEquals("untitled", StyleSpecStorage.slugify("   "))
    }

    @Test
    fun `a non-Latin name still yields a usable file name`() {
        // Cyrillic has no ASCII slug, so it must fall back rather than produce "".
        assertEquals("untitled", StyleSpecStorage.slugify("Нижняя треть"))
    }

    // ── Preset tables ─────────────────────────────────────────────────────────

    @Test
    fun `every canvas preset has a sane pixel size`() {
        assertTrue(CANVAS_PRESETS.isNotEmpty())
        for (preset in CANVAS_PRESETS) {
            assertTrue(preset.width > 0 && preset.height > 0, "${preset.label} has a real size")
            assertTrue(preset.label.isNotBlank())
        }
        assertEquals(
            CANVAS_PRESETS.size,
            CANVAS_PRESETS.map { it.label }.distinct().size,
            "labels are unique so the picker cannot show two identical rows",
        )
    }

    @Test
    fun `the landscape and portrait presets are the same size transposed`() {
        val landscape = CANVAS_PRESETS.single { it.label == "16:9" }
        val portrait = CANVAS_PRESETS.single { it.label == "9:16" }
        assertEquals(landscape.width, portrait.height)
        assertEquals(landscape.height, portrait.width)
    }

    @Test
    fun `timing presets get slower down the list`() {
        assertEquals(
            TIMING_PRESETS.map { it.animDuration }.sorted(),
            TIMING_PRESETS.map { it.animDuration },
            "fast, medium, slow in that order",
        )
        assertTrue(TIMING_PRESETS.all { it.animDuration > 0f && it.holdDuration > 0f })
    }

    @Test
    fun `an unknown timing key falls back to showing the key itself`() {
        assertEquals("custom", TimingPreset("custom", 1f, 1f).label)
    }

    @Test
    fun `every colour theme defines all five colours as hex`() {
        val themes = defaultColorThemes()
        assertTrue(themes.isNotEmpty())
        val hex = Regex("^#[0-9A-Fa-f]{6}$")
        for (theme in themes) {
            val c = theme.colors
            for ((label, value) in listOf(
                "name" to c.nameColor, "info" to c.infoColor, "accent" to c.accentColor,
                "bg" to c.bgColor, "border" to c.borderColor,
            )) {
                assertTrue(hex.matches(value), "${theme.name} $label is '$value', not a hex colour")
            }
        }
    }

    @Test
    fun `theme names are distinct`() {
        val names = defaultColorThemes().map { it.name }
        assertEquals(names.size, names.distinct().size, "the theme picker cannot show duplicates")
    }

    @Test
    fun `every theme is fully opaque by default`() {
        for (theme in defaultColorThemes()) {
            val c = theme.colors
            assertEquals(100, c.nameColorAlpha, "${theme.name} name alpha")
            assertEquals(100, c.bgColorAlpha, "${theme.name} background alpha")
        }
    }
}
