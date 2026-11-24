@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.progress

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.hosanna.composables.defaults.ProgressDefaults
import org.churchpresenter.hosanna.composables.model.ProgressSpinnerSize
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import kotlin.test.Test

class CircularProgressTest {

    private val busy = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate,
    )

    @Test
    fun `a running ring shows its percent, a status ring an icon instead`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Row {
                    CircularProgress(0.64f)
                    CircularProgress(1f, status = ProgressStatus.Finished)
                }
            }
        }
        onNodeWithText("64%").assertExists()
            .assertWidthIsEqualTo(ProgressDefaults.Ring).assertHeightIsEqualTo(ProgressDefaults.Ring)
        onNodeWithText("100%").assertDoesNotExist()
        onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f)),
        ).assertCountEquals(1)
    }

    @Test
    fun `spinners report busy at both sizes`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            HosannaSurface {
                Row {
                    ProgressSpinner()
                    ProgressSpinner(size = ProgressSpinnerSize.Regular)
                }
            }
        }
        settle()
        onAllNodes(busy).assertCountEquals(2)
    }
}
