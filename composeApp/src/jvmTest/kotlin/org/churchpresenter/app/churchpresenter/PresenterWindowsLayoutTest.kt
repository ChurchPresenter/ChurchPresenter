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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
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
        identifying: Boolean = false,
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
                identifyingScreen = identifying,
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

    @Test
    fun `an output aimed at an unused display opens no window`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(ScreenAssignment(targetDisplay = 1)),
                unusedScreens = listOf(Rectangle(1440, 0, 1920, 1080).asDisplayRect().key),
            ),
        ) { rig ->
            assertNull(rig.windows["Presenter View 1"])
        }

    @Test
    fun `a DeckLink output with no device and its key on the card opens no window`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(
                    ScreenAssignment(
                        targetType = Constants.TARGET_TYPE_DECKLINK,
                        keyTargetDisplay = 1,
                        keyTargetType = Constants.TARGET_TYPE_DECKLINK,
                    ),
                ),
            ),
        ) { rig ->
            assertNull(rig.windows["Presenter View 1"])
            assertNull(rig.windows["Key Output 1"])
            assertTrue("Presenter View 2" in rig.windows)
        }

    @Test
    fun `with the outputs hidden a DeckLink output opens no key window`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                screenAssignments = listOf(
                    ScreenAssignment(
                        targetDisplay = 0,
                        targetType = Constants.TARGET_TYPE_DECKLINK,
                        keyTargetDisplay = 2,
                    ),
                    ScreenAssignment(
                        targetDisplay = 2,
                        keyTargetDisplay = 0,
                        keyTargetType = Constants.TARGET_TYPE_DECKLINK,
                    ),
                ),
            ),
            show = false,
        ) { rig ->
            assertNull(rig.windows["Key Output 1"])
            assertFalse(rig.windows.getValue("Presenter View 2").visible)
            assertNull(rig.windows["Key Output 2"])
        }

    @Test
    fun `a key window follows the output from one mode to the next`() =
        rig(
            ProjectionSettings(
                hideCursorOnOutputs = false,
                outputProfiles = listOf(OutputProfile(id = "fade", bibleSettings = BibleSettings(crossfade = true))),
                screenAssignments = listOf(
                    ScreenAssignment(targetDisplay = 1, keyTargetDisplay = 2, activeProfileId = "fade"),
                ),
            ),
        ) { rig ->
            rig.manager.setPresentingMode(Presenting.BIBLE)
            waitForIdle()
            rig.manager.setPresentingMode(Presenting.LYRICS)
            waitForIdle()
            onNodeWithTag("Key Output 1").assertExists()
            assertEquals(Presenting.LYRICS, rig.manager.slideContent.value)
        }

    private val mergedDevWindows = ProjectionSettings(
        devWindowCount = 2,
        hideCursorOnOutputs = false,
        outputProfiles = listOf(
            OutputProfile(id = "wall", merge = OutputMerge(listOf(MergeTile("screen:0"), MergeTile("screen:1")))),
        ),
        screenAssignments = listOf(
            ScreenAssignment(activeProfileId = "wall"),
            ScreenAssignment(activeProfileId = "wall"),
        ),
    )

    @Test
    fun `a merged pair of dev windows each number their own tile while identifying`() =
        rig(mergedDevWindows, screens = arrayOf(operator), identifying = true) {
            onNode(hasText("Screen 1") and hasAnyAncestor(hasTestTag("Presenter View 1"))).assertExists()
            onNode(hasText("Screen 2") and hasAnyAncestor(hasTestTag("Presenter View 2"))).assertExists()
        }

    @Test
    fun `a merged pair of dev windows shows no numbers when not identifying`() =
        rig(mergedDevWindows, screens = arrayOf(operator)) { rig ->
            assertTrue("Presenter View 2" in rig.windows)
            onNode(hasText("Screen 1")).assertDoesNotExist()
        }

    @Test
    fun `with preview mode on the preview gets drivers of its own`() = runComposeUiTest {
        val manager = PresenterManager()
        val projection = ProjectionSettings(hideCursorOnOutputs = false, previewModeEnabled = true)
        val opened = mutableListOf<String>()
        setContent {
            PresenterWindows(
                screens = arrayOf(operator, audience),
                presenterManager = manager,
                mediaViewModel = MediaViewModel(),
                appSettings = AppSettings(projectionSettings = projection),
                identifyingScreen = false,
                serverUrl = "http://localhost:8080",
                qaDisplayUrl = "http://localhost:8080/qa",
                sttManager = STTManager(),
                defaultScreenDevice = { operator },
                window = { spec, _ -> SideEffect { opened += spec.title } },
            )
        }
        waitForIdle()
        assertTrue(manager.previewBus.enabled.value)
        assertTrue("Presenter View 1" in opened)
    }

    @Test
    fun `an announcement finishing clears it and ends the overlay as the settings say`() {
        val manager = PresenterManager()
        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        manager.setAnnouncementText("Welcome")
        manager.setDisplayedAnnouncementText("Welcome")

        val settings = AppSettings(projectionSettings = ProjectionSettings(overlayEndClearsDisplay = false))

        announcementClearer(manager, settings)()

        assertEquals("", manager.announcementText.value)
        assertEquals("", manager.displayedAnnouncementText.value)
        assertFalse(Presenting.ANNOUNCEMENTS in manager.overlays.value)
        assertFalse(manager.clearDisplayRequested.value)
    }

    @Test
    fun `only Escape pressed down on a key window that clears asks the output to clear`() {
        val manager = PresenterManager().apply { setPresentingMode(Presenting.BIBLE) }
        val env = OutputEnvironment(manager, MediaViewModel(), STTManager(), "", "", null) {}

        assertFalse(env.clearOnEscape(false, KeyEventType.KeyDown, Key.Escape))
        assertFalse(env.clearOnEscape(true, KeyEventType.KeyUp, Key.Escape))
        assertFalse(env.clearOnEscape(true, KeyEventType.KeyDown, Key.Spacebar))
        assertFalse(manager.clearDisplayRequested.value)

        assertTrue(env.clearOnEscape(true, KeyEventType.KeyDown, Key.Escape))
        assertTrue(manager.clearDisplayRequested.value)
    }
}
