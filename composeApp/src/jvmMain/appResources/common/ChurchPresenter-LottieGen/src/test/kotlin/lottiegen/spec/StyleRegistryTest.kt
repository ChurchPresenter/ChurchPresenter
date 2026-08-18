package lottiegen.spec

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lottiegen.lottie.LottieGenerator
import lottiegen.model.AnimationStyle
import lottiegen.model.StyleCatalog
import lottiegen.model.StyleInfo
import lottiegen.lottie.styles.Style1Bar
import lottiegen.lottie.styles.StyleGenerator
import lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Registry mechanics: round-trip, dispatch overlay (registry wins, corrupt entries are
 * skipped) and the picker catalog merge rules.
 */
class StyleRegistryTest {

    @Test
    fun registryRoundTrips() {
        val registry = StyleRegistry(
            listOf(
                RegistryEntry("13", "Ribbon", "/styles/style13_ribbon.json"),
                RegistryEntry("3", "Circular v2", "/styles/style3_circular_port.json")
            )
        )
        assertEquals(registry, StyleRegistry.decode(StyleRegistry.encode(registry)))
    }

    @Test
    fun bundledRegistryLoads() {
        // The shipped registry is empty; loading must not fail.
        StyleRegistry.load()
    }

    @Test
    fun overlayReplacesAndAddsAndSkipsCorrupt() {
        val compiled = mapOf<String, StyleGenerator>("1" to Style1Bar())
        val overlaid = LottieGenerator.overlayRegistry(
            compiled,
            listOf(
                // Replaces the compiled Style 1 with the (verified-equivalent) port spec.
                RegistryEntry("1", "Bar v2", "/styles/style1_bar_port.json"),
                // Adds a new style id from an existing bundled spec.
                RegistryEntry("13", "Vine", "/styles/demo_vine.json"),
                // Corrupt: missing resource must be skipped without throwing.
                RegistryEntry("14", "Ghost", "/styles/does_not_exist.json")
            )
        )
        assertTrue(overlaid["1"] is SpecStyleGenerator, "registry entry must replace the compiled style")
        assertTrue(overlaid["13"] is SpecStyleGenerator, "registry entry must add a new style")
        assertTrue("14" !in overlaid, "corrupt registry entry must be skipped")

        // The added style actually generates through the normal entry point's overload.
        val json = LottieGenerator.generate(LottieGenConfig(style = "13"), overlaid["13"])
        assertTrue(json["layers"]!!.jsonArray.isNotEmpty())
        assertEquals(
            "Vine",
            json["layers"]!!.jsonArray.map { it.jsonObject }
                .first { it["nm"]!!.jsonPrimitive.content == "Vine" }["nm"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun catalogAppendsNewIdsAndSkipsCollisions() {
        val entries = StyleCatalog.build(
            AnimationStyle.entries,
            listOf(
                RegistryEntry("13", "Ribbon", "/styles/a.json"),
                // Collides with compiled Style 2: renderer swap only, no extra picker row.
                RegistryEntry("2", "Boxed v2", "/styles/b.json"),
                // Duplicate new id: first one wins.
                RegistryEntry("13", "Ribbon Again", "/styles/c.json")
            )
        )
        assertEquals(13, entries.size, "12 compiled + 1 new registry id")
        assertEquals(List(13) { (it + 1).toString() }, entries.map { it.id }, "numeric ordering")
        val added: StyleInfo = entries.last()
        assertEquals("13", added.id)
        assertEquals("/styles/a.json", added.specResource)
        assertTrue(added.label.contains("Ribbon"), "label falls back to the spec name format")
        assertTrue(entries.first { it.id == "2" }.specResource == null, "collision keeps the compiled entry")
    }
}
