package org.churchpresenter.presentationengine

import org.junit.jupiter.api.io.TempDir
import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.IwaMessage
import org.churchpresenter.presentationengine.keynote.KeynoteBuildMapper
import org.churchpresenter.presentationengine.keynote.KnDrawable
import org.churchpresenter.presentationengine.keynote.KnFields
import org.churchpresenter.presentationengine.keynote.KnGeometry
import org.churchpresenter.presentationengine.keynote.KnParagraph
import org.churchpresenter.presentationengine.keynote.KnPlacedDrawable
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.TransitionType
import java.awt.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.presentationengine.keynote.ObjectIndex

/**
 * Keynote's animation *names* into the engine's effects.
 *
 * Keynote identifies an animation by a string (`apple:build-effect:Move In`) and a direction
 * constant, with no numeric preset table like PowerPoint's — so the mapping is a pile of substring
 * rules, and a name that matches none of them silently becomes a fade. That is the right degrade,
 * but it means a mis-spelled rule is invisible: the deck still plays, every animation just turns
 * into the same cross-fade. These tests name the effects one by one so a rule cannot quietly stop
 * matching.
 *
 * Effect names and direction constants are the provisional set documented on the mapper; where a
 * real deck later disagrees, the fixture here is the thing to update.
 */
class KeynoteBuildEffectMappingTest {

    @TempDir
    lateinit var tempDir: File

    private val drawableId = 500L

    private fun text() = KnDrawable.Text(
        geometry = KnGeometry.ZERO,
        shape = null,
        paragraphs = listOf(KnParagraph("Only", null, 20.0, false, false, Color.BLACK, 0)),
    )

