@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.FrameWindowScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JRadioButtonMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun navigationTopBar(
    theme: (ThemeMode) -> Unit = {},
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
    onLanguageChange: (Language) -> Unit = {},
    onNewSchedule: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onSaveSchedule: () -> Unit = {},
    onSaveScheduleAs: () -> Unit = {},
    onCloseSchedule: () -> Unit = {},
    onExit: () -> Unit = {},
    onAddToSchedule: () -> Unit = {},
    onRemoveFromSchedule: () -> Unit = {},
    onClearSchedule: () -> Unit = {},
    onSettings: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onConnectToInstance: () -> Unit = {},
    onDisconnectInstance: () -> Unit = {},
    isInstanceLinkConnected: Boolean = false,
    onAbout: () -> Unit = {},
    onHelp: () -> Unit = {},
    onHowToBlog: () -> Unit = {},
    onGettingStarted: () -> Unit = {},
    onConverter: () -> Unit = {},
    onKeyboardShortcuts: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onContactUs: () -> Unit = {},
    showDeveloperMenu: Boolean = false,
    isPresenterWindowVisible: Boolean = true,
    onSetPresenterWindowVisible: (Boolean) -> Unit = {},
    isDevWindowAlwaysOnTop: Boolean = false,
    onSetDevWindowAlwaysOnTop: (Boolean) -> Unit = {},
    onOpenStyleEditor: () -> Unit = {},
    onOpenMemoryMonitor: () -> Unit = {},
    onOpenStoryPrompt: () -> Unit = {},
    block: JMenuBar.() -> Unit,
) = runComposeUiTest {
    val window = mockk<ComposeWindow>(relaxed = true)
    val menuBarSlot = slot<JMenuBar>()
    every { window.jMenuBar = capture(menuBarSlot) } returns Unit
    val scope = object : FrameWindowScope {
        override val window: ComposeWindow = window
    }

    setContent {
        with(scope) {
            NavigationTopBar(
                theme = theme,
                currentTheme = currentTheme,
                onLanguageChange = onLanguageChange,
                onNewSchedule = onNewSchedule,
                onOpenSchedule = onOpenSchedule,
                onSaveSchedule = onSaveSchedule,
                onSaveScheduleAs = onSaveScheduleAs,
                onCloseSchedule = onCloseSchedule,
                onExit = onExit,
                onAddToSchedule = onAddToSchedule,
                onRemoveFromSchedule = onRemoveFromSchedule,
                onClearSchedule = onClearSchedule,
                onSettings = onSettings,
                onStatistics = onStatistics,
                onConnectToInstance = onConnectToInstance,
                onDisconnectInstance = onDisconnectInstance,
                isInstanceLinkConnected = isInstanceLinkConnected,
                onAbout = onAbout,
                onHelp = onHelp,
                onHowToBlog = onHowToBlog,
                onGettingStarted = onGettingStarted,
                onConverter = onConverter,
                onKeyboardShortcuts = onKeyboardShortcuts,
                onCheckForUpdates = onCheckForUpdates,
                onContactUs = onContactUs,
                showDeveloperMenu = showDeveloperMenu,
                isPresenterWindowVisible = isPresenterWindowVisible,
                onSetPresenterWindowVisible = onSetPresenterWindowVisible,
                isDevWindowAlwaysOnTop = isDevWindowAlwaysOnTop,
                onSetDevWindowAlwaysOnTop = onSetDevWindowAlwaysOnTop,
                onOpenStyleEditor = onOpenStyleEditor,
                onOpenMemoryMonitor = onOpenMemoryMonitor,
                onOpenStoryPrompt = onOpenStoryPrompt,
            )
        }
    }

    waitForIdle()
    menuBarSlot.captured.block()
}

class NavigationTopBarTest {

    @Test
    fun `renders with only the required parameter, exercising every default value`() = runComposeUiTest {
        val window = mockk<ComposeWindow>(relaxed = true)
        val menuBarSlot = slot<JMenuBar>()
        every { window.jMenuBar = capture(menuBarSlot) } returns Unit
        val scope = object : FrameWindowScope {
            override val window: ComposeWindow = window
        }

        setContent {
            with(scope) {
                NavigationTopBar(theme = {})
            }
        }
        waitForIdle()

        val menuBar = menuBarSlot.captured
        assertEquals(7, menuBar.menuCount)
        val view = menuBar.getMenu(4)
        assertTrue((view.getItem(2) as JRadioButtonMenuItem).isSelected)
        val connect = menuBar.getMenu(3)
        assertFalse(connect.getItem(1).isEnabled)
    }

