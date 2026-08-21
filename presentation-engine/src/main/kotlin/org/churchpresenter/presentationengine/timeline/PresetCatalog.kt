package org.churchpresenter.presentationengine.timeline

import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.EffectSpec

/**
 * Data-driven mapping of PowerPoint effect names to concrete [EffectSpec]s:
 *  1. `animEffect` filter strings (the primary signal — PowerPoint serializes one for most
 *     reveal-style effects), e.g. `wipe(down)`, `blinds(horizontal)`, `dissolve`.
 *  2. Preset ids (`presetClass`/`presetID`/`presetSubtype` on the effect node) for effects whose
 *     behaviors aren't otherwise interpretable.
 *
 * The engine-wide degrade rule lives here: anything unknown becomes [EffectSpec.Fade] at the
 * node's duration — a slide never fails to show content. WS4 broadens both tables.
 */
internal object PresetCatalog {

    // PowerPoint preset ids (`presetID` on the effect node). Entrance and exit share one
    // numbering; emphasis has its own. The names are Microsoft's own effect names — the
    // number is the wire value and the name is what the operator picked in PowerPoint.
    private const val ENTR_APPEAR = 1
    private const val ENTR_FLY = 2
    private const val ENTR_BLINDS = 3
    private const val ENTR_BOX = 4
    private const val ENTR_CHECKERBOARD = 5
    private const val ENTR_CIRCLE = 6
    private const val ENTR_CRAWL = 7
    private const val ENTR_DIAMOND = 8
    private const val ENTR_DISSOLVE = 9
    private const val ENTR_FADE = 10
    private const val ENTR_FLASH_ONCE = 11
    private const val ENTR_PEEK = 12
    private const val ENTR_PLUS = 13
    private const val ENTR_RANDOM_BARS = 14
    private const val ENTR_SPIRAL = 15
    private const val ENTR_SPLIT = 16
    private const val ENTR_STRETCH = 17
    private const val ENTR_STRIPS = 18
    private const val ENTR_SWIVEL = 19
    private const val ENTR_WEDGE = 20
    private const val ENTR_WHEEL = 21
    private const val ENTR_WIPE = 22
    private const val ENTR_ZOOM = 23
    private const val ENTR_RANDOM = 24
    private const val ENTR_BOOMERANG = 25
    private const val ENTR_BOUNCE_GROW_TURN = 26
    private const val ENTR_BOUNCE = 30
    private const val ENTR_FLOAT_UP = 42
    private const val ENTR_FLOAT_DOWN = 47

    private const val EMPH_FILL_COLOR = 1
    private const val EMPH_FONT_COLOR = 3
    private const val EMPH_GROW_SHRINK = 6
    private const val EMPH_SPIN = 8
    private const val EMPH_TRANSPARENCY = 9
    private const val EMPH_PULSE = 26
    private const val EMPH_TEETER = 32
    private const val EMPH_COLOR_PULSE = 35
    private const val EMPH_BLINK = 36

    /** `presetSubtype` values that are not directions: blinds-vertical and split-horizontal. */
    private const val SUBTYPE_BLINDS_VERTICAL = 10
    private const val SUBTYPE_SPLIT_VERTICAL = 21

    /** Grow/Shrink has no serialized factor of its own — PowerPoint's own default is 150%. */
    private const val GROW_SHRINK_FACTOR = 1.5

    /** Teeter is a small rock rather than a spin; degrees chosen to read as a wobble. */
    private const val TEETER_DEGREES = 10.0


    /** Directions encoded in filter arguments like `wipe(down)` / `slide(fromLeft)`. */
    private fun filterDirection(arg: String?): Direction? = when (arg?.lowercase()) {
        "left", "fromright" -> Direction.LEFT
        "right", "fromleft" -> Direction.RIGHT
        "up", "frombottom" -> Direction.UP
        "down", "fromtop" -> Direction.DOWN
        "in" -> Direction.IN
        "out" -> Direction.OUT
        else -> null
    }

