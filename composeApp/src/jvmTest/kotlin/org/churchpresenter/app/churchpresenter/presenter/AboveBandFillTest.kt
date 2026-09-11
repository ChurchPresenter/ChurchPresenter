package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Which wash a lower-third surface ends up with, and whether one is drawn at all. */
class AboveBandFillTest {

    private fun defaults(
        type: String = Constants.BACKGROUND_TRANSPARENT,
        color: String = "#000000",
        opacity: Float = 1f,
    ) = BackgroundSettings(
        defaultLowerThirdAboveBandType = type,
        defaultLowerThirdAboveBandColor = color,
        defaultLowerThirdAboveBandOpacity = opacity,
    )

    private fun surface(
        type: String = Constants.BACKGROUND_DEFAULT,
        color: String = "#000000",
        opacity: Float = 1f,
        bandType: String = Constants.BACKGROUND_COLOR,
    ) = BackgroundConfig(
        backgroundType = bandType,
        aboveBandType = type,
        aboveBandColor = color,
        aboveBandOpacity = opacity,
    )

    @Test
    fun `settings that have never been given a wash paint nothing`() {
        assertNull(
            aboveBandFill(BackgroundSettings(), BackgroundConfig()),
            "an untouched install must draw the band and leave everything above it alone",
        )
    }

    @Test
    fun `a surface on Color paints its own color`() {
        val fill = aboveBandFill(defaults(), surface(Constants.BACKGROUND_COLOR, "#112233"))
        assertEquals(Color(0x11, 0x22, 0x33), fill)
    }

    @Test
    fun `a surface on Transparent paints nothing even where the default has a wash`() {
        val fill = aboveBandFill(
            defaults(Constants.BACKGROUND_COLOR, "#AABBCC"),
            surface(Constants.BACKGROUND_TRANSPARENT),
        )
        assertNull(fill, "Transparent is a decision, not an absence — it must not fall through")
    }

    @Test
    fun `a surface on Default takes the default lower third's wash`() {
        val fill = aboveBandFill(
            defaults(Constants.BACKGROUND_COLOR, "#AABBCC", opacity = 0.5f),
            surface(Constants.BACKGROUND_DEFAULT, color = "#FF0000", opacity = 1f),
        )
        assertEquals(
            Color(0xAA, 0xBB, 0xCC).copy(alpha = 0.5f),
            fill,
            "colour and opacity both come from the surface being deferred to",
        )
        assertEquals(0.5f, assertNotNull(fill).alpha, 1f / 255f)
    }

    @Test
    fun `a surface on Default paints nothing where the default lower third is transparent`() {
        assertNull(aboveBandFill(defaults(Constants.BACKGROUND_TRANSPARENT), surface()))
    }

    @Test
    fun `the wash follows its own type, not the band's`() {
        // The band carries a picture of its own — which is exactly when reading `backgroundType`
        // to decide the wash would have silently dropped the default's.
        val fill = aboveBandFill(
            defaults(Constants.BACKGROUND_COLOR, "#AABBCC"),
            surface(Constants.BACKGROUND_DEFAULT, bandType = Constants.BACKGROUND_IMAGE),
        )
        assertEquals(Color(0xAA, 0xBB, 0xCC), fill)
    }

    @Test
    fun `opacity is applied`() {
        val fill = aboveBandFill(defaults(), surface(Constants.BACKGROUND_COLOR, "#FFFFFF", 0.25f))
        // Color packs alpha into eight bits, so 0.25 comes back as the nearest of 255 steps.
        assertEquals(0.25f, assertNotNull(fill).alpha, 1f / 255f)
    }

    @Test
    fun `an out-of-range opacity is clamped rather than thrown away`() {
        fun alphaAt(opacity: Float) =
            assertNotNull(aboveBandFill(defaults(), surface(Constants.BACKGROUND_COLOR, opacity = opacity))).alpha
        assertEquals(1f, alphaAt(4f))
        assertEquals(0f, alphaAt(-1f))
    }

    @Test
    fun `a default lower third on Color answers for itself`() {
        // What `configFor(DEFAULT_LOWER_THIRD)` hands back: the flat fields, as a config.
        val settings = defaults(Constants.BACKGROUND_COLOR, "#334455")
        val asConfig = surface(Constants.BACKGROUND_COLOR, "#334455")
        assertEquals(Color(0x33, 0x44, 0x55), aboveBandFill(settings, asConfig))
    }
}
