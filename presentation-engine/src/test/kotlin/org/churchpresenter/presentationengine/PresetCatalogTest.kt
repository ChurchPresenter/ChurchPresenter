package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.timeline.PresetCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mapping PowerPoint's effect vocabulary onto the engine's own effects.
 *
 * The rule this module is built around is that **a slide never fails to show content**: an
 * unrecognised effect degrades to a fade rather than leaving a shape invisible. So the two things
 * worth pinning are the direction decoding — a wipe that runs the wrong way is immediately
 * obvious on screen — and that unknown inputs return null so the caller can apply that degrade
 * rather than the catalog guessing.
 */
class PresetCatalogTest {

    private val entrance = EffectSpec.Role.ENTRANCE
    private val exit = EffectSpec.Role.EXIT

    // ── Filter strings ────────────────────────────────────────────────────────

    /** The direction a reveal filter decodes to — the assertion these tests repeat. */
    private fun wipeDirection(filter: String) =
        (PresetCatalog.fromFilter(filter, entrance) as EffectSpec.Wipe).direction

    @Test
    fun `fade-like filters all map to a fade`() {
        for (filter in listOf("fade", "dissolve", "checkerboard", "randombar", "image", "pixelate", "randomeffect")) {
            assertIs<EffectSpec.Fade>(PresetCatalog.fromFilter(filter, entrance), "$filter should fade")
        }
    }

    @Test
    fun `a wipe decodes its direction argument`() {
        assertEquals(Direction.DOWN, wipeDirection("wipe(down)"))
        assertEquals(Direction.UP, wipeDirection("wipe(up)"))
        assertEquals(Direction.LEFT, wipeDirection("wipe(left)"))
        assertEquals(Direction.RIGHT, wipeDirection("wipe(right)"))
    }

    @Test
    fun `from-edge wording is inverted into a direction of travel`() {
        // `fromLeft` describes where it starts; the engine wants where it moves.
        assertEquals(Direction.RIGHT, wipeDirection("wipe(fromLeft)"))
        assertEquals(Direction.LEFT, wipeDirection("wipe(fromRight)"))
        assertEquals(Direction.UP, wipeDirection("wipe(fromBottom)"))
        assertEquals(Direction.DOWN, wipeDirection("wipe(fromTop)"))
    }

    @Test
    fun `a wipe with no argument still has a direction`() {
        assertEquals(Direction.RIGHT, wipeDirection("wipe"))
    }

    @Test
    fun `blinds pick their axis from the argument`() {
        assertEquals(Direction.DOWN, wipeDirection("blinds(horizontal)"))
        assertEquals(Direction.RIGHT, wipeDirection("blinds(vertical)"))
    }

    @Test
    fun `diagonal strips collapse onto one axis`() {
        assertEquals(Direction.DOWN, wipeDirection("strips(downLeft)"))
        assertEquals(Direction.UP, wipeDirection("strips(upRight)"))
        assertEquals(Direction.RIGHT, wipeDirection("strips"))
    }

    @Test
    fun `slide becomes a fly`() {
        val fly = PresetCatalog.fromFilter("slide(fromLeft)", entrance)
        assertIs<EffectSpec.Fly>(fly)
        assertEquals(Direction.RIGHT, fly.direction)
    }

    @Test
    fun `barn doors decode their axis and their direction of opening`() {
        val vertical = PresetCatalog.fromFilter("barn(inVertical)", entrance) as EffectSpec.Split
        assertTrue(!vertical.horizontal)
        assertTrue(!vertical.outward)

        val outward = PresetCatalog.fromFilter("barn(outHorizontal)", entrance) as EffectSpec.Split
        assertTrue(outward.horizontal)
        assertTrue(outward.outward)
    }

    @Test
    fun `shape-reveal filters become a zoom that grows in and shrinks out`() {
        for (filter in listOf("box", "circle", "diamond", "plus", "wedge", "wheel", "spiral", "stretch")) {
            val entering = PresetCatalog.fromFilter(filter, entrance) as EffectSpec.Zoom
            assertEquals(0.0, entering.fromScale, 1e-9, "$filter should grow on entrance")
            val leaving = PresetCatalog.fromFilter(filter, exit) as EffectSpec.Zoom
            assertEquals(1.0, leaving.fromScale, 1e-9, "$filter should start full size on exit")
        }
    }

