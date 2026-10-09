package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.stt.STTManager
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which windows [PresenterWindows] opens for the displays attached, and what each one is: where it
 * sits, whether it hides the pointer, and what closing one does.
 *
 * The windows go through the `window` parameter, which here draws each window's content in place
 * and records what was asked for, so no AWT window opens; the displays are stand-ins with the
 * bounds a real monitor reports.
 */
@OptIn(ExperimentalTestApi::class)
class PresenterWindowsLayoutTest {

    private class FakeDisplay(private val area: Rectangle) : GraphicsDevice() {
        private val config = object : GraphicsConfiguration() {
            override fun getDevice(): GraphicsDevice = this@FakeDisplay
            override fun getColorModel(): ColorModel = ColorModel.getRGBdefault()
            override fun getColorModel(transparency: Int): ColorModel = ColorModel.getRGBdefault()
            override fun getDefaultTransform(): AffineTransform = AffineTransform()
            override fun getNormalizingTransform(): AffineTransform = AffineTransform()
            override fun getBounds(): Rectangle = Rectangle(area)
        }

        override fun getType(): Int = TYPE_RASTER_SCREEN
        override fun getIDstring(): String = "display-${area.x}"
        override fun getConfigurations(): Array<GraphicsConfiguration> = arrayOf(config)
        override fun getDefaultConfiguration(): GraphicsConfiguration = config
    }

    private val operator = FakeDisplay(Rectangle(0, 0, 1440, 900))
    private val audience = FakeDisplay(Rectangle(1440, 0, 1920, 1080))
    private val stage = FakeDisplay(Rectangle(3360, 0, 1280, 720))

    /** What the windows were asked for, by title, and the manager and player they drive. */
    private class Rig(
        val manager: PresenterManager,
        val media: MediaViewModel,
        val windows: Map<String, OutputWindowSpec>,
        val update: (AppSettings) -> Unit,
    )

    private fun rig(
        projection: ProjectionSettings,
        screens: Array<GraphicsDevice> = arrayOf(operator, audience, stage),
        show: Boolean = true,
        block: ComposeUiTest.(Rig) -> Unit,
    ) = runComposeUiTest {
        val manager = PresenterManager().apply { setShowPresenterWindow(show) }
        val media = MediaViewModel()
        val windows = mutableStateMapOf<String, OutputWindowSpec>()
        var settings by mutableStateOf(AppSettings(projectionSettings = projection))
        // Every test but the pointer one passes the setting off; see `the hide-pointer setting`.
        val host: OutputWindowHost = { spec, content ->
            SideEffect { windows[spec.title] = spec }
            if (spec.visible) Box(Modifier.testTag(spec.title)) { content() }
        }
        setContent {
            PresenterWindows(
                screens = screens,
                presenterManager = manager,
                mediaViewModel = media,
                appSettings = settings,
                identifyingScreen = false,
                sttManager = STTManager(),
                defaultScreenDevice = { operator },
                window = host,
            )
        }
        waitForIdle()
        block(Rig(manager, media, windows) { settings = it })
    }

    @Test
    fun `each audience display gets a borderless window over its whole area`() =
        rig(ProjectionSettings(hideCursorOnOutputs = false)) { rig ->
            val first = rig.windows.getValue("Presenter View 1")
            val second = rig.windows.getValue("Presenter View 2")
            assertEquals(1440.dp, first.state.position.x)
            assertEquals(1920.dp, first.state.size.width)
            assertEquals(3360.dp, second.state.position.x)
            assertTrue(first.undecorated && !first.resizable && first.alwaysOnTop)
            assertEquals(false, first.hideCursor)
        }

    @Test
    fun `closing an output window hides every output`() = rig(ProjectionSettings(hideCursorOnOutputs = false)) { rig ->
        rig.windows.getValue("Presenter View 1").onClose()
        waitForIdle()
        assertFalse(rig.manager.showPresenterWindow.value)
        assertFalse(rig.windows.getValue("Presenter View 1").visible)
    }

