@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.viewmodel.BibleSttStatus
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.DetectedReference
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionSource
import org.churchpresenter.app.churchpresenter.viewmodel.DetectionTrack
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

class BibleDetectionPanelTest {

    private class Reports {
        var autoFollow: Boolean? = null
        var level: TextMatchLevel? = null
        var speed: ContinuationSpeed? = null
        val flags = mutableListOf<String>()
        var cleared = 0
        val clicked = mutableListOf<Int>()
        val doubleClicked = mutableListOf<Int>()
    }

    private fun detection(
        label: String = "Genesis 1:1",
        sources: Set<DetectionSource> = setOf(DetectionSource.EXPLICIT),
        tracks: Set<DetectionTrack> = emptySet(),
        verseText: String? = null,
        detectedVersion: String? = null,
    ) = DetectedReference(
        bookIndex = 0, chapter = 1, verseStart = 1, verseEnd = null,
        label = label, key = label, sources = sources, tracks = tracks,
        verseText = verseText, detectedVersion = detectedVersion,
    )

    private fun panel(
        status: BibleSttStatus = BibleSttStatus.LISTENING,
        statusIsError: Boolean = false,
        autoFollowEnabled: Boolean = false,
        textMatchLevel: TextMatchLevel = TextMatchLevel.OFF,
        continuationSpeed: ContinuationSpeed = ContinuationSpeed.BALANCED,
        detections: List<DetectedReference> = emptyList(),
        selectedIndex: Int = 0,
        showFlagButtons: Boolean = false,
        canFlagLive: Boolean = true,
        block: ComposeUiTest.(Reports) -> Unit,
    ) {
        val reports = Reports()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    BibleDetectionPanel(
                        status = status,
                        statusIsError = statusIsError,
                        autoFollowEnabled = autoFollowEnabled,
                        textMatchLevel = textMatchLevel,
                        continuationSpeed = continuationSpeed,
                        detections = detections,
                        selectedIndex = selectedIndex,
                        showFlagButtons = showFlagButtons,
                        canFlagLive = canFlagLive,
                        onAutoFollowChange = { reports.autoFollow = it },
                        onTextMatchLevelChange = { reports.level = it },
                        onContinuationSpeedChange = { reports.speed = it },
                        onFlag = { reports.flags += it },
                        onClearDetections = { reports.cleared++ },
                        onDetectionClick = { reports.clicked += it },
                        onDetectionDoubleClick = { reports.doubleClicked += it },
                    )
                }
            }
            block(reports)
        }
    }

    @Test
    fun `every engine state names itself`() {
        val expected = mapOf(
            BibleSttStatus.ENGINE_UNAVAILABLE to "Engine unavailable",
            BibleSttStatus.NO_BIBLE to "No Bible configured",
            BibleSttStatus.ENGINE_CONNECTING to "Starting engine…",
            BibleSttStatus.ENGINE_STT_DOWN to "Engine lost its connection to the STT server",
            BibleSttStatus.WAITING_FOR_STT to "Running — waiting for STT",
            BibleSttStatus.LISTENING to "Listening…",
            BibleSttStatus.RECONNECTING to "STT disconnected — reconnecting…",
            BibleSttStatus.UNREACHABLE to "Can't reach STT server — retrying…",
            BibleSttStatus.CONNECTING to "Connecting to STT…",
            BibleSttStatus.NOT_CONNECTED to "STT not connected",
        )
        assertEquals(BibleSttStatus.entries.size, expected.size, "a new state needs wording here")
        expected.forEach { (status, text) ->
            panel(status = status) { _ -> assertTrue(showsExactly(text), "$status reads \"$text\"") }
        }
    }

    @Test
    fun `the text match pill names the level it is on and cycles to the next`() {
        val order = listOf(
            TextMatchLevel.OFF to "Off",
            TextMatchLevel.CONSERVATIVE to "Conservative",
            TextMatchLevel.BALANCED to "Balanced",
            TextMatchLevel.AGGRESSIVE to "Aggressive",
        )
        order.forEachIndexed { idx, (level, name) ->
            panel(textMatchLevel = level) { reports ->
                assertTrue(showsExactly("Text match: $name"))
                onNodeWithText("Text match: $name").performClick()
                waitForIdle()
                assertEquals(order[(idx + 1) % order.size].first, reports.level, "$level cycles on")
            }
        }
    }

    @Test
    fun `the verse speed pill names its setting and cycles`() {
        panel(continuationSpeed = ContinuationSpeed.BALANCED) { reports ->
            assertTrue(showsExactly("Next verse speed: Balanced"))
            onNodeWithText("Next verse speed: Balanced").performClick()
            waitForIdle()
            assertEquals(ContinuationSpeed.FAST, reports.speed)
        }
        panel(continuationSpeed = ContinuationSpeed.FAST) { reports ->
            assertTrue(showsExactly("Next verse speed: Fast"))
            onNodeWithText("Next verse speed: Fast").performClick()
            waitForIdle()
            assertEquals(ContinuationSpeed.BALANCED, reports.speed, "it wraps")
        }
    }

    @Test
    fun `auto-follow reports the state it is being switched to`() {
        panel(autoFollowEnabled = false) { reports ->
            onNodeWithText("Auto-follow").performClick()
            waitForIdle()
            assertEquals(true, reports.autoFollow)
        }
        panel(autoFollowEnabled = true) { reports ->
            onNodeWithText("Auto-follow").performClick()
            waitForIdle()
            assertEquals(false, reports.autoFollow)
        }
    }

    @Test
    fun `the flag pills belong to help mode`() {
        panel(showFlagButtons = false) { _ ->
            assertFalse(showsExactly("Wrong passage"))
            assertFalse(showsExactly("Premature"))
            assertFalse(showsExactly("Missed passage"))
        }
        panel(showFlagButtons = true) { _ ->
            assertTrue(showsExactly("Wrong passage"))
            assertTrue(showsExactly("Premature"))
            assertTrue(showsExactly("Missed passage"))
        }
    }

    @Test
    fun `each flag reports its own kind`() =
        panel(showFlagButtons = true) { reports ->
            onNodeWithText("Wrong passage").performClick()
            onNodeWithText("Premature").performClick()
            onNodeWithText("Missed passage").performClick()
            waitForIdle()
            assertEquals(listOf("wrong_passage", "premature", "missed_passage"), reports.flags)
        }

    @Test
    fun `the live flags do nothing with nothing on screen, but missed still reports`() =
        panel(showFlagButtons = true, canFlagLive = false) { reports ->
            onNodeWithText("Wrong passage").performClick()
            onNodeWithText("Premature").performClick()
            waitForIdle()
            assertTrue(reports.flags.isEmpty(), "both describe what went out")

            onNodeWithText("Missed passage").performClick()
            waitForIdle()
            assertEquals(listOf("missed_passage"), reports.flags, "this one needs nothing on screen")
        }

    @Test
    fun `a detection is listed with its reference and verse`() =
        panel(detections = listOf(detection(verseText = "In the beginning God created."))) { _ ->
            assertTrue(showsContainingText("Genesis 1:1"))
            assertTrue(showsContainingText("In the beginning God created."))
        }

    @Test
    fun `every way the engine matched has its own marker`() {
        val expected = mapOf(
            DetectionSource.EXPLICIT to "Spoken reference",
            DetectionSource.REVERSE to "Matched by text",
            DetectionSource.CONTINUATION to "Following along",
            DetectionSource.CHAPTER_SCAN to "Found in current chapter",
            DetectionSource.CHAPTER_HISTORY to "Matched an earlier chapter",
        )
        assertEquals(DetectionSource.entries.size, expected.size, "a new source needs a marker")
        expected.forEach { (source, name) ->
            panel(detections = listOf(detection(sources = setOf(source)))) { _ ->
                assertTrue(hasActionButton(name), "$source is marked \"$name\"")
            }
        }
    }

    @Test
    fun `a track marker appears only for the track that corroborated`() {
        panel(detections = listOf(detection(tracks = setOf(DetectionTrack.TRANSCRIPTION)))) { _ ->
            assertTrue(hasActionButton("Heard in transcription"))
            assertFalse(hasActionButton("Heard in translation"))
        }
        panel(detections = listOf(detection(tracks = DetectionTrack.entries.toSet()))) { _ ->
            assertTrue(hasActionButton("Heard in transcription"))
            assertTrue(hasActionButton("Heard in translation"))
        }
        panel(detections = listOf(detection())) { _ ->
            assertFalse(hasActionButton("Heard in transcription"))
            assertFalse(hasActionButton("Heard in translation"))
        }
    }

    @Test
    fun `the translation being read is shown when the engine could tell`() {
        panel(detections = listOf(detection(detectedVersion = "KJV"))) { _ ->
            assertTrue(showsExactly("KJV"))
        }
        panel(detections = listOf(detection())) { _ ->
            assertFalse(showsExactly("KJV"))
        }
    }

    @Test
    fun `clearing is offered only once there is something to clear`() {
        panel(detections = emptyList()) { _ ->
            assertFalse(hasActionButton("Clear detected references"))
        }
        panel(detections = listOf(detection())) { reports ->
            onNodeWithContentDescription("Clear detected references").performClick()
            waitForIdle()
            assertEquals(1, reports.cleared)
        }
    }

    @Test
    fun `a row reports its own index, and a double-click is not a click`() =
        panel(detections = listOf(detection("Genesis 1:1"), detection("John 3:16"))) { reports ->
            onNodeWithText("John 3:16", substring = true).performClick()
            waitForIdle()
            assertEquals(listOf(1), reports.clicked)
            assertTrue(reports.doubleClicked.isEmpty())
        }
}
