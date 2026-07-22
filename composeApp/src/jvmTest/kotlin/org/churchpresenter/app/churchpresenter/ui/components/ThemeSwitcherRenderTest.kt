package org.churchpresenter.app.churchpresenter.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.ui.theme.ProvideThemeManager
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeManager
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The theme picker in the top bar, driven the way an operator drives it.
 *
 * [ThemeSwitcherTest] covers the glyph table as data. This covers the control: what the button
 * shows, what the menu lists, and what a click actually changes. Two of those are only real in a
 * composition — the menu is a popup that does not exist until the button is pressed, and the
 * selection is made by a lambda per row that is easy to wire to the wrong `mode` (ten near-identical
 * rows built in a loop, each closing over its own value). A row that sets the neighbouring theme
 * compiles, renders, and is only visible as "clicking Ocean gives me Rose".
 *
 * Nothing here is found by a test tag: every node is addressed by what the operator reads on it,
 * which is also the thing worth asserting. The label comes from a string resource, so a row that
 * lost its label — or was wired to the wrong one — fails here rather than passing against a tag.
 *
 * `ThemeManager` is plain in-memory state behind a `CompositionLocal`, so each test provides its
 * own and reads the mode back from it directly.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeSwitcherRenderTest {

    /** The label and glyph each theme is offered under, exactly as the menu renders them. */
    private val themes = mapOf(
        ThemeMode.LIGHT to ("Light Theme" to "☀"),
        ThemeMode.DARK to ("Dark Theme" to "🌙"),
        ThemeMode.SYSTEM to ("System Theme" to "⚙"),
        ThemeMode.WARM to ("Warm Theme" to "🌅"),
        ThemeMode.OCEAN to ("Ocean Theme" to "🌊"),
        ThemeMode.ROSE to ("Rose Theme" to "🌸"),
        ThemeMode.MIDNIGHT to ("Midnight Theme" to "🌃"),
        ThemeMode.FOREST to ("Forest Theme" to "🌲"),
        ThemeMode.MOCHA to ("Mocha Theme" to "☕"),
        ThemeMode.STUDIO to ("Studio Theme" to "🎬"),
    )

    private fun label(mode: ThemeMode) = themes.getValue(mode).first
    private fun glyph(mode: ThemeMode) = themes.getValue(mode).second

    // ── The button ──────────────────────────────────────────────────────────────

    @Test
    fun `the button shows the theme that is in use`() = runComposeUiTest {
        val manager = ThemeManager().apply { setThemeMode(ThemeMode.OCEAN) }
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onNodeWithText(glyph(ThemeMode.OCEAN)).assertExists("the button is the only indication of the current theme")
    }

    @Test
    fun `the button follows a theme changed from elsewhere`() = runComposeUiTest {
        // The same manager backs the settings dialog, which can change the theme without this
        // button being touched.
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }
        onNodeWithText(glyph(ThemeMode.SYSTEM)).assertExists()

        manager.setThemeMode(ThemeMode.FOREST)
        waitForIdle()

        onNodeWithText(glyph(ThemeMode.FOREST)).assertExists()
        onAllNodesWithText(glyph(ThemeMode.SYSTEM)).assertCountEquals(0)
    }

    // ── The menu ────────────────────────────────────────────────────────────────

    @Test
    fun `the menu is closed until the button is pressed`() = runComposeUiTest {
        setContent { MaterialTheme { ProvideThemeManager(ThemeManager()) { ThemeSwitcher() } } }

        onAllNodesWithText(label(ThemeMode.LIGHT)).assertCountEquals(0)
    }

    @Test
    fun `pressing the button offers every theme`() = runComposeUiTest {
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()

        ThemeMode.entries.forEach { mode ->
            onNodeWithText(label(mode)).assertExists("$mode cannot be chosen — its row is not in the menu")
        }
    }

    @Test
    fun `each row is labelled and carries its own glyph`() = runComposeUiTest {
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }
        onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()

        ThemeMode.entries.forEach { mode ->
            // The theme in use shows its glyph twice — once on the button, once on its own row.
            val expected = if (mode == ThemeMode.SYSTEM) 2 else 1

            onAllNodesWithText(glyph(mode)).assertCountEquals(expected)
        }
    }

    @Test
    fun `pressing the button again closes the menu`() = runComposeUiTest {
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()
        onNodeWithText(label(ThemeMode.LIGHT)).assertExists()

        onAllNodesWithText(glyph(ThemeMode.SYSTEM))[0].performClick()

        // The menu has to let go on a second press.
        onAllNodesWithText(label(ThemeMode.LIGHT)).assertCountEquals(0)
    }

    // ── Choosing a theme ────────────────────────────────────────────────────────

    @Test
    fun `choosing a theme applies it`() = runComposeUiTest {
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()
        onNodeWithText(label(ThemeMode.OCEAN)).performClick()

        assertEquals(ThemeMode.OCEAN, manager.themeMode.value)
    }

    @Test
    fun `every row applies the theme it is labelled with`() {
        // The rows are built in a loop and each closes over its own `mode`; one row wired to the
        // wrong value looks perfect and simply hands out the neighbouring theme.
        ThemeMode.entries.forEach { mode ->
            runComposeUiTest {
                val manager = ThemeManager()
                setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

                onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()
                onNodeWithText(label(mode)).performClick()

                assertEquals(mode, manager.themeMode.value, "the row labelled '${label(mode)}' applied the wrong theme")
            }
        }
    }

    @Test
    fun `choosing a theme closes the menu`() = runComposeUiTest {
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()
        onNodeWithText(label(ThemeMode.ROSE)).performClick()

        // A menu left open covers the tab bar.
        onAllNodesWithText(label(ThemeMode.LIGHT)).assertCountEquals(0)
    }

    @Test
    fun `the button shows the newly chosen theme`() = runComposeUiTest {
        val manager = ThemeManager()
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onNodeWithText(glyph(ThemeMode.SYSTEM)).performClick()
        onNodeWithText(label(ThemeMode.MOCHA)).performClick()

        onNodeWithText(glyph(ThemeMode.MOCHA)).assertExists()
    }

    @Test
    fun `choosing the theme already in use leaves it alone`() = runComposeUiTest {
        val manager = ThemeManager().apply { setThemeMode(ThemeMode.DARK) }
        setContent { MaterialTheme { ProvideThemeManager(manager) { ThemeSwitcher() } } }

        onAllNodesWithText(glyph(ThemeMode.DARK))[0].performClick()
        onNodeWithText(label(ThemeMode.DARK)).performClick()

        assertEquals(ThemeMode.DARK, manager.themeMode.value)
        // And the menu still closes.
        onAllNodesWithText(label(ThemeMode.LIGHT)).assertCountEquals(0)
    }
}