    @Test
    fun `filter names are matched case-insensitively and ignore surrounding space`() {
        assertIs<EffectSpec.Fade>(PresetCatalog.fromFilter("  FADE  ", entrance))
        assertIs<EffectSpec.Wipe>(PresetCatalog.fromFilter("Wipe(Down)", entrance))
    }

    @Test
    fun `an unknown or absent filter returns null so the caller can degrade`() {
        assertNull(PresetCatalog.fromFilter(null, entrance))
        assertNull(PresetCatalog.fromFilter("someEffectNobodyHasHeardOf", entrance))
    }

    @Test
    fun `the role given is carried onto the effect`() {
        assertEquals(exit, PresetCatalog.fromFilter("fade", exit)!!.role)
        assertEquals(entrance, PresetCatalog.fromFilter("wipe(down)", entrance)!!.role)
    }

    // ── Preset ids ────────────────────────────────────────────────────────────

    @Test
    fun `the preset class selects the role`() {
        assertEquals(entrance, PresetCatalog.fromPreset("entr", 1, null)!!.role)
        assertEquals(exit, PresetCatalog.fromPreset("exit", 1, null)!!.role)
        assertEquals(EffectSpec.Role.EMPHASIS, PresetCatalog.fromPreset("emph", 6, null)!!.role)
        assertEquals(EffectSpec.Role.EMPHASIS, PresetCatalog.fromPreset("path", 6, null)!!.role)
    }

    @Test
    fun `an unknown preset class or id returns null`() {
        assertNull(PresetCatalog.fromPreset(null, 1, null))
        assertNull(PresetCatalog.fromPreset("nonsense", 1, null))
        assertNull(PresetCatalog.fromPreset("entr", 9999, null))
        assertNull(PresetCatalog.fromPreset("emph", 9999, null))
    }

    @Test
    fun `the basic entrance effects map to their own kinds`() {
        assertIs<EffectSpec.Appear>(PresetCatalog.fromPreset("entr", 1, null))
        assertIs<EffectSpec.Fly>(PresetCatalog.fromPreset("entr", 2, 4))
        assertIs<EffectSpec.Fade>(PresetCatalog.fromPreset("entr", 10, null))
        assertIs<EffectSpec.Zoom>(PresetCatalog.fromPreset("entr", 23, null))
        assertIs<EffectSpec.Split>(PresetCatalog.fromPreset("entr", 16, null))
        assertIs<EffectSpec.Wipe>(PresetCatalog.fromPreset("entr", 22, 4))
    }

    @Test
    fun `fly subtype bits decode to the direction of travel`() {
        // 1=top, 2=right, 4=bottom, 8=left — an entrance moves away from the named edge.
        assertEquals(Direction.UP, (PresetCatalog.fromPreset("entr", 2, 4) as EffectSpec.Fly).direction)
        assertEquals(Direction.DOWN, (PresetCatalog.fromPreset("entr", 2, 1) as EffectSpec.Fly).direction)
        assertEquals(Direction.RIGHT, (PresetCatalog.fromPreset("entr", 2, 8) as EffectSpec.Fly).direction)
        assertEquals(Direction.LEFT, (PresetCatalog.fromPreset("entr", 2, 2) as EffectSpec.Fly).direction)
    }

    @Test
    fun `an exit flies toward the named edge instead of away from it`() {
        val entering = PresetCatalog.fromPreset("entr", 2, 4) as EffectSpec.Fly
        val leaving = PresetCatalog.fromPreset("exit", 2, 4) as EffectSpec.Fly
        assertEquals(Direction.UP, entering.direction, "enters from the bottom, moving up")
        assertEquals(Direction.DOWN, leaving.direction, "exits back toward the bottom")
    }

    @Test
    fun `a corner subtype picks one dominant axis rather than none`() {
        val corner = PresetCatalog.fromPreset("entr", 2, 4 or 8) as EffectSpec.Fly
        assertTrue(corner.direction in listOf(Direction.UP, Direction.RIGHT), "got ${corner.direction}")
    }

