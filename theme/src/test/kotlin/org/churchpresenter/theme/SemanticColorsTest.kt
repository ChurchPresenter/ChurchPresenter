package org.churchpresenter.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The roles M3 has no slot for, as the app reads them: `MaterialTheme.semantic`, per theme. */
@OptIn(ExperimentalTestApi::class)
class SemanticColorsTest {

    private val lightModes = listOf(ThemeMode.LIGHT, ThemeMode.WARM, ThemeMode.OCEAN, ThemeMode.ROSE)
    private val darkModes = listOf(
        ThemeMode.DARK, ThemeMode.MIDNIGHT, ThemeMode.FOREST, ThemeMode.MOCHA, ThemeMode.STUDIO,
    )

    private fun <T> underTheme(mode: ThemeMode, capture: @Composable () -> T): T {
        var captured: T? = null
        runComposeUiTest {
            setContent { ChurchPresenterTheme(themeMode = mode) { captured = capture() } }
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    private fun semanticOf(mode: ThemeMode): SemanticColors = underTheme(mode) { MaterialTheme.semantic }

    /** Every role in one list, so one added to the data class is checked without editing each test. */
    private fun rolesOf(c: SemanticColors) = mapOf(
        "success" to c.success,
        "onSuccess" to c.onSuccess,
        "successContainer" to c.successContainer,
        "onSuccessContainer" to c.onSuccessContainer,
        "warning" to c.warning,
        "onWarning" to c.onWarning,
        "warningContainer" to c.warningContainer,
        "onWarningContainer" to c.onWarningContainer,
        "info" to c.info,
        "favorite" to c.favorite,
        "marker" to c.marker,
        "hebrew" to c.hebrew,
        "greek" to c.greek,
        "chordVerse" to c.chordVerse,
        "chordChorus" to c.chordChorus,
        "chordBridge" to c.chordBridge,
        "chordTag" to c.chordTag,
    )

    @Test
    fun `every light mode gets the light set and every dark mode the dark one`() {
        val light = semanticOf(ThemeMode.LIGHT)
        val dark = semanticOf(ThemeMode.DARK)

        assertNotEquals(light, dark, "one set for both halves means one of them is shown on the wrong surface")
        lightModes.forEach { assertEquals(light, semanticOf(it), "$it is painted with the dark semantic set") }
        darkModes.forEach { assertEquals(dark, semanticOf(it), "$it is painted with the light semantic set") }
    }

    @Test
    fun `the scheme's own surface is what decides the half`() {
        (lightModes + darkModes).forEach {
            val scheme = underTheme(it) { MaterialTheme.colorScheme }

            assertEquals(it in darkModes, isDarkScheme(scheme), "$it is classified against its own surface")
        }
    }

    @Test
    fun `no role is transparent`() {
        listOf(semanticOf(ThemeMode.LIGHT), semanticOf(ThemeMode.DARK)).forEach { set ->
            rolesOf(set).forEach { (name, color) ->
                assertNotEquals(Color.Unspecified, color, "$name is unspecified")
                assertEquals(1f, color.alpha, "$name is not opaque")
            }
        }
    }

    @Test
    fun `each pair reads against the surface it is drawn on`() {
        listOf(semanticOf(ThemeMode.LIGHT), semanticOf(ThemeMode.DARK)).forEach { set ->
            val roles = rolesOf(set)
            listOf("success", "warning").forEach { role ->
                val on = role.replaceFirstChar { it.uppercase() }
                assertSeparated(roles.getValue(role), roles.getValue("on$on"), role)
                assertSeparated(
                    roles.getValue("${role}Container"),
                    roles.getValue("on${on}Container"),
                    "${role}Container",
                )
            }
        }
    }

    private fun assertSeparated(background: Color, foreground: Color, name: String) {
        assertTrue(
            abs(background.luminance() - foreground.luminance()) > 0.2f,
            "$name and its foreground are the same tone, so the text on it disappears",
        )
    }

    @Test
    fun `the light set is what is read when nothing has provided one`() {
        var seen: SemanticColors? = null
        runComposeUiTest { setContent { seen = LocalSemanticColors.current } }

        assertEquals(semanticOf(ThemeMode.LIGHT), seen, "an unprovided set must not leave the UI blank or throw")
    }
}
