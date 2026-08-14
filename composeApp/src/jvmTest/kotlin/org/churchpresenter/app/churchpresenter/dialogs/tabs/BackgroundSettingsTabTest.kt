@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the tab's two "default" cards — the full-screen default background and the lower-third
 * default — asserting both the value written into [BackgroundSettings] (which is what gets
 * serialised to `settings.json`) and the change it makes on screen.
 *
 * These two cards are the fallback every other slot points at when it is left on "Default", so each
 * test also checks the sibling card was not moved along with it.
 */
class BackgroundSettingsTabTest {

    private fun settingsWith(change: BackgroundSettings.() -> BackgroundSettings): AppSettings =
        AppSettings().let { it.copy(backgroundSettings = it.backgroundSettings.change()) }

    // ── Default full-screen background ──────────────────────────────────────────────────────────

    @Test
    fun `the tab composes and shows its first section`() = backgroundTab { _ ->
        onNodeWithText("Default Background").assertExists("the default-background card must render")
    }

    @Test
    fun `the default background starts on Color`() = backgroundTab { get ->
        assertEquals(Constants.BACKGROUND_COLOR, get().backgroundSettings.defaultBackgroundType, "default is a colour")
        typeDropdowns()[TypeDropdown.DEFAULT].assertTextEquals(TypeLabel.COLOR)
    }

