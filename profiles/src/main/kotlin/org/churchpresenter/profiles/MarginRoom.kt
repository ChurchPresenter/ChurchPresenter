package org.churchpresenter.profiles

/** The share of the room the four margins may take between them on each axis; the rest is the text's. */
private const val MARGIN_SHARE = 0.9f

/** The output the margins are measured against when no preview says otherwise: 1080p. */
private const val REFERENCE_WIDTH = 1920
private const val REFERENCE_HEIGHT = 1080
private const val BAND_FULL_PERCENT = 100

/**
 * The room a page's margins are taken from, in output pixels: the screen on a full screen, the band
 * on a lower third.
 *
 * A margin may grow until it and the one opposite leave a tenth of the room for the text, so a
 * margin can take half the screen and more -- the room a sign-language interpreter needs -- without
 * the two ever meeting and leaving nothing to draw in.
 */
internal data class MarginRoom(val width: Int, val height: Int) {
    /** The most [edge] may be while its opposite stays as [margins] has it. */
    fun maxFor(edge: MarginSide, margins: Margins): Int {
        val (size, opposite) = when (edge) {
            MarginSide.TOP -> height to margins.bottom
            MarginSide.BOTTOM -> height to margins.top
            MarginSide.LEFT -> width to margins.right
            MarginSide.RIGHT -> width to margins.left
        }
        return ((size * MARGIN_SHARE).toInt() - opposite).coerceAtLeast(0)
    }

    companion object {
        /** A 1080p full screen. */
        val FULL_SCREEN = MarginRoom(REFERENCE_WIDTH, REFERENCE_HEIGHT)

        /** A [width] × [height] output, or the band [bandPercent] of its height on a lower third. */
        fun of(width: Int, height: Int, bandPercent: Int? = null): MarginRoom =
            MarginRoom(width, bandPercent?.let { height * it / BAND_FULL_PERCENT } ?: height)

        /** A 1080p output's band of [bandPercent], or the whole of it where there is no band. */
        fun reference(bandPercent: Int?): MarginRoom = of(REFERENCE_WIDTH, REFERENCE_HEIGHT, bandPercent)
    }
}

/** The four margins by side. */
internal enum class MarginSide { TOP, BOTTOM, LEFT, RIGHT }
