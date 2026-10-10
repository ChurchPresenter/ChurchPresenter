@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

class PositionGroupDirectTest {

    @Test
    fun `a folded Position group sums up where the block sits and its margins`() = runComposeUiTest {
        var alignment by mutableStateOf<String?>(Constants.TOP)
        var margins by mutableStateOf(Margins(40, 40, 40, 40))
        var folded by mutableStateOf(setOf("position"))
        setContent {
            MaterialTheme {
                val folds = FoldedGroups(folded, mutableSetOf()) { folded = it }
                CompositionLocalProvider(LocalFoldedGroups provides folds) {
                    Column {
                        PositionGroup(
                            verticalAlignment = alignment,
                            onVerticalAlignment = { alignment = it },
                            margins = margins,
                            onMargins = { margins = it },
                            region = ContentRegion(),
                            onRegion = {},
                            reset = { Text("reset key") },
                            extraAdvanced = { Text("element placement") },
                            paths = PositionPaths(vertical = listOf("v"), margins = listOf("m"), region = listOf("r")),
                            room = MarginRoom.of(1920, 1080, 30),
                        )
                    }
                }
            }
        }
        onNodeWithText("Top · margins 40").assertExists()
        alignment = Constants.BOTTOM
        margins = Margins(10, 20, 30, 40)
        waitForIdle()
        onNodeWithText("Bottom · margins 10 / 20 / 30 / 40").assertExists()
        alignment = Constants.MIDDLE
        waitForIdle()
        onNodeWithText("Middle · margins 10 / 20 / 30 / 40").assertExists()
        alignment = null
        waitForIdle()
        onNodeWithText("margins 10 / 20 / 30 / 40").assertExists()
        folded = emptySet()
        waitForIdle()
        onNodeWithText("element placement").assertExists()
        onNodeWithText("reset key").assertExists()
    }
}
