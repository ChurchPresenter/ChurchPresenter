@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

class SourcePropertyFieldsRecomposeTest {

    @Test
    fun `fields handed new values and labels show them`() = runComposeUiTest {
        var generation by mutableIntStateOf(0)
        var modifier by mutableStateOf<Modifier>(Modifier)
        setContent {
            val gen = generation
            MaterialTheme {
                Column {
                    PropertyTextField("Name $gen", "text$gen", modifier) { gen }
                    PropertyCommitTextField("Commit $gen", "commit$gen", modifier) { gen }
                    PropertyIntField("Int $gen", 10 + gen, 0..100, modifier) { gen }
                    PropertyFloatField("Float $gen", 1f + gen, modifier) { gen }
                    PropertySlider("Slider $gen", 0.1f * gen, 0f, 1f) { gen }
                    PropertySliderWithInput("Input $gen", 5f + gen, 0f, 50f, if (gen % 2 == 0) "px" else "") { gen }
                }
            }
        }
        repeat(3) {
            generation++
            modifier = Modifier
            waitForIdle()
        }
        onNodeWithText("text3").assertExists()
        onNodeWithText("commit3").assertExists()
        onNodeWithText("13").assertExists()
        onNodeWithText("4.000").assertExists()
        onNodeWithText("Input 3").assertExists()
    }
}
