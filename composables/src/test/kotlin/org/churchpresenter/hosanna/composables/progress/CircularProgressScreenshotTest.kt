package org.churchpresenter.hosanna.composables.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.ProgressSpinnerSize
import org.churchpresenter.hosanna.composables.model.ProgressStatus
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class CircularProgressScreenshotTest {

    private val section = "progress/circularProgress"

    @Test
    fun spinners() = hosannaComponent(section, "spinners") {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressSpinner()
            ProgressSpinner(size = ProgressSpinnerSize.Regular)
        }
    }

    @Test
    fun running() = hosannaComponent(section, "running") {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(0f, 0.08f, 0.64f, 1f).forEach { CircularProgress(it) }
        }
    }

    @Test
    fun statuses() = hosannaComponent(section, "statuses") {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CircularProgress(1f, status = ProgressStatus.Finished)
            CircularProgress(0.4f, status = ProgressStatus.Failed)
            CircularProgress(0.4f, status = ProgressStatus.Paused)
        }
    }
}
