package org.churchpresenter.bibletab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleDetectionPanelStateChangesTest {

    private fun detection(label: String, sources: Set<DetectionSource>, tracks: Set<DetectionTrack> = emptySet()) =
        DetectedReference(
            bookIndex = 0, chapter = 1, verseStart = 1, verseEnd = null, label = label, key = label,
            sources = sources, tracks = tracks, verseText = "text of $label", detectedVersion = "KJV",
        )

    private class Inputs {
        var status by mutableStateOf(BibleSttStatus.LISTENING)
        var error by mutableStateOf(false)
        var autoFollow by mutableStateOf(false)
        var level by mutableStateOf(TextMatchLevel.OFF)
        var speed by mutableStateOf(ContinuationSpeed.BALANCED)
        var detections by mutableStateOf<List<DetectedReference>>(emptyList())
        var selected by mutableStateOf(0)
        var flags by mutableStateOf(true)
        var canFlag by mutableStateOf(false)
        val events = mutableListOf<String>()
    }

    private fun ComposeUiTest.panel(inputs: Inputs) {
        setContent {
            MaterialTheme {
                BibleDetectionPanel(
                    status = inputs.status,
                    statusIsError = inputs.error,
                    autoFollowEnabled = inputs.autoFollow,
                    textMatchLevel = inputs.level,
                    continuationSpeed = inputs.speed,
                    detections = inputs.detections,
                    selectedIndex = inputs.selected,
                    showFlagButtons = inputs.flags,
                    canFlagLive = inputs.canFlag,
                    onAutoFollowChange = { inputs.events += "follow $it" },
                    onTextMatchLevelChange = { inputs.events += "level $it" },
                    onContinuationSpeedChange = { inputs.events += "speed $it" },
                    onFlag = { inputs.events += "flag $it" },
                    onClearDetections = { inputs.events += "clear" },
                    onDetectionClick = { inputs.events += "click $it" },
                    onDetectionDoubleClick = { inputs.events += "double $it" },
                )
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun ComposeUiTest.click(text: String) {
        onAllNodes(hasText(text, substring = true), useUnmergedTree = true)[0].performClick()
        waitForIdle()
    }

    @Test
    fun `every engine setting cycles from each value it can hold`() = runComposeUiTest {
        val inputs = Inputs()
        panel(inputs)

        TextMatchLevel.entries.forEach { level ->
            inputs.level = level
            waitForIdle()
            click("Text match")
        }
        ContinuationSpeed.entries.forEach { speed ->
            inputs.speed = speed
            waitForIdle()
            click("Next verse speed")
        }
        listOf(true, false).forEach { on ->
            inputs.autoFollow = on
            waitForIdle()
            click("Auto-follow")
        }

        assertEquals(
            listOf(
                "level CONSERVATIVE", "level BALANCED", "level AGGRESSIVE", "level OFF",
                "speed FAST", "speed BALANCED", "follow false", "follow true",
            ),
            inputs.events,
        )
    }

    @Test
    fun `every status reads out, in error colours or not`() = runComposeUiTest {
        val inputs = Inputs()
        panel(inputs)

        BibleSttStatus.entries.forEach { status ->
            inputs.status = status
            inputs.error = !inputs.error
            waitForIdle()
        }
        assertTrue(has("Auto-follow"))
    }

    @Test
    fun `a disabled flag explains why, and comes alive once a verse is live`() = runComposeUiTest {
        val inputs = Inputs()
        panel(inputs)
        click("Wrong passage")
        assertTrue(inputs.events.isEmpty(), inputs.events.toString())

        inputs.canFlag = true
        waitForIdle()
        click("Wrong passage")
        assertEquals(listOf("flag wrong_passage"), inputs.events)

        inputs.flags = false
        waitForIdle()
        assertTrue(!has("Wrong passage"))
    }

    @Test
    fun `each detection source and track has its own badge, and rows follow the selection`() = runComposeUiTest {
        val inputs = Inputs()
        panel(inputs)
        inputs.detections = DetectionSource.entries.mapIndexed { i, source ->
            detection("Ref $i", setOf(source), if (i == 0) DetectionTrack.entries.toSet() else emptySet())
        }
        waitForIdle()

        listOf(
            "Spoken reference", "Matched by text", "Following along", "Found in current chapter",
            "Matched an earlier chapter", "Heard in transcription", "Heard in translation",
        ).forEach { desc ->
            assertTrue(
                onAllNodes(hasContentDescription(desc), useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
                desc,
            )
        }

        inputs.selected = 2
        waitForIdle()
        click("Ref 3")
        onAllNodes(hasContentDescription("Clear detected references"), useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertTrue("click 3" in inputs.events && "clear" in inputs.events, inputs.events.toString())

        inputs.detections = emptyList()
        waitForIdle()
        assertTrue(
            onAllNodes(hasContentDescription("Clear detected references"), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
        )
    }
}
