package org.churchpresenter.sharedui.composables

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecentChipTest {

    private class Clicks {
        var opened = 0
        var pinToggles = 0
    }

    private fun chip(
        name: String = "Easter.pptx",
        isActive: Boolean = false,
        isPinned: Boolean = false,
        block: ComposeUiTest.(Clicks) -> Unit,
    ) = runComposeUiTest {
        val clicks = Clicks()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                RecentChip(
                    name = name,
                    isActive = isActive,
                    isPinned = isPinned,
                    onOpen = { clicks.opened++ },
                    onTogglePin = { clicks.pinToggles++ },
                )
            }
        }
        block(clicks)
    }

    @Test
    fun `the chip shows the file's name`() = chip { onNodeWithText("Easter.pptx").assertExists() }

    @Test
    fun `an unpinned chip offers to pin`() = chip(isPinned = false) {
        onNodeWithContentDescription("Pin").assertExists()
    }

    @Test
    fun `a pinned chip offers to unpin`() = chip(isPinned = true) {
        onNodeWithContentDescription("Unpin").assertExists()
    }

    @Test
    fun `clicking the name opens the file`() = chip { clicks ->
        onNodeWithText("Easter.pptx").performClick()
        waitForIdle()
        assertEquals(1, clicks.opened)
        assertEquals(0, clicks.pinToggles)
    }

    @Test
    fun `clicking the star pins without opening`() = chip { clicks ->
        onNodeWithContentDescription("Pin").performClick()
        waitForIdle()
        assertEquals(1, clicks.pinToggles)
        assertEquals(0, clicks.opened)
    }

    @Test
    fun `the open file's chip is still a chip that opens`() = chip(isActive = true, isPinned = true) { clicks ->
        onNodeWithText("Easter.pptx").performClick()
        waitForIdle()
        assertEquals(1, clicks.opened)
    }

    @Test
    fun `a long name is still clickable through its tooltip wrapper`() =
        chip(name = "Sunday Morning Service 2026-09-21 Full Recording Final.mp4") { clicks ->
            val node = onNodeWithText("Sunday Morning Service 2026-09-21 Full Recording Final.mp4")
            node.performMouseInput { moveTo(center) }
            node.performClick()
            waitForIdle()
            assertEquals(1, clicks.opened)
        }

    @Test
    fun `a chip may be given its own height`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                RecentChip("Psalms", isActive = false, isPinned = false, onOpen = {}, onTogglePin = {}, height = 40.dp)
            }
        }
        onNodeWithText("Psalms").assertExists()
    }
}
