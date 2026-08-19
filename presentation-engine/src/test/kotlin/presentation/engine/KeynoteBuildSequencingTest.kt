package presentation.engine

import org.junit.jupiter.api.io.TempDir
import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.IwaMessage
import presentation.engine.keynote.KeynoteBuildMapper
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFields as F
import presentation.engine.keynote.KnGeometry
import presentation.engine.keynote.KnParagraph
import presentation.engine.keynote.KnPlacedDrawable
import presentation.engine.keynote.ObjectIndex
import presentation.engine.model.EffectSpec
import java.awt.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a Keynote slide's builds become **click steps**.
 *
 * A build chunk says whether its build waits for a click or follows the previous one automatically
 * after a delay, and that is what decides how many times the operator has to press the button to
 * get through a slide. Getting it wrong is not subtle on stage: a build wrongly marked automatic
 * plays too early, and one wrongly marked as a click leaves the operator pressing into a slide
 * that looks finished. Paragraph builds add the second rule — Keynote gives every bullet its own
 * click, whatever the chunk said, except for the first.
 *
 * [KeynoteBuildEffectMappingTest] covers what each build *looks* like; this covers when it runs.
 */
class KeynoteBuildSequencingTest {

    @TempDir
    lateinit var tempDir: File

    private fun text(paragraphs: Int) = KnDrawable.Text(
        geometry = KnGeometry.ZERO,
        shape = null,
        paragraphs = (0 until paragraphs).map {
            KnParagraph("Line $it", null, 20.0, false, false, Color.BLACK, 0)
        },
    )

    private class BuildSpec(
        val id: Long,
        val drawableId: Long,
        val effect: String = "Dissolve",
        val type: String = "In",
        val durationSeconds: Double = 0.5,
        val delivery: String? = null,
    )

    private class ChunkSpec(val buildId: Long?, val automatic: Boolean = false, val delaySeconds: Double = 0.0)