    /**
     * Maps an `animEffect` filter to an effect, or null when the filter isn't recognized
     * (callers fall through to the next synthesis strategy). Filters whose true look needs a
     * mask renderer (checkerboard, dissolve pixels, wheel spokes, …) map to the visually
     * closest primitive — the documented degrade ladder, never a missing shape.
     */
    fun fromFilter(filter: String?, role: EffectSpec.Role): EffectSpec? {
        if (filter == null) return null
        val name = filter.substringBefore('(').trim().lowercase()
        val arg = filter.substringAfter('(', "").substringBefore(')').trim().takeIf { it.isNotEmpty() }
        return when (name) {
            "fade", "dissolve", "checkerboard", "randombar", "image", "pixelate", "randomeffect" ->
                EffectSpec.Fade(role)
            "wipe" -> EffectSpec.Wipe(role, filterDirection(arg) ?: Direction.RIGHT)
            "blinds" -> EffectSpec.Wipe(role, if (arg.equals("horizontal", true)) Direction.DOWN else Direction.RIGHT)
            "strips" -> EffectSpec.Wipe(role, stripsDirection(arg))
            "slide" -> EffectSpec.Fly(role, filterDirection(arg) ?: Direction.UP)
            "barn" -> EffectSpec.Split(
                role,
                horizontal = arg?.contains("vertical", ignoreCase = true) != true,
                outward = arg?.contains("out", ignoreCase = true) == true
            )
            "box", "circle", "diamond", "plus", "wedge", "wheel", "spiral" ->
                EffectSpec.Zoom(role, fromScale = if (role == EffectSpec.Role.EXIT) 1.0 else 0.0)
            "stretch" -> EffectSpec.Zoom(role, fromScale = if (role == EffectSpec.Role.EXIT) 1.0 else 0.0)
            else -> null
        }
    }

    /** strips(downLeft|upRight|…) — diagonal wipes collapse to their vertical component. */
    private fun stripsDirection(arg: String?): Direction {
        val a = arg?.lowercase() ?: return Direction.RIGHT
        return when {
            a.contains("down") -> Direction.DOWN
            a.contains("up") -> Direction.UP
            a.contains("left") -> Direction.LEFT
            else -> Direction.RIGHT
        }
    }

    /** Fly-style presetSubtype direction bits: 1=top, 2=right, 4=bottom, 8=left (corners combine). */
    private fun subtypeDirection(subtype: Int?, entering: Boolean): Direction {
        val bits = subtype ?: 0
        // Pick the dominant axis for combined (corner) subtypes.
        val dir = when {
            bits and 4 != 0 -> Direction.UP      // from bottom → moves up
            bits and 1 != 0 -> Direction.DOWN    // from top → moves down
            bits and 8 != 0 -> Direction.RIGHT   // from left → moves right
            bits and 2 != 0 -> Direction.LEFT    // from right → moves left
            else -> Direction.UP
        }
        // Exits fly toward the stated edge instead of arriving from it.
        return if (entering) dir else when (dir) {
            Direction.UP -> Direction.DOWN
            Direction.DOWN -> Direction.UP
            Direction.LEFT -> Direction.RIGHT
            Direction.RIGHT -> Direction.LEFT
            else -> dir
        }
    }

