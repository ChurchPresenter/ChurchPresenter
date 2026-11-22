@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class LabeledTabTest {

    private fun tab(style: TabLabelStyle, selected: Boolean, block: ComposeUiTest.(clicks: () -> Int) -> Unit) {
        var clicks = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LabeledTab(
                        name = "Songs",
                        icon = Icons.Filled.Palette,
                        selected = selected,
                        labelStyle = style,
                        onClick = { clicks++ },
                    )
                }
            }
            block { clicks }
        }
    }

    @Test
    fun `a text tab with the default style and margin shows its name and reports a press`() =
        tab(TabLabelStyle.TEXT, selected = true) { clicks ->
            onNode(hasText("Songs") and isSelectable()).assertIsSelected().performClick()
            assertEquals(1, clicks())
        }

    @Test
    fun `an icon and text tab shows its name beside the icon`() =
        tab(TabLabelStyle.ICONS_AND_TEXT, selected = false) { clicks ->
            onNode(hasText("Songs") and isSelectable()).assertIsNotSelected().performClick()
            assertEquals(1, clicks())
        }

    @Test
    fun `an icon-only tab is named by its icon`() = tab(TabLabelStyle.ICONS, selected = true) { clicks ->
        onNode(hasContentDescription("Songs") and isSelectable()).assertIsSelected().performClick()
        onNode(hasText("Songs")).assertDoesNotExist()
        assertEquals(1, clicks())
    }

    @Test
    fun `the default margin is the normal one`() {
        assertEquals(
            labeledTabMinWidth(TabLabelStyle.ICONS, TabLabelMargin.NORMAL),
            labeledTabMinWidth(TabLabelStyle.ICONS),
        )
    }
}
