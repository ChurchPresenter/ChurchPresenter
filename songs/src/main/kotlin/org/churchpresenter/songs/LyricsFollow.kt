package org.churchpresenter.songs

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState

/** How the lyrics list moves so the selected entry can be seen. */
internal sealed interface LyricsFollow {
    /** The entry is already in full view: the list stays where it is. */
    data object Stay : LyricsFollow

    /** Scroll by [pixels] (negative is up) so the entry lines up with the nearer edge. */
    data class By(val pixels: Float) : LyricsFollow

    /** The entry is not laid out above the window: bring it to the top. */
    data object ToTop : LyricsFollow

    /** The entry is not laid out below the window: bring it in at the bottom. */
    data object ToBottom : LyricsFollow
}

/**
 * The least movement that shows entry [index] in full: none when it already is, so a row the operator
 * just clicked never moves out from under a second click. An entry taller than the window stays while
 * it fills half the window or more, and otherwise lines up at the top.
 */
internal fun lyricsFollow(info: LazyListLayoutInfo, index: Int): LyricsFollow {
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return LyricsFollow.ToTop
    val item = visible.firstOrNull { it.index == index }
    return when {
        item != null -> {
            val bottom = info.viewportEndOffset - info.afterContentPadding
            val end = item.offset + item.size
            val shown = minOf(end, bottom) - maxOf(item.offset, 0)
            when {
                // Taller than the window: leave it while it fills half the window or more.
                item.size > bottom ->
                    if (shown * 2 >= bottom) LyricsFollow.Stay else LyricsFollow.By(item.offset.toFloat())
                item.offset < 0 -> LyricsFollow.By(item.offset.toFloat())
                end > bottom -> LyricsFollow.By((end - bottom).toFloat())
                else -> LyricsFollow.Stay
            }
        }
        index < visible.first().index -> LyricsFollow.ToTop
        else -> LyricsFollow.ToBottom
    }
}

/** Scrolls this list the least it takes to show entry [index] in full. */
internal suspend fun LazyListState.followLyricsEntry(index: Int) {
    when (val follow = lyricsFollow(layoutInfo, index)) {
        LyricsFollow.Stay -> Unit
        is LyricsFollow.By -> animateScrollBy(follow.pixels)
        LyricsFollow.ToTop -> animateScrollToItem(index)
        LyricsFollow.ToBottom -> {
            // Its height is unknown until it is laid out: jump it to the top, then pull it down to
            // the bottom edge.
            scrollToItem(index)
            val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
            val room = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding - item.size
            if (room > 0) scrollBy(-room.toFloat())
        }
    }
}
