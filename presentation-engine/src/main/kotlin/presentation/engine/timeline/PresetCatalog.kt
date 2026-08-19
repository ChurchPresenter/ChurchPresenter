package presentation.engine.timeline

import presentation.engine.model.Direction
import presentation.engine.model.EffectSpec

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
                1 -> EffectSpec.Appear(role)
                2 -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering))
                3 -> EffectSpec.Wipe(role, if (presetSubtype == 10) Direction.RIGHT else Direction.DOWN) // Blinds
                4 -> zoomIn()                                            // Box
                5 -> EffectSpec.Fade(role)                               // Checkerboard
                6 -> zoomIn()                                            // Circle
                7 -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering)) // Crawl (slow fly)
                8 -> zoomIn()                                            // Diamond
                9 -> EffectSpec.Fade(role)                               // Dissolve
                10 -> EffectSpec.Fade(role)                              // Fade
                11 -> EffectSpec.Fade(role)                              // Flash Once (provisional)
                12 -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering)) // Peek
                13 -> zoomIn()                                           // Plus
                14 -> EffectSpec.Fade(role)                              // Random bars
                15 -> zoomIn()                                           // Spiral (provisional)
                16 -> EffectSpec.Split(role, horizontal = presetSubtype != 21, outward = !entering)
                17 -> zoomIn()                                           // Stretch (provisional)
                18 -> EffectSpec.Wipe(role, subtypeDirection(presetSubtype, entering)) // Strips
                19 -> EffectSpec.GrowShrink(role, 1.0, 1.0)              // Swivel (provisional — no 3D flip)
                20 -> zoomIn()                                           // Wedge (provisional)
                21 -> EffectSpec.Wipe(role, Direction.RIGHT)             // Wheel (provisional)
                22 -> EffectSpec.Wipe(role, subtypeDirection(presetSubtype, entering)) // Wipe
                23 -> zoomIn()                                           // Zoom
                24 -> EffectSpec.Fade(role)                              // Random (provisional)
                25 -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering)) // Boomerang (provisional)
                26 -> zoomIn()                                           // Bounce/Grow&Turn (provisional)
                30 -> EffectSpec.Fly(role, subtypeDirection(presetSubtype, entering)) // Bounce (provisional)
                42 -> EffectSpec.Fly(role, if (entering) Direction.UP else Direction.DOWN)   // Float Up
                47 -> EffectSpec.Fly(role, if (entering) Direction.DOWN else Direction.UP)   // Float Down (provisional)
                else -> null
            }
            EffectSpec.Role.EMPHASIS -> when (presetId) {
                1, 3 -> EffectSpec.Pulse(role)               // Fill/font color change → pulse degrade
                6 -> EffectSpec.GrowShrink(role, 1.5, 1.5)   // Grow/Shrink
                8 -> EffectSpec.Spin(role)                   // Spin
                9 -> EffectSpec.Fade(role)                   // Transparency (provisional)
                26 -> EffectSpec.Pulse(role)                 // Pulse
                32 -> EffectSpec.Spin(role, degrees = 10.0)  // Teeter (provisional — small rock)
                35 -> EffectSpec.Pulse(role)                 // Color pulse (provisional)
                36 -> EffectSpec.Pulse(role)                 // Blink (provisional)
                else -> null
            }
        }
    }
}
