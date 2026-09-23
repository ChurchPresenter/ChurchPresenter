@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A gradient background resolves on a full screen, not only on a band.
 *
 * `backgroundModifier` carried an explicit `BACKGROUND_GRADIENT -> Modifier` branch that painted
 * *nothing*, so a full-screen surface set to Gradient drew an empty frame. The two band surfaces
 * escaped it by drawing their own gradient inside the presenter's band path, keyed to the band's
 * height -- which is why the type was offered on those two alone. The type was hidden rather than
 * the renderer finished.
 *
 * Asserted on what [resolveBackground] hands the renderer, because a `Brush` cannot be read back
 * out of a `Modifier`. [resolveBackground] is a composable, so it is driven rather than called.
 */
class FullScreenGradientTest {

    private val top = "#102030"
    private val bottom = "#405060"

    private fun config(type: String) = BackgroundConfig(
        backgroundType = type,
        backgroundColor = "#FF0000",
        gradientTopColor = top,
        gradientBottomColor = bottom,
        gradientTopOpacity = 1f,
        gradientBottomOpacity = 0.5f,
        gradientPosition = 0.75f,
    )

    /** What a full-screen surface carrying [config] hands the renderer. */
    private fun resolveFullScreen(config: BackgroundConfig): ResolvedBackground {
        lateinit var resolved: ResolvedBackground
        runComposeUiTest {
            setContent {
                resolved = resolveBackground(
                    settings = BackgroundSettings(),
                    config = config,
                    isLowerThird = false,
                    showBackground = true,
                    transparentWhenBlank = false,
                    knownCameras = emptyList(),
                )
            }
        }
        return resolved
    }

    @Test
    fun `a gradient resolves both of its ends`() {
        val resolved = resolveFullScreen(config(Constants.BACKGROUND_GRADIENT))

        assertEquals(
            parseHexColor(top),
            resolved.color,
            "the near end is the gradient's top, not the surface's flat colour",
        )
        assertEquals(
            parseHexColor(bottom).copy(alpha = 0.5f),
            assertNotNull(resolved.gradientEndColor, "a gradient must resolve a far end"),
        )
        assertEquals(0.75f, resolved.gradientPosition, "and where it finishes its transition")
    }

    @Test
    fun `a surface that is not a gradient resolves no far end`() {
        val resolved = resolveFullScreen(config(Constants.BACKGROUND_COLOR))

        assertNull(resolved.gradientEndColor, "only a gradient has one")
        assertEquals(parseHexColor("#FF0000"), resolved.color, "and it keeps its own flat colour")
    }

    @Test
    fun `the two ends fade independently`() {
        val resolved = resolveFullScreen(config(Constants.BACKGROUND_GRADIENT))

        // A single surface opacity could not express this, which is why each end carries its own.
        // Toleranced because Color stores alpha in 8 bits: 0.5f round-trips as 0.5019608.
        assertEquals(1f, resolved.color.alpha, absoluteTolerance = ALPHA_TOLERANCE)
        assertEquals(
            0.5f,
            assertNotNull(resolved.gradientEndColor).alpha,
            absoluteTolerance = ALPHA_TOLERANCE,
        )
    }

    private companion object {
        /** One 8-bit step is ~0.0039; this is comfortably inside a step and outside a real change. */
        const val ALPHA_TOLERANCE = 0.01f
    }
}