    @Test
    fun `the hide-pointer setting reaches every output window`() =
        // Hidden windows: a blank pointer is an AWT cursor, which a headless test cannot build.
        rig(ProjectionSettings(hideCursorOnOutputs = true), show = false) { rig ->
            assertEquals(true, rig.windows.getValue("Presenter View 1").hideCursor)
        }

    @Test
    fun `an output with no display chosen opens no window`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE)),
            ),
        ) { rig ->
            assertNull(rig.windows["Presenter View 1"])
            assertTrue("Presenter View 2" in rig.windows)
        }

    @Test
    fun `a display marked unused is skipped, and the next output takes the one left`() =
        rig(ProjectionSettings(hideCursorOnOutputs = false)) { rig ->
            rig.update(
                AppSettings(
                    projectionSettings = ProjectionSettings(
                        hideCursorOnOutputs = false,
                        unusedScreens = listOf(Rectangle(1440, 0, 1920, 1080).asDisplayRect().key),
                    ),
                ),
            )
            waitForIdle()
            assertEquals(3360.dp, rig.windows.getValue("Presenter View 1").state.position.x)
        }

    @Test
    fun `an output saved against a display's bounds finds it wherever it is listed`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(
                    ScreenAssignment(
                        targetBoundsX = 3360, targetBoundsY = 0, targetBoundsW = 1280, targetBoundsH = 720,
                    ),
                ),
            ),
        ) { rig ->
            assertEquals(3360.dp, rig.windows.getValue("Presenter View 1").state.position.x)
        }

    @Test
    fun `a key on another display opens its own window there`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(ScreenAssignment(targetDisplay = 1, keyTargetDisplay = 2)),
            ),
        ) { rig ->
            val key = rig.windows.getValue("Key Output 1")
            assertEquals(3360.dp, key.state.position.x)
            assertEquals(false, key.hideCursor)
            onNodeWithTag("Key Output 1").assertExists()
            key.onClose()
            waitForIdle()
            assertFalse(rig.manager.showPresenterWindow.value, "closing the key closes the outputs too")
        }

    @Test
    fun `a key aimed at a display that is not attached opens nothing`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(ScreenAssignment(targetDisplay = 1, keyTargetDisplay = 7)),
            ),
        ) { rig ->
            assertNull(rig.windows["Key Output 1"])
        }

    @Test
    fun `a key aimed at an unused display opens nothing`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(ScreenAssignment(targetDisplay = 1, keyTargetDisplay = 2)),
                unusedScreens = listOf(Rectangle(3360, 0, 1280, 720).asDisplayRect().key),
            ),
        ) { rig ->
            assertNull(rig.windows["Key Output 1"])
        }

    @Test
    fun `an output on a DeckLink card keeps its key on a display`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(
                    ScreenAssignment(
                        targetDisplay = 0,
                        targetType = Constants.TARGET_TYPE_DECKLINK,
                        keyTargetDisplay = 2,
                    ),
                ),
            ),
        ) { rig ->
            assertNull(rig.windows["Presenter View 1"], "the fill goes to the card, not a window")
            assertTrue(rig.windows.getValue("Key Output 1").visible, "the card's key shows on its display")
        }

    @Test
    fun `a key on a DeckLink card opens no window of its own`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(
                    ScreenAssignment(
                        targetDisplay = 1,
                        keyTargetDisplay = 0,
                        keyTargetType = Constants.TARGET_TYPE_DECKLINK,
                    ),
                ),
            ),
        ) { rig ->
            assertTrue("Presenter View 1" in rig.windows)
            assertNull(rig.windows["Key Output 1"])
        }

    @Test
    fun `with no audience display a dev build opens ordinary windows on the operator's screen`() =
        rig(ProjectionSettings(devWindowCount = 2, hideCursorOnOutputs = false), screens = arrayOf(operator)) { rig ->
            val first = rig.windows.getValue("Presenter View 1")
            assertTrue("Presenter View 2" in rig.windows)
            assertTrue(!first.undecorated && first.resizable)
            assertNull(first.hideCursor, "the dev window leaves the pointer alone")
            first.onClose()
            waitForIdle()
            assertFalse(rig.manager.showPresenterWindow.value)
        }
}
