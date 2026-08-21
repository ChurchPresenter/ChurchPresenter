@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.app.churchpresenter.data.settings.BibleEngineSettings
import org.churchpresenter.app.churchpresenter.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the training-flag pills say when they cannot be pressed.
 *
 * `FlagPillButton`'s own doc states the rule and the incident behind it: pressing one has no visible
 * effect (it appends a line to a training log), so a disabled pill drawn in the muted palette was
 * indistinguishable from a working one — "an operator logged the same flag seven times in under two
 * seconds trying to make it respond". `enabled = false` therefore keeps the muted look **and hands
 * the tooltip a different string, explaining why**. Nothing verified that the different string ever
 * reached the screen.
 *
 * Two of the three pills describe *what went live*, so they are disabled with an empty output. The
 * third reports that the engine found nothing and is deliberately always available — a distinction
 * worth pinning, because making all three consistent is the obvious tidy-up and would break it.
 *
 * **Not pinned, and worth stating:** that the missed-passage pill is *always enabled* while its two
 * neighbours are gated. Being enabled has no observable difference here — the pill carries no
 * `disabledTooltip`, so it falls back to its own hint either way, and its click only appends to a
 * training log written through `TrainingDataLogger`, which resolves its path once per JVM and so
 * cannot be redirected from this suite. Gating it in the source fails none of these tests; only the
 * two neighbours' disabled state is covered.
 *
 * **The tooltip body only composes on hover**, so each test hovers with `performMouseInput` and
 * asserts **differentially** — a count before against a count after. The enabled tooltip repeats the
 * pill's own hint text, which is already on screen, so `assertExists` would pass without any tooltip
 * being drawn at all.
 */
class BibleTabFlagPillTooltipTest {

    private companion object {
        const val WRONG_LABEL = "Wrong passage"
        const val MISSED_LABEL = "Missed passage"
        const val NEEDS_LIVE =
            "Available once a verse is live — this flag describes the passage on screen"
        const val WRONG_HINT =
            "Flag the currently live passage as wrong, for the BLE developer to review later"
        const val MISSED_HINT =
            "Flag that the engine missed a passage that was actually being read or cited just now"
    }

    private val managers = mutableListOf<STTManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
    }

    /**
     * The panel holding the pills is gated on `engineSettings.enabled && sttConnected`, and the
     * pills themselves on `helpDevMode` inside it — three conditions, following
     * `BibleTabDetectionBadgesTest`.
     */
    private fun connectedStt() = STTManager().also { managers.add(it); it.applyConnected() }

    private fun engine(helpDev: Boolean = true) =
        BibleEngineSettings(enabled = true, helpDevMode = helpDev)

    private fun liveVerse() = SelectedVerse(
        bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world",
    )

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).size

    private fun ComposeUiTest.hover(label: String) {
        onAllNodesWithText(label)[0].performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    @Test
    fun `with nothing live the pill explains why it cannot be used`() {
        val presenter = PresenterManager()
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
            presenter = presenter,
        ) { _, _ ->
            assertTrue(countOf(WRONG_LABEL) > 0, "the pill is drawn in help/dev mode")
            val before = countOf(NEEDS_LIVE)

            hover(WRONG_LABEL)

            assertEquals(
                before + 1, countOf(NEEDS_LIVE),
                "grey has to mean 'not available right now', and the tooltip is what says so",
            )
        }
    }

    @Test
    fun `with a verse live the same pill offers what it does instead`() {
        // The positive twin. Without it, a pill stuck on the disabled tooltip forever would still
        // pass the test above.
        val presenter = PresenterManager().apply { setDisplayedVerses(listOf(liveVerse())) }
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
            presenter = presenter,
        ) { _, _ ->
            val beforeHint = countOf(WRONG_HINT)
            val beforeNeedsLive = countOf(NEEDS_LIVE)

            hover(WRONG_LABEL)

            assertEquals(beforeHint + 1, countOf(WRONG_HINT), "now it describes what pressing it does")
            assertEquals(
                beforeNeedsLive, countOf(NEEDS_LIVE),
                "and must not still be telling the operator it is unavailable",
            )
        }
    }

    @Test
    fun `the missed-passage pill never shows the needs-live message`() {
        // Deliberately unlike its two neighbours: it reports that the engine found *nothing*, so it
        // needs nothing on screen — and it is given no `disabledTooltip` at all.
        val presenter = PresenterManager()
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine()) },
            stt = connectedStt(),
            presenter = presenter,
        ) { _, _ ->
            assertTrue(countOf(MISSED_LABEL) > 0)
            val beforeNeedsLive = countOf(NEEDS_LIVE)
            val beforeOwnHint = countOf(MISSED_HINT)

            hover(MISSED_LABEL)

            assertEquals(beforeOwnHint + 1, countOf(MISSED_HINT), "it offers what it does")
            assertEquals(
                beforeNeedsLive, countOf(NEEDS_LIVE),
                "and never borrows its neighbours' explanation for being unavailable",
            )
        }
    }

    @Test
    fun `the pills are absent outside help mode`() {
        // They are a developer aid for the Bible Lookup Engine, not part of the operator's ordinary
        // toolbar; every other test in this file would pass just as well if they were always shown.
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(helpDev = false)) },
            stt = connectedStt(),
            presenter = PresenterManager(),
        ) { _, _ ->
            assertEquals(0, countOf(WRONG_LABEL))
            assertEquals(0, countOf(MISSED_LABEL))
        }
    }
}
