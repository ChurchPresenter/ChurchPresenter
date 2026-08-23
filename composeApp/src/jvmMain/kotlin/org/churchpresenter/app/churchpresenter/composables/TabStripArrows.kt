package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.ic_arrow_left
import org.churchpresenter.resources.generated.resources.ic_arrow_right
import org.churchpresenter.resources.generated.resources.tab_strip_scroll_back
import org.churchpresenter.resources.generated.resources.tab_strip_scroll_forward
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal const val TAB_STRIP_ARROW_BACK_TAG = "tabStripArrowBack"
internal const val TAB_STRIP_ARROW_FORWARD_TAG = "tabStripArrowForward"

/** How far one press of an overflow arrow scrolls a tab strip. */
private const val TAB_SCROLL_STEP = 200

private val ARROW_BUTTON_SIZE = 28.dp
private val ARROW_ICON_SIZE = 18.dp

/**
 * The overflow arrows for a scrollable tab strip, for the strip to place either side of its
 * [PrimaryScrollableTabRow][androidx.compose.material3.PrimaryScrollableTabRow].
 *
 * Both the main window's strip ([org.churchpresenter.app.churchpresenter.tabs.TabSection]) and the
 * settings dialog's hold more tabs than they can show, and a scrollable tab row gives no hint that
 * there are more past its edge. These are that hint — and being the same two composables in both
 * places, the two strips cannot drift apart in step size, sizing or when an arrow appears.
 *
 * Each shows only when there is somewhere to go in its direction, so a strip that fits shows
 * neither and costs nothing but the two `maxValue` reads.
 *
 * @param scrollState the same state passed to the tab row's `scrollState`.
 */
@Composable
fun TabStripBackArrow(scrollState: ScrollState) {
    // Outside the `if`, so it belongs to the strip rather than to the button: an arrow disappears
    // the moment its own scroll reaches the end, and a scope remembered inside the `if` would be
    // cancelled at that moment — killing the animation that was still finishing the move.
    val coroutineScope = rememberCoroutineScope()
    if (scrollState.maxValue > 0 && scrollState.value > 0) {
        TabStripArrow(Res.drawable.ic_arrow_left, Res.string.tab_strip_scroll_back, TAB_STRIP_ARROW_BACK_TAG, coroutineScope, scrollState) {
            (scrollState.value - TAB_SCROLL_STEP).coerceAtLeast(0)
        }
    }
}

/** The forward half of [TabStripBackArrow]; see its documentation. */
@Composable
fun TabStripForwardArrow(scrollState: ScrollState) {
    val coroutineScope = rememberCoroutineScope()
    if (scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue) {
        TabStripArrow(Res.drawable.ic_arrow_right, Res.string.tab_strip_scroll_forward, TAB_STRIP_ARROW_FORWARD_TAG, coroutineScope, scrollState) {
            (scrollState.value + TAB_SCROLL_STEP).coerceAtMost(scrollState.maxValue)
        }
    }
}

/**
 * @param description the button's name for assistive technology; the arrow itself carries no text.
 * @param target where to scroll to, read at click time rather than at composition.
 */
@Composable
private fun TabStripArrow(
    icon: DrawableResource,
    description: StringResource,
    tag: String,
    coroutineScope: CoroutineScope,
    scrollState: ScrollState,
    target: () -> Int,
) {
    IconButton(
        onClick = { coroutineScope.launch { scrollState.animateScrollTo(target()) } },
        modifier = Modifier.size(ARROW_BUTTON_SIZE).testTag(tag),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            modifier = Modifier.size(ARROW_ICON_SIZE),
        )
    }
}
