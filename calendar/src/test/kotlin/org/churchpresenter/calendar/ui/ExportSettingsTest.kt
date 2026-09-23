@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.PdfExportSettings
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportSettingsTest {

    private class Heard {
        var export: PdfExportSettings? = null
        var choseLogo = false
    }

    private fun withExportTab(
        export: PdfExportSettings = PdfExportSettings(),
        sections: List<SectionStyle> = CalendarPreferences().sections,
        body: ComposeUiTest.(Heard) -> Unit,
    ) {
        val heard = Heard()
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    CalendarSettingsDialog(
                        preferences = CalendarPreferences(sections = sections, pdfExport = export),
                        templates = emptyList(),
                        presets = emptyList(),
                        initialTab = SettingsTab.EXPORT,
                        canInsertSection = false,
                        colorPicker = null,
                        onPreferencesChange = { heard.export = it.pdfExport },
                        onAddSection = { _, _ -> },
                        onRenameSection = { _, _ -> },
                        onSectionColor = { _, _ -> },
                        onRemoveSection = {},
                        onInsertSection = {},
                        onRemoveTemplate = {},
                        onRemovePreset = {},
                        onChooseLogo = { heard.choseLogo = true },
                        onDismiss = {},
                    )
                }
            }
            waitForIdle()
            body(heard)
        }
    }

    @Test
    fun `the export tab is reached from the tab strip`() = withExportTab {
        assertTrue(shows("Church logo"))
        assertTrue(shows("Church name"))
        assertTrue(shows("Church address"))
        assertTrue(shows("Staff-only sections"))
    }

    @Test
    fun `each section has a switch, on for the staff-only ones`() = withExportTab {
        val switches = onAllNodes(isToggleable())

        assertEquals(CalendarPreferences().sections.size, switches.fetchSemanticsNodes().size)
        switches[0].assertIsOn()
        switches[1].assertIsOff()
    }

    @Test
    fun `switching a section changes what the public copy leaves out`() = withExportTab { heard ->
        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        waitForIdle()
        assertTrue(assertNotNull(heard.export).isStaffOnly("Worship"))

        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        waitForIdle()
        assertFalse(assertNotNull(heard.export).isStaffOnly("Pre-Service"))
    }

    @Test
    fun `with no sections the tab says so`() = withExportTab(sections = emptyList()) {
        assertTrue(shows("No sections defined yet"))
        assertTrue(onAllNodes(isToggleable()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the church name is saved on leaving the field, trimmed`() = withExportTab { heard ->
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextInput("  Grace Chapel ")
        fields[1].performClick()
        waitForIdle()

        assertEquals("Grace Chapel", heard.export?.churchName)
    }

    @Test
    fun `the address is saved on leaving the field`() = withExportTab { heard ->
        val fields = onAllNodes(hasSetTextAction())
        fields[1].performTextInput("1 Main St, Springfield")
        fields[0].performClick()
        waitForIdle()

        assertEquals("1 Main St, Springfield", heard.export?.churchAddress)
    }

    @Test
    fun `a field left unchanged saves nothing`() = withExportTab { heard ->
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performClick()
        fields[1].performClick()
        waitForIdle()

        assertNull(heard.export)
    }

    @Test
    fun `choose asks the host for a logo`() = withExportTab { heard ->
        onNodeWithText("Choose…").performClick()
        waitForIdle()

        assertTrue(heard.choseLogo)
    }

    @Test
    fun `with no logo there is nothing to remove`() = withExportTab {
        assertTrue(shows("No logo chosen"))
        assertTrue(onAllNodesWithContentDescription("Remove logo").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a chosen logo is named, and can be removed`() =
        withExportTab(export = PdfExportSettings(logoPath = "/nowhere/church-logo.png")) { heard ->
            assertTrue(shows("church-logo.png"))

            onAllNodesWithContentDescription("Remove logo")[0].performClick()
            waitForIdle()

            assertEquals("", heard.export?.logoPath)
        }
}
