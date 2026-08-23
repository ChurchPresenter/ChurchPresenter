@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.viewmodel.TextMatchLevel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.viewmodel.onEngineScripture
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * The Bible tab's auto-follow panel — the row that appears once speech-to-text is connected.
 *
 * It is deliberately hidden until then: at first launch the tab stays clean with just navigation and
 * verse display, so "is the panel there at all" is itself a rule worth pinning. Reaching the shown
 * state needs a connected [STTManager], which no test could produce until `applyConnected()` existed
 * — it is the same transition the socket's own callback runs, so no socket is involved here.
 *
 * The three pill buttons each do two things that must stay in step: set the live value on the view
 * model (so the running engine changes behaviour now) and hand the host a settings transform (so the
 * choice survives a restart). Every test below asserts both, since a click that updates one and not
 * the other looks correct on screen and is forgotten on the next launch.
 *
 * Detections are seeded through `onEngineScripture`, the same entry point the engine's messages
 * arrive on, so no engine client is needed either.
 *
 * Not covered here: the Help Dev flag pills (`helpDevMode`), which write training-data files.
 */
class BibleTabAutoFollowTest {

    private val managers = mutableListOf<STTManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
    }

    /** A manager already in the connected state, without a socket. */
    private fun connectedStt() = STTManager().also {
        managers.add(it)
        it.applyConnected()
    }

    /**
     * A detection as the engine's `scripture` message delivers it.
     *
     * Verse 17 by default, not 16: the search box's own placeholder reads "Reference or text — e.g.
     * John 3:16, mat 1, or a word", so an assertion looking for "John 3:16" anywhere on screen
     * passes whether the detection row is drawn or not.
     */
    private fun BibleViewModel.detect(verseStart: Int = 17, verseEnd: Int? = null) = onEngineScripture(
        bookId = 43,
        chapter = 3,
        verseStart = verseStart,
        verseEnd = verseEnd,
        verseText = "For God so loved the world.",
        matchType = "explicit",
    )

    private fun engine(
        enabled: Boolean = true,
        autoFollow: Boolean = false,
        textMatchLevel: String = "off",
        continuationSpeed: String = "balanced",
    ) = BibleEngineSettings(
        enabled = enabled,
        autoFollow = autoFollow,
        textMatchLevel = textMatchLevel,
        continuationSpeed = continuationSpeed,
    )

    // ── Whether the panel is there at all ───────────────────────────────────────────────────────

    @Test
    fun `with no stt connection the panel is not drawn`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }) { _, _ ->
            assertFalse(showsExactly(AutoFollowLabel.AUTO_FOLLOW), renderedText().toString())
        }
    }

    @Test
    fun `once stt is connected the panel appears`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
        ) { _, _ ->
            assertTrue(showsExactly(AutoFollowLabel.AUTO_FOLLOW), renderedText().toString())
        }
    }

    @Test
    fun `with the engine disabled the panel stays hidden even on a live connection`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(enabled = false)) },
            stt = connectedStt(),
        ) { _, _ ->
            assertFalse(showsExactly(AutoFollowLabel.AUTO_FOLLOW), renderedText().toString())
        }
    }

    // ── Status line ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `with no engine client yet the status says the engine is starting`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
        ) { _, _ ->
            assertTrue(showsExactly(AutoFollowLabel.STARTING_ENGINE), renderedText().toString())
        }
    }

    @Test
    fun `with no bible configured the status says so instead`() {
        bibleTab(
            content = bibleFixture,
            settings = {
                it.copy(
                    bibleEngineSettings = engine(),
                    bibleSettings = it.bibleSettings.copy(primaryBible = "", secondaryBible = ""),
                )
            },
            stt = connectedStt(),
        ) { _, _ ->
            assertTrue(showsExactly(AutoFollowLabel.NO_BIBLE), renderedText().toString())
        }
    }

    // ── Auto-follow ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `turning auto-follow on sets it live and persists it`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(autoFollow = false)) },
            stt = connectedStt(),
        ) { vm, reports ->
            assertFalse(vm.autoFollowEnabled.value)

            onNodeWithText(AutoFollowLabel.AUTO_FOLLOW).performClick()

            assertTrue(vm.autoFollowEnabled.value, "the running engine has to change behaviour now")
            assertEquals(
                true,
                reports.settingsAfterChange?.bibleEngineSettings?.autoFollow,
                "and the choice has to survive a restart",
            )
        }
    }

    @Test
    fun `turning auto-follow off does both halves too`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(autoFollow = true)) },
            stt = connectedStt(),
        ) { vm, reports ->
            vm.setAutoFollow(true)
            waitForIdle()

            onNodeWithText(AutoFollowLabel.AUTO_FOLLOW).performClick()

            assertFalse(vm.autoFollowEnabled.value)
            assertEquals(false, reports.settingsAfterChange?.bibleEngineSettings?.autoFollow)
        }
    }

    // ── Text match level ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the match button names the level it is on`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(textMatchLevel = "balanced")) },
            stt = connectedStt(),
        ) { _, _ ->
            assertTrue(showsExactly("Text match: Balanced"), renderedText().toString())
        }
    }

    @Test
    fun `the match button cycles Off to Conservative to Balanced to Aggressive and back`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(textMatchLevel = "off")) },
            stt = connectedStt(),
        ) { vm, reports ->
            val expected = listOf(
                TextMatchLevel.CONSERVATIVE to "Text match: Conservative",
                TextMatchLevel.BALANCED to "Text match: Balanced",
                TextMatchLevel.AGGRESSIVE to "Text match: Aggressive",
                TextMatchLevel.OFF to "Text match: Off",
            )
            expected.forEach { (level, label) ->
                // The button is addressed by the label it currently shows, which the last click set.
                onNodeWithText(matchLabel(vm.textMatchLevel.value)).performClick()

                assertEquals(level, vm.textMatchLevel.value)
                assertEquals(
                    level.name.lowercase(),
                    reports.settingsAfterChange?.bibleEngineSettings?.textMatchLevel,
                )
                assertTrue(showsExactly(label), renderedText().toString())
            }
        }
    }

    // ── Verse speed ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the verse speed button toggles between Balanced and Fast`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(continuationSpeed = "balanced")) },
            stt = connectedStt(),
        ) { vm, reports ->
            assertEquals(ContinuationSpeed.BALANCED, vm.continuationSpeed.value)

            onNodeWithText(AutoFollowLabel.SPEED_BALANCED).performClick()

            assertEquals(ContinuationSpeed.FAST, vm.continuationSpeed.value)
            assertEquals("fast", reports.settingsAfterChange?.bibleEngineSettings?.continuationSpeed)
            assertTrue(showsExactly(AutoFollowLabel.SPEED_FAST), renderedText().toString())

            onNodeWithText(AutoFollowLabel.SPEED_FAST).performClick()

            assertEquals(ContinuationSpeed.BALANCED, vm.continuationSpeed.value)
            assertEquals("balanced", reports.settingsAfterChange?.bibleEngineSettings?.continuationSpeed)
        }
    }

    // ── Detected references ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a detection from the engine is listed`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
        ) { vm, _ ->
            vm.detect()
            waitForIdle()

            assertTrue(showsContainingText("John 3:17"), renderedText().toString())
        }
    }

    @Test
    fun `the newest detection leads the list`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
        ) { vm, _ ->
            vm.detect(verseStart = 17)
            vm.detect(verseStart = 18)
            waitForIdle()

            assertEquals(
                18,
                vm.detectedReferences.value.first().verseStart,
                "the list the panel draws is newest-first",
            )
            assertTrue(showsContainingText("John 3:18"), renderedText().toString())
        }
    }

    @Test
    fun `the clear button appears with the first detection and empties the list`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
        ) { vm, _ ->
            // Nothing to clear yet, so no button — it used to sit there doing nothing.
            assertFalse(hasActionButton(AutoFollowLabel.CLEAR_DETECTED))

            vm.detect()
            waitForIdle()
            assertTrue(hasActionButton(AutoFollowLabel.CLEAR_DETECTED))

            actionButton(AutoFollowLabel.CLEAR_DETECTED).performClick()

            assertTrue(vm.detectedReferences.value.isEmpty())
            assertFalse(showsContainingText("John 3:17"), renderedText().toString())
            assertFalse(hasActionButton(AutoFollowLabel.CLEAR_DETECTED))
        }
    }

    @Test
    fun `a range detection is listed as a range`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
        ) { vm, _ ->
            vm.detect(verseStart = 16, verseEnd = 18)
            waitForIdle()

            assertTrue(showsContainingText("John 3:16-18"), renderedText().toString())
        }
    }

    private fun matchLabel(level: TextMatchLevel) = "Text match: " + when (level) {
        TextMatchLevel.OFF -> "Off"
        TextMatchLevel.CONSERVATIVE -> "Conservative"
        TextMatchLevel.BALANCED -> "Balanced"
        TextMatchLevel.AGGRESSIVE -> "Aggressive"
    }

    private object AutoFollowLabel {
        const val AUTO_FOLLOW = "Auto-follow"
        const val CLEAR_DETECTED = "Clear detected references"
        const val STARTING_ENGINE = "Starting engine…"
        const val NO_BIBLE = "No Bible configured"
        const val SPEED_BALANCED = "Next verse speed: Balanced"
        const val SPEED_FAST = "Next verse speed: Fast"
    }
}
