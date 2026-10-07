@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ranked chart beside the report tables, at its edges: a period with nothing in it, and one
 * where everything listed was used zero times -- neither may divide by zero or draw a full bar.
 */
class TopItemsChartTest {

    @Test
    fun `an empty period draws an empty chart`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TopItemsChart(emptyList(), Color.Blue, Modifier.size(300.dp).testTag("chart"))
            }
        }

        onNodeWithTag("chart").assertWidthIsEqualTo(300.dp)
        assertTrue(onAllNodes(hasText("0")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `items never used are still listed, each with a zero`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TopItemsChart(listOf("Psalms" to 0, "John" to 0), Color.Blue, Modifier.size(300.dp))
            }
        }

        assertTrue(onAllNodes(hasText("Psalms")).fetchSemanticsNodes().isNotEmpty())
        assertEquals(2, onAllNodes(hasText("0")).fetchSemanticsNodes().size)
    }

    @Test
    fun `a badge takes the size it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                IconBadge(
                    icon = Icons.Filled.Book,
                    modifier = Modifier.testTag("badge"),
                    container = Color.Black,
                    content = Color.White,
                    size = 24.dp,
                )
            }
        }

        onNodeWithTag("badge").assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    }
}
