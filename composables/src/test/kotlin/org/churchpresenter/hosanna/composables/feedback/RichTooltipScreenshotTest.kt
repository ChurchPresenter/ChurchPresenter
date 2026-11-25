package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RichTooltipScreenshotTest {

    private val section = "feedback/richTooltip"
    private val body = "Bitfocus Companion connects to this port to control ChurchPresenter from a Stream Deck. " +
        "Use the same number in Companion’s connection settings."

    private fun ComposeUiTest.open() {
        onNodeWithContentDescription("Help").performMouseInput { click(center) }
        settle()
    }

    @Composable
    private fun Help(action: String?, state: ButtonState = ButtonState.Rest) {
        Box(Modifier.padding(start = 140.dp)) {
            RichTooltip(
                "Companion port", body, actionText = action,
                actionInteractionSource = remember { frozenSource(state) },
            ) {
                IconButton(
                    testIcon(), "Help", {},
                    size = IconButtonSize.Small, hierarchy = ButtonHierarchy.Tertiary, intent = ButtonIntent.Neutral,
                )
            }
        }
    }

    @Test
    fun withAction() = hosannaPopup(section, "withAction", drive = { open() }) { Help("How to set up Companion") }

    @Test
    fun actionHovered() = hosannaPopup(section, "actionHovered", drive = { open() }) {
        Help("How to set up Companion", ButtonState.Hovered)
    }

    @Test
    fun actionFocused() = hosannaPopup(section, "actionFocused", drive = { open() }) {
        Help("How to set up Companion", ButtonState.Focused)
    }

    @Test
    fun bodyOnly() = hosannaPopup(section, "bodyOnly", drive = { open() }) { Help(null) }
}
