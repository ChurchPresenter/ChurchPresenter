package org.churchpresenter.stt.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.stt.STTSegment
import org.churchpresenter.settings.CAPTION_BREAK_NONE
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_STYLE_ROLL_UP
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Captions already on screen stay where they are: each word keeps its line and its place on it, and
 * the whole block only ever slides up, by the height of the lines that arrived under it.
 *
 * The sequences are [SundayCaptions] -- the shape of a live service, never its words -- fed the way
 * the server sends them: a window of the newest captions, trimmed at its front as it grows. The
 * settings are a translate-only output's: roll-up, four lines, five segments kept, one paragraph.
 *
 * Every wait is on the virtual clock (`autoAdvance = false`).
 */
@OptIn(ExperimentalTestApi::class)
class STTPresenterStabilityRenderTest {

    private val sunday = STTSettings(
        displayMode = "translate",
        layout = "stacked",
        maxSegments = 5,
        maxLines = 4,
        showInProgress = false,
        showTranslationInProgress = false,
        dripFeedEnabled = false,
        fontSize = 70,
        position = Constants.BOTTOM_LEFT,
        reading = CaptionReading(
            style = CAPTION_STYLE_ROLL_UP,
            rollUp = true,
            rollUpMillis = ROLL_MS,
            lineBreaks = CAPTION_BREAK_NONE,
        ),
    )

    private val dripping = sunday.copy(
        dripFeedEnabled = true,
        dripFeedSpeed = 25,
        reading = sunday.reading.copy(readingSpeedLimit = true, readingSpeedCps = 17),
    )

    /** Renders the presenter over [settings] and hands the body a setter for the translation. */
    private fun showing(
        settings: STTSettings,
        body: ComposeUiTest.(send: (List<STTSegment>) -> Unit) -> Unit,
    ) = showingEpochs(settings) { send, _ -> body(send) }

