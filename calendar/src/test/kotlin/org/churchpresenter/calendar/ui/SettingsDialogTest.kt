@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.SECTION_SWATCHES
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The settings dialog on its own, with every callback recorded. */
class SettingsDialogTest {

    private class Heard {
        var preferences: CalendarPreferences? = null
        var cloudSync: Boolean? = null
        var added: Pair<String, String>? = null
        var colored: Pair<String, String>? = null
        var inserted: SectionStyle? = null
    }

    private fun withDialog(
        initialTab: SettingsTab = SettingsTab.SECTIONS,
        canInsert: Boolean = false,
        colorPicker: (@Composable (ColorPickerRequest) -> Unit)? = null,
        cloudSync: Boolean? = null,
        body: ComposeUiTest.(Heard) -> Unit,
    ) {
        val heard = Heard()
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    CalendarSettingsDialog(
                        preferences = CalendarPreferences(),
                        templates = emptyList(),
                        presets = emptyList(),
                        initialTab = initialTab,
                        canInsertSection = canInsert,
                        colorPicker = colorPicker,
                        onPreferencesChange = { heard.preferences = it },
                        onAddSection = { name, hex -> heard.added = name to hex },
                        onRenameSection = { _, _ -> },
                        onSectionColor = { name, hex -> heard.colored = name to hex },
                        onRemoveSection = {},
                        onInsertSection = { heard.inserted = it },
                        onRemoveTemplate = {},
                        onRemovePreset = {},
                        onDismiss = {},
                        cloudSync = cloudSync?.let { on ->
                            CalendarCloudSync(enabled = { on }, setEnabled = { heard.cloudSync = it })
                        },
                    )
                }
            }
            waitForIdle()
            body(heard)
        }
    }

    private fun ComposeUiTest.clickables(): List<Int> = onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id }

    /** Types into the last field on screen -- the one that just opened, past the section names. */
    private fun ComposeUiTest.lastField() = onAllNodes(hasSetTextAction()).let { it[it.fetchSemanticsNodes().size - 1] }

    @Test
    fun `without a picker the hex opens the swatch row, and a swatch recolors the section`() = withDialog { heard ->
        val before = clickables()

        onNodeWithText("#4FD3E8").performClick()
        waitForIdle()

        val after = clickables()
        val swatches = after.withIndex().filter { (_, id) -> id !in before }.map { it.index }
        assertEquals(SECTION_SWATCHES.size, swatches.size, "eight swatches appeared")
        onAllNodes(hasClickAction())[swatches.last()].performClick()
        waitForIdle()

        assertEquals("Pre-Service" to SECTION_SWATCHES.last(), heard.colored)
        assertEquals(before.size, clickables().size, "and the row closed")
    }

    @Test
    fun `the hex clicked again closes the swatch row`() = withDialog {
        val before = clickables()

        onNodeWithText("#4FD3E8").performClick()
        onNodeWithText("#4FD3E8").performClick()
        waitForIdle()

        assertEquals(before.size, clickables().size)
    }

    @Test
    fun `with a picker the hex opens it, and what it picks recolors the section`() {
        val picker: @Composable (ColorPickerRequest) -> Unit = { request ->
            Text("Picking ${request.initialHex}")
            Text("Choose", modifier = Modifier.clickable { request.onPicked("#123456") })
            Text("Give up", modifier = Modifier.clickable { request.onDismiss() })
        }
        withDialog(colorPicker = picker) { heard ->
            onNodeWithText("#5B9DF5").performClick()
            waitForIdle()
            assertTrue(onAllNodesWithText("Picking #5B9DF5").fetchSemanticsNodes().isNotEmpty())

            onNodeWithText("Choose").performScrollTo().performClick()
            waitForIdle()

            assertEquals("Worship" to "#123456", heard.colored)
            assertTrue(onAllNodesWithText("Picking").fetchSemanticsNodes().isEmpty(), "closed once picked")

            onNodeWithText("#5B9DF5").performClick()
            onNodeWithText("Give up").performScrollTo().performClick()
            waitForIdle()
            assertTrue(onAllNodesWithText("Picking").fetchSemanticsNodes().isEmpty(), "closed when dismissed")
        }
    }

    @Test
    fun `a section can be inserted into the open run of show`() = withDialog(canInsert = true) { heard ->
        onAllNodesWithText("Insert")[0].performClick()
        waitForIdle()

        assertEquals("Pre-Service", heard.inserted?.name)
    }

    @Test
    fun `a new section takes the first swatch no section uses`() = withDialog { heard ->
        onNodeWithText("Add a section name").performClick()
        waitForIdle()
        lastField().performTextInput("Youth")
        lastField().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals("Youth" to "#D4C25A", heard.added, "the seventh swatch: six are taken by the defaults")
    }

    @Test
    fun `a blank section name adds nothing and closes the field`() = withDialog { heard ->
        onNodeWithText("Add a section name").performClick()
        waitForIdle()
        val open = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        lastField().performClick()
        lastField().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertNull(heard.added)
        assertEquals(open - 1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "the field closed")
    }

    @Test
    fun `the default start time is committed on leaving the field`() = withDialog(SettingsTab.DEFAULTS) { heard ->
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextClearance()
        fields[0].performTextInput("9:30")
        fields[1].performClick()
        waitForIdle()

        assertEquals("09:30", heard.preferences?.defaultStartTime)
    }

    @Test
    fun `a start time that is not one is put back`() = withDialog(SettingsTab.DEFAULTS) { heard ->
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextClearance()
        fields[0].performTextInput("soon")
        fields[1].performClick()
        waitForIdle()

        assertNull(heard.preferences)
        assertFalse(onAllNodesWithText("soon").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `without cloud sync on offer the defaults tab has no switch for it`() = withDialog(SettingsTab.DEFAULTS) {
        assertTrue(onAllNodesWithText("Sync with the cloud and mobile devices").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the cloud sync switch shows what the app says and hands a flip back`() =
        withDialog(SettingsTab.DEFAULTS, cloudSync = false) { heard ->
            onNodeWithText("Sync with the cloud and mobile devices").performScrollTo().assertExists()
            val switches = onAllNodes(isToggleable())
            val cloud = switches[switches.fetchSemanticsNodes().size - 1]
            cloud.assertIsOff()
            cloud.performScrollTo().performClick()
            waitForIdle()

            assertEquals(true, heard.cloudSync)
        }
}
