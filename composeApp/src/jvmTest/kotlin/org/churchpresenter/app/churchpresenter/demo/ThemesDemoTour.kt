@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.ui.theme.themeChoiceFrom
import org.churchpresenter.dialogs.CustomizeThemeDialog
import org.churchpresenter.profiles.openColorField
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test

/**
 * View → the themes, each shown on the whole app as it plays, then View → Customize Theme…: a palette
 * from one accent on a base, the font and sizes, and the app taking it on as Custom.
 */
class ThemesDemoTour {

    @Test
    fun themes() = recordTour(
        "themes",
        tab = "Themes",
        liveLabel = "The app's own look",
        steps = 3,
        heading = "Choosing " to "",
        theme = theme,
        panel = { settings, change ->
            Box(Modifier.size(DIALOG_W.dp, DIALOG_H.dp)) {
                CustomizeThemeDialog(
                    isVisible = dialogOpen.value,
                    currentTheme = theme.value,
                    initial = themeChoiceFrom(settings, useCustomColors = true),
                    // What the app's own OK does (MainWindowDialogs): the choice saved, Custom put on.
                    onApply = { choice ->
                        if (choice.useCustomColors) theme.value = ThemeMode.CUSTOM
                        change {
                            it.copy(
                                theme = theme.value.toString(),
                                customThemeAccent = choice.accentHex,
                                customThemeDark = choice.dark,
                                customThemeColors = choice.colors,
                                uiFontFamily = choice.fontFamily,
                                uiFontScale = choice.fontScale,
                                uiScale = choice.uiScale,
                                uiRowSpacing = choice.rowSpacing,
                            )
                        }
                    },
                    // Kept drawn after OK until the panel slides away, so its window never shows empty.
                    onDismiss = {},
                    // Drawn in the panel rather than in a window of its own.
                    frame = { _, content -> content() },
                )
            }
        },
    ) { themesTour() }

    private fun DemoTour.themesTour() {
        say("intro")
        intro("Every built-in theme, and one of your own.")
        say("why")
        caption("The app's own look", "Bright room or dark booth")
        segment("menu")
        say("menu")
        caption("View menu", "The whole app changes at once")
        say("outputs")
        caption("Only the app's windows", "The screens stay the same")
        segment("light")
        LIGHT_THEMES.forEach { show(it) }
        segment("dark")
        DARK_THEMES.forEach { show(it) }
        segment("customize")
        customize()
        segment("custom")
        say("custom")
        caption("Custom", "In the View menu's theme list")
        segment("end")
        say("outro")
        outro()
    }

    /** One theme on the whole app, with its line. */
    private fun DemoTour.show(stop: ThemeStop) {
        theme.value = stop.mode
        ui.waitForIdle()
        pause(THEME_SETTLE)
        say(stop.key)
        caption(stop.name, stop.sub)
    }

    /** The Customize Theme dialog, from its colors to OK. */
    private fun DemoTour.customize() {
        theme.value = ThemeMode.DARK
        ui.waitForIdle()
        say("customize")
        caption("View, Customize Theme", "A theme of your own")
        dialogOpen.value = true
        openPanel("Customize Theme", DIALOG_W, DIALOG_H)
        say("use-custom")
        val custom = runCatching { panelItems(isToggleable()).first() }.getOrNull()
        if (custom != null) {
            panelNote("Use custom colors", "A whole palette of your own", custom, numbered = true)
        } else {
            point("Use custom colors", "A whole palette of your own", hasText("Use custom colors"))
        }
        say("accent")
        val accent = panelItem(hasClickAction() and hasText(DEFAULT_ACCENT, ignoreCase = true))
        panelNote("Accent color", "Everything else follows it", accent, numbered = true)
        // The field opens a colour dialog with a hex box; typed and confirmed off camera, so the next
        // picture is the dialog with its new accent.
        clearNote()
        ui.openColorField(DEFAULT_ACCENT)
        // The colour dialog is the newest pop-up, so its hex box and its OK are the last listed — the
        // Customize Theme window has an OK of its own.
        ui.onAllNodes(hasSetTextAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performTextReplacement(ACCENT)
        ui.waitForIdle()
        ui.onAllNodes(hasText("OK")).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        ui.waitForIdle()
        giveKeyboardBack()
        pause(THEME_SETTLE)
        say("base")
        point("Light or dark base", "Under the accent", hasText("Base"))
        say("more")
        point("More colors", "Auto comes from the accent", hasText("Selection", ignoreCase = true))
        say("font")
        point("Font", "The app's own windows", hasText("System default"))
        say("sizes")
        point("Text size, Interface size, Margin", "Larger, smaller, more room", hasText("Interface size"))
        say("preview")
        point("Preview", "Before anything changes", hasText("Sunday Service"))
        say("reset")
        point("Reset to defaults", "Back to the start", hasText("Reset to defaults"))
        say("apply")
        panelNote("OK", "The whole app takes it on", panelItem(hasText("OK")), click = true, numbered = true)
        closePanel()
        dialogOpen.value = false
        pause(THEME_SETTLE)
    }
}

private data class ThemeStop(val mode: ThemeMode, val key: String, val name: String, val sub: String)

private val theme = mutableStateOf(ThemeMode.DARK)
private val dialogOpen = mutableStateOf(false)

// The light themes, System among them as it follows a light computer under test, then the dark ones.
private val LIGHT_THEMES = listOf(
    ThemeStop(ThemeMode.LIGHT, "light", "Light", "Neutral gray, for a bright room"),
    ThemeStop(ThemeMode.SYSTEM, "system", "System", "Follows your computer"),
    ThemeStop(ThemeMode.WARM, "warm", "Warm", "Cream and amber"),
    ThemeStop(ThemeMode.OCEAN, "ocean", "Ocean", "Soft blue-tinted surfaces"),
    ThemeStop(ThemeMode.ROSE, "rose", "Rose", "A warm, pinkish gray"),
    ThemeStop(ThemeMode.SLATE, "slate", "Slate", "Cool blue-gray"),
    ThemeStop(ThemeMode.SAND, "sand", "Sand", "A warm, light taupe"),
)
private val DARK_THEMES = listOf(
    ThemeStop(ThemeMode.DARK, "dark", "Dark", "Classic neutral dark"),
    ThemeStop(ThemeMode.MIDNIGHT, "midnight", "Midnight", "Deep navy blue"),
    ThemeStop(ThemeMode.FOREST, "forest", "Forest", "Green-tinted surfaces"),
    ThemeStop(ThemeMode.MOCHA, "mocha", "Mocha", "A soft lilac accent"),
    ThemeStop(ThemeMode.STUDIO, "studio", "Studio", "Navy panels, amber accent"),
    ThemeStop(ThemeMode.PLUM, "plum", "Plum", "Aubergine, warm pink accent"),
)

private const val ACCENT = "#14B8A6"
private const val DEFAULT_ACCENT = "#3F7FBF"
private const val THEME_SETTLE = 0.4

// The real Customize Theme window's size (DialogSizes).
private const val DIALOG_W = 940f
private const val DIALOG_H = 760f