    /**
     * Maps a preset id triple to an effect when behavior interpretation found nothing usable
     * (rare — PowerPoint serializes full behavior lists, so this table is the backstop).
     *
     * Entrance/exit ids follow the MS-OI29500 basic-effect numbering. The ids marked
     * provisional have not been validated against a real deck yet — the DumpTiming tool turns
     * any mis-mapped report into a one-line fix here. Unknown → null (caller degrades to Fade).
     */
    fun fromPreset(presetClass: String?, presetId: Int?, presetSubtype: Int?): EffectSpec? {
        val role = when (presetClass?.lowercase()) {
            "entr" -> EffectSpec.Role.ENTRANCE
            "exit" -> EffectSpec.Role.EXIT
            "emph" -> EffectSpec.Role.EMPHASIS
            "path" -> EffectSpec.Role.EMPHASIS
            else -> return null
        }
        val entering = role != EffectSpec.Role.EXIT
        fun zoomIn() = EffectSpec.Zoom(role, if (entering) 0.0 else 1.0)
        return when (role) {
            EffectSpec.Role.ENTRANCE, EffectSpec.Role.EXIT -> when (presetId) {
                ENTR_APPEAR -> EffectSpec.Appear(role)
                ENTR_FLY -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering))
                ENTR_BLINDS -> EffectSpec.Wipe(
                    role,
                    if (presetSubtype == SUBTYPE_BLINDS_VERTICAL) Direction.RIGHT else Direction.DOWN,
                )
                ENTR_BOX -> zoomIn()
                ENTR_CHECKERBOARD -> EffectSpec.Fade(role)
                ENTR_CIRCLE -> zoomIn()
                ENTR_CRAWL -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering))  // Crawl (slow fly)
                ENTR_DIAMOND -> zoomIn()
                ENTR_DISSOLVE -> EffectSpec.Fade(role)
                ENTR_FADE -> EffectSpec.Fade(role)
                ENTR_FLASH_ONCE -> EffectSpec.Fade(role)  // Flash Once (provisional)
                ENTR_PEEK -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering))
                ENTR_PLUS -> zoomIn()
                ENTR_RANDOM_BARS -> EffectSpec.Fade(role)
                ENTR_SPIRAL -> zoomIn()  // Spiral (provisional)
                ENTR_SPLIT -> EffectSpec.Split(
                    role,
                    horizontal = presetSubtype != SUBTYPE_SPLIT_VERTICAL,
                    outward = !entering,
                )
                ENTR_STRETCH -> zoomIn()  // Stretch (provisional)
                ENTR_STRIPS -> EffectSpec.Wipe(role, subtypeDirection(presetSubtype, entering))
                ENTR_SWIVEL -> EffectSpec.GrowShrink(role, 1.0, 1.0)  // Swivel (provisional — no 3D flip)
                ENTR_WEDGE -> zoomIn()  // Wedge (provisional)
                ENTR_WHEEL -> EffectSpec.Wipe(role, Direction.RIGHT)  // Wheel (provisional)
                ENTR_WIPE -> EffectSpec.Wipe(role, subtypeDirection(presetSubtype, entering))
                ENTR_ZOOM -> zoomIn()
                ENTR_RANDOM -> EffectSpec.Fade(role)  // Random (provisional)
                // Provisional: a boomerang's arc is not modeled, only its arrival direction.
                ENTR_BOOMERANG -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering))
                ENTR_BOUNCE_GROW_TURN -> zoomIn()  // Bounce/Grow&Turn (provisional)
                ENTR_BOUNCE -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering))  // Bounce (provisional)
                ENTR_FLOAT_UP -> EffectSpec.Fly(role, if (entering) Direction.UP else Direction.DOWN)
                // Provisional: float has an ease PowerPoint applies that the engine does not.
                ENTR_FLOAT_DOWN -> EffectSpec.Fly(role, if (entering) Direction.DOWN else Direction.UP)
                else -> null
            }
            EffectSpec.Role.EMPHASIS -> when (presetId) {
                EMPH_FILL_COLOR, EMPH_FONT_COLOR -> EffectSpec.Pulse(role)  // Fill/font color change → pulse degrade
                EMPH_GROW_SHRINK -> EffectSpec.GrowShrink(role, GROW_SHRINK_FACTOR, GROW_SHRINK_FACTOR)
                EMPH_SPIN -> EffectSpec.Spin(role)
                EMPH_TRANSPARENCY -> EffectSpec.Fade(role)  // Transparency (provisional)
                EMPH_PULSE -> EffectSpec.Pulse(role)
                EMPH_TEETER -> EffectSpec.Spin(role, degrees = TEETER_DEGREES)  // Teeter (provisional — small rock)
                EMPH_COLOR_PULSE -> EffectSpec.Pulse(role)  // Color pulse (provisional)
                EMPH_BLINK -> EffectSpec.Pulse(role)  // Blink (provisional)
                else -> null
            }
        }
    }
}
