@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a toolbar key's icon sits in the middle of the key, in every style and state.
 *
 * The panel-toggle style marks an open panel with a dot under the icon. That dot used to be stacked
 * under the icon in a Column and laid out even while transparent, so the key centred icon *and dot*
 * and every panel toggle — the ribbon's Tab Visibility, Background and Settings keys among them —
 * drew its icon about 3dp high. The dot is an overlay now; these hold the icon to the centre whether
 * it is drawn or not.
 */
class ToolbarKeyTest {

    private val label = "Tab Visibility"

    private fun key(style: ToolbarKeyStyle, open: Boolean, check: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(ThemeMode.DARK) {
                ToolbarKey(
                    painter = rememberVectorPainter(Icons.Default.Tune),
                    text = label,
                    onClick = {},
                    modifier = Modifier.testTag(KEY),
                    style = style,
                    open = open,
                    buttonSize = 40.dp,
                )
            }
        }
        waitForIdle()
        check()
    }

    private fun ComposeUiTest.assertIconCentred() {
        val keyBounds = onNodeWithTag(KEY).getBoundsInRoot()
        val iconBounds = onNodeWithContentDescription(label, useUnmergedTree = true).getBoundsInRoot()
        val keyCentreX = (keyBounds.left + keyBounds.right) / 2
        val keyCentreY = (keyBounds.top + keyBounds.bottom) / 2
        val iconCentreX = (iconBounds.left + iconBounds.right) / 2
        val iconCentreY = (iconBounds.top + iconBounds.bottom) / 2
        assertTrue(abs((iconCentreX - keyCentreX).value) <= TOLERANCE, "icon is ${iconCentreX - keyCentreX} off across")
        assertTrue(abs((iconCentreY - keyCentreY).value) <= TOLERANCE, "icon is ${iconCentreY - keyCentreY} off down")
    }

    @Test
    fun `a closed panel toggle centres its icon`() = key(ToolbarKeyStyle.PANEL_TOGGLE, open = false) {
        assertIconCentred()
    }

    @Test
    fun `an open panel toggle still centres its icon, its dot drawn over the key`() =
        key(ToolbarKeyStyle.PANEL_TOGGLE, open = true) { assertIconCentred() }

    @Test
    fun `a flat key centres its icon`() = key(ToolbarKeyStyle.FLAT, open = false) { assertIconCentred() }

    @Test
    fun `a raised key centres its icon`() = key(ToolbarKeyStyle.RAISED, open = false) { assertIconCentred() }

    private companion object {
        const val KEY = "toolbar-key"
        const val TOLERANCE = 0.5f
    }
}