    @Test
    fun `every no-op default callback can be invoked without throwing`() = runComposeUiTest {
        val window = mockk<ComposeWindow>(relaxed = true)
        val menuBarSlot = slot<JMenuBar>()
        every { window.jMenuBar = capture(menuBarSlot) } returns Unit
        val scope = object : FrameWindowScope {
            override val window: ComposeWindow = window
        }

        setContent {
            with(scope) {
                NavigationTopBar(theme = {}, isInstanceLinkConnected = true)
            }
        }
        waitForIdle()

        val menuBar = menuBarSlot.captured
        for (menuIndex in 0 until menuBar.menuCount) {
            val menu = menuBar.getMenu(menuIndex)
            if (menu.text == "Language") {
                menu.getItem(0).doClick()
                continue
            }
            for (itemIndex in 0 until menu.itemCount) {
                menu.getItem(itemIndex).doClick()
            }
        }
    }

    @Test
    fun `every no-op developer default callback can be invoked without throwing`() = runComposeUiTest {
        val window = mockk<ComposeWindow>(relaxed = true)
        val menuBarSlot = slot<JMenuBar>()
        every { window.jMenuBar = capture(menuBarSlot) } returns Unit
        val scope = object : FrameWindowScope {
            override val window: ComposeWindow = window
        }

        setContent {
            with(scope) {
                NavigationTopBar(theme = {}, showDeveloperMenu = true)
            }
        }
        waitForIdle()

        val developer = menuBarSlot.captured.getMenu(7)
        val display = developer.getItem(0) as JMenu
        for (itemIndex in 0 until display.itemCount) display.getItem(itemIndex).doClick()
        developer.getItem(1).doClick()
        developer.getItem(2).doClick()
        developer.getItem(3).doClick()
    }

    @Test
    fun `file menu shows all items and wires callbacks`() {
        var newSchedule = 0
        var open = 0
        var save = 0
        var saveAs = 0
        var close = 0
        var exit = 0
        navigationTopBar(
            onNewSchedule = { newSchedule++ },
            onOpenSchedule = { open++ },
            onSaveSchedule = { save++ },
            onSaveScheduleAs = { saveAs++ },
            onCloseSchedule = { close++ },
            onExit = { exit++ },
        ) {
            val file = getMenu(0)
            assertEquals("File", file.text)
            assertEquals(6, file.itemCount)
            assertEquals("New Schedule", file.getItem(0).text)
            assertEquals("Open Schedule", file.getItem(1).text)
            assertEquals("Save Schedule", file.getItem(2).text)
            assertEquals("Save Schedule As...", file.getItem(3).text)
            assertEquals("Close Schedule", file.getItem(4).text)
            assertEquals("Exit", file.getItem(5).text)
            for (i in 0 until file.itemCount) file.getItem(i).doClick()
        }
        assertEquals(1, newSchedule)
        assertEquals(1, open)
        assertEquals(1, save)
        assertEquals(1, saveAs)
        assertEquals(1, close)
        assertEquals(1, exit)
    }

    @Test
    fun `schedule menu shows all items and wires callbacks`() {
        var add = 0
        var remove = 0
        var clear = 0
        navigationTopBar(
            onAddToSchedule = { add++ },
            onRemoveFromSchedule = { remove++ },
            onClearSchedule = { clear++ },
        ) {
            val schedule = getMenu(1)
            assertEquals("Schedule", schedule.text)
            assertEquals(3, schedule.itemCount)
            assertEquals("Add to Schedule", schedule.getItem(0).text)
            assertEquals("Remove from Schedule", schedule.getItem(1).text)
            assertEquals("Clear Schedule", schedule.getItem(2).text)
            for (i in 0 until schedule.itemCount) schedule.getItem(i).doClick()
        }
        assertEquals(1, add)
        assertEquals(1, remove)
        assertEquals(1, clear)
    }

    @Test
    fun `edit menu shows all items and wires callbacks`() {
        var settings = 0
        var statistics = 0
        navigationTopBar(
            onSettings = { settings++ },
            onStatistics = { statistics++ },
        ) {
            val edit = getMenu(2)
            assertEquals("Edit", edit.text)
            assertEquals(2, edit.itemCount)
            assertEquals("Settings", edit.getItem(0).text)
            assertEquals("CCLI Reports", edit.getItem(1).text)
            for (i in 0 until edit.itemCount) edit.getItem(i).doClick()
        }
        assertEquals(1, settings)
        assertEquals(1, statistics)
    }

