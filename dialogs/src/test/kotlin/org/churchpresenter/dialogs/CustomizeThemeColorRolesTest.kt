@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.CustomThemeColors
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Customize Theme window's colour grid, one role at a time: each optional colour lands on its
 * own role and on no other, and while custom colours are off the accent and base cannot change.
 */
class CustomizeThemeColorRolesTest {

    /** Every optional colour set to a hex no default would produce, so each field is found by it. */
    private val allSet = CustomThemeColors(
        background = "#101010", text = "#202020", secondary = "#303030", selection = "#404040",
        success = "#505050", warning = "#606060", error = "#707070",
    )

    private fun content(
        start: ThemeCustomizationChoice,
        block: ComposeUiTest.(MutableList<ThemeCustomizationChoice>) -> Unit,
    ) =
        runComposeUiTest {
            val applied = mutableListOf<ThemeCustomizationChoice>()
            setContent {
                ChurchPresenterTheme(ThemeMode.DARK) {
                    Box(Modifier.size(CUSTOMIZE_THEME_DIALOG_WIDTH, CUSTOMIZE_THEME_DIALOG_HEIGHT)) {
                        CustomizeThemeContent(
                            currentTheme = ThemeMode.DARK,
                            initial = start,
                            previewDensity = LocalDensity.current,
                            onApply = { applied += it },
                            onDismiss = {},
                        )
                    }
                }
            }
            waitForIdle()
            block(applied)
        }

    private fun choice(useCustomColors: Boolean = true) = ThemeCustomizationChoice(
        useCustomColors = useCustomColors, accentHex = "#3F7FBF", dark = true, colors = allSet,
        fontFamily = "", fontScale = 1f,
    )

    private fun ComposeUiTest.recolour(fromHex: String, toHex: String) {
        onAllNodes(hasClickAction() and hasText(fromHex)).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction() and hasText("#", substring = true)).onLast().performTextReplacement(toHex)
        waitForIdle()
        onAllNodes(hasText("OK") and hasClickAction()).onLast().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.apply() {
        onAllNodes(hasText("Apply") and hasClickAction()).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun `secondary and selection each land on their own role`() = content(choice()) { applied ->
        recolour("#303030", "#313131")
        recolour("#404040", "#414141")
        apply()
        assertEquals(allSet.copy(secondary = "#313131", selection = "#414141"), applied.single().colors)
    }

    @Test
    fun `success and warning each land on their own role`() = content(choice()) { applied ->
        recolour("#505050", "#515151")
        recolour("#606060", "#616161")
        apply()
        assertEquals(allSet.copy(success = "#515151", warning = "#616161"), applied.single().colors)
    }

    @Test
    fun `background and text land on theirs`() = content(choice()) { applied ->
        recolour("#101010", "#111111")
        recolour("#202020", "#212121")
        apply()
        assertEquals(allSet.copy(background = "#111111", text = "#212121"), applied.single().colors)
    }

    @Test
    fun `with custom colours off, the base and the colours do not change`() =
        content(choice(useCustomColors = false)) { applied ->
            onAllNodes(hasText("Light") and hasClickAction()).onFirst().performClick()
            waitForIdle()
            recolour("#303030", "#313131")
            apply()
            val handed = applied.single()
            assertEquals(true, handed.dark, "the base stays dark")
            assertEquals(allSet, handed.colors)
        }
}
