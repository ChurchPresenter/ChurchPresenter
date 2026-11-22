@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.dialogs.clearLayerItemTag
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.messageOnAir
import org.churchpresenter.liveoutput.showMessage
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.ProfileSet
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewSidebarLayersTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
    }

    private fun sidebar(
        settings: AppSettings,
        manager: PresenterManager = PresenterManager(),
        devMode: Boolean = true,
        surfaces: MutableList<String> = mutableListOf(),
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                PreviewSidebar(
                    geometry = PreviewPanelGeometry(false, 1f, 360f, 600f),
                    state = if (devMode) {
                        PreviewSidebarState(settings, settings, null, "", "", SidebarShowControl(devMode = true))
                    } else {
                        PreviewSidebarState(settings, settings, null, "", "")
                    },
                    actions = PreviewSidebarActions(
                        onClearDisplay = {},
                        onQuickBackgroundPicked = {},
                        onSettingsChange = {},
                    ),
                    presenterManager = manager,
                    sttManager = null,
                    companionSurface = { connection, _ ->
                        surfaces += connection.name
                        Text("surface ${connection.name}")
                    },
                )
            }
        }
        waitForIdle()
        block()
    }

    @Test
    fun `a single layer cleared from the menu takes only that layer off`() {
        val manager = PresenterManager()
        sidebar(AppSettings(), manager) {
            runOnIdle { manager.showMessage(Cue.Message("Cars in lot B")) }
            waitForIdle()
            assertNotNull(manager.messageOnAir)
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithTag(clearLayerItemTag(Layer.MESSAGES)).performClick()
            waitForIdle()
            assertNull(manager.messageOnAir)
            onNodeWithTag(clearLayerItemTag(Layer.MESSAGES)).assertDoesNotExist()
        }
    }

    @Test
    fun `out of dev mode the menu offers no messages layer and the dev tools are not drawn`() {
        sidebar(AppSettings(), devMode = false) {
            onNodeWithTag(DEV_MODE_BOX_TAG).assertDoesNotExist()
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithTag(clearLayerItemTag(Layer.MESSAGES)).assertDoesNotExist()
            onNodeWithTag(clearLayerItemTag(Layer.SLIDE)).assertExists()
        }
    }

    @Test
    fun `the profile sets list closes on a click outside it`() {
        val settings = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    outputProfiles = listOf(OutputProfile(id = "a"), OutputProfile(id = "b")),
                    screenAssignments = List(2) { ScreenAssignment(activeProfileId = "a") },
                    profileSets = listOf(ProfileSet("set1", "Youth", mapOf("screen:0" to "b"))),
                    showProfileSetsButton = true,
                ),
            )
        }
        sidebar(settings) {
            onNodeWithTag(PROFILE_SETS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithText("Youth").assertExists()
            onAllNodes(isRoot())[0].performMouseInput { click(Offset(width - 4f, height - 4f)) }
            waitForIdle()
            onNodeWithText("Youth").assertDoesNotExist()
        }
    }

    @Test
    fun `a preview filling the panel puts a right-sidebar surface straight under it`() {
        val surfaces = mutableListOf<String>()
        val settings = AppSettings(
            companionSatelliteConnections = listOf(
                CompanionSatelliteSettings(id = "a", name = "Front", host = "10.0.0.1", showInRightSidebar = true),
            ),
        ).let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    previewLayouts = listOf(PreviewLayout(id = "l1", name = "Main")),
                    activePreviewLayout = "l1",
                    previewLayoutFillsPanel = true,
                ),
            )
        }
        sidebar(settings, surfaces = surfaces) {
            onNodeWithText("surface Front").assertExists()
            assertEquals(listOf("Front"), surfaces.distinct())
        }
    }

    @Test
    fun `a sidebar state made without show control has the dev tools off`() {
        val state = PreviewSidebarState(AppSettings(), AppSettings(), null, "u", "q")
        assertFalse(state.showControl.devMode)
        assertEquals("u", state.serverUrl)
        assertEquals("q", state.qaDisplayUrl)
    }
}
