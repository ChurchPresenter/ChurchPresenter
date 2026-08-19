package presentation.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.KeynoteBuildMapper
import presentation.engine.keynote.KeynoteLayerPlanner
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFields
import presentation.engine.keynote.KnGeometry
import presentation.engine.keynote.KnParagraph
import presentation.engine.keynote.KnPlacedDrawable
import presentation.engine.keynote.KnSlide
import presentation.engine.keynote.ObjectIndex
import presentation.engine.keynote.IwaMessage
import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.LayerSpec
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import java.awt.Color
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Keynote "By Paragraph"/"By Bullet" text builds: [KeynoteBuildMapper] fans one build into a
 * click step per paragraph, and [KeynoteLayerPlanner] emits one [LayerSpec.ParagraphText] per
 * paragraph — the exact layer kind PPTX's planner already produces, reused rather than
 * reinvented. Delivery string(s) are provisional (no real per-paragraph .key file was available
 * this session to confirm them against `dumpKeynote`) — same "validated per-deck" caveat this
 * file already carries for direction constants and effect-name mapping.
 */
class KeynoteParagraphBuildTest {

    @TempDir
    lateinit var tempDir: File

    private fun threeParagraphText(): KnDrawable.Text = KnDrawable.Text(
        geometry = KnGeometry.ZERO,
        shape = null,
        paragraphs = listOf(
            KnParagraph("First", null, 20.0, false, false, Color.BLACK, 0),
            KnParagraph("Second", null, 20.0, false, false, Color.BLACK, 0),
            KnParagraph("Third", null, 20.0, false, false, Color.BLACK, 0)
        )
    )

    // ── KeynoteBuildMapper: delivery detection + interval synthesis ───────────

