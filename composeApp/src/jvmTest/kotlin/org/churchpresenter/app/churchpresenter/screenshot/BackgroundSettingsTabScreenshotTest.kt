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
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.settings.utils.Constants
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The Background tab of the settings dialog, in both themes.
 *
 * **The background *type* is the axis.** The tab is a rail of six surfaces beside one editor, and
 * the editor's shape is decided by the type the open surface is set to — a colour swatch and the
 * six one-click solids for Color, a path row with browse and stock-search buttons for Image and
 * Video, nothing at all for Transparent, and a block of two-colour controls for Gradient. Shooting
 * one type would leave the rest of the editor unseen, so each gets its own image.
 *
 * **The rail is the second axis.** Which surface is open decides which types are offered — the
 * full-screen default inherits from nothing, the default lower third adds *Follow Default*, a
 * content surface says *Default*, and only a content lower third offers *Gradient* — and it decides
 * what the stage preview draws, since the three surfaces paint different parts of the output.
 *
 * Video is offered everywhere but is disabled without VLC, which is how a test machine runs, and
 * Camera is offered everywhere but needs ffmpeg or a capture card, which a test machine may lack.
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

    /**
     * Camera: a device picker where Image and Video have a file row.
     *
     * The device list is this machine's, so the picker's *contents* are not pinned — what this
     * image is for is the shape of the slot and the segment being selected. On a machine with no
     * capture device the slot says so instead, which is itself worth seeing.
     */
    @Test
    fun `a camera background`() = shoot(
        "type_camera",
        settings = defaults(Constants.BACKGROUND_CAMERA),
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
    ) { openRailRow(DEFAULT_LOWER_THIRD, nth = 0) }

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
    ) { openRailRow(LOWER_THIRD, nth = 0) }

    // ── The look, which every surface now carries ───────────────────────────────────────────────

    /**
     * A dimmed and blurred picture.
     *
     * The one state where the stage preview earns its place: dim and blur are invisible in the
     * controls and obvious in the picture, and they are drawn here with the same arithmetic the
     * presenters use, so this image is what the output looks like.
     */
    @Test
    fun `a look applied to a picture`() = shoot(
        "look_dim_blur",
        settings = defaults(Constants.BACKGROUND_IMAGE).let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundImage = photo().absolutePath,
                    defaultBackgroundDim = 45,
                    defaultBackgroundBlur = 12,
                )
            )
        },
    )

    // ── The rail ────────────────────────────────────────────────────────────────────────────────

    /**
     * A content surface open, with its own look.
     *
     * Also the only image showing the header's *Use Default* button and the Copy This Look To row,
     * both of which exist only for a surface that can inherit and does not.
     */
    @Test
    fun `a content surface open`() = shoot("per_content", settings = perContent()) {
        openRailRow(FULL_SCREEN, nth = 0)
    }

    /** The same surface put back on Default: the rail's meta lines change with it. */
    @Test
    fun `a content surface following the default`() = shoot("scope_inheriting") {
        openRailRow(FULL_SCREEN, nth = 0)
        onAllNodesWithText(USE_DEFAULT)[0].performClick()
        waitForIdle()
    }

    /**
     * The default lower third open, which is the one surface that paints *above* the band rather
     * than the band itself — so its stage preview is the odd one out.
     */
    @Test
    fun `the default lower third open`() = shoot("scope_default_lower_third") {
        openRailRow(DEFAULT_LOWER_THIRD, nth = 0)
    }

    // ── The quick backgrounds shelf ─────────────────────────────────────────────────────────────

    /** The shelf with a tray configured, which is where the tiles are made. */
    @Test
    fun `the quick backgrounds shelf`() = shoot("quick_backgrounds", settings = withQuickTray())

    /** A tile's panel open over the tab — the same editor a song's own background gets. */
    @Test
    fun `a quick background panel`() = shoot(
        "quick_background_panel",
        settings = withQuickTray(),
        rootIndex = 1,
    ) {
        onAllNodesWithText("1")[0].performClick()
        waitForIdle()
    }

    // Not shot: stock-search API keys saved. They change nothing on this tab — the picker row looks
    // the same with and without them, because the keys are asked for inside the stock browser the
    // row opens, not beside it. The image is identical to `type_image_empty`.

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** Opens the [nth] rail row reading [label] — the rail is the leftmost column. */
    private fun ComposeUiTest.openRailRow(label: String, nth: Int) {
        val matches = onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val inRail = matches.indices.filter { matches[it].boundsInRoot.left < RAIL_RIGHT_EDGE }
        onAllNodesWithText(label)[inRail[nth]].performScrollTo().performClick()
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

    /** A tray of three, so the shelf shows tiles, their names and the slot each answers to. */
    private fun withQuickTray() = AppSettings(
        quickBackgrounds = listOf(
            quickTile("tile1", "#1B2A5B"),
            quickTile("tile2", "#7B3FA6"),
            quickTile("tile3", "#2E6B4F"),
        ),
    )

    private fun quickTile(id: String, hex: String) = QuickBackground(
        id = id,
        background = SongBackground(type = SongBackgroundType.COLOR, color = hex),
        lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = hex),
    )

    private companion object {
        const val SECTION = "backgroundSettingsTab"

        /** Where the surface rail ends, in px — these run at density 1. */
        const val RAIL_RIGHT_EDGE = 232f

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/backgrounds") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/backgrounds")

        const val FULL_SCREEN = "Full Screen"
        const val LOWER_THIRD = "Lower Third"
        const val DEFAULT_LOWER_THIRD = "Default Lower Third"
        const val USE_DEFAULT = "Use Default"
    }
}
