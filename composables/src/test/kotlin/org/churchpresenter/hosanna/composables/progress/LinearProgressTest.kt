@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import kotlin.test.Test
import kotlin.test.assertEquals

class LinearProgressTest {

    private val width = Modifier.width(320.dp)

    private fun progress(value: Float) =
        SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f))

    @Test
    fun `a running bar shows its label and detail and reports its value`() = runComposeUiTest {
        setContent { HosannaSurface { LinearProgress(0.45f, width, label = "Downloading", detail = "12 of 30") } }
        onNodeWithText("Downloading").assertExists()
        onNodeWithText("12 of 30").assertExists()
        onAllNodes(progress(0.45f)).assertCountEquals(1)
    }

    @Test
    fun `a status replaces the detail`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                LinearProgress(
                    1f, width, label = "Downloading", detail = "30 of 30",
                    status = ProgressStatus.Finished, statusText = "Done",
                )
            }
        }
        onNodeWithText("Done").assertExists()
        onNodeWithText("30 of 30").assertDoesNotExist()
    }

    @Test
    fun `Retry shows only when failed and calls back`() = runComposeUiTest {
        var retries = 0
        setContent {
            HosannaSurface {
                Column {
                    LinearProgress(0.6f, width, label = "A", status = ProgressStatus.Failed, statusText = "Failed",
                        retryText = "Retry", onRetry = { retries++ })
                    LinearProgress(0.6f, width, label = "B", status = ProgressStatus.Paused, statusText = "Paused",
                        retryText = "Retry")
                }
            }
        }
        onAllNodesWithText("Retry").assertCountEquals(1)
        onNodeWithText("Retry").performClick()
        waitForIdle()
        assertEquals(1, retries)
    }

    @Test
    fun `thin and indeterminate bars carry no text`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            HosannaSurface {
                Column {
                    LinearProgress(0.7f, width, label = "Hidden", thin = true)
                    LinearProgress(null, width)
                }
            }
        }
        settle()
        onNodeWithText("Hidden").assertDoesNotExist()
        onAllNodes(progress(0.7f)).assertCountEquals(1)
        val busy = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )
        onAllNodes(busy).assertCountEquals(1)
    }
}
