@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.messageOnAir
import org.churchpresenter.liveoutput.propsOnAir
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.app.churchpresenter.dialogs.CLEAR_GROUPS_EDIT_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropDefinition
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sidebar's Message and Props buttons, with their editors drawn as stand-ins (the real ones are
 * windows): what each editor's answers do to the output and to the settings.
 */
class PreviewSidebarEditorsTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
    }

    /** Remembers the callbacks of whichever editor is open, so a test can answer as the editor would. */
    private class StandInEditors : SidebarDialogs {
        var message: MessageAnswers? = null
        var props: PropsAnswers? = null
        var groups: GroupsAnswers? = null

        class GroupsAnswers(
            val onGroupsChange: (List<ClearGroup>) -> Unit,
            val onClearGroup: (ClearGroup) -> Unit,
            val onDismiss: () -> Unit,
        )

        class MessageAnswers(
            val onTemplatesChange: (List<MessageTemplate>) -> Unit,
            val onGoLive: (Cue.Message) -> Unit,
            val onClear: () -> Unit,
            val onDismiss: () -> Unit,
        )

        class PropsAnswers(
            val onPropsChange: (List<PropDefinition>) -> Unit,
            val onSwitch: (String, Boolean) -> Unit,
            val onDismiss: () -> Unit,
        )

        @Composable
        override fun Message(
            isVisible: Boolean,
            templates: List<MessageTemplate>,
            onTemplatesChange: (List<MessageTemplate>) -> Unit,
            onAir: Cue.Message?,
            onGoLive: (Cue.Message) -> Unit,
            onClear: () -> Unit,
            onDismiss: () -> Unit,
        ) {
            message = if (isVisible) MessageAnswers(onTemplatesChange, onGoLive, onClear, onDismiss) else null
        }

        @Composable
        override fun Props(
            isVisible: Boolean,
            props: List<PropDefinition>,
            onPropsChange: (List<PropDefinition>) -> Unit,
            onAir: Set<String>,
            onSwitch: (id: String, on: Boolean) -> Unit,
            onChoosePicture: suspend () -> String?,
            onDismiss: () -> Unit,
        ) {
            this.props = if (isVisible) PropsAnswers(onPropsChange, onSwitch, onDismiss) else null
        }

        @Composable
        override fun ClearGroups(
            isVisible: Boolean,
            groups: List<ClearGroup>,
            onGroupsChange: (List<ClearGroup>) -> Unit,
            onClearGroup: (ClearGroup) -> Unit,
            onDismiss: () -> Unit,
        ) {
            this.groups = if (isVisible) GroupsAnswers(onGroupsChange, onClearGroup, onDismiss) else null
        }
    }

    private fun sidebar(
        manager: PresenterManager,
        editors: StandInEditors,
        changes: MutableList<(AppSettings) -> AppSettings>,
        settings: AppSettings = AppSettings(),
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalSidebarDialogs provides editors) {
                MaterialTheme {
                    PreviewSidebar(
                        geometry = PreviewPanelGeometry(false, 1f, 360f, 600f),
                        state = PreviewSidebarState(
                            appSettings = settings,
                            livePreviewAppSettings = settings,
                            activeQuickBackground = null,
                            serverUrl = "",
                            qaDisplayUrl = "",
                            showControl = SidebarShowControl(devMode = true),
                        ),
                        actions = PreviewSidebarActions(
                            onClearDisplay = {},
                            onQuickBackgroundPicked = {},
                            onSettingsChange = { changes += it },
                        ),
                        presenterManager = manager,
                        sttManager = null,
                        companionSurface = { _, _ -> },
                    )
                }
            }
        }
        waitForIdle()
        block()
    }

    @Test
    fun `the message editor's answers go live, clear, save templates and close`() {
        val manager = PresenterManager()
        val editors = StandInEditors()
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        sidebar(manager, editors, changes) {
            onNodeWithTag(MESSAGE_BUTTON_TAG).performClick()
            waitForIdle()
            val answers = checkNotNull(editors.message) { "the editor opened" }

            runOnIdle { answers.onGoLive(Cue.Message("Cars in lot B")) }
            waitForIdle()
            assertEquals("Cars in lot B", manager.messageOnAir?.text)
            assertTrue(manager.showPresenterWindow.value)

            runOnIdle { answers.onClear() }
            waitForIdle()
            assertNull(manager.messageOnAir)

            val template = MessageTemplate(id = "t", name = "Parking", text = "Cars in lot B")
            runOnIdle { answers.onTemplatesChange(listOf(template)) }
            assertEquals(listOf(template), changes.last()(AppSettings()).messageTemplates)

            runOnIdle { answers.onDismiss() }
            waitForIdle()
            assertNull(editors.message, "dismissing closes the editor")
        }
    }

    @Test
    fun `the props editor's answers switch a prop, save the list and close`() {
        val manager = PresenterManager()
        val editors = StandInEditors()
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        sidebar(manager, editors, changes) {
            onNodeWithTag(PROPS_BUTTON_TAG).performClick()
            waitForIdle()
            val answers = checkNotNull(editors.props) { "the editor opened" }

            runOnIdle { answers.onSwitch("logo", true) }
            waitForIdle()
            assertEquals(setOf("logo"), manager.propsOnAir)
            assertTrue(manager.showPresenterWindow.value)
            runOnIdle { answers.onSwitch("logo", false) }
            waitForIdle()
            assertTrue(manager.propsOnAir.isEmpty())

            val prop = PropDefinition(id = "logo", name = "Logo")
            runOnIdle { answers.onPropsChange(listOf(prop)) }
            assertEquals(listOf(prop), changes.last()(AppSettings()).props)

            runOnIdle { answers.onDismiss() }
            waitForIdle()
            assertNull(editors.props, "dismissing closes the editor")
        }
    }

    @Test
    fun `the clear-group editor opens from the menu and its answers save and clear`() {
        val manager = PresenterManager()
        val editors = StandInEditors()
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        val everything = ClearGroup(id = "g1", name = "Everything")
        sidebar(manager, editors, changes, settings = AppSettings(clearGroups = listOf(everything))) {
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithTag(CLEAR_GROUPS_EDIT_TAG).performClick()
            waitForIdle()
            val answers = checkNotNull(editors.groups) { "the editor opened" }

            val renamed = everything.copy(name = "All of it")
            runOnIdle { answers.onGroupsChange(listOf(renamed)) }
            assertEquals(listOf(renamed), changes.last()(AppSettings()).clearGroups)
            runOnIdle { answers.onClearGroup(renamed) }
            waitForIdle()
            assertTrue(manager.program.value.isEmpty())

            runOnIdle { answers.onDismiss() }
            waitForIdle()
            assertNull(editors.groups, "dismissing closes the editor")
        }
    }
}
