package org.churchpresenter.presenter

import org.churchpresenter.settings.DisplayRect
import org.churchpresenter.settings.ResolvedMerge
import kotlin.math.roundToInt

/**
 * Where the whole merged picture is laid out, inside an output showing only [tile] of it.
 *
 * The picture is measured at its own size in the output's pixels -- so text wraps and scales as it
 * would on one screen that big -- and shifted up and left by the tile's corner; everything outside
 * the output is clipped. [outWidth] x [outHeight] is the output as it is actually drawn, which for a
 * dev window is whatever the operator has dragged it to.
 */
internal data class TilePlacement(val width: Int, val height: Int, val x: Int, val y: Int)

internal fun tilePlacement(outWidth: Int, outHeight: Int, merge: ResolvedMerge, tile: DisplayRect): TilePlacement {
    val scaleX = outWidth / tile.width.toFloat()
    val scaleY = outHeight / tile.height.toFloat()
    return TilePlacement(
        width = (merge.width * scaleX).roundToInt(),
        height = (merge.height * scaleY).roundToInt(),
        x = -(tile.x * scaleX).roundToInt(),
        y = -(tile.y * scaleY).roundToInt(),
    )
}
