@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.StreamingSettings
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * The tab composed with nothing but the one thing it cannot do without, and again with everything
 * supplied.
 *
 * `LowerThirdTab` takes eleven defaulted parameters and `MainDesktop` passes all of them, so nothing
 * else ever runs the defaults. They are not decoration — `onGoLive` and `onAddToSchedule` default to
 * doing nothing, `queryAtemState` to the *real* UDP client, and `isWindowMaximized` to true — and a
 * tab handed only its settings has to draw either way.
 *
 * The two shapes together are the point: each `$default` branch is taken one way by the first test
 * and the other way by the second.
 */
class LowerThirdTabDefaultsTest {

    @Test
    fun `the tab draws given nothing but its settings`() = runComposeUiTest {
        setContent { MaterialTheme { LowerThirdTab(appSettings = AppSettings()) } }
        waitForIdle()

        // No folder configured is the shape a fresh install is in, and it must say so rather than
        // sit blank — including with the real ATEM client behind `queryAtemState`, which is never
        // reached because no host is set.
        assertTrue(showsContainingText(LowerThirdLabel.NO_PRESETS), renderedText().toString())
    }

    @Test
    fun `every parameter can be given explicitly`() {
        val folder = lottieFolder("Welcome")
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        LowerThirdTab(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = AppSettings(
                                streamingSettings = StreamingSettings(lowerThirdFolder = folder.absolutePath),
                                atemSettings = AtemSettings(host = "10.0.0.9", port = 9910),
                            ),
                            selectedLowerThirdItem = ScheduleItem.LowerThirdItem(
                                id = "item-1",
                                presetId = "welcome",
                                presetLabel = "Welcome",
                                pauseAtFrame = false,
                                pauseDurationMs = 0L,
                            ),
                            selectedLowerThirdItemVersion = 1,
                            isWindowMaximized = false,
                            onSettingsChange = {},
                            onAddToSchedule = { _, _, _, _ -> },
                            onGoLive = { _, _, _, _, _ -> },
                            onOpenLottieGen = { _, _ -> },
                            queryAtemState = { _, _ -> atem() },
                            probeAtemReachable = { _, _ -> true },
                        )
                    }
                }
                waitForIdle()

                assertTrue(showsContainingText("Welcome"), renderedText().toString())
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun atem() = AtemState(
        fps = 25.0,
        videoMode = "1080p25",
        stillSlots = listOf(AtemMediaSlot(index = 0, name = "Welcome", isUsed = true)),
        clipSlots = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames = listOf(600),
    )
}
