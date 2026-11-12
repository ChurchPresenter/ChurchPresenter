package org.churchpresenter.web.offscreen

import androidx.compose.ui.unit.IntSize
import io.mockk.mockk
import org.cef.CefClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The desktop and mobile layouts of an off-screen page: a desktop page is always landscape and a
 * mobile page always portrait and phone-wide, a screen of the other shape getting the largest page
 * of its own shape that fits; and a page whose mode flips taking the new shape at once, with or
 * without a surface asking for a size.
 */
class OffscreenPageLayoutTest {
    private val client = mockk<CefClient>(relaxed = true)
    private val opened = mutableListOf<OffscreenPage>()
    private var mobile = false

    private fun open(): OffscreenPage = assertNotNull(
        OffscreenPage.openWith(
            client, "https://example.org/", {}, {},
            OffscreenPage.Options("Mac OS X", reports = false, mobileMode = { mobile }, start = {}, send = {}),
        ),
    ).also { opened += it }

    @AfterTest
    fun closePages() {
        opened.forEach(OffscreenPage::close)
    }

    private val OffscreenPage.view get() = browser.viewWidth to browser.viewHeight

    @Test
    fun `a screen of the page's own shape is the page's shape`() {
        assertEquals(IntSize(1920, 1080), OffscreenPage.shapeFor(IntSize(1920, 1080), mobile = false))
        assertEquals(IntSize(1080, 1920), OffscreenPage.shapeFor(IntSize(1080, 1920), mobile = true))
    }

    @Test
    fun `a screen of the other shape gets the largest page of the right shape that fits`() {
        assertEquals(IntSize(1080, 607), OffscreenPage.shapeFor(IntSize(1080, 1920), mobile = false))
        assertEquals(IntSize(607, 1080), OffscreenPage.shapeFor(IntSize(1920, 1080), mobile = true))
        val square = IntSize(1000, 1000)
        assertEquals(IntSize(562, 1000), OffscreenPage.shapeFor(square, mobile = true), "square is landscape")
        assertEquals(IntSize(1000, 1000), OffscreenPage.shapeFor(IntSize(1000, 1000), mobile = false))
    }

    @Test
    fun `a mobile page is laid out phone-wide, scaled up to a large screen and never narrower on a small one`() {
        val (big, bigScale) = OffscreenPage.layoutFor(IntSize(1080, 1920), mobile = true)
        assertEquals(IntSize(OffscreenPage.MOBILE_LAYOUT_WIDTH, 764), big)
        assertEquals(1080.0 / OffscreenPage.MOBILE_LAYOUT_WIDTH, bigScale, absoluteTolerance = 1e-9)

        val (small, smallScale) = OffscreenPage.layoutFor(IntSize(200, 400), density = 2.0, mobile = true)
        assertEquals(IntSize(OffscreenPage.MOBILE_LAYOUT_WIDTH, 860) to 1.0, small to smallScale)
    }

    @Test
    fun `the largest request is compared in the page's own shape`() {
        val page = open()
        page.requestSize(Any(), IntSize(1920, 1080))
        page.requestSize(Any(), IntSize(1080, 1920))
        page.applySize()
        assertEquals(1920 to 1080, page.view, "the portrait screen's desktop page is smaller")

        mobile = true
        page.applySize()
        assertEquals(OffscreenPage.MOBILE_LAYOUT_WIDTH to 764, page.view, "now the portrait screen's page is larger")
    }

    @Test
    fun `a page opened mobile starts portrait`() {
        mobile = true
        assertEquals(OffscreenPage.MOBILE_LAYOUT_WIDTH to 765, open().view)
    }

    @Test
    fun `a change of mode reshapes the page even with no surface asking for a size`() {
        val page = open()
        assertEquals(1920 to 1080, page.view)

        mobile = true
        page.modeChanged()
        assertEquals(OffscreenPage.MOBILE_LAYOUT_WIDTH to 765, page.view)

        mobile = false
        page.modeChanged()
        assertEquals(1920 to 1080, page.view, "the screen's own shape, not a landscape cut of the portrait page")
    }

    @Test
    fun `nothing changes without a request or a change of mode`() {
        val page = open()
        page.modeChanged()
        assertEquals(1920 to 1080, page.view)
    }
}