    /** [showing], with a setter for the translation's epoch too -- a resync bumps it. */
    private fun showingEpochs(
        settings: STTSettings,
        body: ComposeUiTest.(send: (List<STTSegment>) -> Unit, resync: () -> Unit) -> Unit,
    ) = runComposeUiTest {
        var translation by mutableStateOf(emptyList<STTSegment>())
        var epoch by mutableStateOf(0)
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                STTPresenter(
                    segments = emptyList(),
                    inProgressText = "",
                    translationSegments = translation,
                    inProgressTranslation = "",
                    highlightedWords = emptyList(),
                    sttSettings = settings,
                    translationEpoch = epoch,
                )
            }
        }
        mainClock.advanceTimeByFrame()
        body({ translation = it }, { epoch++ })
    }

    /** Where each word on screen starts, keyed by its first [SundayCaptions.KEY_CHARS] characters. */
    private fun ComposeUiTest.wordsOnScreen(): Map<String, Offset> {
        val words = mutableMapOf<String, Offset>()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: return@forEach
            val laidOut = layout.layoutInput.text
            // Laid out only to place the line at its finished width, and not seen
            val hidden = laidOut.spanStyles.filter { it.item.color == Color.Transparent }
            TOKEN.findAll(laidOut.text).filter { match -> hidden.none { match.range.first in it.start until it.end } }
                .forEach { match ->
                val box = layout.getBoundingBox(match.range.first)
                words[match.value.take(SundayCaptions.KEY_CHARS)] = node.positionInRoot + box.topLeft
            }
        }
        return words
    }

    /**
     * Every word on screen both [before] and [after] kept its x and moved up by one shared amount:
     * nothing re-wrapped, nothing slid sideways. Returns that amount (negative is up).
     */
    private fun assertRigid(before: Map<String, Offset>, after: Map<String, Offset>, step: String): Float {
        val common = before.keys intersect after.keys
        if (common.isEmpty()) return 0f
        val dy = common.first().let { after.getValue(it).y - before.getValue(it).y }
        common.forEach { key ->
            val was = before.getValue(key)
            val now = after.getValue(key)
            if (abs(was.x - now.x) > TOLERANCE || abs((now.y - was.y) - dy) > TOLERANCE) {
                fail("$step: $key moved from $was to $now while the block moved by $dy")
            }
        }
        assertTrue(dy <= TOLERANCE, "$step: the captions moved down by $dy")
        return dy
    }

    /** [key] moved up from [from] to [to], and was part-way there at [mid]: it slid, it did not jump. */
    private fun assertSlidUp(key: String, from: Float, mid: Float, to: Float) {
        assertTrue(to < from - TOLERANCE, "$key moves up: $from -> $to")
        assertTrue(mid < from - TOLERANCE && mid > to + TOLERANCE, "$key is part-way up mid-slide: $from, $mid, $to")
    }

    /**
     * Sends windows of [window] for [from]..[count] segments, settling each, and checks every step.
     * Opening on [from] - 1 at once is a backlog, as on an output opened mid-service.
     */
    private fun ComposeUiTest.speakSteadily(send: (List<STTSegment>) -> Unit, count: Int, window: Int, from: Int = 1) {
        if (from > 1) {
            send(SundayCaptions.windowAt(from - 1, window))
            mainClock.advanceTimeBy(SETTLE_MS)
        }
        var before = wordsOnScreen()
        for (n in from..count) {
            send(SundayCaptions.windowAt(n, window))
            mainClock.advanceTimeBy(SETTLE_MS)
            val after = wordsOnScreen()
            assertRigid(before, after, "segment $n of a $window-entry window")
            before = after
        }
    }

    @Test
    fun `steady speech with five segments kept never re-wraps what is on screen`() =
        showing(sunday) { send -> speakSteadily(send, count = 24, window = 100) }

    @Test
    fun `the server trimming its window never re-wraps what is on screen`() =
        showing(sunday.copy(maxSegments = 0)) { send -> speakSteadily(send, count = 24, window = 6) }

    @Test
    fun `a full hundred-entry window trimming its front never re-wraps what is on screen`() =
        showing(sunday.copy(maxSegments = 0)) { send -> speakSteadily(send, count = 108, window = 100, from = 96) }

    @Test
    fun `segment line breaks with every segment kept never re-wrap under the window's trim`() = showing(
        sunday.copy(maxSegments = 0, reading = sunday.reading.copy(lineBreaks = CAPTION_BREAK_SEGMENT)),
    ) { send -> speakSteadily(send, count = 24, window = 6) }

    @Test
    fun `a new caption under a trimmed front slides the old lines up rather than jumping`() =
        showing(sunday) { send ->
            for (n in 1..12) {
                send(SundayCaptions.windowAt(n))
                mainClock.advanceTimeBy(SETTLE_MS)
            }
            val before = wordsOnScreen()
            send(SundayCaptions.windowAt(13))
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(ROLL_MS / 2L)
            val midway = wordsOnScreen()
            mainClock.advanceTimeBy(SETTLE_MS)
            val after = wordsOnScreen()
            val common = before.keys intersect midway.keys intersect after.keys
            assertTrue(common.isNotEmpty(), "some words stay on screen across the new caption")
            common.forEach { key ->
                val from = before.getValue(key).y
                val mid = midway.getValue(key).y
                val to = after.getValue(key).y
                assertSlidUp(key, from, mid, to)
            }
        }

    @Test
    fun `a caption still filling its lines slides up for a new line rather than jumping`() =
        showing(sunday) { send ->
            send(SundayCaptions.windowAt(1))
            mainClock.advanceTimeBy(SETTLE_MS)
            val before = wordsOnScreen()
            send(SundayCaptions.windowAt(2))
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(ROLL_MS / 2L)
            val midway = wordsOnScreen()
            mainClock.advanceTimeBy(SETTLE_MS)
            val after = wordsOnScreen()
            val key = before.keys.first()
            val from = before.getValue(key).y
            val mid = midway.getValue(key).y
            val to = after.getValue(key).y
            assertSlidUp(key, from, mid, to)
        }

    @Test
    fun `a word being typed out never changes line`() = showing(dripping) { send ->
        var before = wordsOnScreen()
        for (n in 1..6) {
            send(SundayCaptions.windowAt(n))
            // Every typed letter, until this segment is out
            repeat(REVEAL_FRAMES) { frame ->
                mainClock.advanceTimeBy(LETTER_MS)
                val after = wordsOnScreen()
                assertRigid(before, after, "segment $n, letter $frame")
                before = after
            }
        }
    }

    @Test
    fun `a centred line being typed out never slides sideways`() =
        showing(dripping.copy(position = Constants.BOTTOM)) { send ->
            send(SundayCaptions.windowAt(1))
            var before = wordsOnScreen()
            repeat(REVEAL_FRAMES) { frame ->
                mainClock.advanceTimeBy(LETTER_MS)
                val after = wordsOnScreen()
                assertRigid(before, after, "letter $frame")
                before = after
            }
        }

    @Test
    fun `a correction above the cursor leaves the reveal where it was`() = showing(dripping) { send ->
        send(SundayCaptions.windowAt(3))
        mainClock.advanceTimeBy(TYPED_OUT_MS) // all three typed out
        send(SundayCaptions.windowAt(4))
        mainClock.advanceTimeBy(LETTER_MS * 5)
        val newest = SundayCaptions.text(3).split(' ')
        val last = newest.last().take(SundayCaptions.KEY_CHARS)
        val shown = wordsOnScreen().keys
        assertTrue(last !in shown, "the newest segment is still being typed")
        val corrected = SundayCaptions.windowAt(4).mapIndexed { i, segment ->
            if (i == 2) segment.copy(text = segment.text.replace("w0", "W0")) else segment
        }
        send(corrected)
        // Two letters on: long enough for the reveal to have re-anchored, too short to finish it
        mainClock.advanceTimeBy(LETTER_MS * 2)
        val after = wordsOnScreen().keys
        assertTrue(last !in after, "the correction does not reveal the rest of the newest segment")
        val correctedKeys = SundayCaptions.text(2).split(' ').map { it.take(SundayCaptions.KEY_CHARS) }.toSet()
        // A corrected line may wrap longer and push the top line off, but never hides typed words
        // under ones still on screen, as a reveal starting over would
        val lost = shown - correctedKeys - after
        val oldestLeft = (after - correctedKeys).minOf { it.drop(1).toInt() }
        assertTrue(lost.all { it.drop(1).toInt() < oldestLeft }, "typed words only leave from the top: $lost")
    }

    @Test
    fun `a resync starts the captions afresh, shown whole`() = showingEpochs(dripping) { send, resync ->
        send(SundayCaptions.windowAt(4))
        mainClock.advanceTimeBy(TYPED_OUT_MS)
        // Reconnected: the list sent again whole holds captions never shown live, so it does not
        // extend the one on screen
        val again = (0 until 6).map { SundayCaptions.segment(it, SundayCaptions.text(it + 20)) }
        resync()
        send(again)
        mainClock.advanceTimeBy(LETTER_MS)
        val shown = wordsOnScreen().keys
        val newest = SundayCaptions.text(25).split(' ').last().take(SundayCaptions.KEY_CHARS)
        val oldest = SundayCaptions.word(0).take(SundayCaptions.KEY_CHARS)
        assertTrue(newest in shown, "the resent list is shown whole, as a newly opened output shows it: $shown")
        assertTrue(oldest !in shown, "nothing of the list before it is left: $shown")
    }

    @Test
    fun `a list that does not extend the last one starts afresh even without a resync`() = showing(dripping) { send ->
        send(SundayCaptions.windowAt(4))
        mainClock.advanceTimeBy(TYPED_OUT_MS)
        send((0 until 6).map { SundayCaptions.segment(it, SundayCaptions.text(it + 20)) })
        mainClock.advanceTimeBy(LETTER_MS)
        val shown = wordsOnScreen().keys
        val newest = SundayCaptions.text(25).split(' ').last().take(SundayCaptions.KEY_CHARS)
        assertTrue(newest in shown, "a list that is not an edit of the last is shown whole: $shown")
    }

    private companion object {
        val TOKEN = Regex("w\\d{3}[a-z]*")
        const val TOLERANCE = 0.5f
        const val ROLL_MS = 250
        const val SETTLE_MS = 2_000L
        const val TYPED_OUT_MS = 30_000L

        // 17 characters a second is slower than 25 ms a letter, so it sets the pace
        const val LETTER_MS = 1_000L / 17
        const val REVEAL_FRAMES = 110
    }
}
