@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A complete inventory of what the tab puts on screen: every heading, caption, label, content
 * description and stored value it displays, with the exact number of times each appears.
 *
 * The behaviour tests drive the controls; this asserts the words around them are actually there. A
 * caption is as much a part of the tab as the control it names — a `stringResource` pointed at the
 * wrong key, or a row quietly dropped in a refactor, changes nothing a behaviour test would notice,
 * because those find their targets by position or by displayed value rather than by caption.
 *
 * The counts carry as much weight as the strings. "Color" appearing five times is what says all
 * four content surfaces start out with a look of their own and the open one offers the segment; and
 * "Default" appearing twice is the rail row and the editor heading, which is the pair a locator
 * has to tell apart. The list was generated from the rendered semantics tree.
 */
class BackgroundSettingsTabLabelsTest {

    /** Every string the tab renders out of the box, and how many times it must appear. */
    private val outOfTheBox = mapOf(
        // The rail: its own heading, the three group headings, and the six rows with their metas.
        "BACKGROUNDS" to 1,
        "DEFAULTS" to 1,
        "BIBLE" to 1,
        "SONGS" to 1,
        "Default Lower Third" to 1,
        "Full Screen" to 2,
        "Lower Third" to 2,
        "Bible or Songs set to Default" to 1,
        "Lower third set to Default" to 1,

        // The rail row that is open, plus the editor heading that names it — the pair every
        // locator in this package has to keep apart.
        "Default" to 2,
        "Set explicitly for this surface" to 1,

        // The type segments the Default surface offers. It inherits from nothing, so there are
        // four; "Color" is also the meta on each of the four content rows, hence five in all.
        "TYPE" to 1,
        "Color" to 5,
        "Image" to 1,
        "Video Loop" to 1,
        "Camera" to 1,
        "Transparent" to 1,

        // On Color: the field, its stored value, and the look controls under it.
        "COLOR" to 1,
        "#000000" to 1,
        "LOOK" to 1,
        "None" to 1,
        "Soft" to 1,
        "Legible" to 1,
        "Cinema" to 1,
        "OPACITY" to 1,
        "100%" to 1,
        "DIM" to 1,
        "0%" to 1,
        "BLUR" to 1,
        "0px" to 1,

        // Copying this look onto the two content full screens, each named by its group.
        "COPY THIS LOOK TO" to 1,
        "Bible" to 1,
        "Songs" to 1,

        // The stage preview: which part of the output this surface paints, and the sample line.
        "FULL SCREEN" to 1,
        "Amazing grace! How sweet the sound" to 1,

        // The Quick backgrounds shelf. Out of the box the tray is empty, so the only tile is the
        // one that adds the first background.
        "QUICK BACKGROUNDS" to 1,
        "0 / 10" to 1,
        QUICK_BACKGROUNDS_HELP to 1,
        "Add a background" to 1,
    )

    @Test
    fun `every label the tab renders is on screen the expected number of times`() = backgroundTab { _ ->
        for ((text, count) in outOfTheBox) {
            onAllNodesWithText(text).assertCountEquals(count)
        }
    }

    /** Guards against a control being added to the tab without a test noticing. */
    @Test
    fun `the tab renders no text beyond the inventory`() = backgroundTab { _ ->
        val rendered = mutableSetOf<String>()
        onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .forEach { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { rendered += it.text }
                node.config.getOrNull(SemanticsProperties.EditableText)?.let { rendered += it.text }
            }
        assertEquals(
            emptyList(),
            rendered.filter { it.isNotBlank() && it !in outOfTheBox }.sorted(),
            "the tab renders text the inventory does not list — add it, with its expected count",
        )
    }

    /**
     * The rest of the tab's vocabulary appears only once a surface is set to something else: the
     * picker rows, their icon buttons, the ATEM badges and the gradient controls. Each is put on
     * screen by opening the surface that shows it.
     */
    @Test
    fun `the image picker row and its buttons are labelled`() {
        val settings = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/tmp/backdrops/still.png",
                ),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(settings) { _ ->
            onAllNodesWithText("IMAGE FILE").assertCountEquals(1)
            onAllNodesWithText("still.png").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(1)
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(1)
            onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(1)
        }
    }

    @Test
    fun `the video picker row is labelled`() {
        val settings = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                    defaultBackgroundVideo = "/tmp/backdrops/loop.mp4",
                ),
            )
        }
        backgroundTab(settings) { _ ->
            onAllNodesWithText("VIDEO FILE").assertCountEquals(1)
            onAllNodesWithText("loop.mp4").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(1)
        }
    }

    @Test
    fun `the gradient controls are labelled`() {
        val settings = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    bibleLowerThirdBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_GRADIENT,
                        gradientEnabled = true,
                    ),
                ),
            )
        }
        backgroundTab(settings) { _ ->
            openSurface(Surface.BIBLE_LOWER_THIRD)
            onAllNodesWithText("TOP COLOR:").assertCountEquals(1)
            onAllNodesWithText("BOTTOM COLOR:").assertCountEquals(1)
            onAllNodesWithText("TOP OPACITY").assertCountEquals(1)
            onAllNodesWithText("BOTTOM OPACITY").assertCountEquals(1)
            onAllNodesWithText("TRANSITION POSITION").assertCountEquals(1)
            onAllNodesWithText("80%").assertCountEquals(1)
            onAllNodesWithText("50%").assertCountEquals(1)
        }
    }

    @Test
    fun `a lower-third surface names its heading and its badge`() = backgroundTab { _ ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        onAllNodesWithText("Bible · Lower Third").assertCountEquals(1)
        onAllNodesWithText("LOWER THIRD").assertCountEquals(1)
        onAllNodesWithText("Use Default").assertCountEquals(1)
    }

    @Test
    fun `an inheriting surface says so in both places`() = backgroundTab { _ ->
        setSurfaceType(Surface.BIBLE, TypeLabel.DEFAULT)
        onAllNodesWithText("Following Default").assertCountEquals(1)
        onAllNodesWithText("Follows the default").assertCountEquals(2)
    }
}

/** The Quick backgrounds shelf's help line, too long for one source line. */
private const val QUICK_BACKGROUNDS_HELP =
    "Backgrounds the preview panel keeps one click away. Click a tile to choose what it shows, " +
        "drag it to change which key reaches it. Picking one during a service overrides every " +
        "screen until you go back to normal — it changes nothing here."