    @Test
    fun `connect menu disables disconnect when not connected`() {
        var connect = 0
        var disconnect = 0
        navigationTopBar(
            onConnectToInstance = { connect++ },
            onDisconnectInstance = { disconnect++ },
            isInstanceLinkConnected = false,
        ) {
            val connectMenu = getMenu(3)
            assertEquals("Connect", connectMenu.text)
            assertEquals(2, connectMenu.itemCount)
            assertEquals("Connect to Instance…", connectMenu.getItem(0).text)
            assertEquals("Disconnect", connectMenu.getItem(1).text)
            assertTrue(connectMenu.getItem(0).isEnabled)
            assertFalse(connectMenu.getItem(1).isEnabled)
            connectMenu.getItem(0).doClick()
        }
        assertEquals(1, connect)
        assertEquals(0, disconnect)
    }

    @Test
    fun `connect menu enables disconnect when connected`() {
        var disconnect = 0
        navigationTopBar(
            onDisconnectInstance = { disconnect++ },
            isInstanceLinkConnected = true,
        ) {
            val connectMenu = getMenu(3)
            assertTrue(connectMenu.getItem(1).isEnabled)
            connectMenu.getItem(1).doClick()
        }
        assertEquals(1, disconnect)
    }

    @Test
    fun `view menu selects the radio button matching the current theme`() {
        val themesInOrder = listOf(
            ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM, ThemeMode.WARM, ThemeMode.OCEAN,
            ThemeMode.ROSE, ThemeMode.MIDNIGHT, ThemeMode.FOREST, ThemeMode.MOCHA, ThemeMode.STUDIO,
        )
        for ((selectedIndex, selectedTheme) in themesInOrder.withIndex()) {
            navigationTopBar(currentTheme = selectedTheme) {
                val view = getMenu(4)
                assertEquals("View", view.text)
                assertEquals(themesInOrder.size, view.itemCount)
                for (i in 0 until view.itemCount) {
                    val item = view.getItem(i) as JRadioButtonMenuItem
                    assertEquals(i == selectedIndex, item.isSelected, "theme=$selectedTheme index=$i")
                }
            }
        }
    }

    @Test
    fun `view menu invokes theme callback for every radio button`() {
        val expectedLabels = listOf(
            "Light Theme" to ThemeMode.LIGHT,
            "Dark Theme" to ThemeMode.DARK,
            "System Theme" to ThemeMode.SYSTEM,
            "Warm Theme" to ThemeMode.WARM,
            "Ocean Theme" to ThemeMode.OCEAN,
            "Rose Theme" to ThemeMode.ROSE,
            "Midnight Theme" to ThemeMode.MIDNIGHT,
            "Forest Theme" to ThemeMode.FOREST,
            "Mocha Theme" to ThemeMode.MOCHA,
            "Studio Theme" to ThemeMode.STUDIO,
        )
        val invoked = mutableListOf<ThemeMode>()
        navigationTopBar(theme = { invoked.add(it) }) {
            val view = getMenu(4)
            for ((index, expected) in expectedLabels.withIndex()) {
                val item = view.getItem(index)
                assertEquals(expected.first, item.text)
                item.doClick()
            }
        }
        assertEquals(expectedLabels.map { it.second }, invoked)
    }

    @Test
    fun `language menu shows every language and invokes callback with the matching enum`() {
        val expected = listOf(
            "Russian" to Language.RUSSIAN,
            "English" to Language.ENGLISH,
            "Ukrainian" to Language.UKRAINIAN,
            "Kazakh" to Language.KAZAKH,
            "German" to Language.GERMAN,
            "Polish" to Language.POLISH,
            "Belarusian" to Language.BELARUSIAN,
            "Czech" to Language.CZECH,
            "Spanish" to Language.SPANISH,
            "French" to Language.FRENCH,
            "Dutch" to Language.DUTCH,
            "Portuguese" to Language.PORTUGUESE,
            "Romanian" to Language.ROMANIAN,
            "Slovak" to Language.SLOVAK,
            "Estonian" to Language.ESTONIAN,
        )
        val invoked = mutableListOf<Language>()
        navigationTopBar(onLanguageChange = { invoked.add(it) }) {
            val language = getMenu(5)
            assertEquals("Language", language.text)
            assertEquals(expected.size, language.itemCount)
            for ((index, expectedEntry) in expected.withIndex()) {
                val item = language.getItem(index)
                assertTrue(
                    item.text.endsWith(expectedEntry.first),
                    "item $index was '${item.text}', expected to end with '${expectedEntry.first}'",
                )
                item.doClick()
            }
        }
        assertEquals(expected.map { it.second }, invoked)
    }