    /**
     * Assembles a slide with [builds] and, when given, [chunks] — the ordered list Keynote writes
     * to say how the builds are triggered. With no chunks at all (older documents) every build is
     * its own click.
     */
    private fun mapBuilds(
        builds: List<BuildSpec>,
        chunks: List<ChunkSpec>? = null,
        drawables: List<KnPlacedDrawable>,
        name: String = "case",
    ): KeynoteBuildMapper.Result? {
        val objects = mutableListOf<Triple<Long, Int, ByteArray>>()
        for (build in builds) {
            val anim = ProtoWriter().apply {
                stringField(F.ANIM_ATTRS_TYPE, build.type)
                stringField(F.ANIM_ATTRS_EFFECT, "apple:build-effect:${build.effect}")
                doubleField(F.ANIM_ATTRS_DURATION, build.durationSeconds)
            }.toByteArray()
            val payload = ProtoWriter().apply {
                bytesField(
                    F.BUILD_DRAWABLE,
                    ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, build.drawableId) }.toByteArray(),
                )
                if (build.delivery != null) stringField(F.BUILD_DELIVERY, build.delivery)
                bytesField(
                    F.BUILD_ATTRIBUTES,
                    ProtoWriter().apply { bytesField(F.BUILD_ATTRS_ANIMATION, anim) }.toByteArray(),
                )
            }.toByteArray()
            objects += Triple(build.id, F.TYPE_KN_BUILD, payload)
        }
        chunks?.forEachIndexed { index, chunk ->
            val payload = ProtoWriter().apply {
                if (chunk.buildId != null) {
                    bytesField(
                        F.BUILD_CHUNK_BUILD,
                        ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, chunk.buildId) }.toByteArray(),
                    )
                }
                doubleField(F.BUILD_CHUNK_DELAY, chunk.delaySeconds)
                varintField(F.BUILD_CHUNK_AUTOMATIC, if (chunk.automatic) 1 else 0)
            }.toByteArray()
            objects += Triple(900L + index, F.TYPE_KN_BUILD_CHUNK, payload)
        }

        val dir = Fixtures.writeKeynoteDir(File(tempDir, name).apply { mkdirs() }, objects)
        val index = assertNotNull(ObjectIndex.load(dir))
        val slide = assertNotNull(
            IwaMessage.parse(
                ProtoWriter().apply {
                    builds.forEach {
                        bytesField(
                            F.SLIDE_BUILDS,
                            ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, it.id) }.toByteArray(),
                        )
                    }
                    chunks?.forEachIndexed { i, _ ->
                        bytesField(
                            F.SLIDE_BUILD_CHUNKS,
                            ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, 900L + i) }.toByteArray(),
                        )
                    }
                }.toByteArray()
            )
        )
        return KeynoteBuildMapper.map(index, slide, drawables)
    }

    private fun shapeDrawable(id: Long) = KnPlacedDrawable(
        id,
        KnDrawable.Shape(KnGeometry.ZERO, null, null, null, 0.0, 1.0),
    )

    // ── Clicks and automatic follows ──────────────────────────────────────────

    @Test
    fun `two clicked builds are two steps`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L), BuildSpec(101L, 2L)),
                chunks = listOf(ChunkSpec(100L), ChunkSpec(101L)),
                drawables = listOf(shapeDrawable(1L), shapeDrawable(2L)),
                name = "two-clicks",
            )
        )
        val timeline = assertNotNull(result.timeline)
        assertEquals(2, timeline.stepCount)
        assertEquals(setOf(1L, 2L), result.builtDrawableIds)
    }

    @Test
    fun `an automatic build joins the step before it instead of opening its own`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L, durationSeconds = 0.5), BuildSpec(101L, 2L)),
                chunks = listOf(ChunkSpec(100L), ChunkSpec(101L, automatic = true)),
                drawables = listOf(shapeDrawable(1L), shapeDrawable(2L)),
                name = "auto-follow",
            )
        )
        val timeline = assertNotNull(result.timeline)
        assertEquals(1, timeline.stepCount, "the second build follows on the same click")
        assertEquals(2, timeline.steps.single().intervals.size)
    }

    @Test
    fun `an automatic build starts when the one before it has finished, plus its own delay`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L, durationSeconds = 2.0), BuildSpec(101L, 2L)),
                chunks = listOf(ChunkSpec(100L), ChunkSpec(101L, automatic = true, delaySeconds = 0.25)),
                drawables = listOf(shapeDrawable(1L), shapeDrawable(2L)),
                name = "auto-delay",
            )
        )
        val intervals = assertNotNull(result.timeline).steps.single().intervals
        assertEquals(0L, intervals[0].beginMs)
        assertEquals(2000L, intervals[0].durMs)
        assertEquals(2250L, intervals[1].beginMs, "2s of the first build, then a quarter-second delay")
    }

    @Test
    fun `a clicked build's own delay offsets it within its step`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L)),
                chunks = listOf(ChunkSpec(100L, delaySeconds = 0.75)),
                drawables = listOf(shapeDrawable(1L)),
                name = "click-delay",
            )
        )
        assertEquals(750L, assertNotNull(result.timeline).steps.single().intervals.single().beginMs)
    }

    @Test
    fun `an automatic build with nothing before it still opens a step`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L)),
                chunks = listOf(ChunkSpec(100L, automatic = true)),
                drawables = listOf(shapeDrawable(1L)),
                name = "auto-first",
            )
        )
        assertEquals(1, assertNotNull(result.timeline).stepCount, "there is nothing to follow, so it leads")
    }

    @Test
    fun `chunks decide the order, not the order the builds are declared in`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L), BuildSpec(101L, 2L)),
                chunks = listOf(ChunkSpec(101L), ChunkSpec(100L)),
                drawables = listOf(shapeDrawable(1L), shapeDrawable(2L)),
                name = "chunk-order",
            )
        )
        val timeline = assertNotNull(result.timeline)
        assertEquals(
            listOf(KeynoteBuildMapper.layerIdFor(2L), KeynoteBuildMapper.layerIdFor(1L)),
            timeline.steps.map { it.intervals.single().layerId },
            "the chunk list is the running order",
        )
    }

    @Test
    fun `a document with no chunk list gives every build its own click`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L), BuildSpec(101L, 2L)),
                chunks = null,
                drawables = listOf(shapeDrawable(1L), shapeDrawable(2L)),
                name = "no-chunks",
            )
        )
        assertEquals(2, assertNotNull(result.timeline).stepCount)
    }

    @Test
    fun `a chunk pointing at no build is skipped rather than breaking the sequence`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L)),
                chunks = listOf(ChunkSpec(buildId = null), ChunkSpec(100L)),
                drawables = listOf(shapeDrawable(1L)),
                name = "orphan-chunk",
            )
        )
        assertEquals(1, assertNotNull(result.timeline).stepCount)
    }

    // ── Nothing to sequence ───────────────────────────────────────────────────

    @Test
    fun `a slide with no builds has no timeline`() {
        val dir = Fixtures.writeKeynoteDir(
            File(tempDir, "no-builds").apply { mkdirs() },
            listOf(Triple(1L, F.TYPE_KN_DOCUMENT, ProtoWriter().toByteArray())),
        )
        val index = assertNotNull(ObjectIndex.load(dir))
        val slide = assertNotNull(IwaMessage.parse(ProtoWriter().toByteArray()))
        assertNull(KeynoteBuildMapper.map(index, slide, listOf(shapeDrawable(1L))))
    }

    @Test
    fun `a build with no drawable is not a build`() {
        val payload = ProtoWriter().apply { stringField(F.BUILD_DELIVERY, "All at Once") }.toByteArray()
        val dir = Fixtures.writeKeynoteDir(
            File(tempDir, "no-drawable").apply { mkdirs() },
            listOf(Triple(100L, F.TYPE_KN_BUILD, payload)),
        )
        val index = assertNotNull(ObjectIndex.load(dir))
        val slide = assertNotNull(
            IwaMessage.parse(
                ProtoWriter().apply {
                    bytesField(
                        F.SLIDE_BUILDS,
                        ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, 100L) }.toByteArray(),
                    )
                }.toByteArray()
            )
        )
        assertNull(KeynoteBuildMapper.map(index, slide, listOf(shapeDrawable(1L))))
    }

    @Test
    fun `chunks that all point at nothing leave the slide unanimated`() {
        assertNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L)),
                chunks = listOf(ChunkSpec(buildId = null)),
                drawables = listOf(shapeDrawable(1L)),
                name = "all-orphans",
            )
        )
    }

    // ── Paragraph builds ──────────────────────────────────────────────────────

    @Test
    fun `a by-paragraph build gives every bullet after the first its own click`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L, delivery = "By Paragraph")),
                chunks = listOf(ChunkSpec(100L)),
                drawables = listOf(KnPlacedDrawable(1L, text(paragraphs = 3))),
                name = "by-paragraph",
            )
        )
        val timeline = assertNotNull(result.timeline)
        assertEquals(3, timeline.stepCount)
        assertEquals(setOf(1L), result.paragraphBuiltDrawableIds)
        assertEquals(
            (0 until 3).map { KeynoteBuildMapper.paragraphLayerIdFor(1L, it) },
            timeline.steps.map { it.intervals.single().layerId },
        )
    }

    @Test
    fun `only the first bullet of an automatic paragraph build follows the previous step`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(
                    BuildSpec(100L, 1L),
                    BuildSpec(101L, 2L, delivery = "By Bullet"),
                ),
                chunks = listOf(ChunkSpec(100L), ChunkSpec(101L, automatic = true)),
                drawables = listOf(shapeDrawable(1L), KnPlacedDrawable(2L, text(paragraphs = 3))),
                name = "auto-paragraph",
            )
        )
        val timeline = assertNotNull(result.timeline)
        assertEquals(3, timeline.stepCount, "first bullet joins the opening click; the other two are their own")
        assertEquals(2, timeline.steps[0].intervals.size, "the shape and the first bullet share a step")
    }

    @Test
    fun `a single-paragraph text box is not fanned out`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L, delivery = "By Paragraph")),
                chunks = listOf(ChunkSpec(100L)),
                drawables = listOf(KnPlacedDrawable(1L, text(paragraphs = 1))),
                name = "one-paragraph",
            )
        )
        assertEquals(1, assertNotNull(result.timeline).stepCount)
        assertTrue(result.paragraphBuiltDrawableIds.isEmpty(), "nothing was fanned out, so nothing is flagged")
        assertEquals(KeynoteBuildMapper.layerIdFor(1L), result.timeline!!.steps.single().intervals.single().layerId)
    }

    @Test
    fun `a delivery the engine does not fan out animates the whole object`() {
        // "By Word"/"By Character" are a known gap: they degrade to one build of the whole shape
        // rather than to nothing.
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L, delivery = "By Word")),
                chunks = listOf(ChunkSpec(100L)),
                drawables = listOf(KnPlacedDrawable(1L, text(paragraphs = 3))),
                name = "by-word",
            )
        )
        assertEquals(1, assertNotNull(result.timeline).stepCount)
        assertTrue(result.paragraphBuiltDrawableIds.isEmpty())
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Test
    fun `a movie build is an emphasis so its video is on screen before the click`() {
        val result = assertNotNull(
            mapBuilds(
                builds = listOf(BuildSpec(100L, 1L, effect = "Movie Start", type = "In")),
                // Capitalised as Keynote writes it — the role check has to match case-insensitively.
                chunks = listOf(ChunkSpec(100L)),
                drawables = listOf(shapeDrawable(1L)),
                name = "movie-role",
            )
        )
        val effect = assertNotNull(result.timeline).steps.single().intervals.single().effect
        assertEquals(
            EffectSpec.Role.EMPHASIS,
            effect.role,
            "a movie build classified as an entrance leaves the video area blank until the click",
        )
    }

    @Test
    fun `an action build is an emphasis and an out build is an exit`() {
        fun roleOf(type: String, name: String): EffectSpec.Role {
            val result = assertNotNull(
                mapBuilds(
                    builds = listOf(BuildSpec(100L, 1L, type = type)),
                    chunks = listOf(ChunkSpec(100L)),
                    drawables = listOf(shapeDrawable(1L)),
                    name = name,
                )
            )
            return assertNotNull(result.timeline).steps.single().intervals.single().effect.role
        }
        assertEquals(EffectSpec.Role.EMPHASIS, roleOf("Action", "role-action"))
        assertEquals(EffectSpec.Role.EXIT, roleOf("Out", "role-out"))
        assertEquals(EffectSpec.Role.ENTRANCE, roleOf("", "role-blank"))
    }

    @Test
    fun `a build with no duration of its own gets Keynote's default`() {
        val payloadAnim = ProtoWriter().apply {
            stringField(F.ANIM_ATTRS_TYPE, "In")
            stringField(F.ANIM_ATTRS_EFFECT, "apple:build-effect:Dissolve")
        }.toByteArray()
        val build = ProtoWriter().apply {
            bytesField(
                F.BUILD_DRAWABLE,
                ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, 1L) }.toByteArray(),
            )
            bytesField(
                F.BUILD_ATTRIBUTES,
                ProtoWriter().apply { bytesField(F.BUILD_ATTRS_ANIMATION, payloadAnim) }.toByteArray(),
            )
        }.toByteArray()
        val dir = Fixtures.writeKeynoteDir(
            File(tempDir, "default-duration").apply { mkdirs() },
            listOf(Triple(100L, F.TYPE_KN_BUILD, build)),
        )
        val index = assertNotNull(ObjectIndex.load(dir))
        val slide = assertNotNull(
            IwaMessage.parse(
                ProtoWriter().apply {
                    bytesField(
                        F.SLIDE_BUILDS,
                        ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, 100L) }.toByteArray(),
                    )
                }.toByteArray()
            )
        )
        val result = assertNotNull(KeynoteBuildMapper.map(index, slide, listOf(shapeDrawable(1L))))
        assertEquals(500L, assertNotNull(result.timeline).steps.single().intervals.single().durMs)
    }
}
