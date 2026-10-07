package org.churchpresenter.liveoutput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The lower-third cue tells the manager how large its output is, so a large one can get frames of its size. */
@OptIn(ExperimentalTestApi::class)
class LowerThirdCueTest {

    private fun surface(kind: OutputSurfaceKind, manager: PresenterManager) = OutputSurface(
        kind = kind,
        profile = OutputProfile(),
        appSettings = AppSettings(),
        presenterManager = manager,
        outputRole = Constants.OUTPUT_ROLE_NORMAL,
        showBg = false,
    )

    @Test
    fun `an output holds its pixel size while it draws the lower third, and lets go when it stops`() {
        val manager = PresenterManager()
        val held = manager.lowerThird.outputFrames
        var shown by mutableStateOf(true)
        runComposeUiTest {
            setContent {
                val side = with(LocalDensity.current) { SIDE_PX.toDp() }
                if (shown) {
                    Box(Modifier.size(side * 2, side)) { LowerThirdCue(surface(OutputSurfaceKind.OFFSCREEN, manager)) }
                }
            }
            waitForIdle()
            assertEquals(setOf(IntSize(SIDE_PX * 2, SIDE_PX)), held.heldSizes)

            shown = false
            waitForIdle()
            assertTrue(held.heldSizes.isEmpty())
        }
    }

    @Test
    fun `a preview tile holds no size, it draws the desktop frames`() {
        val manager = PresenterManager()
        runComposeUiTest {
            setContent { Box(Modifier.size(SIDE_DP)) { LowerThirdCue(surface(OutputSurfaceKind.PREVIEW, manager)) } }
            waitForIdle()
            assertTrue(manager.lowerThird.outputFrames.heldSizes.isEmpty())
        }
    }

    private companion object {
        const val SIDE_PX = 120
        val SIDE_DP = 60.dp
    }
}
