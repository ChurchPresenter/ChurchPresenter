@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every theme has a name, and no two share one.
 *
 * This exists because of a failure that compiled cleanly: the top bar listed its ten themes as ten
 * hand-written rows, so the three themes added after it was written were simply absent from that
 * menu while appearing everywhere else. A `when` over the enum cannot do that — it fails to
 * compile — but nothing stopped a caller pairing it with a literal list, and nothing tested that
 * the names were distinct.
 */
class ThemeNamesTest {

    private fun namesInOrder(): List<String> {
        val names = mutableListOf<String>()
        runComposeUiTest {
            setContent { ThemeMode.entries.forEach { names.add(themeDisplayName(it)) } }
        }
        return names
    }

    @Test
    fun `every theme mode resolves to a name`() {
        val names = namesInOrder()
        assertEquals(ThemeMode.entries.size, names.size, "one name per theme")
        assertTrue(names.none { it.isBlank() }, "a blank name would render an empty menu row: $names")
    }

    @Test
    fun `no two themes share a name`() {
        // Two rows reading the same thing are two ways to pick something the user cannot tell apart.
        val names = namesInOrder()
        assertEquals(names.size, names.toSet().size, "duplicate theme names: $names")
    }

    @Test
    fun `the newest themes are named rather than falling through to a placeholder`() {
        runComposeUiTest {
            setContent {
                listOf(ThemeMode.SLATE, ThemeMode.SAND, ThemeMode.PLUM).forEach { Text(themeDisplayName(it)) }
            }
            listOf("Slate Theme", "Sand Theme", "Plum Theme").forEach {
                onNodeWithText(it).assertIsDisplayed()
            }
        }
    }
}
