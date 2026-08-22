package org.churchpresenter.lottiegen.spec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Serialization round-trip guards for the StyleSpec format. These run only in the
 * submodule's own build (`./gradlew test` from the submodule root) — the main app
 * mounts src/main only.
 */
class SpecRoundTripTest {

    @Test
    fun bundledStyle1PortDecodesAndRoundTrips() {
        val stream = assertNotNull(
            javaClass.getResourceAsStream("/styles/style1_bar_port.json"),
            "bundled template resource missing"
        )
        val spec = SpecJson.decode(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })

        assertEquals(5, spec.elements.size)
        assertEquals(listOf("logo", "accent", "text"), spec.layout.slots.map { it.id })

        val reencoded = SpecJson.encode(spec)
        assertEquals(spec, SpecJson.decode(reencoded))
    }

    @Test
    fun everyElementAndSizeKindRoundTrips() {
        val spec = StyleSpec(
            id = "13",
            name = "Kitchen Sink",
            elements = listOf(
                RectElement(
                    id = "r1",
                    size = SizeSpec.TextWrap(TextFieldRef.NAME, 0.4, 0.2),
                    paint = PaintSpec(
                        fill = FillSpec(ColorRole.ACCENT, GradientSpec(0.0, 0.0, 5.0, 0.0)),
                        stroke = StrokeSpec(ColorRole.BORDER, StrokeWidthSpec.Em(0.1))
                    ),
                    corner = CornerSpec.Em(0.5),
                    growFrom = GrowOrigin.ALIGN_EDGE,
                    tracks = listOf(
                        AnimTrack(
                            AnimProperty.RECT_SIZE,
                            listOf(SpecKeyframe(0.0, listOf(0.0, 1.0)), SpecKeyframe(100.0, listOf(1.0, 1.0))),
                            easing = EasingKind.LINEAR,
                            alignOverrides = mapOf(
                                "right" to listOf(
                                    SpecKeyframe(0.0, listOf(0.5, 1.0)),
                                    SpecKeyframe(100.0, listOf(1.0, 1.0)),
                                )
                            )
                        )
                    )
                ),
                EllipseElement(id = "e1", size = SizeSpec.Em(2.0, 2.0)),
                PolygonElement(
                    id = "p1",
                    verticesEm = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(0.5, 1.0)),
                    fitWidthTo = WidthBasis.INFO
                ),
                TextElement(
                    id = "t1",
                    name = "Name",
                    field = TextFieldRef.NAME,
                    maskReveal = MaskRevealSpec(),
                    animator = TextAnimatorSpec(TextAnimatorKind.SEQUENTIAL_REVEAL, 10.0, 80.0, 0.8),
                    placement = Placement(
                        slot = "text",
                        anchorIn = AnchorIn.START,
                        line = LineAnchor.NAME_LINE,
                        alignOverrides = mapOf(
                            "center" to PlacementOverride(anchorIn = AnchorIn.CENTER, offsetXEm = -0.6),
                        )
                    )
                ),
                LogoElement(id = "l1"),
                ImageElement(
                    id = "i1",
                    dataUri = "data:image/png;base64,AAAA",
                    naturalW = 8,
                    naturalH = 4,
                    size = SizeSpec.ContentDerived(padXEm = 0.5, padYEm = 0.25),
                    scaleMode = ImageScaleMode.COVER,
                    corner = CornerSpec.Em(0.4),
                    alphaFactor = 0.35
                ),
                BackgroundElement(id = "b1", size = SizeSpec.CanvasWidth(4.0)),
                PathElement(
                    id = "v1",
                    name = "Vine",
                    placement = Placement(slot = "vine", pivotXEm = -0.35, pivotYEm = 0.1),
                    verticesEm = listOf(
                        CurveVertex(0.0, 2.0, outX = -0.5, outY = -0.8),
                        CurveVertex(0.0, -2.0, inX = 0.5, inY = 0.8)
                    ),
                    closed = false,
                    paint = PaintSpec(
                        fill = null,
                        stroke = StrokeSpec(ColorRole.ACCENT, StrokeWidthSpec.Em(0.12), dashEm = 0.2)
                    ),
                    repeat = RepeatSpec(
                        copies = 4, offsetXEm = 0.8, rotationDeg = 10.0, scalePct = 90.0,
                        fadeOut = true, fitWidthTo = WidthBasis.NAME
                    ),
                    fitWidthTo = WidthBasis.TEXT_BLOCK,
                    tracks = listOf(
                        AnimTrack(
                            AnimProperty.TRIM,
                            listOf(SpecKeyframe(0.0, listOf(0.0, 0.0)), SpecKeyframe(100.0, listOf(0.0, 1.0)))
                        ),
                        AnimTrack(
                            AnimProperty.SCALE,
                            listOf(SpecKeyframe(0.0, listOf(0.0, 50.0)), SpecKeyframe(100.0, listOf(100.0, 100.0)))
                        )
                    )
                )
            )
        )

        val decoded = SpecJson.decode(SpecJson.encode(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun newerFormatVersionIsRejected() {
        val text = SpecJson.encode(StyleSpec(formatVersion = StyleSpec.CURRENT_FORMAT_VERSION + 1))
        assertFailsWith<IllegalArgumentException> { SpecJson.decode(text) }
    }

    @Test
    fun malformedJsonIsRejectedWithDescriptiveError() {
        assertFailsWith<IllegalArgumentException> { SpecJson.decode("{ not json") }
    }
}
