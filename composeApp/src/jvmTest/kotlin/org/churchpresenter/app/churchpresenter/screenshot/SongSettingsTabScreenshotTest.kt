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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.settings.utils.Constants
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Songs tab of the settings dialog, in both themes.
 *
 * A rail of slide-wide settings on the left and, on the right, a live preview over one set of
 * styling controls. That one set stands for ten stored profiles — number, title, lyrics, look-ahead
 * and next-section, on each of the two outputs — chosen by the element tabs and the Full Screen /
 * Lower Third switch above it. Nothing is below the fold any more: the rail scrolls on its own and
 * the styling side always fits.
 *
 * The preview renders `SongPresenter` itself rather than reproducing its layout, so these images
 * also catch a presenter change that alters what the operator is shown.
 *
 * The axes worth shooting are each position of those two switches, the controls that appear only
 * for some elements (reference position, chord colour), and the rail rows that come and go.
 */
class SongSettingsTabScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

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
                            SongSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            drive()
            waitForIdle()
            captureTo(file, rootIndex = rootIndex)
        }
    }

    // ── What opens ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `as it opens, at the defaults`() = shoot("top")

    // ── What the one control set is pointed at ──────────────────────────────────────────────────

    @Test
    fun `the lower third`() = shoot("lower_third") {
        onNodeWithText("Lower Third").performClick()
        waitForIdle()
    }

    /** The number: the one element besides the title with a position of its own. */
    @Test
    fun `the number element`() = shoot("element_number") {
        onNodeWithText("Number").performClick()
        waitForIdle()
    }

    /** The title, which the preview now shows above the lyrics rather than nowhere. */
    @Test
    fun `the title element`() = shoot("element_title") {
        onNodeWithText("Title").performClick()
        waitForIdle()
    }

    /**
     * The look-ahead, which turns the preview's look-ahead on by itself.
     *
     * That element only appears on a look-ahead slide, so selecting it forces the preview there —
     * editing a control whose effect is off screen is what this tab exists to stop.
     */
    @Test
    fun `the look ahead element`() = shoot("element_look_ahead") {
        onNodeWithText("Look Ahead").performClick()
        waitForIdle()
    }

    @Test
    fun `the next section element`() = shoot("element_next_section") {
        onNodeWithText("Next Section").performClick()
        waitForIdle()
    }

    // ── Rows that come and go ───────────────────────────────────────────────────────────────────

    /**
     * Switched on, which revives the row under it.
     *
     * Off is the default and so is already in `top`; the pair is worth having because the difference
     * is only a colour — the disabled row still draws its label and its box, just at 38% alpha.
     */
    @Test
    fun `the title slide switched on`() = shoot(
        "title_slide_on",
        settings = songSettings { copy(titleSlideEnabled = true) },
    )

    /** Single: the bilingual layout row has nothing to lay out, so it is not drawn. */
    @Test
    fun `a single language`() = shoot("language_single", settings = singleLanguage())

    /** Auto off, which hands the size box back the say over how big the lyrics are. */
    @Test
    fun `auto fit switched off`() = shoot(
        "auto_fit_off",
        settings = songSettings { copy(lyricsFontSizeAutoFit = false) },
    )

    /** Shadow on: three more controls fold out beside the checkbox, on the transform's own row. */
    @Test
    fun `shadow switched on`() = shoot("shadow_on") {
        onNodeWithText("Shadow").performClick()
        waitForIdle()
    }

    /** The typography the redesign added: tracking, word spacing, a case transform, strikethrough. */
    @Test
    fun `the new typography controls in use`() = shoot(
        "styled_typography",
        settings = songSettings {
            copy(
                lyricsLetterSpacing = 6,
                lyricsWordSpacing = 12,
                lyricsStrikethrough = true,
                lyricsTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
            )
        },
    )

    // ── The pickers a row opens ─────────────────────────────────────────────────────────────────

    /** The colour picker: a hue strip, a saturation square and a hex field. */
    @Test
    fun `the colour picker open`() = shoot("colour_picker", rootIndex = 1) {
        onAllNodesWithText(WHITE_HEX)[0].performClick()
        waitForIdle()
    }

    /**
     * The corner dropdown open, on the Number element -- the only element that carries one.
     *
     * A fixed list of five, unlike the font dropdown below, so the image belongs to the control
     * rather than to the machine that recorded it.
     */
    @Test
    fun `the number's corner dropdown open`() = shoot("element_number_corner_open", rootIndex = 1) {
        onNodeWithText("Number").performClick()
        waitForIdle()
        onNodeWithTag("song_number_corner").performClick()
        waitForIdle()
    }

    // Not shot: the font dropdown open. Its list is whatever `GraphicsEnvironment` reports on the
    // recording machine, so the image would belong to whoever recorded it; `settingsFields` already
    // shoots that control against a fixed list, and `bibleSettingsTab` shoots the in-tab case once.

    // Not shot: a different segment lit in any of the eleven segmented rows — display mode, language,
    // bilingual layout, alignment. Selecting one moves the highlight and nothing else, and
    // `segmentedButton` already carries an image per state of that control.

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Every output down to one language, which is what the rail's Single writes. */
    private fun singleLanguage(): AppSettings {
        val base = songSettings { this }
        return base.copy(
            projectionSettings = base.projectionSettings.copy(
                screenAssignments = base.projectionSettings.screenAssignments.map {
                    it.copy(songMode = Constants.SONG_LANG_PRIMARY)
                },
            ),
        )
    }

    /**
     * [AppSettings] with the song block rewritten by [edit], over a real folder.
     *
     * The folder is real because the tab is handed the same settings object the rest of the dialog
     * is, and a path that is not there reads as a broken setup rather than a default one.
     */
    private fun songSettings(edit: SongSettings.() -> SongSettings): AppSettings {
        val defaults = AppSettings()
        return defaults.copy(
            songSettings = defaults.songSettings
                .copy(storageDirectory = songFolder().absolutePath)
                .edit(),
        )
    }

    /**
     * A fixed folder under a neutral root.
     *
     * Not a temp directory: a `createTempDirectory` name carries a random suffix, and any of it that
     * reaches the image would rewrite these files on every recording. Not a repo-relative `build/`
     * one either — that resolves through the developer's home directory, i.e. their name, and would
     * be committed into the PNGs for ever.
     */
    private fun songFolder(): File {
        val dir = FIXTURES.absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        SONGS.forEach { File(dir, it).writeText("fixture") }
        return dir
    }

    private companion object {
        const val SECTION = "songSettingsTab"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/songs") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/songs")

        val SONGS = listOf("001 Amazing Grace.sps", "002 It Is Well.sps", "003 Be Thou My Vision.sps")

        // As the tab renders them — the scroll targets for each block below the fold.
        const val LYRICS = "Lyrics"
        const val FULLSCREEN_DISPLAY = "Fullscreen Display"
        const val LOWER_THIRD_DISPLAY = "Lower Third Display"

        /** The default of every colour on this tab, and so the handle on a colour row. */
        const val WHITE_HEX = "#FFFFFF"
    }
}
