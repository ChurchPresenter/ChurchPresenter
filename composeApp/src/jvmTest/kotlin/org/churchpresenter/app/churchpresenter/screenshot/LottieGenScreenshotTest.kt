@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lottiegen.model.LottieGenConfig
import lottiegen.model.Preset
import lottiegen.persistence.PresetStorage
import lottiegen.ui.Strings
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.theme.ChurchPresenterTheme
import lottiegen.App as LottieGenApp
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The bundled Lottie lower-third generator, reached from the Help menu and from the Lower Third tab.
 *
 * It draws its own chrome from `lottiegen.ui.Tokens` rather than from Material, so **both halves of
 * each stacked image are load-bearing here**: the tool follows the host app's theme, and the light
 * half is the only thing that shows it still does. It is mounted with `embedded = true`, which is
 * how ChurchPresenter mounts it — standalone it owns the theme and is dark either way, so a
 * standalone capture would photograph the same pixels twice.
 */
class LottieGenScreenshotTest {

    private val section = "lottieGen"

    /** The generator's own window size, so the layout is the one a user gets. */
    private val window = Size(1200f, 800f)

    @AfterTest
    fun restoreLocale() = Strings.setLocale(Locale.getDefault())

    @Test
    fun `the generator`() = generator("generator")

    @Test
    fun `canvas section`() = generator("section_canvas") { expand(Strings.sectionCanvas) }

    @Test
    fun `text style section`() = generator("section_text_style") { expand(Strings.sectionTextStyle) }

    @Test
    fun `shape section`() = generator("section_shape") { expand(Strings.sectionShape) }

    @Test
    fun `logo section`() = generator("section_logo") { expand(Strings.sectionLogo) }

    @Test
    fun `timing section`() = generator("section_timing") { expand(Strings.sectionTiming) }

    @Test
    fun `position section`() = generator("section_position") { expand(Strings.sectionPosition) }

    /**
     * The saved-preset library at the foot of the panel — the card that stayed dark under a light
     * app, and so the one state this suite exists to keep honest.
     */
    @Test
    fun `the library`() = generator("library") {
        // The last saved preset, not the card's header: scrolling to the header alone leaves the
        // rows it exists to show below the fold.
        onNodeWithText(LAST_PRESET).performScrollTo()
        waitForIdle()
    }

    private fun generator(name: String, drive: ComposeUiTest.() -> Unit = {}) {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        // The panel's labels come from a ResourceBundle keyed on the default locale, so on a
        // machine set to one of the eight translated locales every caption would differ.
        Strings.setLocale(Locale.ENGLISH)
        seedLibrary()
        stackedThemes(section, name) { mode, file ->
            runSkikoComposeUiTest(size = window, density = Density(1f)) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(window.width.dp, window.height.dp)) {
                            LottieGenApp(embedded = true)
                        }
                    }
                }
                waitForIdle()
                scrubToHeldFrame()
                drive()
                captureTo(file)
            }
        }
    }

    /**
     * Scrubs the transport to a frame where the lower third is actually on screen.
     *
     * **The preview never animates under test and cannot be made to.** Compose's test framework
     * installs an `InfiniteAnimationPolicy` that holds infinite animations back so the tree can ever
     * reach idle, and the preview is `iterations = Int.MAX_VALUE` — so it sits at 0%, which is
     * before the entrance begins, and the canvas photographs as an empty checkerboard. Advancing
     * `mainClock` by hand does not help: the policy, not the clock, is what is holding it.
     *
     * Scrubbing goes the other way round. A tap on the transport track sets the position *and*
     * pauses, and the painter then draws that position directly — no animation involved, the same
     * frame every run.
     *
     * The tap is located from the pause button's own bounds rather than from a pixel guess, so it
     * survives a change to the panel width or the transport's padding: same row, [SCRUB_FRACTION] of
     * the way across what is left of the width.
     */
    private fun ComposeUiTest.scrubToHeldFrame() {
        val transport = onNodeWithContentDescription("Pause").fetchSemanticsNode().boundsInRoot
        val x = transport.right + (window.width - transport.right) * SCRUB_FRACTION
        onRoot().performTouchInput { click(Offset(x, transport.center.y)) }
        waitForIdle()
    }

    /** Opens a collapsed section, scrolling it into view first — the panel is taller than the window. */
    private fun ComposeUiTest.expand(title: String) {
        onNodeWithText(title).performScrollTo().performClick()
        waitForIdle()
    }

    /**
     * Three saved lower thirds, written where the generator reads them.
     *
     * Through `PresetStorage` rather than by writing the JSON here, so the fixture cannot drift from
     * the format the tool actually loads — and under the test home, which is why
     * [TestSingletons.latchToTestHome] has to run first.
     */
    private fun seedLibrary() = PresetStorage.save(
        listOf(
            preset("Guest Speaker", "Dr. Helen Marsh", "Guest Speaker"),
            preset("Worship Leader", "James Okoye", "Worship Leader"),
            preset("Welcome", "Grace Community Church", "Sunday Morning Service"),
        )
    )

    private companion object {
        /**
         * Where to tap, as a fraction of the width to the right of the pause button.
         *
         * Not the same thing as a fraction of the timeline — the track starts a gap in from the
         * button and ends short of the percentage readout — so this is calibrated rather than
         * derived: 0.6 lands on ~65%, which on the default 4s-in / 3s-hold timing is 4.5s, inside
         * the hold. The badge in the capture reads the position back, so a change here is visible in
         * the image rather than silent.
         */
        const val SCRUB_FRACTION = 0.6f

        /** The name of the last preset [seedLibrary] writes — the foot of the panel. */
        const val LAST_PRESET = "Welcome"
    }

    private fun preset(name: String, nameText: String, infoText: String) = Preset(
        name = name,
        // Fixed rather than Instant.now(): the panel does not draw the timestamp today, but a
        // recorded-at value in a committed fixture is a date-dependent screenshot waiting to happen.
        savedAt = "2026-01-04T09:30:00Z",
        config = LottieGenConfig(nameText = nameText, infoText = infoText),
    )
}
