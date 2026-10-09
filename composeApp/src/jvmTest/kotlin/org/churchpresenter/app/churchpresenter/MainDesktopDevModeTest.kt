@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.dialogs.clearGroupItemTag
import org.churchpresenter.app.churchpresenter.dialogs.clearLayerItemTag
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.clearMessage
import org.churchpresenter.liveoutput.messageOnAir
import org.churchpresenter.liveoutput.propsOnAir
import org.churchpresenter.liveoutput.setPropOn
import org.churchpresenter.liveoutput.showMessage
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.showcontrol.Action

/**
 * The dev mode only features on the root: the sidebar's box, preview mode and Take, and the macro
 * and clear-group keys.
 */
class MainDesktopDevModeTest : MainDesktopComposeHarness() {

    @Test
    fun `in dev mode the sidebar's box holds the unfinished features, and its toggle switches preview mode`() {
        val wiring = Wiring()
        root(withPreviewMode(false), wiring = wiring) { _ ->
            onNodeWithTag(DEV_MODE_BOX_TAG).assertExists()
            onNodeWithTag(MESSAGE_BUTTON_TAG).assertExists()
            onNodeWithTag(PROPS_BUTTON_TAG).assertExists()
            onNodeWithTag(MACROS_BUTTON_TAG).assertExists()
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).assertExists()
            onAllNodesWithTag(PREVIEW_TAKE_TAG).assertCountEquals(0)
            onNodeWithTag(PREVIEW_MODE_TOGGLE_TAG).performClick()
            waitForIdle()
            assertTrue(wiring.settingsChanges.last()(withPreviewMode(false)).projectionSettings.previewModeEnabled)
        }
    }

    @Test
    fun `outside dev mode the box, Take and the macro and clear-group keys are gone`() {
        val wiring = Wiring()
        val keyed = withPreviewMode(true, KeyChord.of(Key.F12, ctrl = true, shift = true)).let {
            it.copy(
                macros = listOf(Macro("macro1", "Walk in", listOf(Action.ClearAll))),
                keyboardShortcutSettings = KeyboardShortcutSettings(
                    overrides = it.keyboardShortcutSettings.overrides +
                        (ShortcutAction.MACRO_1.name to listOf(KeyChord.of(Key.F11, ctrl = true, shift = true))),
                ),
            )
        }
        val manager = cuedManager()
        root(keyed, wiring = wiring, presenterManager = manager, devMode = false) { _ ->
            onAllNodesWithTag(DEV_MODE_BOX_TAG).assertCountEquals(0)
            onAllNodesWithTag(PREVIEW_TAKE_TAG).assertCountEquals(0)
            press(Key.F11, ctrl = true, shift = true)
            press(Key.F12, ctrl = true, shift = true)
            assertTrue(wiring.macrosRun.isEmpty(), "the macro key does nothing")
            assertFalse(manager.isLive(Presenting.LOWER_THIRD), "Take's key does nothing")
        }
    }

    @Test
    fun `in dev mode a macro's key runs it`() {
        val wiring = Wiring()
        val walkIn = Macro("macro1", "Walk in", listOf(Action.ClearAll))
        val keyed = withOneSong().copy(
            macros = listOf(walkIn),
            keyboardShortcutSettings = KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.MACRO_1.name to listOf(KeyChord.of(Key.F11, ctrl = true, shift = true)),
                ),
            ),
        )
        root(keyed, wiring = wiring) { _ ->
            press(Key.F11, ctrl = true, shift = true)
            assertEquals(listOf(walkIn), wiring.macrosRun)
        }
    }

    @Test
    fun `in dev mode a clear group's key clears its layers, and an empty slot's key does nothing`() {
        val keyed = withOneSong().let {
            it.copy(
                clearGroups = listOf(ClearGroup("g1", "Graphics", listOf(Layer.GRAPHICS.name))),
                keyboardShortcutSettings = KeyboardShortcutSettings(
                    overrides = mapOf(
                        ShortcutAction.CLEAR_GROUP_1.name to listOf(KeyChord.of(Key.F11, ctrl = true, shift = true)),
                        ShortcutAction.CLEAR_GROUP_2.name to listOf(KeyChord.of(Key.F10, ctrl = true, shift = true)),
                    ),
                ),
            )
        }
        val manager = cuedManager().apply { previewBus.take() }
        root(keyed, presenterManager = manager) { _ ->
            press(Key.F10, ctrl = true, shift = true)
            assertTrue(manager.isLive(Presenting.LOWER_THIRD), "slot 2 has no group")
            press(Key.F11, ctrl = true, shift = true)
            assertFalse(manager.isLive(Presenting.LOWER_THIRD))
        }
    }

    @Test
    fun `Take's shortcut puts what is cued on air`() {
        val manager = cuedManager()
        root(withPreviewMode(true, KeyChord.of(Key.F12, ctrl = true, shift = true)), presenterManager = manager) { _ ->
            press(Key.F12, ctrl = true, shift = true)
            assertTrue(manager.isLive(Presenting.LOWER_THIRD))
            assertFalse(manager.previewBus.anythingCued)
        }
    }

    @Test
    fun `the sidebar's Take button puts what is cued on air, and waits while nothing is`() {
        val manager = cuedManager()
        root(withPreviewMode(true), presenterManager = manager) { _ ->
            onNodeWithTag(PREVIEW_TAKE_TAG).assertIsEnabled().performClick()
            waitForIdle()
            assertTrue(manager.isLive(Presenting.LOWER_THIRD))
            onNodeWithTag(PREVIEW_TAKE_TAG).assertIsNotEnabled()
        }
    }

    @Test
    fun `the sidebar's message and props buttons light while theirs is on air`() {
        val settings = withPreviewMode(false).copy(props = listOf(PropDefinition("p1", "Logo")))
        val manager = PresenterManager()
        root(settings, presenterManager = manager) { _ ->
            manager.showMessage(Cue.Message("Car ABC please"))
            manager.setPropOn("p1", true)
            waitForIdle()
            assertTrue(manager.messageOnAir != null)
            assertTrue(manager.propsOnAir.isNotEmpty())
            onNodeWithTag(MESSAGE_BUTTON_TAG).assertExists()
            manager.clearMessage()
            manager.setPropOn("p1", false)
            waitForIdle()
            onNodeWithTag(PROPS_BUTTON_TAG).assertExists()
        }
    }

    @Test
    fun `a clip a cue starts is put on the live output`() {
        val media = MediaViewModel()
        val manager = PresenterManager(showPresenterWindowInitially = false)
        root(withOneSong(), presenterManager = manager, media = media) { _ ->
            checkNotNull(media.onCuePlaybackStarted)("http://example.invalid/clip.mp4", "video")
            waitForIdle()
            assertTrue(manager.isLive(Presenting.MEDIA))
            assertTrue(manager.showPresenterWindow.value)
        }
    }

    @Test
    fun `the clear layers menu clears a group, or one layer that is on air`() {
        val gfx = ClearGroup("g1", "Graphics", listOf(Layer.GRAPHICS.name))
        val settings = withPreviewMode(false).copy(clearGroups = listOf(gfx))
        val manager = cuedManager().apply { previewBus.take() }
        root(settings, presenterManager = manager) { _ ->
            assertTrue(manager.isLive(Presenting.LOWER_THIRD))
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithTag(clearGroupItemTag("g1")).performClick()
            waitForIdle()
            assertFalse(manager.isLive(Presenting.LOWER_THIRD), "the group cleared its layer")
        }

        val again = cuedManager().apply { previewBus.take() }
        root(settings, presenterManager = again) { _ ->
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithTag(clearLayerItemTag(Layer.GRAPHICS)).performClick()
            waitForIdle()
            assertFalse(again.isLive(Presenting.LOWER_THIRD), "the layer cleared on its own")
        }
    }

    @Test
    fun `Take is not in the sidebar while preview mode is off`() =
        root(withPreviewMode(false)) { _ ->
            onAllNodesWithTag(PREVIEW_TAKE_TAG).assertCountEquals(0)
        }

    @Test
    fun `the dev mode sidebar survives the show-control settings changing in turn`() {
        val base = withPreviewMode(false)
        var appSettings by mutableStateOf(base)
        var devMode by mutableStateOf(true)
        val manager = PresenterManager()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainDesktop(
                        appSettings = appSettings,
                        presenterManager = manager,
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        live = LiveOutputCallbacks(
                            presenting = {},
                            onVerseSelected = {},
                            onSongItemSelected = {},
                            devMode = devMode,
                        ),
                    )
                }
            }
            waitForIdle()
            val steps: List<() -> Unit> = listOf(
                { appSettings = withPreviewMode(true) },
                { appSettings = appSettings.copy(clearGroups = listOf(ClearGroup("g1", "Graphics"))) },
                { appSettings = appSettings.copy(props = listOf(PropDefinition("p1", "Logo"))) },
                { appSettings = appSettings.copy(macros = listOf(Macro("macro1", "Walk in"))) },
                { appSettings = appSettings.copy(messageTemplates = listOf(MessageTemplate("m1", "Car", "Car"))) },
                { manager.showMessage(Cue.Message("Car")) },
                { manager.setPropOn("p1", true) },
                { devMode = false },
                { devMode = true },
                { appSettings = base },
            )
            steps.forEach { step ->
                step()
                waitForIdle()
            }
            onNodeWithTag(DEV_MODE_BOX_TAG).assertExists()
        }
    }
}
