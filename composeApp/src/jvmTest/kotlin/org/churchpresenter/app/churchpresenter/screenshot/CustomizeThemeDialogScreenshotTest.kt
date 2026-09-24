@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.dialogs.CUSTOMIZE_THEME_DIALOG_HEIGHT
import org.churchpresenter.app.churchpresenter.dialogs.CUSTOMIZE_THEME_DIALOG_WIDTH
import org.churchpresenter.app.churchpresenter.dialogs.CustomizeThemeContent
import org.churchpresenter.app.churchpresenter.dialogs.ThemeCustomizationChoice
import org.churchpresenter.settings.CustomThemeColors
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Customize Theme window, opened from View → Customize Theme…, in both themes.
 *
 * Shot through `CustomizeThemeContent` rather than the `DialogWindow` around it, which a headless
 * test cannot photograph, at the window's own 940x720 so what is reviewed is the size it opens at.
 * The window itself is drawn in the theme in use — the stacked light and dark halves — while the
 * preview column on its right is drawn in the draft, which is the point of it.
 *
 * The font field shows "System default" in the platform face rather than a named family, so these
 * do not depend on which fonts the recording machine has installed.
 */
class CustomizeThemeDialogScreenshotTest {

    private fun shoot(name: String, choice: ThemeCustomizationChoice = DEFAULT) =
        stackedThemes(SECTION, name) { mode, file ->
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(CUSTOMIZE_THEME_DIALOG_WIDTH, CUSTOMIZE_THEME_DIALOG_HEIGHT)) {
                            CustomizeThemeContent(
                                currentTheme = mode,
                                initial = choice,
                                previewDensity = LocalDensity.current,
                                onApply = {},
                                onDismiss = {},
                            )
                        }
                    }
                }
                waitForIdle()
                captureTo(file)
            }
        }

    /** First open: the default accent on a dark base, every optional colour on Auto. */
    @Test
    fun `first open`() = shoot("default")

    /** The same accent on a light base — the preview flips, the window around it does not. */
    @Test
    fun `light base`() = shoot("light_base", DEFAULT.copy(dark = false, accentHex = "#B5651D"))

    /** Every optional colour picked: each box shows its own colour and offers to go back to Auto. */
    @Test
    fun `every colour picked`() = shoot(
        "every_colour",
        DEFAULT.copy(
            accentHex = "#8E44AD",
            colors = CustomThemeColors(
                background = "#1B2A49",
                text = "#F5E6C8",
                secondary = "#16A085",
                selection = "#C0392B",
                success = "#27AE60",
                warning = "#F39C12",
                error = "#E74C3C",
            ),
        ),
    )

    /** Custom colours off: the controls dim and the preview shows the theme already in use. */
    @Test
    fun `custom colours off`() = shoot("colours_off", DEFAULT.copy(useCustomColors = false))

    /** Extra large chosen: the preview's text grows, the window's does not until Apply. */
    @Test
    fun `extra large text`() = shoot("extra_large", DEFAULT.copy(fontScale = 1.3f))

    private companion object {
        const val SECTION = "customizeThemeDialog"
        val DEFAULT = ThemeCustomizationChoice(
            useCustomColors = true,
            accentHex = "#3F7FBF",
            dark = true,
            fontFamily = "",
            fontScale = 1f,
        )
    }
}
