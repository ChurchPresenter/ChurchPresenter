@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.liveoutput

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertTrue

/** What `PropsCue` draws, drawn on its own: each kind, each corner, and edits made while it is up. */
class PropsCueTest {

    private fun surface(settings: AppSettings) = OutputSurface(
        kind = OutputSurfaceKind.WINDOW,
        profile = OutputProfile(),
        appSettings = settings,
        presenterManager = PresenterManager(showPresenterWindowInitially = false),
        outputRole = "",
        showBg = false,
    )

    private fun badge(id: String, text: String, corner: PropCorner = PropCorner.TOP_RIGHT) =
        PropDefinition(id, id, PropKind.BADGE, corner, text = text)

    @Test
    fun `only the props that are up are drawn, and a badge with no words draws nothing`() = runComposeUiTest {
        val settings = AppSettings(props = listOf(badge("a", "ON AIR"), badge("b", "HIDDEN"), badge("c", "")))
        setContent { Box(Modifier.size(800.dp, 450.dp)) { PropsCue(Cue.Props(setOf("a", "c")), surface(settings)) } }
        onNodeWithText("ON AIR").assertExists()
        onNodeWithText("HIDDEN").assertDoesNotExist()
    }

    @Test
    fun `nothing is drawn when none of the props that are up is defined`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(800.dp, 450.dp).testTag("host")) {
                PropsCue(Cue.Props(setOf("gone")), surface(AppSettings()))
            }
        }
        onNodeWithText("gone").assertDoesNotExist()
        onNodeWithTag("host").assertExists()
    }

    @Test
    fun `each prop sits in its own corner`() = runComposeUiTest {
        val settings = AppSettings(
            props = listOf(
                badge("tl", "TL", PropCorner.TOP_LEFT),
                badge("tr", "TR", PropCorner.TOP_RIGHT),
                badge("bl", "BL", PropCorner.BOTTOM_LEFT),
                badge("br", "BR", PropCorner.BOTTOM_RIGHT),
            ),
        )
        setContent {
            Box(Modifier.size(800.dp, 450.dp)) {
                PropsCue(Cue.Props(setOf("tl", "tr", "bl", "br")), surface(settings))
            }
        }
        val tl = onNodeWithText("TL").getBoundsInRoot()
        val tr = onNodeWithText("TR").getBoundsInRoot()
        val bl = onNodeWithText("BL").getBoundsInRoot()
        val br = onNodeWithText("BR").getBoundsInRoot()
        assertTrue(tl.left < tr.left && bl.left < br.left, "left corners are left of right ones")
        assertTrue(tl.top < bl.top && tr.top < br.top, "top corners are above bottom ones")
    }

    @Test
    fun `a clock follows the edited format while up, and a countdown that has passed reads zero`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val clock = PropDefinition("clock", "Clock", PropKind.CLOCK)
            val passed =
                PropDefinition("count", "Count", PropKind.COUNTDOWN, PropCorner.BOTTOM_LEFT, countdownTo = "never")
            var settings by mutableStateOf(
                AppSettings(
                    props = listOf(clock, passed),
                    announcementsSettings = AnnouncementsSettings(liveClockFormat = "'Alpha'"),
                ),
            )
            setContent {
                Box(Modifier.size(800.dp, 450.dp)) {
                    PropsCue(Cue.Props(setOf("clock", "count")), surface(settings))
                }
            }
            mainClock.advanceTimeBy(100)
            onNodeWithText("Alpha").assertExists()
            onNodeWithText("0:00").assertExists()

            settings = settings.copy(announcementsSettings = AnnouncementsSettings(liveClockFormat = "'Beta'"))
            mainClock.advanceTimeBy(1_100)
            onNodeWithText("Beta").assertExists()
        }

    @Test
    fun `a picture prop draws its picture, and a missing one draws nothing`() = runComposeUiTest {
        val file = Files.createTempFile("cp-prop", ".png")
        try {
            val red = BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().apply { color = java.awt.Color.RED; fillRect(0, 0, 40, 40); dispose() }
            }
            ImageIO.write(red, "png", file.toFile())
            val logo =
                PropDefinition("logo", "Logo", PropKind.IMAGE, PropCorner.TOP_LEFT, 50, file.absolutePathString())
            val gone =
                PropDefinition("gone", "Gone", PropKind.IMAGE, PropCorner.BOTTOM_RIGHT, imagePath = "/nowhere.png")
            setContent {
                Box(Modifier.size(400.dp, 200.dp).testTag("out")) {
                    PropsCue(Cue.Props(setOf("logo", "gone")), surface(AppSettings(props = listOf(logo, gone))))
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                val pixels = onNodeWithTag("out").captureToImage().toPixelMap()
                (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] == Color.Red } }
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a message is drawn in the announcements' look, on any surface`() = runComposeUiTest {
        val settings = AppSettings()
        setContent {
            Box(Modifier.size(800.dp, 450.dp)) {
                MessageCue(Cue.Message("Parent of 42, please"), surface(settings))
            }
        }
        onNodeWithText("Parent of 42, please", substring = true).assertExists()
    }
}
