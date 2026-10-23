package org.churchpresenter.web.offscreen

import io.mockk.mockk
import java.awt.Rectangle
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * An off-screen page on a stub client that is never started natively, its reports to [onReport]: what a
 * suite draws or steers where the real page needs Chromium. JCEF's native calls are no-ops without
 * its library, so everything the page itself decides still runs. Close it after the test.
 */
fun stubOffscreenPage(onReport: (Any) -> Unit = {}): OffscreenPage = checkNotNull(
    OffscreenPage.openWith(
        mockk(relaxed = true), "https://example.org/", {}, {},
        OffscreenPage.Options("Mac OS X", reports = true, mobileMode = { false }, start = {}, send = { onReport(it) }),
    ),
)

/** Hands [page] a full paint of [width]×[height] filled from [seed], as Chromium would. */
fun paintStub(page: OffscreenPage, width: Int, height: Int, seed: Int = 1) {
    val bytes = ByteArray(width * height * BYTES).also { Random(seed).nextBytes(it) }
    val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).rewind()
    page.browser.onPaint(page.browser, false, arrayOf(Rectangle(0, 0, width, height)), buffer, width, height)
}

private const val BYTES = 4
