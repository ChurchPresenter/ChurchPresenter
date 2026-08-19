package lottiegen

import lottiegen.editor.EditorViewModel
import lottiegen.model.AnimationStyle
import lottiegen.model.LottieAlignment
import lottiegen.model.StyleCatalog
import lottiegen.model.TIMING_PRESETS
import lottiegen.model.TextTransform
import lottiegen.model.TimingPreset
import lottiegen.spec.RegistryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StyleCatalogAndModelTest {

    // ── Enum id round trips ───────────────────────────────────────────────────

    @Test
    fun `every alignment survives an id round trip and has a label`() {
        LottieAlignment.entries.forEach {
            assertEquals(it, LottieAlignment.fromId(it.id))
            assertTrue(it.label.isNotBlank(), "${it.name} has no label")
        }
    }

    @Test
    fun `every text transform survives an id round trip and has a label`() {
        TextTransform.entries.forEach {
            assertEquals(it, TextTransform.fromId(it.id))
            assertTrue(it.label.isNotBlank(), "${it.name} has no label")
        }
    }

    @Test
    fun `an unknown alignment id fails loudly rather than silently defaulting`() {
        assertFailsWith<NoSuchElementException> { LottieAlignment.fromId("diagonal") }
    }

    @Test
    fun `an unknown text transform id fails loudly rather than silently defaulting`() {
        assertFailsWith<NoSuchElementException> { TextTransform.fromId("smallcaps") }
    }

    // ── Timing presets ────────────────────────────────────────────────────────

    @Test
    fun `each built-in timing preset resolves a translated label`() {
        TIMING_PRESETS.forEach {
            assertTrue(it.label.isNotBlank(), "${it.labelKey} has no label")
            assertTrue(it.label != it.labelKey, "${it.labelKey} fell through to its raw key")
        }
    }

    @Test
    fun `an unrecognised timing key shows itself rather than blank`() {
        assertEquals("custom", TimingPreset("custom", 1f, 1f).label)
    }

    // ── StyleCatalog ──────────────────────────────────────────────────────────

    @Test
    fun `the catalog lists every compiled style`() {
        val built = StyleCatalog.build(AnimationStyle.entries, emptyList())

        assertEquals(AnimationStyle.entries.map { it.id }.toSet(), built.map { it.id }.toSet())
        assertTrue(built.all { it.specResource == null }, "a compiled style has no spec resource")
    }

    @Test
    fun `a registry entry with a new id adds a picker entry carrying its resource`() {
        val built = StyleCatalog.build(
            AnimationStyle.entries,
            listOf(RegistryEntry("99", "Ribbon", "/styles/style99_ribbon.json"))
        )

        val added = assertNotNull(built.firstOrNull { it.id == "99" })
        assertEquals("/styles/style99_ribbon.json", added.specResource)
    }

    @Test
    fun `a registry entry colliding with a compiled id does not add a second picker entry`() {
        val collide = AnimationStyle.entries.first().id
        val built = StyleCatalog.build(
            AnimationStyle.entries,
            listOf(RegistryEntry(collide, "Override", "/styles/override.json"))
        )

        assertEquals(1, built.count { it.id == collide })
        assertEquals(null, built.first { it.id == collide }.specResource, "the compiled entry stays")
    }

    @Test
    fun `duplicate registry ids collapse to one entry`() {
        val built = StyleCatalog.build(
            emptyList(),
            listOf(
                RegistryEntry("99", "First", "/styles/first.json"),
                RegistryEntry("99", "Second", "/styles/second.json"),
            )
        )

        assertEquals(1, built.size)
        assertEquals("/styles/first.json", built.single().specResource)
    }

    @Test
    fun `entries are ordered numerically, with non-numeric ids last`() {
        val built = StyleCatalog.build(
            emptyList(),
            listOf(
                RegistryEntry("custom", "C", "/styles/c.json"),
                RegistryEntry("30", "B", "/styles/b.json"),
                RegistryEntry("4", "A", "/styles/a.json"),
            )
        )

        assertEquals(listOf("4", "30", "custom"), built.map { it.id })
    }

    @Test
    fun `labelFor falls back to the id when nothing matches`() {
        assertEquals("no-such-style", StyleCatalog.labelFor("no-such-style"))
    }

    // ── Editor template ───────────────────────────────────────────────────────

    @Test
    fun `the bundled template loads as a real spec`() {
        val spec = EditorViewModel.templateSpec()

        assertTrue(spec.elements.isNotEmpty(), "the default template should not be blank")
    }

    @Test
    fun `a missing template resource degrades to a blank spec`() {
        val spec = EditorViewModel.templateSpec("/styles/does-not-exist.json")

        assertEquals(emptyList(), spec.elements)
    }

    @Test
    fun `a template that is not spec JSON degrades to a blank spec`() {
        // a real classpath resource that is not a spec
        val spec = EditorViewModel.templateSpec("/lottiegen_strings.properties")

        assertEquals(emptyList(), spec.elements)
    }
}
