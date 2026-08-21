package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertTrue

class SpecTruncatedKeyframeTest {

    private fun generate(element: ElementSpec, cfg: LottieGenConfig = LottieGenConfig()): JsonObject =
        LottieGenerator.generate(cfg, SpecStyleGenerator(StyleSpec(elements = listOf(element))))

    private fun JsonObject.layers() = getValue("layers").jsonArray.map { it.jsonObject }

    private fun JsonObject.hasShapeLayer() = layers().any { it["ty"]?.jsonPrimitive?.int == 4 }

    private fun short(property: AnimProperty, vararg values: List<Double>) = AnimTrack(
        property = property,
        keyframes = values.mapIndexed { i, v -> SpecKeyframe(pct = i * 100.0, values = v) }
    )

    private fun rect(vararg tracks: AnimTrack, paint: PaintSpec = PaintSpec()) = RectElement(
        id = "r", size = SizeSpec.Em(2.0, 1.0), tracks = tracks.toList(), paint = paint
    )

    @Test
    fun `a size track with no values falls back rather than throwing`() {
        val el = rect(short(AnimProperty.RECT_SIZE, emptyList(), emptyList()))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a size track supplying only a width falls back for the height`() {
        val el = rect(short(AnimProperty.RECT_SIZE, listOf(0.0), listOf(1.0)))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a scale track supplying one value applies it to both axes`() {
        val el = rect(short(AnimProperty.SCALE, listOf(50.0), listOf(100.0)))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a scale track with no values falls back to unscaled`() {
        val el = rect(short(AnimProperty.SCALE, emptyList(), emptyList()))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `an opacity track with no values falls back to opaque`() {
        val el = rect(short(AnimProperty.OPACITY, emptyList(), emptyList()))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a position track with no values falls back to the rest point`() {
        val el = rect(short(AnimProperty.POSITION_OFFSET, emptyList(), emptyList()))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a position track supplying only dx falls back for dy`() {
        val el = rect(short(AnimProperty.POSITION_OFFSET, listOf(0.0), listOf(2.0)))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a rotation track with no values falls back to unrotated`() {
        val el = rect(short(AnimProperty.ROTATION, emptyList(), emptyList()))

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a stroke width track with no values falls back to the static width`() {
        val el = rect(
            short(AnimProperty.STROKE_WIDTH, emptyList(), emptyList()),
            paint = PaintSpec(stroke = StrokeSpec(width = StrokeWidthSpec.Em(0.2)))
        )

        assertTrue(generate(el).hasShapeLayer())
    }

    @Test
    fun `a polygon vertex missing its y coordinate falls back to zero`() {
        val poly = PolygonElement(
            id = "p",
            verticesEm = listOf(listOf(0.0), listOf(2.0), emptyList())
        )

        assertTrue(generate(poly).hasShapeLayer())
    }

    @Test
    fun `an image whose resolved height is zero draws nothing`() {
        val flat = ImageElement(
            id = "img", name = "Art",
            dataUri = "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            naturalW = 100, naturalH = 50, size = SizeSpec.Em(2.0, 0.0)
        )

        assertTrue(generate(flat).layers().none { it["ty"]?.jsonPrimitive?.int == 2 })
    }

    @Test
    fun `a repeat fitted to a basis with one copy needs no spacing`() {
        val poly = PolygonElement(
            id = "p",
            verticesEm = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0)),
            repeat = RepeatSpec(copies = 1, fitWidthTo = WidthBasis.NAME)
        )

        assertTrue(generate(poly).hasShapeLayer())
    }

    @Test
    fun `a repeat fitted to a basis spaces its copies across it`() {
        val poly = PolygonElement(
            id = "p",
            verticesEm = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0)),
            repeat = RepeatSpec(copies = 3, fitWidthTo = WidthBasis.TEXT_BLOCK)
        )

        assertTrue(generate(poly).hasShapeLayer())
    }
}