    @Test
    fun `help menu shows all items and wires callbacks`() {
        var gettingStarted = 0
        var keyboardShortcuts = 0
        var howToBlog = 0
        var converter = 0
        var about = 0
        var help = 0
        var contactUs = 0
        var checkForUpdates = 0
        navigationTopBar(
            onGettingStarted = { gettingStarted++ },
            onKeyboardShortcuts = { keyboardShortcuts++ },
            onHowToBlog = { howToBlog++ },
            onConverter = { converter++ },
            onAbout = { about++ },
            onHelp = { help++ },
            onContactUs = { contactUs++ },
            onCheckForUpdates = { checkForUpdates++ },
        ) {
            val helpMenu = getMenu(6)
            assertEquals("Help", helpMenu.text)
            assertEquals(8, helpMenu.itemCount)
            assertEquals("Getting Started…", helpMenu.getItem(0).text)
            assertEquals("Keyboard Shortcuts", helpMenu.getItem(1).text)
            assertEquals("How To Blog", helpMenu.getItem(2).text)
            assertEquals("Converter", helpMenu.getItem(3).text)
            assertEquals("About", helpMenu.getItem(4).text)
            assertEquals("Help", helpMenu.getItem(5).text)
            assertEquals("Contact", helpMenu.getItem(6).text)
            assertEquals("Check for Updates…", helpMenu.getItem(7).text)
            for (i in 0 until helpMenu.itemCount) helpMenu.getItem(i).doClick()
        }
        assertEquals(1, gettingStarted)
        assertEquals(1, keyboardShortcuts)
        assertEquals(1, howToBlog)
        assertEquals(1, converter)
        assertEquals(1, about)
        assertEquals(1, help)
        assertEquals(1, contactUs)
        assertEquals(1, checkForUpdates)
    }

    @Test
    fun `developer menu is absent by default`() {
        navigationTopBar {
            assertEquals(7, menuCount)
            for (i in 0 until menuCount) assertFalse(getMenu(i).text == "Developer")
        }
    }

    @Test
    fun `developer menu display submenu toggles checkboxes`() {
        var visibleCalls = mutableListOf<Boolean>()
        var alwaysOnTopCalls = mutableListOf<Boolean>()
        navigationTopBar(
            showDeveloperMenu = true,
            isPresenterWindowVisible = true,
            onSetPresenterWindowVisible = { visibleCalls.add(it) },
            isDevWindowAlwaysOnTop = false,
            onSetDevWindowAlwaysOnTop = { alwaysOnTopCalls.add(it) },
        ) {
            assertEquals(8, menuCount)
            val developer = getMenu(7)
            assertEquals("Developer", developer.text)
            assertEquals(4, developer.itemCount)

            val display = developer.getItem(0) as JMenu
            assertEquals("Display", display.text)
            assertEquals(2, display.itemCount)

            val showWindow = display.getItem(0) as JCheckBoxMenuItem
            assertEquals("Show Window", showWindow.text)
            assertTrue(showWindow.isSelected)
            showWindow.doClick()

            val alwaysOnTop = display.getItem(1) as JCheckBoxMenuItem
            assertEquals("Always on Top", alwaysOnTop.text)
            assertFalse(alwaysOnTop.isSelected)
            alwaysOnTop.doClick()
        }
        assertEquals(listOf(false), visibleCalls)
        assertEquals(listOf(true), alwaysOnTopCalls)
    }

    @Test
    fun `developer menu style editor and memory monitor items invoke callbacks`() {
        var styleEditor = 0
        var memoryMonitor = 0
        navigationTopBar(
            showDeveloperMenu = true,
            onOpenStyleEditor = { styleEditor++ },
            onOpenMemoryMonitor = { memoryMonitor++ },
        ) {
            val developer = getMenu(7)
            assertEquals("Animation Style Editor…", developer.getItem(1).text)
            assertEquals("Memory Monitor…", developer.getItem(2).text)
            developer.getItem(1).doClick()
            developer.getItem(2).doClick()
        }
        assertEquals(1, styleEditor)
        assertEquals(1, memoryMonitor)
    }

    @Test
    fun `developer menu story prompt item invokes its callback`() {
        var storyPrompt = 0
        navigationTopBar(
            showDeveloperMenu = true,
            onOpenStoryPrompt = { storyPrompt++ },
        ) {
            val developer = getMenu(7)
            assertEquals("Share Your Story Dialog", developer.getItem(3).text)
            developer.getItem(3).doClick()
        }
        assertEquals(1, storyPrompt)
    }

    @Test
    fun `top level menu mnemonics match their first letter`() {
        navigationTopBar {
            assertEquals('F'.code, getMenu(0).mnemonic)
            assertEquals('S'.code, getMenu(1).mnemonic)
            assertEquals('E'.code, getMenu(2).mnemonic)
            assertEquals('C'.code, getMenu(3).mnemonic)
            assertEquals('V'.code, getMenu(4).mnemonic)
            assertEquals('L'.code, getMenu(5).mnemonic)
            assertEquals('H'.code, getMenu(6).mnemonic)
        }
    }
}
