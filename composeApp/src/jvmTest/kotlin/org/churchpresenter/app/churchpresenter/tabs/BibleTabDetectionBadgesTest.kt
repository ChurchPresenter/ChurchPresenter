@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.viewmodel.getSelectedVerses
import org.churchpresenter.app.churchpresenter.viewmodel.onEngineScripture
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * Why a verse was detected, as the auto-follow panel shows it — and the Help Dev flags.
 *
 * Each detected row carries a badge per source: a spoken reference, a match on the text, following
 * along verse by verse, a scan of the current chapter, or a match against an earlier one. The operator
 * decides whether to trust a row from that badge, so the mapping from the engine's `matchType` string
 * to the badge has to be right — and all five are checked, because a wrong badge quietly recasts a
 * guess as a stated reference.
 *
 * A row can also carry **more than one** badge when the engine reached the same verse two ways, which
 * is the strongest signal there is; that is asserted rather than assumed.
 *
 * The Help Dev flag pills are here too. They only appear with Help Dev on, and two of the three are
 * disabled until something is live because they describe the passage on screen — a click that quietly
 * did nothing was the bug that made them worth guarding.
 */
class BibleTabDetectionBadgesTest {

    private val managers = mutableListOf<STTManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
    }

    private fun connectedStt() = STTManager().also {
        managers.add(it)
        it.applyConnected()
    }

    private fun engine(helpDev: Boolean = false) =
        BibleEngineSettings(enabled = true, helpDevMode = helpDev)

    /** A detection with the engine's own `matchType` string, which is what picks the badge. */
    private fun BibleViewModel.detect(
        matchType: String,
        verseStart: Int = 17,
        tracks: List<String> = emptyList(),
        detectedVersion: String? = null,
    ) = onEngineScripture(
        bookId = 43,
        chapter = 3,
        verseStart = verseStart,
        verseEnd = null,
        verseText = "For God so loved the world.",
        matchType = matchType,
        tracks = tracks,
        detectedVersion = detectedVersion,
    )

    /** Every content description on screen — the badges publish themselves this way. */
    private fun ComposeUiTest.descriptions(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    // ── One badge per source ────────────────────────────────────────────────────

    @Test
    fun `a spoken reference is badged as spoken`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit")
            waitForIdle()

            assertTrue(descriptions().contains(Badge.EXPLICIT), descriptions().toString())
        }
    }

    @Test
    fun `a text match is badged as matched by text`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("reverse")
            waitForIdle()

            assertTrue(descriptions().contains(Badge.REVERSE), descriptions().toString())
            assertFalse(
                descriptions().contains(Badge.EXPLICIT),
                "a guess must not be shown as a stated reference",
            )
        }
    }

    @Test
    fun `following along verse by verse has its own badge`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("continuation")
            waitForIdle()

            assertTrue(descriptions().contains(Badge.CONTINUATION), descriptions().toString())
        }
    }

    @Test
    fun `a chapter scan has its own badge`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("chapter-scan")
            waitForIdle()

            assertTrue(descriptions().contains(Badge.CHAPTER_SCAN), descriptions().toString())
        }
    }

    @Test
    fun `a match against an earlier chapter has its own badge`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("chapter-history")
            waitForIdle()

            assertTrue(descriptions().contains(Badge.CHAPTER_HISTORY), descriptions().toString())
        }
    }

    @Test
    fun `an unrecognised match type falls back to the text-match badge`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            // A newer engine sending a source this build does not know must still produce a row with a
            // badge, not a row with none.
            vm.detect("some-future-source")
            waitForIdle()

            assertTrue(descriptions().contains(Badge.REVERSE), descriptions().toString())
        }
    }

    @Test
    fun `reaching one verse two ways shows both badges`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit", verseStart = 17)
            vm.detect("continuation", verseStart = 17)
            waitForIdle()

            // Two sources agreeing on one verse is the strongest signal the panel can show.
            assertEquals(1, vm.detectedReferences.value.size, "the same verse must not stack up as two rows")
            assertTrue(descriptions().contains(Badge.EXPLICIT), descriptions().toString())
            assertTrue(descriptions().contains(Badge.CONTINUATION))
        }
    }

    @Test
    fun `a track corroborated by transcription is badged`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit", tracks = listOf("transcription"))
            waitForIdle()

            assertTrue(descriptions().contains(Track.TRANSCRIPTION), descriptions().toString())
            assertFalse(descriptions().contains(Track.TRANSLATION))
        }
    }

    @Test
    fun `a track corroborated by translation is badged`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit", tracks = listOf("translation"))
            waitForIdle()

            assertTrue(descriptions().contains(Track.TRANSLATION), descriptions().toString())
            assertFalse(descriptions().contains(Track.TRANSCRIPTION))
        }
    }

    @Test
    fun `a detection corroborated by both tracks carries both badges`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit", tracks = listOf("transcription", "translation"))
            waitForIdle()

            assertTrue(descriptions().contains(Track.TRANSCRIPTION), descriptions().toString())
            assertTrue(descriptions().contains(Track.TRANSLATION))
        }
    }

    @Test
    fun `a detection with no corroborating track carries neither badge`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit")
            waitForIdle()

            assertFalse(descriptions().contains(Track.TRANSCRIPTION), descriptions().toString())
            assertFalse(descriptions().contains(Track.TRANSLATION))
        }
    }

    @Test
    fun `the detected translation is shown alongside the reference`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit", detectedVersion = "NASB")
            waitForIdle()

            assertTrue(showsExactly("NASB"), renderedText().toString())
        }
    }

    @Test
    fun `with no detected translation nothing extra is shown`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit")
            waitForIdle()

            assertFalse(showsExactly("NASB"))
        }
    }

    @Test
    fun `two different verses keep their own badges`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine()) }, stt = connectedStt()) { vm, _ ->
            vm.detect("explicit", verseStart = 17)
            vm.detect("chapter-scan", verseStart = 18)
            waitForIdle()

            assertEquals(2, vm.detectedReferences.value.size)
            assertTrue(descriptions().contains(Badge.EXPLICIT), descriptions().toString())
            assertTrue(descriptions().contains(Badge.CHAPTER_SCAN))
        }
    }

    // ── The Help Dev flag pills ─────────────────────────────────────────────────

    @Test
    fun `the flag pills are hidden unless help dev is on`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine(helpDev = false)) }, stt = connectedStt()) { _, _ ->
            assertFalse(showsExactly(Flag.WRONG), renderedText().toString())
            assertFalse(showsExactly(Flag.PREMATURE))
            assertFalse(showsExactly(Flag.MISSED))
        }
    }

    @Test
    fun `with help dev on all three pills are offered`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine(helpDev = true)) }, stt = connectedStt()) { _, _ ->
            assertTrue(showsExactly(Flag.WRONG), renderedText().toString())
            assertTrue(showsExactly(Flag.PREMATURE))
            assertTrue(showsExactly(Flag.MISSED))
        }
    }

    @Test
    fun `the two pills about the live passage cannot be clicked with nothing live`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine(helpDev = true)) }, stt = connectedStt()) { _, _ ->
            // Both describe what went live, so with an empty output the pill drops its `clickable`
            // entirely rather than publishing a disabled one — it used to swallow the click silently.
            assertTrue(showsExactly(Flag.WRONG), "the pill is still shown: ${renderedText()}")
            assertFalse(clickable(Flag.WRONG), "but it must not be pressable")
            assertFalse(clickable(Flag.PREMATURE))
        }
    }

    @Test
    fun `the wrong-passage pill is clickable once something is live`() {
        val presenter = PresenterManager()
        bibleTab(
            settings = { it.copy(bibleEngineSettings = engine(helpDev = true)) },
            stt = connectedStt(),
            presenter = presenter,
        ) { vm, _ ->
            runOnIdle { presenter.setDisplayedVerses(vm.getSelectedVerses()) }
            waitForIdle()

            assertTrue(clickable(Flag.WRONG), renderedText().toString())

            onNode(hasText(Flag.WRONG) and hasClickAction()).performClick()
            waitForIdle()

            assertTrue(showsExactly(Flag.WRONG))
        }
    }

    @Test
    fun `the missed-passage pill is clickable with nothing live`() {
        bibleTab(settings = { it.copy(bibleEngineSettings = engine(helpDev = true)) }, stt = connectedStt()) { _, _ ->
            // It reports that the engine found nothing, so it needs nothing on screen.
            assertTrue(clickable(Flag.MISSED), renderedText().toString())

            onNode(hasText(Flag.MISSED) and hasClickAction()).performClick()
            waitForIdle()

            // The click was accepted; what it writes is covered by BibleViewModel's training-log tests.
            assertTrue(showsExactly(Flag.MISSED))
        }
    }

    /** Whether the pill labelled [label] currently carries a click action. */
    private fun ComposeUiTest.clickable(label: String): Boolean =
        onAllNodes(hasText(label) and hasClickAction())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private object Badge {
        const val EXPLICIT = "Spoken reference"
        const val REVERSE = "Matched by text"
        const val CONTINUATION = "Following along"
        const val CHAPTER_SCAN = "Found in current chapter"
        const val CHAPTER_HISTORY = "Matched an earlier chapter"
    }

    private object Track {
        const val TRANSCRIPTION = "Heard in transcription"
        const val TRANSLATION = "Heard in translation"
    }

    private object Flag {
        const val WRONG = "Wrong passage"
        const val PREMATURE = "Premature"
        const val MISSED = "Missed passage"
    }
}
