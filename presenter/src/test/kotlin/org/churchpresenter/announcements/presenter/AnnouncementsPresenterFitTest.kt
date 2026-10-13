package org.churchpresenter.announcements.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.math.abs
import org.churchpresenter.sharedui.presenter.referenceScale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How big the announcement is drawn as its text and its room change: refitted to the whole space
 * when it stands still, to the width when it slides up or down, and left at its configured size
 * when it slides sideways -- and refitted again whenever the text or the space it has changes.
 *
 * Sizes are compared with each other rather than against pixel values, which differ across the
 * platforms' font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class AnnouncementsPresenterFitTest {

    private val short = "Welcome"
    private val long = "Welcome to the morning service -- coffee and tea are served in the hall afterwards"

    private fun ComposeUiTest.boundsOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().first().boundsInRoot

    /** Shows [first], measures, swaps in [second] and the width [secondWidth], and measures again. */
    private fun refit(
        settings: AnnouncementsSettings,
        first: String,
        second: String,
        secondWidth: Int = WIDTH,
    ): Pair<Rect, Rect> {
        var before = Rect.Zero
        var after = Rect.Zero
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            mainClock.autoAdvance = false
            var text by mutableStateOf(first)
            var width by mutableStateOf(WIDTH)
            setContent {
                Box(Modifier.size(width.dp, HEIGHT.dp)) {
                    AnnouncementsPresenter(text = text, appSettings = AppSettings(announcementsSettings = settings))
                }
            }
            mainClock.advanceTimeByFrame()
            before = boundsOf(first)
            text = second
            width = secondWidth
            mainClock.advanceTimeByFrame()
            onNodeWithText(second, substring = true).assertExists()
            after = boundsOf(second)
        }
        return before to after
    }

    @Test
    fun `standing still, a longer text is refitted to the space`() {
        val settings = AnnouncementsSettings(animationType = Constants.ANIMATION_FADE, fontSize = 400)
        val (_, crowded) = refit(settings, short, long)
        assertTrue(
            crowded.top >= -1f && crowded.bottom <= HEIGHT + 1,
            "the longer text is fitted, not left to run off: $crowded",
        )
    }

    @Test
    fun `standing still, a narrower room shrinks the text again`() {
        val settings = AnnouncementsSettings(animationType = Constants.ANIMATION_FADE, fontSize = 400)
        val (_, narrow) = refit(settings, short, short, secondWidth = WIDTH / 4)
        assertTrue(narrow.right <= WIDTH / 4 + 1, "the text stays inside the narrower room: $narrow")
        assertTrue(narrow.bottom <= HEIGHT + 1, "and inside its height: $narrow")
    }

    @Test
    fun `sliding up, the text is refitted to the width as it changes`() {
        val settings = AnnouncementsSettings(animationType = Constants.ANIMATION_SLIDE_FROM_BOTTOM, fontSize = 400)
        val (_, narrow) = refit(settings, short, short, secondWidth = WIDTH / 4)
        assertTrue(narrow.width <= WIDTH / 4 + 1, "the width alone still fits a sliding word: $narrow")
        val (_, changed) = refit(settings, short, long)
        assertTrue(changed.width <= WIDTH + 1, "a longer text is fitted to the width too: $changed")
    }

    @Test
    fun `sliding sideways, the configured size is kept whatever the room`() {
        val settings = AnnouncementsSettings(animationType = Constants.ANIMATION_SLIDE_FROM_RIGHT, fontSize = 60)
        val (wide, narrow) = refit(settings, short, short, secondWidth = WIDTH / 4)
        // Never fitted to the room -- only scaled with the output, as every stored size is.
        val scaled = wide.height * referenceScale((WIDTH / 4).dp, HEIGHT.dp) / referenceScale(WIDTH.dp, HEIGHT.dp)
        assertTrue(abs(scaled - narrow.height) < 1f, "a sideways slide is never fitted: $wide vs $narrow")
    }

    @Test
    fun `an unknown position stands the text in the middle`() {
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                    AnnouncementsPresenter(
                        text = short,
                        appSettings = AppSettings(
                            announcementsSettings = AnnouncementsSettings(
                                animationType = Constants.ANIMATION_FADE,
                                position = "somewhere",
                            ),
                        ),
                    )
                }
            }
            val bounds = onAllNodesWithText(short).fetchSemanticsNodes().first().boundsInRoot
            assertTrue(bounds.center.x in WIDTH * 0.4f..WIDTH * 0.6f, "centred across: $bounds")
            assertTrue(bounds.center.y in HEIGHT * 0.4f..HEIGHT * 0.6f, "centred down: $bounds")
        }
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
    }
}