    /**
     * Runs one build through the mapper and returns the effect it produced. [type] is Keynote's
     * animation type ("In"/"Out"/"Action"), which decides the role.
     */
    private fun effectOf(
        effect: String,
        type: String = "In",
        direction: Long? = null,
        durationSeconds: Double = 1.0,
    ): EffectSpec {
        val animAttrs = ProtoWriter().apply {
            stringField(KnFields.ANIM_ATTRS_TYPE, type)
            stringField(KnFields.ANIM_ATTRS_EFFECT, "apple:build-effect:$effect")
            doubleField(KnFields.ANIM_ATTRS_DURATION, durationSeconds)
            if (direction != null) varintField(KnFields.ANIM_ATTRS_DIRECTION, direction)
        }.toByteArray()
        val buildAttrs = ProtoWriter().apply { bytesField(KnFields.BUILD_ATTRS_ANIMATION, animAttrs) }.toByteArray()
        val drawableRef = ProtoWriter().apply {
            varintField(KnFields.REFERENCE_IDENTIFIER, drawableId)
        }.toByteArray()
        val buildPayload = ProtoWriter().apply {
            bytesField(KnFields.BUILD_DRAWABLE, drawableRef)
            bytesField(KnFields.BUILD_ATTRIBUTES, buildAttrs)
        }.toByteArray()

        val buildId = 100L
        val chunkId = 101L
        val buildRef = ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, buildId) }.toByteArray()
        val chunkPayload = ProtoWriter().apply {
            bytesField(KnFields.BUILD_CHUNK_BUILD, buildRef)
            doubleField(KnFields.BUILD_CHUNK_DELAY, 0.0)
            varintField(KnFields.BUILD_CHUNK_AUTOMATIC, 0)
        }.toByteArray()

        val dir = Fixtures.writeKeynoteDir(
            File(tempDir, effect.filter { it.isLetterOrDigit() } + type).apply { mkdirs() },
            listOf(
                Triple(buildId, KnFields.TYPE_KN_BUILD, buildPayload),
                Triple(chunkId, KnFields.TYPE_KN_BUILD_CHUNK, chunkPayload),
            ),
        )
        val index = assertNotNull(ObjectIndex.load(dir))
        val slidePayload = ProtoWriter().apply {
            bytesField(
                KnFields.SLIDE_BUILDS,
                ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, buildId) }.toByteArray(),
            )
            bytesField(
                KnFields.SLIDE_BUILD_CHUNKS,
                ProtoWriter().apply { varintField(KnFields.REFERENCE_IDENTIFIER, chunkId) }.toByteArray(),
            )
        }.toByteArray()
        val slide = assertNotNull(IwaMessage.parse(slidePayload))

        val result = assertNotNull(
            KeynoteBuildMapper.map(index, slide, listOf(KnPlacedDrawable(drawableId, text()))),
            "the build produced no timeline at all",
        )
        val timeline = assertNotNull(result.timeline)
        return timeline.steps.single().intervals.single().effect
    }

    // ── Effect names ──────────────────────────────────────────────────────────

    @Test
    fun `dissolve-family names become a fade`() {
        for (name in listOf("Dissolve", "Fade In", "Typewriter", "Shimmer", "Sparkle")) {
            assertIs<EffectSpec.Fade>(effectOf(name), "$name should fade")
        }
    }

    @Test
    fun `move-family names become a fly`() {
        for (name in listOf("Move In", "Fly In", "Drift")) {
            assertIs<EffectSpec.Fly>(effectOf(name), "$name should fly")
        }
    }

    @Test
    fun `wipe-family names become a wipe`() {
        for (name in listOf("Wipe", "Reveal")) {
            assertIs<EffectSpec.Wipe>(effectOf(name), "$name should wipe")
        }
    }

    @Test
    fun `scale-family names become a zoom`() {
        for (name in listOf("Pop", "Scale", "Zoom", "Compress")) {
            assertIs<EffectSpec.Zoom>(effectOf(name), "$name should zoom")
        }
    }

    @Test
    fun `rotation-family names become a spin`() {
        for (name in listOf("Spin", "Twirl", "Rotate", "Pivot")) {
            assertIs<EffectSpec.Spin>(effectOf(name), "$name should spin")
        }
    }

    @Test
    fun `flashing names become a pulse`() {
        for (name in listOf("Pulse", "Blink", "Flash")) {
            assertIs<EffectSpec.Pulse>(effectOf(name), "$name should pulse")
        }
    }

    @Test
    fun `a movie build is present rather than revealed`() {
        // The poster frame is already on screen; fading it in on the click would be a flicker.
        assertIs<EffectSpec.Appear>(effectOf("Movie"))
        assertIs<EffectSpec.Appear>(effectOf("Appear"))
        assertIs<EffectSpec.Appear>(effectOf("None"))
    }

    @Test
    fun `drop falls from above whatever the direction field says`() {
        val effect = assertIs<EffectSpec.Fly>(effectOf("Drop", direction = 2L))
        assertEquals(Direction.DOWN, effect.direction, "the name carries the motion, not the field")
    }

    @Test
    fun `a name matching no rule still animates, as a fade`() {
        assertIs<EffectSpec.Fade>(effectOf("Anvil"), "an unknown effect must not vanish")
    }

    // ── Roles and directions ──────────────────────────────────────────────────

    @Test
    fun `the animation type decides whether the effect enters or leaves`() {
        assertEquals(EffectSpec.Role.ENTRANCE, effectOf("Dissolve", type = "In").role)
        assertEquals(EffectSpec.Role.EXIT, effectOf("Dissolve", type = "Out").role)
    }

    @Test
    fun `each direction constant decodes to the movement it names`() {
        fun flyDirection(value: Long) = assertIs<EffectSpec.Fly>(effectOf("Move In", direction = value)).direction

        assertEquals(Direction.LEFT, flyDirection(1L))
        assertEquals(Direction.RIGHT, flyDirection(2L))
        assertEquals(Direction.UP, flyDirection(3L))
        assertEquals(Direction.DOWN, flyDirection(4L))
    }

    @Test
    fun `a missing direction defaults by role rather than failing`() {
        assertEquals(
            Direction.UP,
            assertIs<EffectSpec.Fly>(effectOf("Move In", type = "In")).direction,
            "an entrance with no direction arrives upward",
        )
        assertEquals(
            Direction.DOWN,
            assertIs<EffectSpec.Fly>(effectOf("Move In", type = "Out")).direction,
            "an exit with no direction leaves downward",
        )
    }

    // ── Slide transitions ─────────────────────────────────────────────────────

    private fun transitionOf(effect: String?, durationSeconds: Double? = null, direction: Long? = null) =
        KeynoteBuildMapper.mapTransition(
            assertNotNull(
                IwaMessage.parse(
                    ProtoWriter().apply {
                        val anim = ProtoWriter().apply {
                            if (effect != null) stringField(KnFields.ANIM_ATTRS_EFFECT, "apple:transition:$effect")
                            if (durationSeconds != null) {
                                doubleField(KnFields.ANIM_ATTRS_DURATION, durationSeconds)
                            }
                            if (direction != null) varintField(KnFields.ANIM_ATTRS_DIRECTION, direction)
                        }.toByteArray()
                        val attrs = ProtoWriter().apply {
                            bytesField(KnFields.TRANSITION_ATTRS_ANIMATION, anim)
                        }.toByteArray()
                        bytesField(
                            KnFields.SLIDE_TRANSITION,
                            ProtoWriter().apply { bytesField(KnFields.TRANSITION_ATTRIBUTES, attrs) }.toByteArray(),
                        )
                    }.toByteArray()
                )
            )
        )

    @Test
    fun `transition names map onto the transition kinds the renderer has`() {
        assertEquals(TransitionType.FADE, assertNotNull(transitionOf("Dissolve")).type)
        assertEquals(TransitionType.PUSH, assertNotNull(transitionOf("Push")).type)
        assertEquals(TransitionType.WIPE, assertNotNull(transitionOf("Wipe")).type)
        assertEquals(TransitionType.COVER, assertNotNull(transitionOf("Move In")).type)
    }

    @Test
    fun `a three-dimensional transition keeps its displacement rather than flattening to a fade`() {
        for (name in listOf("Swoosh", "Swing", "Twist", "Reflection")) {
            assertEquals(TransitionType.COVER, assertNotNull(transitionOf(name)).type, "$name should cover")
        }
    }

    @Test
    fun `a transition with no motion equivalent fades`() {
        for (name in listOf("Cube", "Confetti", "Doorway", "Flip")) {
            assertEquals(TransitionType.FADE, assertNotNull(transitionOf(name)).type, "$name should fade")
        }
    }

    @Test
    fun `Magic Move is covered by the move rule, not by the fade backstop`() {
        // "Magic Move" contains "move", so it takes the COVER branch before reaching the fade
        // backstop. Keynote's Magic Move morphs matched objects between slides, which the engine
        // does not model either way; a displacement reads closer than a cross-fade, so this is
        // pinned as the behavior rather than treated as a miss.
        assertEquals(TransitionType.COVER, assertNotNull(transitionOf("Magic Move")).type)
    }

    @Test
    fun `transition duration comes back in milliseconds, with a default when absent`() {
        assertEquals(1500L, assertNotNull(transitionOf("Dissolve", durationSeconds = 1.5)).durationMs)
        assertEquals(500L, assertNotNull(transitionOf("Dissolve")).durationMs, "half a second is Keynote's own default")
    }

    @Test
    fun `a zero-length transition still lasts a frame rather than dividing by zero`() {
        assertTrue(assertNotNull(transitionOf("Dissolve", durationSeconds = 0.0)).durationMs >= 1)
    }

    @Test
    fun `a transition direction is decoded like a build's`() {
        assertEquals(Direction.LEFT, assertNotNull(transitionOf("Push", direction = 1L)).direction)
        assertEquals(Direction.DOWN, assertNotNull(transitionOf("Push", direction = 4L)).direction)
    }

    @Test
    fun `a slide with no transition, or an explicit none, has none`() {
        assertNull(transitionOf(null))
        assertNull(transitionOf("None"))
        assertNull(KeynoteBuildMapper.mapTransition(assertNotNull(IwaMessage.parse(ByteArray(0)))))
    }
}
