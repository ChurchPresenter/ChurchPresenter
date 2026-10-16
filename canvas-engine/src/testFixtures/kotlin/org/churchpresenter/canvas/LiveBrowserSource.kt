package org.churchpresenter.canvas

import kotlinx.coroutines.awaitCancellation

/**
 * Holds browser source [sourceId] live, as a layer on the canvas does, with its page at [currentUrl] --
 * and no browser behind it. Returns the release, which the caller runs when done.
 *
 * What the properties panel reads from a live source is its current address, which a page that
 * navigated on its own moves away from the one the source was given. A test of that cannot launch a
 * real headless Chrome, so the launch is the one step replaced.
 */
fun holdLiveBrowserSource(sourceId: String, currentUrl: String): () -> Unit {
    val page = SharedBrowserFrameCache.BrowserPage(
        url = currentUrl, renderWidth = 640, renderHeight = 360, customCss = "", fps = 1, forceTransparent = false,
    )
    SharedBrowserFrameCache.acquireWith(sourceId, page) { entry, _ ->
        entry.currentUrl.value = currentUrl
        awaitCancellation()
    }
    return { SharedBrowserFrameCache.release(sourceId) }
}
