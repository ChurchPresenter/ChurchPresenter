@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.helper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class GuideSpotlightHostTest {

    private val target = GuideTarget("test.panel")
    private val session = GuideSession()

    private fun ComposeUiTest.host(covered: Boolean = false) {
        // The ring breathes forever: step the clock by hand, never wait for idle.
        mainClock.autoAdvance = false
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalGuideSession provides session) {
                    GuideSpotlightHost(Modifier.size(300.dp)) {
                        Box(Modifier.offset(100.dp, 100.dp).size(100.dp).guideTarget(target))
                        if (covered) {
                            // Something laid over the control that takes every press for itself.
                            Box(
                                Modifier.offset(100.dp, 100.dp).size(100.dp).pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        settle()
    }

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }

    private fun ComposeUiTest.clickAt(xDp: Float, yDp: Float) {
        val px = with(density) { Offset(xDp.dp.toPx(), yDp.dp.toPx()) }
        onRoot().performMouseInput { click(px) }
        settle()
    }

    @Test
    fun `a press on the ringed control counts`() = runComposeUiTest {
        session.activeTarget = target
        host()
        clickAt(150f, 150f)
        assertEquals(1, session.activePresses)
    }

    @Test
    fun `a press on the ring just outside the control counts`() = runComposeUiTest {
        session.activeTarget = target
        host()
        clickAt(97f, 150f)
        assertEquals(1, session.activePresses)
    }

    @Test
    fun `a press on whatever covers the control still counts`() = runComposeUiTest {
        session.activeTarget = target
        host(covered = true)
        clickAt(150f, 150f)
        assertEquals(1, session.activePresses)
    }

    @Test
    fun `a press elsewhere, or with nothing ringed, does not count`() = runComposeUiTest {
        session.activeTarget = target
        host()
        clickAt(20f, 20f)
        assertEquals(0, session.activePresses)
        session.activeTarget = null
        settle()
        clickAt(150f, 150f)
        assertEquals(0, session.activePresses)
    }
}