    @Test
    fun `a missing subtype still yields a usable direction`() {
        assertEquals(Direction.UP, (PresetCatalog.fromPreset("entr", 2, null) as EffectSpec.Fly).direction)
    }

    @Test
    fun `the emphasis effects map to their own kinds`() {
        assertIs<EffectSpec.Pulse>(PresetCatalog.fromPreset("emph", 1, null))
        assertIs<EffectSpec.GrowShrink>(PresetCatalog.fromPreset("emph", 6, null))
        assertIs<EffectSpec.Spin>(PresetCatalog.fromPreset("emph", 8, null))
        assertIs<EffectSpec.Fade>(PresetCatalog.fromPreset("emph", 9, null))
        assertIs<EffectSpec.Pulse>(PresetCatalog.fromPreset("emph", 26, null))
    }

    @Test
    fun `teeter is a small rock rather than a full spin`() {
        val teeter = PresetCatalog.fromPreset("emph", 32, null) as EffectSpec.Spin
        val spin = PresetCatalog.fromPreset("emph", 8, null) as EffectSpec.Spin
        assertTrue(teeter.degrees < spin.degrees, "teeter=${teeter.degrees} spin=${spin.degrees}")
    }

    @Test
    fun `float up and float down travel in opposite directions and invert on exit`() {
        assertEquals(Direction.UP, (PresetCatalog.fromPreset("entr", 42, null) as EffectSpec.Fly).direction)
        assertEquals(Direction.DOWN, (PresetCatalog.fromPreset("exit", 42, null) as EffectSpec.Fly).direction)
        assertEquals(Direction.DOWN, (PresetCatalog.fromPreset("entr", 47, null) as EffectSpec.Fly).direction)
        assertEquals(Direction.UP, (PresetCatalog.fromPreset("exit", 47, null) as EffectSpec.Fly).direction)
    }

    @Test
    fun `a zoom-family preset grows in on entrance and starts full size on exit`() {
        for (id in listOf(4, 6, 8, 13, 23)) {
            assertEquals(0.0, (PresetCatalog.fromPreset("entr", id, null) as EffectSpec.Zoom).fromScale, 1e-9, "id $id")
            assertEquals(1.0, (PresetCatalog.fromPreset("exit", id, null) as EffectSpec.Zoom).fromScale, 1e-9, "id $id")
        }
    }

    // ── The whole table ───────────────────────────────────────────────────────

    /** Every entrance/exit preset id the catalog claims to know, from PowerPoint's own numbering. */
    private val entranceExitIds = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
        26, 30, 42, 47,
    )

    /** Every emphasis preset id the catalog claims to know. */
    private val emphasisIds = listOf(1, 3, 6, 8, 9, 26, 32, 35, 36)

    @Test
    fun `every entrance id in the table maps to an effect that enters`() {
        for (id in entranceExitIds) {
            val effect = assertNotNull(PresetCatalog.fromPreset("entr", id, null), "entrance preset $id fell through")
            assertEquals(entrance, effect.role, "preset $id came back with the wrong role")
        }
    }

    @Test
    fun `every exit id in the table maps to an effect that leaves`() {
        for (id in entranceExitIds) {
            val effect = assertNotNull(PresetCatalog.fromPreset("exit", id, null), "exit preset $id fell through")
            assertEquals(exit, effect.role, "preset $id came back with the wrong role")
        }
    }

    @Test
    fun `every emphasis id in the table maps to an emphasis effect`() {
        for (id in emphasisIds) {
            val effect = assertNotNull(PresetCatalog.fromPreset("emph", id, null), "emphasis preset $id fell through")
            assertEquals(EffectSpec.Role.EMPHASIS, effect.role, "preset $id came back with the wrong role")
        }
    }

    @Test
    fun `a subtype the catalog does not understand never turns a known preset into nothing`() {
        // Subtypes vary per effect and per PowerPoint version; an unrecognized one must not be the
        // difference between an animation and a blank slide.
        for (id in entranceExitIds) {
            assertNotNull(
                PresetCatalog.fromPreset("entr", id, 12345),
                "preset $id with an unknown subtype fell through",
            )
        }
    }
}