    @Test
    fun `choosing the transparent background type updates the setting`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.DEFAULT, TypeLabel.TRANSPARENT)
        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            get().backgroundSettings.defaultBackgroundType,
            "picking Transparent must be stored",
        )
        // Transparent has nothing to configure, so the card's colour field goes away.
        onNodeWithTag("bg_defaultColor").assertDoesNotExist()
        colorFields().assertCountEquals(TypeDropdown.COUNT - 1)
    }

    @Test
    fun `choosing the image background type reveals the image picker`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.DEFAULT, TypeLabel.IMAGE)
        assertEquals(
            Constants.BACKGROUND_IMAGE,
            get().backgroundSettings.defaultBackgroundType,
            "picking Image must be stored",
        )
        onNodeWithText("Background Image:").assertExists("the image row must appear")
        onNodeWithText("No image selected").assertExists("with an empty picker")
        onNodeWithTag("bg_defaultColor").assertDoesNotExist()
    }

    /**
     * The video row is driven from a stored type rather than from a click: where VLC is absent the
     * Video menu item is disabled and cannot be picked, but the row it governs still renders — so
     * this holds on a machine with VLC and on one without. Picking it is covered separately, below.
     */
    @Test
    fun `the video background type shows the video picker`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_VIDEO) }) { _ ->
            typeDropdowns()[TypeDropdown.DEFAULT].assertTextEquals(TypeLabel.VIDEO)
            onNodeWithText("Background Video:").assertExists("the video row must appear")
            onNodeWithText("No video selected").assertExists("with an empty picker")
            onNodeWithTag("bg_defaultColor").assertDoesNotExist()
        }
    }

    /**
     * Video is the one type the tab can refuse: it needs VLC, and without it the option is still
     * listed — labelled "(Install VLC)" — but disabled, and clicking it must store nothing.
     */
    @Test
    fun `the video option is offered and is pickable only where VLC is installed`() = backgroundTab { get ->
        typeDropdowns()[TypeDropdown.DEFAULT].scrollThenClick()
        waitForIdle()

        val option = onNodeWithText(videoMenuLabel)
        option.assertExists("Video Loop must be offered whether or not VLC is installed")
        if (isVlcAvailable) {
            option.assertIsEnabled()
            option.performClick()
            waitForIdle()
            assertEquals(
                Constants.BACKGROUND_VIDEO,
                get().backgroundSettings.defaultBackgroundType,
                "picking Video Loop must be stored",
            )
            typeDropdowns()[TypeDropdown.DEFAULT].assertTextEquals(TypeLabel.VIDEO)
            onNodeWithText("Background Video:").assertExists("and must reveal the video row")
        } else {
            option.assertIsNotEnabled()
            option.performClick()
            waitForIdle()
            assertEquals(
                Constants.BACKGROUND_COLOR,
                get().backgroundSettings.defaultBackgroundType,
                "a disabled Video option must leave the stored type alone",
            )
            onNodeWithText("Background Video:").assertDoesNotExist()
        }
    }

    @Test
    fun `switching back to Color restores the colour field`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE) }) { get ->
            onNodeWithTag("bg_defaultColor").assertDoesNotExist()

            typeDropdowns()[TypeDropdown.DEFAULT].scrollThenClick()
            waitForIdle()
            // Five other dropdowns already read "Color"; the menu item is the last one added.
            onAllNodesWithText(TypeLabel.COLOR).onLast().performClick()
            waitForIdle()

            assertEquals(
                Constants.BACKGROUND_COLOR,
                get().backgroundSettings.defaultBackgroundType,
                "picking Color must be stored",
            )
            onNodeWithTag("bg_defaultColor").assertExists("the colour field must come back")
        }
    }

    @Test
    fun `cancelling the colour dialog leaves the default colour alone`() = backgroundTab { get ->
        assertEquals("#000000", get().backgroundSettings.defaultBackgroundColor, "default is black")
        onNodeWithTag("bg_defaultColor").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Cancel").assertExists("the colour dialog must open")
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals("#000000", get().backgroundSettings.defaultBackgroundColor, "Cancel must change nothing")
        onNodeWithText("Cancel").assertDoesNotExist() // the dialog must have closed
    }

    @Test
    fun `the default colour picker stores the confirmed hex`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundColor = "#101112") }) { get ->
            recolor(fromHex = "#101112", toHex = "#20A0C0")
            assertTrue(
                get().backgroundSettings.defaultBackgroundColor.equals("#20A0C0", ignoreCase = true),
                "the confirmed hex must become the default background colour, " +
                    "was ${get().backgroundSettings.defaultBackgroundColor}",
            )
            assertEquals(
                "#000000",
                get().backgroundSettings.defaultLowerThirdBackgroundColor,
                "the lower-third default colour must be untouched",
            )
        }
    }

    @Test
    fun `the default opacity slider stores a new opacity`() = backgroundTab { get ->
        assertSliderShows("Background Opacity",
            get().backgroundSettings.defaultBackgroundOpacity,
            "the default opacity")
        val reading = dragSlider("Background Opacity", fraction = 0.4f)
        val stored = get().backgroundSettings.defaultBackgroundOpacity
        assertTrue(stored < 1f, "dragging left of the end must lower the opacity, was $stored")
        assertBetween("the default opacity", stored, 0f, 1f)
        assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        assertEquals(
            1.0f,
            get().backgroundSettings.defaultLowerThirdBackgroundOpacity,
            "the lower-third default opacity must be untouched",
        )
    }

    // ── Default lower-third background ──────────────────────────────────────────────────────────

    @Test
    fun `the lower-third default offers Follow Default and stores it`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.DEFAULT_LOWER_THIRD, TypeLabel.FOLLOW_DEFAULT)
        assertEquals(
            Constants.BACKGROUND_FOLLOW_DEFAULT,
            get().backgroundSettings.defaultLowerThirdBackgroundType,
            "picking Follow Default must be stored",
        )
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.defaultBackgroundType,
            "the full-screen default must be untouched",
        )
    }

    /** Follow Default is the one option the full-screen card must not offer — it has nothing to follow. */
    @Test
    fun `the full-screen default card does not offer Follow Default`() = backgroundTab { _ ->
        typeDropdowns()[TypeDropdown.DEFAULT].scrollThenClick()
        waitForIdle()
        onAllNodesWithText(TypeLabel.FOLLOW_DEFAULT).assertCountEquals(0)
    }

    @Test
    fun `the lower-third default colour picker stores the confirmed hex`() {
        backgroundTab(initial = settingsWith { copy(defaultLowerThirdBackgroundColor = "#131415") }) { get ->
            recolor(fromHex = "#131415", toHex = "#30B040")
            assertTrue(
                get().backgroundSettings.defaultLowerThirdBackgroundColor.equals("#30B040", ignoreCase = true),
                "the lower-third default colour must be stored",
            )
            assertEquals(
                "#000000",
                get().backgroundSettings.defaultBackgroundColor,
                "the full-screen default colour must be untouched",
            )
        }
    }

    @Test
    fun `the lower-third default image picker appears and offers its browse buttons`() {
        backgroundTab(
            initial = settingsWith { copy(defaultLowerThirdBackgroundType = Constants.BACKGROUND_IMAGE) },
        ) { _ ->
            onNodeWithText("Background Image:").assertExists()
            onNodeWithContentDescription("Browse downloaded library").assertExists()
            onNodeWithContentDescription("Browse stock photos/videos").assertExists()
        }
    }

    @Test
    fun `a stored image path is shown by its picker`() {
        backgroundTab(
            initial = settingsWith {
                copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/tmp/backdrops/sunrise.png",
                )
            },
        ) { _ ->
            // The picker shows the file's name rather than the whole path.
            onNodeWithText("sunrise.png").assertExists("the picker must name the chosen file")
            onAllNodesWithText("No image selected").assertCountEquals(0)
        }
    }

    @Test
    fun `a stored video path is shown by its picker`() {
        backgroundTab(
            initial = settingsWith {
                copy(
                    defaultLowerThirdBackgroundType = Constants.BACKGROUND_VIDEO,
                    defaultLowerThirdBackgroundVideo = "/tmp/backdrops/loop.mp4",
                )
            },
        ) { _ ->
            onNodeWithText("loop.mp4").assertExists("the picker must name the chosen file")
            onAllNodesWithText("No video selected").assertCountEquals(0)
        }
    }

    @Test
    fun `the lower-third default opacity slider stores a new opacity`() {
        backgroundTab(
            initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT) },
        ) { get ->
            // With the full-screen card on Transparent, the first opacity slider is the lower third's.
            val reading = dragSlider("Background Opacity", fraction = 0.25f)
            val stored = get().backgroundSettings.defaultLowerThirdBackgroundOpacity
            assertTrue(stored < 1f, "dragging must lower the opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    /**
     * Each background type draws its *own* opacity slider — the Color, Image and Video branches are
     * separate blocks of the same `when`, each wiring up its own callback. A slider that works on one
     * branch says nothing about the others, so all three are driven per card.
     */
    @Test
    fun `the default opacity slider works while the card is on Image`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE) }) { get ->
            val reading = dragSlider("Background Opacity", fraction = 0.5f)
            val stored = get().backgroundSettings.defaultBackgroundOpacity
            assertTrue(stored < 1f, "the Image branch's slider must store a lower opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    @Test
    fun `the default opacity slider works while the card is on Video Loop`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_VIDEO) }) { get ->
            val reading = dragSlider("Background Opacity", fraction = 0.3f)
            val stored = get().backgroundSettings.defaultBackgroundOpacity
            assertTrue(stored < 1f, "the Video branch's slider must store a lower opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    @Test
    fun `the lower-third opacity slider works while the card is on Image`() {
        val settings = settingsWith {
            copy(
                defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_IMAGE,
            )
        }
        backgroundTab(initial = settings) { get ->
            val reading = dragSlider("Background Opacity", fraction = 0.45f)
            val stored = get().backgroundSettings.defaultLowerThirdBackgroundOpacity
            assertTrue(stored < 1f, "the Image branch's slider must store a lower opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    @Test
    fun `the lower-third opacity slider works while the card is on Video Loop`() {
        val settings = settingsWith {
            copy(
                defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_VIDEO,
            )
        }
        backgroundTab(initial = settings) { get ->
            val reading = dragSlider("Background Opacity", fraction = 0.15f)
            val stored = get().backgroundSettings.defaultLowerThirdBackgroundOpacity
            assertTrue(stored < 1f, "the Video branch's slider must store a lower opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    // ── ATEM upload buttons ─────────────────────────────────────────────────────────────────────

    /**
     * The upload buttons are only useful once there is both a switcher to send to and an image to
     * send, so they stay hidden until both are configured. They are never clicked here: clicking one
     * opens a TCP connection to the configured host.
     */
    @Test
    fun `the ATEM upload buttons stay hidden until a host and an image are both set`() {
        val imageOnly = settingsWith {
            copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE, defaultBackgroundImage = "/tmp/a.png")
        }
        backgroundTab(initial = imageOnly) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)
        }

        val hostOnly = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(initial = hostOnly) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)
        }
    }

    @Test
    fun `both ATEM upload buttons appear once a host and an image are configured`() {
        val configured = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/tmp/a.png",
                ),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(initial = configured) { _ ->
            onNodeWithContentDescription("Upload to Background Slot 1").assertExists()
            onNodeWithContentDescription("Upload to Background Slot 2").assertExists()
            // Each carries a small numeric badge so the two are told apart at a glance.
            onNodeWithText("1").assertExists()
            onNodeWithText("2").assertExists()
        }
    }

    @Test
    fun `the video picker offers no ATEM upload, since a clip cannot be a still`() {
        val configured = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                    defaultBackgroundVideo = "/tmp/a.mp4",
                ),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(initial = configured) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)
        }
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the values the controls write survive a settings json round trip`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.DEFAULT, TypeLabel.TRANSPARENT)
        chooseBackgroundType(TypeDropdown.DEFAULT_LOWER_THIRD, TypeLabel.FOLLOW_DEFAULT)

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<AppSettings>(json.encodeToString(get()))

        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            restored.backgroundSettings.defaultBackgroundType,
            "the full-screen default type must survive",
        )
        assertEquals(
            Constants.BACKGROUND_FOLLOW_DEFAULT,
            restored.backgroundSettings.defaultLowerThirdBackgroundType,
            "the lower-third default type must survive",
        )
    }
}
