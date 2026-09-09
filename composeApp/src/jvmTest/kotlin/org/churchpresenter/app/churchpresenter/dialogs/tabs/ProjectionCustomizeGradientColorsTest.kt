@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The two ends of a band's gradient. Only the lower-third surfaces offer one, so every fixture here
 * drives a band; the Bible and Songs surfaces are driven separately because they are different
 * stored configs reached through the same rows.
 */
class ProjectionCustomizeGradientColorsTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun gradient(top: String, bottom: String, position: Float = 0.37f) = BackgroundConfig(
        backgroundType = Constants.BACKGROUND_GRADIENT,
        gradientTopColor = top,
        gradientBottomColor = bottom,
        gradientPosition = position,
    )

    private fun output() = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = gradient("#101010", "#202020"),
            songLowerThirdBackground = gradient("#303030", "#404040"),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = band)),
        ),
    )

    private fun AppSettings.stored(): BackgroundSettings = assertNotNull(
        projectionSettings.screenAssignments[0].backgroundOverride,
        "the output must have its own Backgrounds",
    )

    // ── The Bible band's gradient ─────────────────────────────────────────────

    @Test
    fun `both ends are shown by the color they hold`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText("#101010").assertExists()
            onNodeWithText("#202020").assertExists()
        }
    }

    @Test
    fun `recoloring the top end writes it`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            recolor("#101010", "#112233")

            val stored = get().stored().bibleLowerThirdBackground
            assertEquals("#112233", stored.gradientTopColor)
            assertEquals("#202020", stored.gradientBottomColor, "the other end must not move")
        }
    }

    @Test
    fun `recoloring the bottom end writes it`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            recolor("#202020", "#334455")

            val stored = get().stored().bibleLowerThirdBackground
            assertEquals("#334455", stored.gradientBottomColor)
            assertEquals("#101010", stored.gradientTopColor)
        }
    }

    @Test
    fun `recoloring an end leaves the turnover alone`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            recolor("#101010", "#112233")

            assertEquals(0.37f, get().stored().bibleLowerThirdBackground.gradientPosition)
        }
    }

    @Test
    fun `both ends can be set in turn`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            recolor("#101010", "#112233")
            recolor("#202020", "#334455")

            val stored = get().stored().bibleLowerThirdBackground
            assertEquals("#112233", stored.gradientTopColor)
            assertEquals("#334455", stored.gradientBottomColor)
        }
    }

    @Test
    fun `the Bible band's ends are not the Songs band's`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            recolor("#101010", "#112233")

            val stored = get().stored()
            assertEquals("#112233", stored.bibleLowerThirdBackground.gradientTopColor)
            assertEquals("#303030", stored.songLowerThirdBackground.gradientTopColor, "the Songs surface is its own")
        }
    }

    // ── The Songs band's gradient ─────────────────────────────────────────────

    @Test
    fun `the Songs surface shows its own ends`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG, override = false)
            onNodeWithText("#303030").assertExists()
            onNodeWithText("#404040").assertExists()
        }
    }

    @Test
    fun `recoloring the Songs top end writes the Songs surface`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG)
            recolor("#303030", "#556677")

            val stored = get().stored()
            assertEquals("#556677", stored.songLowerThirdBackground.gradientTopColor)
            assertEquals("#101010", stored.bibleLowerThirdBackground.gradientTopColor)
        }
    }

    @Test
    fun `recoloring the Songs bottom end writes the Songs surface`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG)
            recolor("#404040", "#667788")

            assertEquals("#667788", get().stored().songLowerThirdBackground.gradientBottomColor)
        }
    }

    // ── The turnover ──────────────────────────────────────────────────────────

    @Test
    fun `the turnover is shown as a percentage`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText("POSITION").assertExists()
        }
    }

    @Test
    fun `retyping the turnover writes a fraction`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            retypeNumberField(37, 25)

            assertEquals(0.25f, get().stored().bibleLowerThirdBackground.gradientPosition)
        }
    }

    @Test
    fun `a turnover at either extreme is a value`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            retypeNumberField(37, 0)
            assertEquals(0f, get().stored().bibleLowerThirdBackground.gradientPosition)
        }
    }

    @Test
    fun `the turnover leaves both ends alone`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            retypeNumberField(37, 80)

            val stored = get().stored().bibleLowerThirdBackground
            assertEquals("#101010", stored.gradientTopColor)
            assertEquals("#202020", stored.gradientBottomColor)
        }
    }

    // ── What a gradient is not ────────────────────────────────────────────────

    @Test
    fun `a gradient surface draws no plain color field`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText("Image File").assertDoesNotExist()
            onNodeWithText("Video File").assertDoesNotExist()
        }
    }

    @Test
    fun `the Default surface on a band offers no gradient`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_DEFAULT, override = false)
            onNodeWithText("POSITION").assertDoesNotExist()
        }
    }

    @Test
    fun `each surface keeps its own ends through a chip change`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            recolor("#101010", "#112233")
            openElement(CustomizeElement.BACKGROUND_SONG)
            recolor("#303030", "#556677")
            openElement(CustomizeElement.BACKGROUND_BIBLE)

            val stored = get().stored()
            assertEquals("#112233", stored.bibleLowerThirdBackground.gradientTopColor)
            assertEquals("#556677", stored.songLowerThirdBackground.gradientTopColor)
        }
    }
}
