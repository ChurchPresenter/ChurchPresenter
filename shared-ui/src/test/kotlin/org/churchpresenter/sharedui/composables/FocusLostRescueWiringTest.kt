package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FocusLostRescueWiringTest {

    private val bannerText = "Click to restore keyboard control"

    @Test
    fun `the rescue follows whether its window is focused`() = runComposeUiTest {
        var state: FocusLostRescueState? = null
        var windowFocused: Boolean? = null
        setContent {
            val requester = remember { FocusRequester() }
            windowFocused = LocalWindowInfo.current.isWindowFocused
            state = rememberFocusLostRescue(hostWindow = null, focusRequester = requester, active = false)
            Box(Modifier.size(10.dp).focusRequester(requester).focusable())
        }
        waitForIdle()
        assertEquals(windowFocused, state?.windowFocused)
    }

    @Test
    fun `an inactive rescue never shows its banner`() = runComposeUiTest {
        setContent {
            val requester = remember { FocusRequester() }
            val state = rememberFocusLostRescue(hostWindow = null, focusRequester = requester, active = false)
            Column {
                Box(Modifier.size(10.dp).focusRequester(requester).focusable())
                FocusLostBanner(state, bannerText)
            }
        }
        onNodeWithText(bannerText).assertDoesNotExist()
    }

    @Test
    fun `an active rescue in a focused window takes the tab's focus back on its own`() = runComposeUiTest {
        var state: FocusLostRescueState? = null
        setContent {
            val requester = remember { FocusRequester() }
            val rescue = rememberFocusLostRescue(hostWindow = null, focusRequester = requester)
            state = rescue
            Box(
                Modifier.size(10.dp)
                    .focusRequester(requester)
                    .onFocusChanged { rescue.onFocusChanged(it.hasFocus) }
                    .focusable(),
            )
        }
        waitForIdle()
        if (state?.windowFocused == true) {
            waitUntil(timeoutMillis = 5_000) { state?.tabHasFocus == true }
            assertFalse(state!!.bannerVisible)
        } else {
            assertTrue(state!!.bannerVisible)
        }
    }

    @Test
    fun `clicking the banner gives the tab its focus back and hides it`() = runComposeUiTest {
        var state: FocusLostRescueState? = null
        setContent {
            MaterialTheme {
                val requester = remember { FocusRequester() }
                val scope = rememberCoroutineScope()
                val rescue = remember { FocusLostRescueState(null, requester, scope) }
                state = rescue
                Column {
                    FocusLostBanner(rescue, bannerText)
                    Box(
                        Modifier.size(10.dp)
                            .focusRequester(requester)
                            .onFocusChanged { rescue.onFocusChanged(it.hasFocus) }
                            .focusable(),
                    )
                }
            }
        }
        onNodeWithText(bannerText).performClick()
        waitForIdle()
        assertTrue(state!!.tabHasFocus)
        onNodeWithText(bannerText).assertDoesNotExist()
    }

    @Test
    fun `the hint banner reports its click`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme { FocusHintBanner(text = "Leave the search box", onClick = { clicks++ }, topPadding = 0.dp) }
        }
        onNodeWithText("Leave the search box").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a press inside a hooked tab still reaches what was pressed`() = runComposeUiTest {
        var clicks = 0
        setContent {
            val requester = remember { FocusRequester() }
            val scope = rememberCoroutineScope()
            val rescue = remember { FocusLostRescueState(null, requester, scope) }
            Box(Modifier.size(100.dp).focusRescuePressHook(rescue)) {
                Text("Content", Modifier.testTag("content").clickable { clicks++ })
            }
        }
        onNodeWithTag("content").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }
}