    @Test
    fun `by-paragraph delivery fans one build into one step per paragraph`() {
        val drawableId = 500L
        val buildId = 100L
        val chunkId = 101L

        val animAttrs = ProtoWriter().apply {
            stringField(KnFields.ANIM_ATTRS_TYPE, "In")
            stringField(KnFields.ANIM_ATTRS_EFFECT, "apple:build-effect:Appear")
            doubleField(KnFields.ANIM_ATTRS_DURATION, 1.0)
        }.toByteArray()
        val buildAttrs = ProtoWriter().apply {
            bytesField(KnFields.BUILD_ATTRS_ANIMATION, animAttrs)
        }.toByteArray()
        val drawableRef = ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, drawableId) }.toByteArray()
        val buildPayload = ProtoWriter().apply {
            bytesField(KnFields.BUILD_DRAWABLE, drawableRef)
            stringField(KnFields.BUILD_DELIVERY, "By Paragraph")
            bytesField(KnFields.BUILD_ATTRIBUTES, buildAttrs)
        }.toByteArray()

        val buildRef = ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, buildId) }.toByteArray()
        val chunkPayload = ProtoWriter().apply {
            bytesField(KnFields.BUILD_CHUNK_BUILD, buildRef)
            doubleField(KnFields.BUILD_CHUNK_DELAY, 0.0)
            varintField(KnFields.BUILD_CHUNK_AUTOMATIC, 0)
        }.toByteArray()

        val dir = Fixtures.writeKeynoteDir(
            tempDir,
            listOf(
                Triple(buildId, KnFields.TYPE_KN_BUILD, buildPayload),
                Triple(chunkId, KnFields.TYPE_KN_BUILD_CHUNK, chunkPayload)
            )
        )
        val index = assertNotNull(ObjectIndex.load(dir))

        val slidePayload = ProtoWriter().apply {
            bytesField(
                KnFields.SLIDE_BUILDS,
                ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, buildId) }.toByteArray()
            )
            bytesField(
                KnFields.SLIDE_BUILD_CHUNKS,
                ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, chunkId) }.toByteArray()
            )
        }.toByteArray()
        val slideMessage = assertNotNull(IwaMessage.parse(slidePayload))

        val drawables = listOf(KnPlacedDrawable(drawableId, threeParagraphText()))

        val result = assertNotNull(KeynoteBuildMapper.map(index, slideMessage, drawables))
        assertEquals(setOf(drawableId), result.paragraphBuiltDrawableIds, "drawable should be flagged as paragraph-built")
        val timeline = assertNotNull(result.timeline)
        assertEquals(3, timeline.steps.size, "one click step per paragraph")
        timeline.steps.forEachIndexed { i, step ->
            assertEquals(1, step.intervals.size)
            assertEquals(
                KeynoteBuildMapper.paragraphLayerIdFor(drawableId, i),
                step.intervals[0].layerId,
                "step $i should target paragraph $i's layer"
            )
        }
    }

    @Test
    fun `all-at-once delivery is unaffected by paragraph fan-out`() {
        val drawableId = 500L
        val buildId = 100L
        val chunkId = 101L

        val animAttrs = ProtoWriter().apply {
            stringField(KnFields.ANIM_ATTRS_TYPE, "In")
            stringField(KnFields.ANIM_ATTRS_EFFECT, "apple:build-effect:Appear")
            doubleField(KnFields.ANIM_ATTRS_DURATION, 1.0)
        }.toByteArray()
        val buildAttrs = ProtoWriter().apply {
            bytesField(KnFields.BUILD_ATTRS_ANIMATION, animAttrs)
        }.toByteArray()
        val drawableRef = ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, drawableId) }.toByteArray()
        val buildPayload = ProtoWriter().apply {
            bytesField(KnFields.BUILD_DRAWABLE, drawableRef)
            stringField(KnFields.BUILD_DELIVERY, "All at Once")
            bytesField(KnFields.BUILD_ATTRIBUTES, buildAttrs)
        }.toByteArray()
        val buildRef = ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, buildId) }.toByteArray()
        val chunkPayload = ProtoWriter().apply {
            bytesField(KnFields.BUILD_CHUNK_BUILD, buildRef)
            doubleField(KnFields.BUILD_CHUNK_DELAY, 0.0)
            varintField(KnFields.BUILD_CHUNK_AUTOMATIC, 0)
        }.toByteArray()
        val dir = Fixtures.writeKeynoteDir(
            tempDir,
            listOf(
                Triple(buildId, KnFields.TYPE_KN_BUILD, buildPayload),
                Triple(chunkId, KnFields.TYPE_KN_BUILD_CHUNK, chunkPayload)
            )
        )
        val index = assertNotNull(ObjectIndex.load(dir))
        val slidePayload = ProtoWriter().apply {
            bytesField(
                KnFields.SLIDE_BUILDS,
                ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, buildId) }.toByteArray()
            )
            bytesField(
                KnFields.SLIDE_BUILD_CHUNKS,
                ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, chunkId) }.toByteArray()
            )
        }.toByteArray()
        val slideMessage = assertNotNull(IwaMessage.parse(slidePayload))
        val drawables = listOf(KnPlacedDrawable(drawableId, threeParagraphText()))

        val result = assertNotNull(KeynoteBuildMapper.map(index, slideMessage, drawables))
        assertTrue(result.paragraphBuiltDrawableIds.isEmpty(), "All at Once must not fan out")
        val timeline = assertNotNull(result.timeline)
        assertEquals(1, timeline.steps.size, "whole-object build stays a single step")
        assertEquals(KeynoteBuildMapper.layerIdFor(drawableId), timeline.steps[0].intervals[0].layerId)
    }

    // ── KeynoteLayerPlanner: layer emission + remapTimeline pass-through ──────

    @Test
    fun `planner emits one ParagraphText layer per paragraph`() {
        val drawableId = 500L
        val text = threeParagraphText()
        val slide = KnSlide(
            index = 0,
            background = null,
            drawables = listOf(KnPlacedDrawable(drawableId, text)),
            notes = "",
            timeline = Timeline(
                (0 until 3).map { i ->
                    Step(
                        listOf(
                            EffectInterval(
                                layerId = KeynoteBuildMapper.paragraphLayerIdFor(drawableId, i),
                                effect = EffectSpec.Appear(EffectSpec.Role.ENTRANCE),
                                beginMs = 0,
                                durMs = 500
                            )
                        )
                    )
                }
            ),
            builtDrawableIds = setOf(drawableId),
            paragraphBuiltDrawableIds = setOf(drawableId),
            transition = null,
            gateReason = null
        )

        val layers = assertNotNull(KeynoteLayerPlanner.plan(slide, slideWidthPt = 1920.0, slideHeightPt = 1080.0))
        val paragraphLayers = layers.filterIsInstance<LayerSpec.ParagraphText>()
        assertEquals(3, paragraphLayers.size)
        assertEquals(listOf(0, 1, 2), paragraphLayers.map { it.paragraphIndex })
        paragraphLayers.forEach { assertEquals(0, it.shapeIndex) }

        val (remappedTimeline, hidden) = assertNotNull(KeynoteLayerPlanner.remapTimeline(slide, layers))
        assertEquals(3, remappedTimeline.steps.size)
        // Every paragraph layer's own first effect is ENTRANCE, so all three should start hidden.
        paragraphLayers.forEach { layer -> assertTrue(layer.id in hidden, "${layer.id} should start hidden") }
    }

    @Test
    fun `single-paragraph or non-paragraph-built text still gets a plain Shape layer`() {
        val drawableId = 500L
        // paragraphBuiltDrawableIds is empty here — mapper never flagged this drawable.
        val slide = KnSlide(
            index = 0,
            background = null,
            drawables = listOf(KnPlacedDrawable(drawableId, threeParagraphText())),
            notes = "",
            timeline = Timeline(
                listOf(
                    Step(
                        listOf(
                            EffectInterval(
                                layerId = KeynoteBuildMapper.layerIdFor(drawableId),
                                effect = EffectSpec.Appear(EffectSpec.Role.ENTRANCE),
                                beginMs = 0,
                                durMs = 500
                            )
                        )
                    )
                )
            ),
            builtDrawableIds = setOf(drawableId),
            paragraphBuiltDrawableIds = emptySet(),
            transition = null,
            gateReason = null
        )

        val layers = assertNotNull(KeynoteLayerPlanner.plan(slide, slideWidthPt = 1920.0, slideHeightPt = 1080.0))
        assertTrue(layers.none { it is LayerSpec.ParagraphText })
        val shape = assertIs<LayerSpec.Shape>(layers.first { it.id == KeynoteBuildMapper.layerIdFor(drawableId) })
        assertEquals(0, shape.shapeIndex)
    }
}
