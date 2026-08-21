@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The Background tab of the settings dialog, in both themes.
 *
 * **The background *type* is the axis.** Each slot is a type dropdown with a different set of
 * controls underneath it — a colour swatch and an opacity slider for Color, a path row with browse
 * and stock-search buttons for Image and Video, nothing at all for Transparent, and a block of
 * two-colour gradient controls for Gradient. Shooting one type would leave the rest of the tab
 * unseen, so each gets its own image.
 *
 * The slots are not interchangeable either: the two default slots offer four types, the lower-third
 * ones add *Follow Default*, and only a lower-third slot offers *Gradient*. Video is offered
 * everywhere but is disabled without VLC — which is how a test machine runs, so that is the state
 * the dropdown images show.
 */
class BackgroundSettingsTabScreenshotTest {

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            BackgroundSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── As it opens ─────────────────────────────────────────────────────────────────────────────

    /** Both default slots on Color, which is what a fresh install has. */
    @Test
    fun `as it opens`() = shoot("defaults")

    // ── One image per background type ───────────────────────────────────────────────────────────

    @Test
    fun `a colour background`() = shoot(
        "type_colour",
        settings = defaults(Constants.BACKGROUND_COLOR).let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundColor = "#1B2A5B",
                    defaultBackgroundOpacity = 0.8f,
                )
            )
        },
    )

    /** Image adds a path row — browse, the stock-photo search, and the file it is set to. */
    @Test
    fun `an image background`() = shoot(
        "type_image",
        settings = defaults(Constants.BACKGROUND_IMAGE).let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundImage = photo().absolutePath,
                )
            )
        },
    )

    /** The same slot with nothing chosen yet: the row is there, the path is not. */
    @Test
    fun `an image background with no file chosen`() =
        shoot("type_image_empty", settings = defaults(Constants.BACKGROUND_IMAGE))

    /** Video: the same shape as Image, over a video path. */
    @Test
    fun `a video background`() = shoot(
        "type_video",
        settings = defaults(Constants.BACKGROUND_VIDEO).let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundVideo = "/media/Welcome Loop.mp4",
                )
            )
        },
    )

    /** Transparent carries no controls at all — the dropdown is the whole slot. */
    @Test
    fun `a transparent background`() = shoot("type_transparent", settings = defaults(Constants.BACKGROUND_TRANSPARENT))

    /** Follow Default, which only a lower-third slot offers. */
    @Test
    fun `a lower third following the default`() = shoot(
        "type_follow_default",
        settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_FOLLOW_DEFAULT,
            )
        ),
    )

    /** Gradient, which only a lower-third slot offers: two colours, their opacities and a position. */
    @Test
    fun `a gradient lower third`() = shoot(
        "type_gradient",
        settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                    gradientTopColor = "#7B3FA6",
                    gradientTopOpacity = 0.9f,
                    gradientBottomColor = "#1B2A5B",
                    gradientBottomOpacity = 0.4f,
                    gradientPosition = 0.35f,
                ),
            )
        ),
    ) { scrollTo(BIBLE) }

    // ── The type dropdown itself ────────────────────────────────────────────────────────────────

    /** What a full-screen slot offers: four types, with Video disabled where VLC is absent. */
    @Test
    fun `the type menu on a full screen slot`() = shoot("type_menu", rootIndex = 1) {
        onAllNodesWithText(COLOUR_OPTION)[0].performClick()
        waitForIdle()
    }

    /** What a lower-third slot offers instead: Follow Default first, and Gradient at the end. */
    @Test
    fun `the type menu on a lower third slot`() = shoot("type_menu_lower_third", rootIndex = 1) {
        onAllNodesWithText(COLOUR_OPTION)[1].performClick()
        waitForIdle()
    }

    // ── The per-content slots ───────────────────────────────────────────────────────────────────

    /**
     * Bible and Songs, each with a full-screen slot and a lower-third one.
     *
     * One image, not two: the pair sit side by side in the same row, so a shot of either is a shot
     * of both.
     */
    @Test
    fun `the per-content slots`() = shoot("per_content", settings = perContent()) { scrollTo(BIBLE) }

    // Not shot: stock-search API keys saved. They change nothing on this tab — the picker row looks
    // the same with and without them, because the keys are asked for inside the stock browser the
    // row opens, not beside it. The image is identical to `type_image_empty`.

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.scrollTo(label: String) {
        onAllNodesWithText(label)[0].performScrollTo()
        waitForIdle()
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Both default slots switched to [type]. */
    private fun defaults(type: String) = AppSettings(
        backgroundSettings = BackgroundSettings(
            defaultBackgroundType = type,
            defaultLowerThirdBackgroundType = type,
        )
    )

    /** Bible and Songs each given a background of their own, so their slots are not all identical. */
    private fun perContent() = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_IMAGE,
                backgroundImage = photo().absolutePath,
            ),
            bibleLowerThirdBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_COLOR,
                backgroundColor = "#1B2A5B",
                backgroundOpacity = 0.6f,
            ),
            songBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_COLOR,
                backgroundColor = "#3B1F5B",
            ),
            songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
        )
    )

    /**
     * A real, decodable image under a neutral root.
     *
     * Fixed rather than temporary because the picker prints the path, and free of anything personal
     * because a repo-relative `build/` fixture resolves through the developer's home directory.
     */
    private fun photo(): File {
        FIXTURES.mkdirs()
        val file = File(FIXTURES, "Sunrise.png")
        if (!file.exists()) {
            val image = BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB)
            val canvas = image.createGraphics()
            canvas.paint = GradientPaint(0f, 0f, Color(0x2B3A67), 640f, 360f, Color(0x8FB3F5))
            canvas.fillRect(0, 0, 640, 360)
            canvas.dispose()
            ImageIO.write(image, "png", file)
        }
        return file
    }

    private companion object {
        const val SECTION = "backgroundSettingsTab"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/backgrounds") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/backgrounds")

        const val COLOUR_OPTION = "Color"
        const val BIBLE = "Bible"
    }
}
