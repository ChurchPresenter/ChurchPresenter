package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlin.test.Test

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
}
