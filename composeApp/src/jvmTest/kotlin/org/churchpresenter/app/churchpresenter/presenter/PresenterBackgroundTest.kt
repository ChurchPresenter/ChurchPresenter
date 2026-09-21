package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [presenterScale] is what stops a song or a verse authored against a 1920x1080 output from running
 * off the edge of any other one. The property that matters is not a specific number for a specific
 * box (that would just re-pin whatever the function currently returns, bug included) but that the
 * scaled-down 1920x1080 reference box actually fits inside the real one -- which is exactly the
 * property a floor set too high breaks for a narrow/portrait output (issue: lyrics overflowing a
 * vertical output that a 16:9-family one never showed).
 */
class PresenterBackgroundTest {

    private fun scale(width: Int, height: Int) = presenterScale(width.dp, height.dp)

    /** The reference box, scaled down, must land inside the real one -- the property the bug broke. */
    private fun assertReferenceBoxFits(width: Int, height: Int) {
        val result = scale(width, height)
        val scaledWidth = result * BACKGROUND_REFERENCE_WIDTH
        val scaledHeight = result * REFERENCE_HEIGHT
        assertTrue(
            scaledWidth <= width + 0.01f,
            "presenterScale($width, $height) = $result scales the reference width to $scaledWidth, " +
                "which overflows the ${width}px output",
        )
        assertTrue(
            scaledHeight <= height + 0.01f,
            "presenterScale($width, $height) = $result scales the reference height to $scaledHeight, " +
                "which overflows the ${height}px output",
        )
    }

    // ── 16:9-family landscape presets: must not regress ────────────────────────────────────────

    @Test
    fun `every shipped landscape preset keeps the reference box inside its output`() {
        listOf(
            1280 to 720,
            1920 to 1080,
            2560 to 1440,
            3840 to 2160,
            2560 to 1080,
            3440 to 1440,
        ).forEach { (w, h) -> assertReferenceBoxFits(w, h) }
    }

    @Test
    fun `a 1080p output scales at exactly 1`() {
        assertEquals(1f, scale(1920, 1080))
    }

    // ── Vertical / mobile outputs: this is the bug ──────────────────────────────────────────────

    @Test
    fun `the shipped portrait preset keeps the reference box inside its output`() {
        assertReferenceBoxFits(1080, 1920)
    }

    @Test
    fun `a narrow vertical output keeps the reference box inside its output`() {
        // 720x1280 is exactly the case that was floored up to 0.5 and overflowed: min(720/1920,
        // 1280/1080) = 0.375, which the old MIN_PRESENTER_SCALE of 0.5 discarded.
        assertReferenceBoxFits(720, 1280)
    }

    @Test
    fun `a custom narrow vertical output keeps the reference box inside its output`() {
        assertReferenceBoxFits(400, 1200)
    }

    @Test
    fun `a narrow vertical output is not floored up past its true aspect ratio`() {
        // The failure mode was not "the scale is a bit small" but "the floor overrides a legitimate,
        // smaller ratio" -- so pin the exact value a 720x1280 output must compute, not just that it
        // fits (which a coincidentally-larger floor could also satisfy).
        assertEquals(720f / BACKGROUND_REFERENCE_WIDTH, scale(720, 1280))
    }

    // ── The sanity floor: a genuinely tiny/pathological output, not merely narrow ───────────────

    @Test
    fun `an absurdly narrow output is held at the floor rather than shrinking to nothing`() {
        // 16 is RESOLUTION_RANGE's own lower bound (ResolutionPicker). Its true ratio is far below any
        // legible floor (16 / 1920 ~= 0.008), so MIN_PRESENTER_SCALE existing at all is still correct
        // here -- this output overflows no matter what, and the floor keeps its type from vanishing
        // rather than pretending a fit exists.
        assertEquals(MIN_PRESENTER_SCALE, scale(16, 1200))
    }

    @Test
    fun `the floor never fires for an ordinary portrait size`() {
        listOf(1080 to 1920, 720 to 1280, 400 to 1200).forEach { (w, h) ->
            val result = scale(w, h)
            assertTrue(result > MIN_PRESENTER_SCALE, "scale($w, $h) = $result sat right on the floor")
        }
    }

    // ── The ceiling: untouched by this fix ──────────────────────────────────────────────────────

    @Test
    fun `a huge output is held at the ceiling rather than growing without bound`() {
        assertEquals(MAX_PRESENTER_SCALE, scale(3840 * 10, 2160 * 10))
    }
}
