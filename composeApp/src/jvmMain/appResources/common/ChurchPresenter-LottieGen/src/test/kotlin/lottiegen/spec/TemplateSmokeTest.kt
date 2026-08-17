package lottiegen.spec

import kotlinx.serialization.json.jsonArray
import lottiegen.editor.EditorViewModel
import lottiegen.lottie.LottieGenerator
import lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards every bundled template (style ports, vine demo, starter designs — and any
 * added later): each must decode and generate a non-empty composition with zero
 * interpreter warnings at every alignment.
 */
class TemplateSmokeTest {

    @Test
    fun everyBundledTemplateDecodesAndGeneratesCleanly() {
        for (template in EditorViewModel.BUNDLED_TEMPLATES) {
            val stream = assertNotNull(
                javaClass.getResourceAsStream(template.resource),
                "missing template resource ${template.resource}"
            )
            val spec = SpecJson.decode(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            assertTrue(spec.elements.isNotEmpty(), "${template.resource} has no elements")

            for (align in listOf("left", "center", "right")) {
                val generator = SpecStyleGenerator(spec)
                val json = LottieGenerator.generate(LottieGenConfig(align = align), generator)
                assertTrue(
                    json["layers"]!!.jsonArray.isNotEmpty(),
                    "${template.resource} produced no layers at $align"
                )
                assertTrue(
                    generator.lastWarnings.isEmpty(),
                    "${template.resource} warned at $align: ${generator.lastWarnings}"
                )
            }
        }
    }
}
