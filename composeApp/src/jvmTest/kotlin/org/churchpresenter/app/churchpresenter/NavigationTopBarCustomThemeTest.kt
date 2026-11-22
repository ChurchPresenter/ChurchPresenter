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

class NavigationTopBarCustomThemeTest {

    @Test
    fun `a saved custom theme is offered beside the presets, unticked until it is chosen`() {
        val picked = mutableListOf<ThemeMode>()
        runComposeUiTest {
            val window = mockk<ComposeWindow>(relaxed = true)
            val menuBar = slot<JMenuBar>()
            every { window.jMenuBar = capture(menuBar) } returns Unit
            val scope = object : FrameWindowScope {
                override val window: ComposeWindow = window
            }
            setContent {
                with(scope) {
                    NavigationTopBar(theme = { picked += it }, currentTheme = ThemeMode.SYSTEM, hasCustomTheme = true)
                }
            }
            waitForIdle()

            val presets = ThemeMode.entries.count { it != ThemeMode.CUSTOM }
            val custom = menuBar.captured.getMenu(4).getItem(presets + 1) as JRadioButtonMenuItem
            assertFalse(custom.isSelected)
            custom.doClick(0)
        }
        assertEquals(listOf(ThemeMode.CUSTOM), picked)
    }
}
