package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import org.churchpresenter.settings.ResolvedMerge
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

/**
 * Draws [output]'s rectangle of [merge]'s picture: the content below is laid out at the picture's
 * size and only this output's part of it shows. No change for an output the merge does not hold.
 */
internal fun Modifier.mergeTile(merge: ResolvedMerge?, output: String): Modifier {
    val tile = merge?.tiles?.get(output) ?: return this
    return clipToBounds().layout { measurable, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else tile.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else tile.height
        val placement = tilePlacement(width, height, merge, tile)
        val placeable = measurable.measure(Constraints.fixed(placement.width, placement.height))
        layout(width, height) { placeable.place(placement.x, placement.y) }
    }
}

/**
 * Whether what is being drawn is one tile of a merged picture. A live web page is a Swing panel,
 * which cannot be shifted or clipped to a tile, so inside one the website is drawn from its
 * snapshot instead -- as the off-screen outputs always draw it.
 */
val LocalInMergedTile = staticCompositionLocalOf { false }

/** [content] as [output]'s tile of [merge] -- see [mergeTile]; drawn as it is when there is none. */
@Composable
fun MergedTile(merge: ResolvedMerge?, output: String, content: @Composable BoxScope.() -> Unit) {
    val inTile = merge?.tiles?.containsKey(output) == true
    CompositionLocalProvider(LocalInMergedTile provides inTile) {
        Box(Modifier.fillMaxSize().mergeTile(merge, output), content = content)
    }
}

/**
 * [this] output sized as the whole of [merge]'s picture, whatever kind it is -- how the preview
 * panel draws a merge: once, at the picture's shape, in place of its separate tiles.
 */
fun ScreenAssignment.sizedAs(merge: ResolvedMerge): ScreenAssignment = copy(
    targetBoundsW = if (targetBoundsW > 0) merge.width else 0,
    targetBoundsH = if (targetBoundsH > 0) merge.height else 0,
    devWindowWidth = merge.width,
    devWindowHeight = merge.height,
    ndiWidth = merge.width,
    ndiHeight = merge.height,
    omtWidth = merge.width,
    omtHeight = merge.height,
    browserSourceWidth = merge.width,
    browserSourceHeight = merge.height,
)

/**
 * The output whose live mode [output] shows: the first member of its merge, so a lock on that one
 * holds the whole picture together -- every tile of one picture has to show the same thing. An
 * output outside every merge follows its own.
 */
fun mergeHostIndex(merges: Map<String, ResolvedMerge>, kind: String, index: Int): Int {
    val host = merges[Constants.previewOutputKey(kind, index)]?.host ?: return index
    return host.substringAfter(':').toIntOrNull() ?: index
}
