@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.statistics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertFalse

/** The statistics window's closed state. */
class DialogClosedStateTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a hidden statistics window draws nothing`() = withStatsHome { _ ->
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CCLIReportDialog(
                        isVisible = false,
                        theme = ThemeMode.LIGHT,
                        statisticsManager = StatisticsManager(),
                        onDismiss = {},
                    )
                }
            }

            assertFalse(onRoot().printToString().contains("CCLI"))
        }
    }
}
