@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.FrameWindowScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.churchpresenter.theme.ThemeMode
import javax.swing.JMenuBar
import javax.swing.JRadioButtonMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NavigationViewMenuCustomThemeTest {

    private fun viewMenu(currentTheme: ThemeMode, theme: (ThemeMode) -> Unit, block: JMenuBar.() -> Unit) =
        runComposeUiTest {
            val window = mockk<ComposeWindow>(relaxed = true)
            val menuBar = slot<JMenuBar>()
            every { window.jMenuBar = capture(menuBar) } returns Unit
            val scope = object : FrameWindowScope {
                override val window: ComposeWindow = window
            }
            setContent {
                with(scope) { NavigationTopBar(theme = theme, currentTheme = currentTheme, hasCustomTheme = true) }
            }
            waitForIdle()
            menuBar.captured.block()
        }

    @Test
    fun `a saved custom theme is offered beside the presets and can be picked`() {
        val presets = ThemeMode.entries.filter { it != ThemeMode.CUSTOM }
        val picked = mutableListOf<ThemeMode>()
        viewMenu(ThemeMode.DARK, { picked += it }) {
            val view = getMenu(4)
            assertEquals(presets.size + 3, view.itemCount, "the presets, a separator, Custom and Customize Theme")
            val custom = view.getItem(presets.size + 1) as JRadioButtonMenuItem
            assertFalse(custom.isSelected, "a preset is the theme in use")
            custom.doClick(0)
        }
        assertEquals(listOf(ThemeMode.CUSTOM), picked)
    }
}
