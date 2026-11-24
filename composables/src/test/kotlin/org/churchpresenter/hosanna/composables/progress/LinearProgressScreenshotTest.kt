package org.churchpresenter.hosanna.composables.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class LinearProgressScreenshotTest {

    private val section = "progress/linearProgress"
    private val width = Modifier.width(380.dp)

    @Test
    fun running() = hosannaComponent(section, "running") {
        Column(width, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LinearProgress(0f, label = "Downloading NIV", detail = "0 of 27.5 MB")
            LinearProgress(0.45f, label = "Downloading NIV", detail = "12.4 of 27.5 MB")
            LinearProgress(1f, label = "Downloading NIV", detail = "100%")
            LinearProgress(0.45f, detail = "45%")
        }
    }

    @Test
    fun statuses() = hosannaComponent(section, "statuses") {
        Column(width, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LinearProgress(1f, label = "Downloading NIV", status = ProgressStatus.Finished, statusText = "Done")
            LinearProgress(0.62f, label = "Downloading NIV", status = ProgressStatus.Failed, statusText = "Failed")
            LinearProgress(0.3f, label = "Uploading clips", status = ProgressStatus.Paused, statusText = "Paused")
        }
    }

    @Test
    fun retry() = hosannaComponent(section, "retry") {
        Column(width, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(ButtonState.Rest, ButtonState.Hovered, ButtonState.Pressed, ButtonState.Focused).forEach { state ->
                LinearProgress(
                    0.62f, label = "Downloading NIV", status = ProgressStatus.Failed, statusText = "Failed",
                    retryText = "Retry", retryInteractionSource = remember { frozenSource(state) },
                )
            }
        }
    }

    @Test
    fun indeterminateAndThin() = hosannaComponent(section, "indeterminateAndThin") {
        Column(width, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LinearProgress(null, label = "Importing songs…")
            LinearProgress(null)
            LinearProgress(0.7f, thin = true)
            LinearProgress(null, thin = true)
        }
    }
}
