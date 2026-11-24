package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.ButtonState
import org.churchpresenter.hosanna.composables.button.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class SearchFieldScreenshotTest {

    private val section = "field/searchField"
    private val width = Modifier.width(340.dp)

    @Composable
    private fun BothSizes(
        query: String = "",
        state: ButtonState = ButtonState.Rest,
        resultCount: String? = null,
        searching: Boolean = false,
        detection: String? = null,
        enabled: Boolean = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(false, true).forEach { compact ->
                SearchField(
                    query, {}, "John 3:16 or words", "Clear", width,
                    interactionSource = remember { frozenSource(state) },
                    compact = compact, shortcut = "⌘F", resultCount = resultCount,
                    searching = searching, detection = detection, enabled = enabled,
                )
            }
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { BothSizes() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { BothSizes(state = ButtonState.Hovered) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { BothSizes(state = ButtonState.Focused) }

    @Test
    fun typing() = hosannaComponent(section, "typing") { BothSizes("grace", resultCount = "12 results") }

    @Test
    fun typingFocused() = hosannaComponent(section, "typing_focused") {
        BothSizes("grace", ButtonState.Focused, resultCount = "12 results")
    }

    @Test
    fun searching() = hosannaComponent(section, "searching") { BothSizes("grace", searching = true) }

    @Test
    fun detection() = hosannaComponent(section, "detection") {
        BothSizes("john 3:16", resultCount = "1 verse", detection = "Reference")
    }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") {
        BothSizes("grace", resultCount = "12 results", enabled = false)
    }
}
