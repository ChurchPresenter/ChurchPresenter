@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.announcements.AnnouncementsPresenter
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import kotlin.test.Test

/**
 * What the congregation sees when an announcement or a timer is live, full screen.
 *
 * **One image per state, not two.** The rest of the screenshot suite stacks a light and a dark
 * render of each state, because those surfaces follow the operator's theme. This one does not: the
 * audience screen is drawn from [AnnouncementsSettings] and looks the same whichever theme the
 * operator has chosen. Stacking would write the same picture twice.
 *
 * Rendered at 1920x1080, which is what this surface is drawn onto in practice.
 */
class AnnouncementsPresenterScreenshotTest {

    /** A 1080p output. */
    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    @Test
    fun `an announcement`() = shoot("announcement") {
        AnnouncementsPresenter(text = NOTICE, appSettings = announcementSettings())
    }

    @Test
    fun `an announcement in a corner`() = shoot("announcement_corner") {
        AnnouncementsPresenter(
            text = NOTICE,
            appSettings = announcementSettings(position = Constants.TOP_LEFT),
        )
    }

    @Test
    fun `a styled announcement on a plate`() = shoot("announcement_styled") {
        AnnouncementsPresenter(
            text = NOTICE,
            appSettings = announcementSettings(
                textColor = "#FFD54F",
                backgroundColor = "#1B2A5B",
                fontSize = 96,
                bold = true,
            ),
        )
    }

    @Test
    fun `a countdown on screen`() = shoot("announcement_timer") {
        AnnouncementsPresenter(text = "05:00", appSettings = announcementSettings(fontSize = 200))
    }

    // Not shot: an announcement with the background suppressed. The announcement's own plate is
    // drawn either way and its ground is transparent by default, so it renders as `announcement` does.

    private fun announcementSettings(
        textColor: String = AnnouncementsSettings().textColor,
        backgroundColor: String = AnnouncementsSettings().backgroundColor,
        fontSize: Int = AnnouncementsSettings().fontSize,
        bold: Boolean = false,
        position: String = AnnouncementsSettings().position,
    ) = AppSettings(
        announcementsSettings = AnnouncementsSettings(
            text = NOTICE,
            textColor = textColor,
            backgroundColor = backgroundColor,
            fontSize = fontSize,
            bold = bold,
            position = position,
            // The shipped default slides the text in over twelve seconds, so a capture of it is an
            // empty frame — the same reason the Announcements *tab* shots pin this off.
            animationType = Constants.ANIMATION_NONE,
        ),
    )

    private companion object {
        const val SECTION = "announcementsPresenter"

        const val NOTICE = "Prayer meeting Wednesday at 7pm in the hall"
    }
}
