package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.model.AnimationStyle
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.StyleCatalog
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.ui.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the shipped registry styles (13-60). Every entry must resolve to a decodable spec
 * that generates a non-empty composition with zero interpreter warnings across the config
 * matrix operators can actually reach — three alignments crossed with the logo and
 * background toggles and the hide-name/hide-info toggles.
 *
 * A style that warns here renders with silently dropped geometry in the app, so the
 * zero-warning assertion is the real bar, not the layer count.
 */
class RegistryStyleSmokeTest {

    private companion object {
        /** 1x1 transparent PNG — enough for the logo layer to be built and placed. */
        const val STUB_LOGO = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ" +
            "AAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
    }

    private val registry = StyleRegistry.load().entries

    @Test
    fun registryShipsTheNewStyles() {
        assertEquals(48, registry.size, "expected 48 registered spec styles (13-36 and 37-60)")

        val compiledIds = AnimationStyle.entries.map { it.id }.toSet()
        val ids = registry.map { it.id }
        assertEquals(ids.distinct(), ids, "registry ids must be unique")
        assertTrue(
            ids.none { it in compiledIds },
            "registry must add new ids, not replace compiled styles: ${ids.filter { it in compiledIds }}"
        )
        assertEquals((13..60).map { it.toString() }, ids.sortedBy { it.toInt() })
    }

    @Test
    fun pickerListsEveryStyle() {
        val entries = StyleCatalog.entries
        assertEquals(60, entries.size, "12 compiled + 48 registered")
        assertEquals((1..60).map { it.toString() }, entries.map { it.id }, "numeric ordering")
        // Every registered style must carry a spec resource; the compiled dozen must not.
        assertTrue(entries.take(12).all { it.specResource == null })
        assertTrue(entries.drop(12).all { it.specResource != null })
    }

    @Test
    fun everyRegisteredStyleUsesItsLocalizedLabel() {
        for (entry in registry) {
            val label = StyleCatalog.labelFor(entry.id)
            assertEquals(
                Strings.styleLabel(entry.id), label,
                "style ${entry.id} must use its style_<id> bundle key, not the fallback format"
            )
            assertTrue(label.contains(entry.id), "label '$label' should carry the style number")
        }
    }

    /**
     * Every element in a spec must survive into the composition as its own layer. Zero
     * warnings alone would not catch an element the layout silently declines to build,
     * which is how a decoration disappears in the app while the tests stay green.
     */
    @Test
    fun everyElementBecomesALayer() {
        for (entry in registry) {
            val spec = SpecJson.decode(
                javaClass.getResourceAsStream(entry.resource)!!
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            )
            val generator = SpecStyleGenerator(spec)
            val json = LottieGenerator.generate(
                LottieGenConfig(
                    style = entry.id,
                    logoEnabled = true,
                    bgEnabled = true,
                    // The logo layer is only built when real image data is present, so a
                    // stub asset is required to exercise the logo slot at all.
                    logoData = STUB_LOGO,
                    logoW = 64,
                    logoH = 64
                ),
                generator
            )
            val layerNames = json["layers"]!!.jsonArray
                .map { it.jsonObject["nm"]!!.jsonPrimitive.content }
                .toSet()
            for (element in spec.elements) {
                assertTrue(
                    element.name in layerNames,
                    "${entry.resource}: element '${element.name}' (${element.id}) built no layer; " +
                        "layers were $layerNames"
                )
            }
        }
    }

    @Test
    @Suppress("NestedBlockDepth") // Every style x every config axis; the nesting is the matrix.
    fun everyRegisteredStyleGeneratesCleanlyAcrossTheConfigMatrix() {
        for (entry in registry) {
            val stream = assertNotNull(
                javaClass.getResourceAsStream(entry.resource),
                "missing spec resource ${entry.resource} for style ${entry.id}"
            )
            val spec = SpecJson.decode(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            assertEquals(entry.id, spec.id, "${entry.resource}: spec id must match its registry id")
            assertTrue(spec.elements.isNotEmpty(), "${entry.resource} has no elements")

            for (align in listOf("left", "center", "right")) {
                for (logo in listOf(false, true)) {
                    for (bg in listOf(true, false)) {
                        val generator = SpecStyleGenerator(spec)
                        val json = LottieGenerator.generate(
                            LottieGenConfig(
                                style = entry.id,
                                align = align,
                                logoEnabled = logo,
                                bgEnabled = bg,
                                // Without data the logo layer is skipped entirely, which
                                // would make the logo=true half of this matrix vacuous.
                                logoData = if (logo) STUB_LOGO else null,
                                logoW = if (logo) 64 else 0,
                                logoH = if (logo) 64 else 0
                            ),
                            generator
                        )
                        val where = "${entry.resource} [align=$align logo=$logo bg=$bg]"
                        assertTrue(json["layers"]!!.jsonArray.isNotEmpty(), "$where produced no layers")
                        assertTrue(generator.lastWarnings.isEmpty(), "$where warned: ${generator.lastWarnings}")
                    }
                }
            }

            // Hiding either line must not break layout or drop the composition.
            for ((hideName, hideInfo) in listOf(true to false, false to true)) {
                val generator = SpecStyleGenerator(spec)
                val json = LottieGenerator.generate(
                    LottieGenConfig(style = entry.id, hideName = hideName, hideInfo = hideInfo),
                    generator
                )
                val where = "${entry.resource} [hideName=$hideName hideInfo=$hideInfo]"
                assertTrue(json["layers"]!!.jsonArray.isNotEmpty(), "$where produced no layers")
                assertTrue(generator.lastWarnings.isEmpty(), "$where warned: ${generator.lastWarnings}")
            }
        }
    }
}
