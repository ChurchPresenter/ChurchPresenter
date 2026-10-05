package org.churchpresenter.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MetronomeDotTest {

    @Test
    fun `the dot flashes while active and goes dark when stopped`() = runComposeUiTest {
        var active by mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent { MetronomeDot(bpm = 120, active = active, modifier = Modifier.testTag("dot")) }
        mainClock.advanceTimeBy(1_100)
        onNodeWithTag("dot").assertExists()
        active = false
        mainClock.advanceTimeBy(100)
        onNodeWithTag("dot").assertExists()
    }

    @Test
    fun `no tempo never flashes`() = runComposeUiTest {
        setContent { MetronomeDot(bpm = 0, active = true, modifier = Modifier.testTag("dot")) }
        onNodeWithTag("dot").assertExists()
    }
}
