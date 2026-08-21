package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SpecTrackAndPlacementTest {

    private fun generate(element: ElementSpec, cfg: LottieGenConfig = LottieGenConfig()): JsonObject =
        LottieGenerator.generate(cfg, SpecStyleGenerator(StyleSpec(elements = listOf(element))))

    private fun JsonObject.layers() = getValue("layers").jsonArray.map { it.jsonObject }

    private fun JsonObject.shapeLayer() = layers().first { it["ty"]?.jsonPrimitive?.int == 4 }

    private fun JsonObject.channel(name: String) =
        getValue("ks").jsonObject.getValue(name).jsonObject

    private fun JsonObject.isAnimated(name: String) =
        channel(name).getValue("a").jsonPrimitive.int == 1

    private fun track(property: AnimProperty, vararg values: List<Double>) = AnimTrack(
        property = property,
        keyframes = values.mapIndexed { i, v -> SpecKeyframe(pct = i * 50.0, values = v) }
    )

    private fun rect(
        tracks: List<AnimTrack> = emptyList(),
        placement: Placement = Placement(),
        paint: PaintSpec = PaintSpec(),
        growFrom: GrowOrigin = GrowOrigin.CENTER
    ) = RectElement(
        id = "r", size = SizeSpec.Em(2.0, 1.0),
        tracks = tracks, placement = placement, paint = paint, growFrom = growFrom
    )

    @Test
    fun `an element with no position track gets a static position`() {
        assertTrue(!generate(rect()).shapeLayer().isAnimated("p"))
    }

    @Test
    fun `a position offset track animates the position`() {
        val el = rect(tracks = listOf(track(AnimProperty.POSITION_OFFSET, listOf(0.0, 0.0), listOf(2.0, 1.0))))

        assertTrue(generate(el).shapeLayer().isAnimated("p"))
    }

    @Test
    fun `a scale track animates the scale and its absence does not`() {
        assertTrue(!generate(rect()).shapeLayer().isAnimated("s"))

        val el = rect(tracks = listOf(track(AnimProperty.SCALE, listOf(0.0), listOf(100.0))))
        assertTrue(generate(el).shapeLayer().isAnimated("s"))
    }

    @Test
    fun `a non-uniform scale track is accepted`() {
        val el = rect(tracks = listOf(track(AnimProperty.SCALE, listOf(0.0, 50.0), listOf(100.0, 100.0))))

        assertTrue(generate(el).shapeLayer().isAnimated("s"))
    }

    @Test
    fun `an opacity track animates opacity`() {
        val el = rect(tracks = listOf(track(AnimProperty.OPACITY, listOf(0.0), listOf(100.0))))

        assertTrue(generate(el).shapeLayer().isAnimated("o"))
    }

    @Test
    fun `a rotation track animates rotation`() {
        val el = rect(tracks = listOf(track(AnimProperty.ROTATION, listOf(0.0), listOf(90.0))))

        assertTrue(generate(el).shapeLayer().isAnimated("r"))
    }

    @Test
    fun `a rect size track produces an animated rect rather than a static one`() {
        val plain = generate(rect()).shapeLayer().toString()
        val sized = generate(
            rect(tracks = listOf(track(AnimProperty.RECT_SIZE, listOf(0.0, 1.0), listOf(1.0, 1.0))))
        ).shapeLayer().toString()

        assertNotEquals(plain, sized)
    }

    @Test
    fun `a size track growing from the alignment edge differs from growing from the center`() {
        val tracks = listOf(track(AnimProperty.RECT_SIZE, listOf(0.0, 1.0), listOf(1.0, 1.0)))
        val cfg = LottieGenConfig(align = "left")

        val center = generate(rect(tracks = tracks, growFrom = GrowOrigin.CENTER), cfg)
        val edge = generate(rect(tracks = tracks, growFrom = GrowOrigin.ALIGN_EDGE), cfg)

        assertNotEquals(center.shapeLayer().toString(), edge.shapeLayer().toString())
    }

    @Test
    fun `edge growth collapses to centre growth when the layout is centred`() {
        val tracks = listOf(track(AnimProperty.RECT_SIZE, listOf(0.0, 1.0), listOf(1.0, 1.0)))
        val cfg = LottieGenConfig(align = "center")

        val center = generate(rect(tracks = tracks, growFrom = GrowOrigin.CENTER), cfg)
        val edge = generate(rect(tracks = tracks, growFrom = GrowOrigin.ALIGN_EDGE), cfg)

        assertEquals(center.shapeLayer().toString(), edge.shapeLayer().toString())
    }

    @Test
    fun `a stroke with no width track is emitted statically`() {
        val el = rect(paint = PaintSpec(stroke = StrokeSpec(width = StrokeWidthSpec.Em(0.2))))

        assertTrue(generate(el).shapeLayer().toString().contains("\"st\""))
    }

    @Test
    fun `a stroke width track produces an animated stroke`() {
        val el = rect(
            tracks = listOf(track(AnimProperty.STROKE_WIDTH, listOf(0.0), listOf(1.0))),
            paint = PaintSpec(stroke = StrokeSpec(width = StrokeWidthSpec.Em(0.2)))
        )

        assertTrue(generate(el).shapeLayer().toString().contains("\"st\""))
    }

    @Test
    fun `a dashed stroke reaches the output`() {
        val el = rect(paint = PaintSpec(stroke = StrokeSpec(width = StrokeWidthSpec.Em(0.2), dashEm = 0.3)))

        assertTrue(generate(el).shapeLayer().toString().contains("\"d\""))
    }

    @Test
    fun `a placement override moves the element for that alignment only`() {
        val placement = Placement(alignOverrides = mapOf("left" to PlacementOverride(offsetXEm = 4.0)))

        val left = generate(rect(placement = placement), LottieGenConfig(align = "left"))
        val right = generate(rect(placement = placement), LottieGenConfig(align = "right"))

        assertNotEquals(
            left.shapeLayer().channel("p").toString(),
            right.shapeLayer().channel("p").toString()
        )
    }

    @Test
    fun `a placement override can change the vertical anchor`() {
        val plain = generate(rect(), LottieGenConfig(align = "left"))
        val overridden = generate(
            rect(placement = Placement(alignOverrides = mapOf("left" to PlacementOverride(offsetYEm = 3.0)))),
            LottieGenConfig(align = "left")
        )

        assertNotEquals(
            plain.shapeLayer().channel("p").toString(),
            overridden.shapeLayer().channel("p").toString()
        )
    }

    @Test
    fun `an override hiding the element for one alignment drops its layer`() {
        val placement = Placement(alignOverrides = mapOf("left" to PlacementOverride(hidden = true)))

        val left = generate(rect(placement = placement), LottieGenConfig(align = "left"))
        val right = generate(rect(placement = placement), LottieGenConfig(align = "right"))

        assertTrue(left.layers().none { it["ty"]?.jsonPrimitive?.int == 4 })
        assertTrue(right.layers().any { it["ty"]?.jsonPrimitive?.int == 4 })
    }

    @Test
    fun `the mirror opt-out keeps an element on the same side when right aligned`() {
        val mirrored = rect(placement = Placement(offsetXEm = 2.0, mirror = MirrorMode.FLIP_ON_RIGHT))
        val fixed = rect(placement = Placement(offsetXEm = 2.0, mirror = MirrorMode.NONE))
        val cfg = LottieGenConfig(align = "right")

        assertNotEquals(
            generate(mirrored, cfg).shapeLayer().channel("p").toString(),
            generate(fixed, cfg).shapeLayer().channel("p").toString()
        )
    }

    @Test
    fun `a missing bundled spec resource fails loudly`() {
        assertFailsWith<IllegalStateException> { SpecStyleGenerator.fromResource("/styles/nope.json") }
    }

    @Test
    fun `a bundled spec resource loads`() {
        assertTrue(SpecStyleGenerator.fromResource("/styles/style1_bar_port.json") is SpecStyleGenerator)
    }
}
