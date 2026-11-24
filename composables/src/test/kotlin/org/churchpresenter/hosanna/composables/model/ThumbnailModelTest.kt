package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThumbnailModelTest {

    @Test
    fun `only a shown, available item can go live`() {
        assertTrue(canGoLive(ThumbnailStatus.Ready, hidden = false))
        assertTrue(canGoLive(ThumbnailStatus.Offline, hidden = false))
        assertFalse(canGoLive(ThumbnailStatus.Ready, hidden = true))
        assertFalse(canGoLive(ThumbnailStatus.Loading, hidden = false))
        assertFalse(canGoLive(ThumbnailStatus.Missing, hidden = false))
    }

    @Test
    fun `hover buttons skip small, loading and missing thumbnails`() {
        assertTrue(showsHoverActions(ThumbnailSize.Medium, ThumbnailStatus.Ready))
        assertTrue(showsHoverActions(ThumbnailSize.Large, ThumbnailStatus.Offline))
        assertFalse(showsHoverActions(ThumbnailSize.Small, ThumbnailStatus.Ready))
        assertFalse(showsHoverActions(ThumbnailSize.Medium, ThumbnailStatus.Loading))
        assertFalse(showsHoverActions(ThumbnailSize.Medium, ThumbnailStatus.Missing))
    }
}
