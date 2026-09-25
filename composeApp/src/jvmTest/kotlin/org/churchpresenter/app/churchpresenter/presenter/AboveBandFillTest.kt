package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun fill(settings: BackgroundSettings, config: BackgroundConfig) =
        resolveAboveBand(settings, config).fill

    @Test
    fun `settings that have never been given a wash paint nothing`() {
        assertNull(
            fill(BackgroundSettings(), BackgroundConfig()),
            "an untouched install must draw the band and leave everything above it alone",
        )
    }

    @Test
    fun `a surface on Color paints its own color`() {
        val fill = fill(defaults(), surface(Constants.BACKGROUND_COLOR, "#112233"))
        assertEquals(Color(0x11, 0x22, 0x33), fill)
    }

    @Test
    fun `a surface on Transparent paints nothing even where the default has a wash`() {
        val fill = fill(
            defaults(Constants.BACKGROUND_COLOR, "#AABBCC"),
            surface(Constants.BACKGROUND_TRANSPARENT),
        )
        assertNull(fill, "Transparent is a decision, not an absence — it must not fall through")
    }

    @Test
    fun `a surface on Default takes the default lower third's wash`() {
        val fill = fill(
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
        assertNull(fill(defaults(Constants.BACKGROUND_TRANSPARENT), surface()))
    }

    @Test
    fun `the wash follows its own type, not the band's`() {
        // The band carries a picture of its own — which is exactly when reading `backgroundType`
        // to decide the wash would have silently dropped the default's.
        val fill = fill(
            defaults(Constants.BACKGROUND_COLOR, "#AABBCC"),
            surface(Constants.BACKGROUND_DEFAULT, bandType = Constants.BACKGROUND_IMAGE),
        )
        assertEquals(Color(0xAA, 0xBB, 0xCC), fill)
    }

    @Test
    fun `opacity is applied`() {
        val fill = fill(defaults(), surface(Constants.BACKGROUND_COLOR, "#FFFFFF", 0.25f))
        // Color packs alpha into eight bits, so 0.25 comes back as the nearest of 255 steps.
        assertEquals(0.25f, assertNotNull(fill).alpha, 1f / 255f)
    }

    @Test
    fun `an out-of-range opacity is clamped rather than thrown away`() {
        fun alphaAt(opacity: Float) =
            assertNotNull(fill(defaults(), surface(Constants.BACKGROUND_COLOR, opacity = opacity))).alpha
        assertEquals(1f, alphaAt(4f))
        assertEquals(0f, alphaAt(-1f))
    }

    @Test
    fun `a default lower third on Color answers for itself`() {
        // What `configFor(DEFAULT_LOWER_THIRD)` hands back: the flat fields, as a config.
        val settings = defaults(Constants.BACKGROUND_COLOR, "#334455")
        val asConfig = surface(Constants.BACKGROUND_COLOR, "#334455")
        assertEquals(Color(0x33, 0x44, 0x55), fill(settings, asConfig))
    }

    @Test
    fun `fillsBehindBand defaults on and follows the same defer chain as the colour`() {
        assertEquals(true, resolveAboveBand(BackgroundSettings(), BackgroundConfig()).fillsBehindBand)
        val settings = BackgroundSettings(defaultLowerThirdAboveBandFillsBehindBand = false)
        assertEquals(
            false,
            resolveAboveBand(settings, surface(Constants.BACKGROUND_DEFAULT)).fillsBehindBand,
            "a surface on Default follows the Default Lower Third's choice",
        )
        val ownType = BackgroundConfig(aboveBandType = Constants.BACKGROUND_COLOR, aboveBandFillsBehindBand = true)
        assertEquals(
            true,
            resolveAboveBand(settings, ownType).fillsBehindBand,
            "a surface with its own type does not defer, even to a Default that disagrees",
        )
    }

    // ── A picture, a clip or a camera above the band ─────────────────────────────

    private val webcam = CameraDeviceRef(devicePath = "avfoundation://1", deviceName = "Logitech BRIO")

    @Test
    fun `a surface above the band on Image carries its picture and no wash`() {
        val above = resolveAboveBand(
            BackgroundSettings(),
            BackgroundConfig(
                aboveBandType = Constants.BACKGROUND_IMAGE,
                aboveBandImage = "/pictures/hall.jpg",
                aboveBandOpacity = 0.5f,
            ),
        )

        assertNull(above.fill, "a picture is drawn, not a colour")
        val media = assertNotNull(above.media)
        assertEquals(Constants.BACKGROUND_IMAGE, media.type)
        assertEquals("/pictures/hall.jpg", media.imagePath)
        assertEquals(0.5f, media.opacity)
    }

    @Test
    fun `a clip and a camera above the band are what the renderer keys on`() {
        val clip = resolveAboveBand(
            BackgroundSettings(),
            BackgroundConfig(aboveBandType = Constants.BACKGROUND_VIDEO, aboveBandVideo = "/clips/loop.mp4"),
        ).media
        val camera = resolveAboveBand(
            BackgroundSettings(),
            BackgroundConfig(aboveBandType = Constants.BACKGROUND_CAMERA, aboveBandCamera = webcam),
        ).media

        assertTrue(assertNotNull(clip).usesVideo)
        assertEquals(webcam, assertNotNull(camera).camera)
        assertTrue(camera.usesCamera)
    }

    @Test
    fun `media with nothing chosen draws nothing rather than black`() {
        listOf(Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO, Constants.BACKGROUND_CAMERA).forEach { type ->
            val above = resolveAboveBand(BackgroundSettings(), BackgroundConfig(aboveBandType = type))
            assertNull(above.media, "$type with no file or device must leave the output alone")
            assertNull(above.fill)
        }
    }

    @Test
    fun `a surface on Default takes the Default Lower Third's clip`() {
        val above = resolveAboveBand(
            BackgroundSettings(
                defaultLowerThirdAboveBandType = Constants.BACKGROUND_VIDEO,
                defaultLowerThirdAboveBandVideo = "/clips/house.mp4",
            ),
            surface(Constants.BACKGROUND_DEFAULT),
        )

        assertEquals("/clips/house.mp4", assertNotNull(above.media).videoPath)
    }
}
