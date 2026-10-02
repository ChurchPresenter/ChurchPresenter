package org.churchpresenter.sharedui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * One block of presented text a preview can point at: a Bible translation or its reference, keyed
 * by the translation's file name, or one element of a song slide, keyed as its move is stored.
 * [Kind.CELL] is the clipped cell a translation is drawn in, which nothing moved should leave.
 * [Kind.BOX] is an item's text box, keyed as the box is stored.
 */
data class PresentedBlock(val kind: Kind, val key: String) {
    enum class Kind { TRANSLATION, REFERENCE, ELEMENT, CELL, BOX }
}

/**
 * Where each [PresentedBlock] was drawn, in window pixels -- provided only by the Profiles preview,
 * whose Adjust handles select and move blocks. Null everywhere else, so an output window records
 * nothing: this is layout reporting and never changes what is drawn.
 */
val LocalPresentedBlocks = staticCompositionLocalOf<MutableMap<PresentedBlock, Rect>?> { null }

/** [this] reporting where it was drawn as [block], while a preview is listening. */
@Composable
fun Modifier.reportsBlock(block: PresentedBlock): Modifier {
    val blocks = LocalPresentedBlocks.current ?: return this
    return onGloballyPositioned { blocks[block] = it.boundsInWindow() }
}
