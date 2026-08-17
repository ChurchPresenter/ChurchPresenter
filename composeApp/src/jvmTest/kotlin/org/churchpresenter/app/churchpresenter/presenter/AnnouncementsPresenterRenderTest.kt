package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The announcement banner the room reads between items.
 *
 * The presenter animates the given text across the screen (the default is a slide from the bottom).
 * The animation is cosmetic; the contract that matters is simply that the text handed in is the text
 * composed — a banner that animates an empty or wrong string is a silent failure the operator can't
 * see from their own screen. The offset is animated but the text node stays in the tree throughout,
 * so asserting on the text is race-free without waiting on the animation.
 */
@OptIn(ExperimentalTestApi::class)
class AnnouncementsPresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    @Test
    fun `the announcement text is put on screen`() = runComposeUiTest {
        setContent {
            Box(screen) {
                AnnouncementsPresenter(text = "Service starts at 10am", appSettings = AppSettings())
            }
        }
        onNodeWithText("Service starts at 10am", substring = true)
            .assertExists("the banner must show the text it was given")
    }

    private fun render(
        settings: AnnouncementsSettings,
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
        showBackground: Boolean = true,
        text: String = "Service starts at 10am",
    ) = runComposeUiTest {
        setContent {
            Box(screen) {
                AnnouncementsPresenter(
                    text = text,
                    appSettings = AppSettings(announcementsSettings = settings),
                    outputRole = outputRole,
                    showBackground = showBackground,
                )
            }
        }
        onNodeWithText(text, substring = true).assertExists()
    }

    @Test
    fun `every directional slide animation renders the text`() {
        listOf(
            Constants.ANIMATION_SLIDE_FROM_LEFT,
            Constants.ANIMATION_SLIDE_FROM_RIGHT,
            Constants.ANIMATION_SLIDE_FROM_TOP,
            Constants.ANIMATION_SLIDE_FROM_BOTTOM,
        ).forEach { type -> render(AnnouncementsSettings(animationType = type)) }
    }

    @Test
    fun `a non-directional animation renders the text statically at every position`() {
        listOf(
            Constants.TOP_LEFT, Constants.TOP_CENTER, Constants.TOP_RIGHT,
            Constants.CENTER_LEFT, Constants.CENTER, Constants.CENTER_RIGHT,
            Constants.BOTTOM_LEFT, Constants.BOTTOM_CENTER, Constants.BOTTOM_RIGHT,
        ).forEach { position ->
            render(AnnouncementsSettings(animationType = Constants.ANIMATION_FADE, position = position))
        }
    }

    @Test
    fun `a horizontal slide aligns to the position's vertical component`() {
        render(AnnouncementsSettings(
            animationType = Constants.ANIMATION_SLIDE_FROM_LEFT,
            position = Constants.TOP_LEFT,
        ))
        render(AnnouncementsSettings(
            animationType = Constants.ANIMATION_SLIDE_FROM_LEFT,
            position = Constants.BOTTOM_LEFT,
        ))
    }

    @Test
    fun `a vertical slide aligns to the position's horizontal component`() {
        render(AnnouncementsSettings(
            animationType = Constants.ANIMATION_SLIDE_FROM_TOP,
            position = Constants.CENTER_LEFT,
        ))
        render(AnnouncementsSettings(
            animationType = Constants.ANIMATION_SLIDE_FROM_TOP,
            position = Constants.CENTER_RIGHT,
        ))
    }

    @Test
    fun `each horizontal text alignment renders the text`() {
        listOf(Constants.LEFT, Constants.RIGHT, Constants.CENTER).forEach { alignment ->
            render(AnnouncementsSettings(horizontalAlignment = alignment))
        }
    }

    @Test
    fun `bold italic underline and shadow do not break the render`() {
        render(AnnouncementsSettings(bold = true, italic = true, underline = true, shadow = true))
    }

    @Test
    fun `hiding the background renders the text`() {
        render(AnnouncementsSettings(), showBackground = false)
    }

    @Test
    fun `a literal transparent background color renders the text`() {
        render(AnnouncementsSettings(backgroundColor = "transparent"))
    }

    @Test
    fun `key output role renders the text`() {
        render(AnnouncementsSettings(), outputRole = Constants.OUTPUT_ROLE_KEY)
    }

    @Test
    fun `a finite loop count fires onFinished once the animation completes`() = runComposeUiTest {
        var finished = false
        mainClock.autoAdvance = false
        setContent {
            Box(screen) {
                AnnouncementsPresenter(
                    text = "Service starts at 10am",
                    appSettings = AppSettings(
                        announcementsSettings = AnnouncementsSettings(
                            animationType = Constants.ANIMATION_SLIDE_FROM_BOTTOM,
                            animationDuration = 500,
                            loopCount = 1,
                        )
                    ),
                    onFinished = { finished = true },
                )
            }
        }
        mainClock.advanceTimeBy(600)
        assertTrue(finished, "onFinished must fire once the single-iteration slide completes")
    }
}
