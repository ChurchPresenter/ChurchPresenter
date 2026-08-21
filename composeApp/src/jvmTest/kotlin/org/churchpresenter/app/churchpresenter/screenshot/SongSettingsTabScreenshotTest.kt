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
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Songs tab of the settings dialog, in both themes.
 *
 * Two columns side by side inside **one** scroll container, so both move together and most of the
 * tab is below the fold — Title Slide, Song Number, Title, Transition and Text Margins down the
 * left, Lyrics, Fullscreen Display, Lower Third Display and the two Look Ahead blocks down the
 * right. The scroll positions below walk that, the way an operator reaches them.
 *
 * The axes that change what is drawn, rather than merely which segment of a row is lit:
 *
 *  - **Eight shadow toggles**, each unfolding a colour/size/opacity block under it. Off by default,
 *    so the shape with them on is a different tab and is shot separately.
 *  - **The title-slide checkbox**, which greys the "show song number" row under it when off — its
 *    default — and revives it when on.
 *  - **"Number before title"**, which is present only when the song number and the title agree on
 *    *both* their vertical and horizontal alignment, in either the fullscreen or the lower-third
 *    set. The defaults disagree, so it is absent from every other image here.
 *  - **A live [PresenterManager]**, which adds an auto-fit button beside each font size.
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
        presenterManager: PresenterManager? = null,
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
                                presenterManager = presenterManager,
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

    /** Scrolls [label]'s section heading into view and shoots what that lands on. */
    private fun section(name: String, label: String, settings: AppSettings = AppSettings()) =
        shoot(name, settings = settings) {
            onAllNodesWithText(label)[0].performScrollTo()
            waitForIdle()
        }

    // ── What opens ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `as it opens, at the defaults`() = shoot("top")

    /**
     * With a presenter attached.
     *
     * The auto-fit buttons this adds beside the lyrics font sizes are drawn disabled, because they
     * need a fullscreen or lower-third screen assigned *and* lyrics already live — neither of which
     * a bare [PresenterManager] has. Disabled is still the state an operator meets first, and it is
     * the only one reachable without a display.
     */
    @Test
    fun `with a presenter attached`() = shoot("presenter_attached", presenterManager = PresenterManager())

    // ── Below the fold ──────────────────────────────────────────────────────────────────────────
    // The left column runs out well before the right one, so a scroll target low on the left lands
    // mid-way down the right. Each of these is a distinct picture; the sections not listed sit in a
    // viewport one of them already covers.

    // Not shot: the title, fullscreen-display and lower-third blocks on their own. All three sit in
    // the first viewport, so scrolling to any of them lands where the tab already was and produces
    // `top` again. Only the look-ahead blocks and the foot of the tab are really below the fold.

    @Test
    fun `scrolled to the look ahead block`() = section("look_ahead", LOOK_AHEAD_FULLSCREEN)

    @Test
    fun `scrolled to the bottom`() = section("bottom", LOOK_AHEAD_LOWER_THIRD)

    // ── The title slide ─────────────────────────────────────────────────────────────────────────

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

    // ── Rows that appear ────────────────────────────────────────────────────────────────────────

    /**
     * "Number before title", which the defaults keep hidden.
     *
     * The number sits below the verse and to the right while the title is centred in the middle, so
     * neither the fullscreen nor the lower-third pair matches and the row is not drawn. Matching the
     * fullscreen pair to the title's is enough to bring it back — the check is an `||`.
     */
    @Test
    fun `the number-before-title row showing`() = section(
        "number_before_title",
        SONG_NUMBER,
        settings = songSettings {
            copy(
                songNumberPosition = Constants.MIDDLE,
                songNumberHorizontalAlignment = Constants.CENTER,
            )
        },
    )

    // ── Shadows ─────────────────────────────────────────────────────────────────────────────────
    // All eight on in one settings object, then shot at the scroll positions where the blocks they
    // unfold actually are. One state, several viewports — the same tab, not several tabs.

    @Test
    fun `shadow controls under the title`() = section("shadow_title", TITLE, settings = allShadowsOn())

    // Not shot: the lyrics shadow row on its own — the title and lyrics blocks share a viewport, so
    // it is the same picture as `shadow_title`.

    @Test
    fun `shadow controls under the look ahead blocks`() =
        section("shadow_look_ahead", LOOK_AHEAD_FULLSCREEN, settings = allShadowsOn())

    // ── The pickers a row opens ─────────────────────────────────────────────────────────────────

    /** The colour picker: a hue strip, a saturation square and a hex field. */
    @Test
    fun `the colour picker open`() = shoot("colour_picker", rootIndex = 1) {
        onAllNodesWithText(WHITE_HEX)[0].performClick()
        waitForIdle()
    }

    // Not shot: the font dropdown open. Its list is whatever `GraphicsEnvironment` reports on the
    // recording machine, so the image would belong to whoever recorded it; `settingsFields` already
    // shoots that control against a fixed list, and `bibleSettingsTab` shoots the in-tab case once.

    // Not shot: a different segment lit in any of the eleven segmented rows — display mode, language,
    // bilingual layout, alignment. Selecting one moves the highlight and nothing else, and
    // `segmentedButton` already carries an image per state of that control.

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Every shadow flag on, so all eight colour/size/opacity blocks are unfolded at once. */
    private fun allShadowsOn() = songSettings {
        copy(
            titleShadow = true,
            titleLowerThirdShadow = true,
            lyricsShadow = true,
            lyricsLowerThirdShadow = true,
            lookAheadShadow = true,
            lookAheadNextShadow = true,
            lowerThirdLookAheadShadow = true,
            lowerThirdLookAheadNextShadow = true,
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
        const val TITLE = "Title"
        const val SONG_NUMBER = "Song Number"
        const val LYRICS = "Lyrics"
        const val FULLSCREEN_DISPLAY = "Fullscreen Display"
        const val LOWER_THIRD_DISPLAY = "Lower Third Display"
        const val LOOK_AHEAD_FULLSCREEN = "Look Ahead (Fullscreen)"
        const val LOOK_AHEAD_LOWER_THIRD = "Look Ahead (Lower Third)"

        /** The default of every colour on this tab, and so the handle on a colour row. */
        const val WHITE_HEX = "#FFFFFF"
    }
}
