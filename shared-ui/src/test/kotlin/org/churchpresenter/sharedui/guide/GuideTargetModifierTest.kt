@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuideTargetModifierTest {

    private val button = GuideTarget("test.button")
    private val other = GuideTarget("test.other")

    @Test
    fun `a tagged control reports where it is and counts presses only while pointed at`() = runComposeUiTest {
        val registry = GuideTargetRegistry()
        val session = GuideSession()
        var clicks = 0
        setContent {
            CompositionLocalProvider(
                LocalGuideTargetRegistry provides registry,
                LocalGuideSession provides session,
                LocalGuideRingColor provides Color.Red,
            ) {
                Box(Modifier.size(40.dp).guideTarget(button).clickable { clicks++ }.testTag("button"))
            }
        }
        waitForIdle()
        assertNotNull(registry.boundsOf(button))

        onNodeWithTag("button").performClick()
        assertEquals(0, session.activePresses)

        session.activeTarget = button
        waitForIdle()
        onNodeWithTag("button").performClick()
        waitForIdle()
        assertEquals(1, session.activePresses)
        // The press is observed, never consumed: the control still gets its click.
        assertEquals(2, clicks)
    }

    @Test
    fun `a control that changes its target or leaves is forgotten`() = runComposeUiTest {
        val registry = GuideTargetRegistry()
        var target by mutableStateOf(button)
        var shown by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(
                LocalGuideTargetRegistry provides registry,
                LocalGuideSession provides GuideSession(),
            ) {
                if (shown) Box(Modifier.size(20.dp).guideTarget(target))
            }
        }
        waitForIdle()
        assertNotNull(registry.boundsOf(button))

        target = other
        waitForIdle()
        assertNull(registry.boundsOf(button))
        assertNotNull(registry.boundsOf(other))

        shown = false
        waitForIdle()
        assertNull(registry.boundsOf(other))
    }

    @Test
    fun `outside a spotlight host the tag does nothing`() = runComposeUiTest {
        setContent { Box(Modifier.size(20.dp).guideTarget(button).testTag("plain")) }
        onNodeWithTag("plain").performClick()
        waitForIdle()
    }
}
