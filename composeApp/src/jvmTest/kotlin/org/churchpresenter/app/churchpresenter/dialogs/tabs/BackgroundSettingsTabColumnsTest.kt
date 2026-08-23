@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.recolor

/**
 * Drives the Bible and Songs cards — four `BackgroundColumn`s, one per content type per output —
 * asserting the [BackgroundConfig] each writes and what it puts on screen.
 *
 * All four columns are the same composable with different callbacks, and every one of them keeps a
 * complete config of its own. The risk this class is really guarding against is a column writing
 * into a neighbour's settings, so each test also asserts the other three were left alone.
 */
class BackgroundSettingsTabColumnsTest {

    private fun settingsWith(change: BackgroundSettings.() -> BackgroundSettings): AppSettings =
        AppSettings().let { it.copy(backgroundSettings = it.backgroundSettings.change()) }

    /** Puts every slot on a type that is not [Constants.BACKGROUND_COLOR], freeing "Color" to be picked. */
    private fun allTransparent(): AppSettings = settingsWith {
        copy(
            defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT,
            defaultLowerThirdBackgroundType = Constants.BACKGROUND_TRANSPARENT,
            bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
        )
    }

    // ── Type selection, per column ──────────────────────────────────────────────────────────────

    @Test
    fun `the Bible full-screen column stores the type it is given`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.BIBLE_FULLSCREEN, TypeLabel.IMAGE)
        assertEquals(
            Constants.BACKGROUND_IMAGE,
            get().backgroundSettings.bibleBackground.backgroundType,
            "the Bible full-screen type must be stored",
        )
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.bibleLowerThirdBackground.backgroundType,
            "the Bible lower-third column must be untouched",
        )
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.songBackground.backgroundType,
            "the Songs full-screen column must be untouched",
        )
    }

    @Test
    fun `the Bible lower-third column stores the type it is given`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.BIBLE_LOWER_THIRD, TypeLabel.TRANSPARENT)
        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            get().backgroundSettings.bibleLowerThirdBackground.backgroundType,
            "the Bible lower-third type must be stored",
        )
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.bibleBackground.backgroundType,
            "the Bible full-screen column must be untouched",
        )
    }

    @Test
    fun `the Songs full-screen column stores the type it is given`() = backgroundTab { get ->
        // Deliberately not Video: that option is disabled where VLC is missing, so it cannot be
        // picked on every machine. A column's video row is covered from a fixture instead, below.
        // Gradient is not an option here either — the tab offers it to lower-third slots only.
        chooseBackgroundType(TypeDropdown.SONG_FULLSCREEN, TypeLabel.IMAGE)
        assertEquals(
            Constants.BACKGROUND_IMAGE,
            get().backgroundSettings.songBackground.backgroundType,
            "the Songs full-screen type must be stored",
        )
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.songLowerThirdBackground.backgroundType,
            "the Songs lower-third column must be untouched",
        )
    }

    /** A column set to Video renders the same picker row the default cards do. */
    @Test
    fun `a column set to Video shows the video picker`() {
        val songVideo = settingsWith {
            copy(
                songBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_VIDEO,
                    backgroundVideo = "/tmp/backdrops/column.mp4",
                ),
            )
        }
        backgroundTab(initial = songVideo) { _ ->
            typeDropdowns()[TypeDropdown.SONG_FULLSCREEN].assertTextEquals(TypeLabel.VIDEO)
            onAllNodesWithText("Background Video:").assertCountEquals(1)
            onNodeWithText("column.mp4").assertExists("the column's picker must name the stored file")
        }
    }

    @Test
    fun `the Songs lower-third column stores the type it is given`() = backgroundTab { get ->
        chooseBackgroundType(TypeDropdown.SONG_LOWER_THIRD, TypeLabel.DEFAULT)
        assertEquals(
            Constants.BACKGROUND_DEFAULT,
            get().backgroundSettings.songLowerThirdBackground.backgroundType,
            "the Songs lower-third type must be stored",
        )
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.songBackground.backgroundType,
            "the Songs full-screen column must be untouched",
        )
    }

    @Test
    fun `a column can be put back on Color`() {
        backgroundTab(initial = allTransparent()) { get ->
            chooseBackgroundType(TypeDropdown.BIBLE_FULLSCREEN, TypeLabel.COLOR)
            assertEquals(
                Constants.BACKGROUND_COLOR,
                get().backgroundSettings.bibleBackground.backgroundType,
                "picking Color must be stored",
            )
            onNodeWithText("BACKGROUND COLOR:").assertExists("the colour field must appear")
        }
    }

    // ── Gradient ────────────────────────────────────────────────────────────────────────────────

    /**
     * Gradient is the one type that carries a second flag: picking it also sets `gradientEnabled`,
     * and picking anything else clears it again. The renderer reads that flag rather than the type,
     * so a column left with the flag set on a non-gradient type would draw the wrong thing.
     */
    @Test
    fun `choosing Gradient also raises the gradientEnabled flag`() = backgroundTab { get ->
        assertEquals(false, get().backgroundSettings.bibleLowerThirdBackground.gradientEnabled, "starts clear")
        chooseBackgroundType(TypeDropdown.BIBLE_LOWER_THIRD, TypeLabel.GRADIENT)
        assertEquals(
            Constants.BACKGROUND_GRADIENT,
            get().backgroundSettings.bibleLowerThirdBackground.backgroundType,
            "Gradient must be stored as the type",
        )
        assertEquals(
            true,
            get().backgroundSettings.bibleLowerThirdBackground.gradientEnabled,
            "Gradient must also raise the flag the renderer reads",
        )
        onNodeWithText("Top Opacity").assertExists("the gradient controls must appear")
    }

    @Test
    fun `leaving Gradient for another type clears the gradientEnabled flag`() {
        val gradient = settingsWith {
            copy(
                songLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                ),
            )
        }
        backgroundTab(initial = gradient) { get ->
            chooseBackgroundType(TypeDropdown.SONG_LOWER_THIRD, TypeLabel.TRANSPARENT)
            assertEquals(
                Constants.BACKGROUND_TRANSPARENT,
                get().backgroundSettings.songLowerThirdBackground.backgroundType,
                "the new type must be stored",
            )
            assertEquals(
                false,
                get().backgroundSettings.songLowerThirdBackground.gradientEnabled,
                "leaving Gradient must clear the flag",
            )
            onAllNodesWithText("Top Opacity").assertCountEquals(0)
        }
    }

    private fun withGradient(topColor: String = "#000000", bottomColor: String = "#000000"): AppSettings =
        settingsWith {
            copy(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                    gradientTopColor = topColor,
                    gradientBottomColor = bottomColor,
                ),
            )
        }

    @Test
    fun `the gradient top colour field stores the confirmed hex`() {
        backgroundTab(initial = withGradient(topColor = "#212223")) { get ->
            recolor(fromHex = "#212223", toHex = "#40C0D0")
            assertTrue(
                get().backgroundSettings.bibleLowerThirdBackground.gradientTopColor
                    .equals("#40C0D0", ignoreCase = true),
                "the top colour must be stored",
            )
            assertEquals(
                "#000000",
                get().backgroundSettings.bibleLowerThirdBackground.gradientBottomColor,
                "the bottom colour must be untouched",
            )
        }
    }

    @Test
    fun `the gradient bottom colour field stores the confirmed hex`() {
        backgroundTab(initial = withGradient(bottomColor = "#242526")) { get ->
            recolor(fromHex = "#242526", toHex = "#50D0E0")
            assertTrue(
                get().backgroundSettings.bibleLowerThirdBackground.gradientBottomColor
                    .equals("#50D0E0", ignoreCase = true),
                "the bottom colour must be stored",
            )
            assertEquals(
                "#000000",
                get().backgroundSettings.bibleLowerThirdBackground.gradientTopColor,
                "the top colour must be untouched",
            )
        }
    }

    @Test
    fun `the gradient sliders read back what is stored`() {
        backgroundTab(initial = withGradient()) { get ->
            val config = get().backgroundSettings.bibleLowerThirdBackground
            assertSliderShows("Top Opacity", config.gradientTopOpacity, "the top opacity")
            assertSliderShows("Bottom Opacity", config.gradientBottomOpacity, "the bottom opacity")
            assertSliderShows("Transition Position", config.gradientPosition, "the transition position")
        }
    }

    @Test
    fun `the gradient top opacity slider stores a new value`() {
        backgroundTab(initial = withGradient()) { get ->
            val reading = dragSlider("Top Opacity", fraction = 0.6f)
            val stored = get().backgroundSettings.bibleLowerThirdBackground.gradientTopOpacity
            assertTrue(stored > 0f, "dragging right of zero must raise the top opacity, was $stored")
            assertBetween("the top opacity", stored, 0f, 1f)
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored value")
            assertEquals(
                0.8f,
                get().backgroundSettings.bibleLowerThirdBackground.gradientBottomOpacity,
                "the bottom opacity must be untouched",
            )
        }
    }

    @Test
    fun `the gradient bottom opacity slider stores a new value`() {
        backgroundTab(initial = withGradient()) { get ->
            val reading = dragSlider("Bottom Opacity", fraction = 0.2f)
            val stored = get().backgroundSettings.bibleLowerThirdBackground.gradientBottomOpacity
            assertTrue(stored < 0.8f, "dragging left must lower the bottom opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored value")
        }
    }

    @Test
    fun `the gradient position slider stores a new value`() {
        backgroundTab(initial = withGradient()) { get ->
            val reading = dragSlider("Transition Position", fraction = 0.8f)
            val stored = get().backgroundSettings.bibleLowerThirdBackground.gradientPosition
            assertTrue(stored > 0.5f, "dragging right must move the transition down, was $stored")
            assertBetween("the transition position", stored, 0f, 1f)
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored value")
        }
    }

    // ── Colour and opacity, per column ──────────────────────────────────────────────────────────

    @Test
    fun `a column's colour field stores the confirmed hex against that column only`() {
        backgroundTab(
            initial = settingsWith { copy(songBackground = BackgroundConfig(backgroundColor = "#272829")) },
        ) { get ->
            recolor(fromHex = "#272829", toHex = "#60E0F0")
            assertTrue(
                get().backgroundSettings.songBackground.backgroundColor.equals("#60E0F0", ignoreCase = true),
                "the Songs full-screen colour must be stored",
            )
            assertEquals(
                "#000000",
                get().backgroundSettings.bibleBackground.backgroundColor,
                "the Bible colour must be untouched",
            )
        }
    }

    @Test
    fun `a column's opacity slider stores a new opacity against that column only`() {
        // Only the Bible full-screen column keeps a slider, so its caption is unambiguous.
        val onlyBible = settingsWith {
            copy(
                defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            )
        }
        backgroundTab(initial = onlyBible) { get ->
            onAllNodesWithText("Background Opacity").assertCountEquals(1)
            val reading = dragSlider("Background Opacity", fraction = 0.35f)
            val stored = get().backgroundSettings.bibleBackground.backgroundOpacity
            assertTrue(stored < 1f, "dragging must lower the opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
            assertEquals(
                1.0f,
                get().backgroundSettings.songBackground.backgroundOpacity,
                "the Songs opacity must be untouched",
            )
        }
    }

    @Test
    fun `a column's opacity slider works while the column is on Image`() {
        val onlyBible = settingsWith {
            copy(
                defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE),
                bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
            )
        }
        backgroundTab(initial = onlyBible) { get ->
            onAllNodesWithText("Background Opacity").assertCountEquals(1)
            val reading = dragSlider("Background Opacity", fraction = 0.55f)
            val stored = get().backgroundSettings.bibleBackground.backgroundOpacity
            assertTrue(stored < 1f, "the Image branch's slider must store a lower opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    @Test
    fun `a column's opacity slider works while the column is on Video Loop`() {
        val onlySong = settingsWith {
            copy(
                defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_TRANSPARENT,
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                bibleLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_VIDEO),
            )
        }
        backgroundTab(initial = onlySong) { get ->
            onAllNodesWithText("Background Opacity").assertCountEquals(1)
            val reading = dragSlider("Background Opacity", fraction = 0.25f)
            val stored = get().backgroundSettings.songLowerThirdBackground.backgroundOpacity
            assertTrue(stored < 1f, "the Video branch's slider must store a lower opacity, was $stored")
            assertEquals((stored * 100).toInt(), reading, "the readout must follow the stored opacity")
        }
    }

    /**
     * A settings file can hold a background type this build does not know — written by a newer
     * version, or hand-edited. The dropdown falls back to showing the stored value verbatim rather
     * than rendering blank, so the operator can see what it is set to and pick something valid.
     */
    @Test
    fun `an unrecognised stored type is displayed as it was stored`() {
        backgroundTab(
            initial = settingsWith { copy(bibleBackground = BackgroundConfig(backgroundType = "Hologram")) },
        ) { get ->
            // typeDropdowns() matches the labels this build knows, so an unknown one is found by text.
            onNodeWithText("Hologram").assertExists("the dropdown must show the stored value verbatim")
            typeDropdowns().assertCountEquals(TypeDropdown.COUNT - 1)
            assertEquals(
                "Hologram",
                get().backgroundSettings.bibleBackground.backgroundType,
                "the stored value itself must be left alone",
            )
        }
    }

    // ── Picker rows ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a column on Image shows its stored file and its browse buttons`() {
        backgroundTab(
            initial = settingsWith {
                copy(
                    songLowerThirdBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_IMAGE,
                        backgroundImage = "/tmp/backdrops/stage.jpg",
                    ),
                )
            },
        ) { _ ->
            onNodeWithText("stage.jpg").assertExists("the picker must name the stored file")
            onNodeWithContentDescription("Browse downloaded library").assertExists()
            onNodeWithContentDescription("Browse stock photos/videos").assertExists()
        }
    }

    @Test
    fun `a column on Video Loop shows its stored clip`() {
        backgroundTab(
            initial = settingsWith {
                copy(
                    bibleBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_VIDEO,
                        backgroundVideo = "/tmp/backdrops/waves.mov",
                    ),
                )
            },
        ) { _ ->
            onNodeWithText("waves.mov").assertExists("the picker must name the stored clip")
            onAllNodesWithText("No video selected").assertCountEquals(0)
        }
    }

    @Test
    fun `a column on Image offers its ATEM uploads once a host is configured`() {
        val configured = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    bibleBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_IMAGE,
                        backgroundImage = "/tmp/a.png",
                    ),
                ),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(initial = configured) { _ ->
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(1)
            onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(1)
        }
    }
}
