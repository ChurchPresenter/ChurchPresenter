package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [LoopingVideoBackground] needs a real, working native VLC install to get past its
 * `!isVlcAvailable` guard — which, like every other VLC-backed composable in this codebase
 * (see [SceneSourceRendererTest], [VideoPlayerTest]), varies by machine and is not exercised here.
 * Only its two guard clauses that return *before* that check — a blank path and a nonexistent
 * file — are deterministic on every machine, so those are what this class covers.
 */
@OptIn(ExperimentalTestApi::class)
class LoopingVideoBackgroundTest {

    @Test
    fun `a blank videoPath renders nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LoopingVideoBackground(videoPath = "", modifier = Modifier.testTag("bg"))
            }
        }
        onNodeWithTag("bg").assertDoesNotExist()
    }

    @Test
    fun `a nonexistent file renders nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LoopingVideoBackground(videoPath = "/nonexistent/cp-test-video.mp4", modifier = Modifier.testTag("bg"))
            }
        }
        onNodeWithTag("bg").assertDoesNotExist()
    }
}
