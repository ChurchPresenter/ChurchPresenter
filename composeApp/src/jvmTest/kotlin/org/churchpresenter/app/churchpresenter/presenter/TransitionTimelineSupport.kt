package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Records what a transition actually does, frame by frame, so a double fade or a flash of the wrong
 * text fails a test instead of having to be spotted by eye on a running output.
 *
 * The existing driver suites assert the *list of phases* a change goes through, which passes for a
 * swap that is cancelled and restarted: the phase never leaves [BibleBandPhase.TEXT_SWAP] while the
 * progress runs 0→0.4 and then 0→1 again. That restart is the double fade, so this harness samples
 * the progress too and asserts it never goes backwards inside one phase.
 *
 * **The Lottie band's text is not in the semantics tree.** It is painted into the Lottie composition
 * through Compottie's dynamic text layers, so there is no Compose `Text` node to read. What is
 * observable is the state the band renders *from* — the clock, the displayed content and
 * [PresenterManager.bandOutgoing] — which is what a [TransitionProbe] reduces to a string. Rendering
 * faults below that state (a dropped frame, a layer built mid-animation) are out of this harness's
 * reach by construction, and are noted where the suite covers them.
 */
internal data class TransitionFrame(
    val phase: BibleBandPhase,
    val progress: Float,
    /** The text on the output this frame, as the probe reduces it. */
    val displayed: String,
    /** The text the band is playing out this frame, or empty when it is not crossfading. */
    val outgoing: String,
    val bibleAlpha: Float,
    val songAlpha: Float,
)

/** What one frame's text is, reduced to something two frames can be compared on. */
internal data class TransitionText(val displayed: String, val outgoing: String)

/** Reads the on-screen and outgoing text out of a [PresenterManager] for one content type. */
internal fun interface TransitionProbe {
    fun read(): TransitionText
}

/** The Bible probe: every displayed verse's text, and the verses being played out. */
internal fun bibleProbe(manager: PresenterManager) = TransitionProbe {
    TransitionText(
        manager.displayedVerses.value.joinToString(" | ") { it.verseText },
        manager.bandOutgoing.value.verses.joinToString(" | ") { it.verseText },
    )
}

/**
 * The song probe: the line the band is on, not the whole section — a line change inside one section
 * leaves the section identical, so a section-level probe could not see the change at all.
 */
internal fun songProbe(manager: PresenterManager) = TransitionProbe {
    val outgoing = manager.bandOutgoing.value
    TransitionText(
        lineOf(manager.displayedLyricSection.value, manager.bandSongLineIndex.value),
        outgoing.lyricSection?.let { lineOf(it, outgoing.lyricLineIndex) }.orEmpty(),
    )
}

/** The line [index] of [section], or the whole section when it is not on a single line. */
private fun lineOf(section: LyricSection, index: Int): String =
    if (index >= 0 && index < section.lines.size) section.lines[index] else section.lines.joinToString(" / ")

/** Every frame of one transition, in order. */
internal class TransitionTimeline(val frames: List<TransitionFrame>) {

    /** The distinct texts the output showed, in the order it showed them. */
    val shownTexts: List<String> = frames.map { it.displayed }.dedupeConsecutive()

    /**
     * How many separate text crossfades ran.
     *
     * Counted by entering [BibleBandPhase.TEXT_SWAP] **or** by the progress dropping while already
     * in it, because two crossfades back to back need not record a frame on the hold between them —
     * a progress reset is a new crossfade whether or not the phase left.
     */
    val swapRuns: Int = frames.withIndex().count { (i, frame) ->
        if (frame.phase != BibleBandPhase.TEXT_SWAP) return@count false
        val previous = frames.getOrNull(i - 1)
        previous?.phase != BibleBandPhase.TEXT_SWAP || frame.progress < previous.progress
    }

    override fun toString(): String = buildString {
        appendLine("${frames.size} frames:")
        frames.map { it.phase }.dedupeConsecutive().forEach { appendLine("  phase $it") }
        appendLine("  texts ${shownTexts.joinToString(" -> ")}")
    }
}

private fun <T> List<T>.dedupeConsecutive(): List<T> = filterIndexed { i, v -> i == 0 || this[i - 1] != v }

/**
 * Composes [content] with a per-frame recorder attached, and hands back the frames collected while
 * [drive] runs.
 *
 * The frame clock is taken off auto-advance so the transition can be stepped deterministically, and
 * put back in a `finally`: a test that leaves `autoAdvance` false hangs every suite that runs after
 * it in the same fork, which `HungTestReporter` exists to diagnose.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.recordTimeline(
    manager: PresenterManager,
    probe: TransitionProbe,
    content: @Composable () -> Unit,
    drive: ComposeUiTest.() -> Unit,
): TransitionTimeline {
    val frames = mutableListOf<TransitionFrame>()
    mainClock.autoAdvance = false
    try {
        setContent {
            content()
            FrameRecorder(manager, probe, frames)
        }
        drive()
    } finally {
        mainClock.autoAdvance = true
    }
    return TransitionTimeline(frames.toList())
}

@Composable
private fun FrameRecorder(manager: PresenterManager, probe: TransitionProbe, into: MutableList<TransitionFrame>) {
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val clock = manager.lottieBandClock.value
                val text = probe.read()
                into += TransitionFrame(
                    phase = clock.phase,
                    progress = clock.progress,
                    displayed = text.displayed,
                    outgoing = text.outgoing,
                    bibleAlpha = manager.bibleTransitionAlpha.value,
                    songAlpha = manager.songTransitionAlpha.value,
                )
            }
        }
    }
}

/**
 * Steps the frame clock until [predicate] holds, then one frame more so the settled frame is
 * recorded. Fails rather than returning quietly if it never holds — a wait that ends by running out
 * is a failed test, not a passed one.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.advanceUntil(reason: String, maxFrames: Int = MAX_FRAMES, predicate: () -> Boolean) {
    repeat(maxFrames) {
        if (predicate()) {
            mainClock.advanceTimeByFrame()
            return
        }
        mainClock.advanceTimeByFrame()
    }
    fail("never reached: $reason")
}

/** Long enough for any transition this suite configures, short enough to fail fast on a stall. */
private const val MAX_FRAMES = 600

// ---------------------------------------------------------------------------------------------
// Assertions. Each one names the fault it detects, so a failure reads as a symptom.
// ---------------------------------------------------------------------------------------------

/** Exactly [expected] text crossfades ran. More than one for a single change is the double fade. */
internal fun TransitionTimeline.assertSwapRuns(expected: Int) {
    assertEquals(expected, swapRuns, "wrong number of text crossfades\n$this")
}

/**
 * Progress never goes backwards **within one crossfade**.
 *
 * A fade that is cancelled and restarted over the same words shows one unbroken run of
 * [BibleBandPhase.TEXT_SWAP] whose progress resets, which is a stutter the phase list cannot see.
 *
 * The text on screen delimits one crossfade from the next: a change that lands mid-fade waits for
 * the fade to finish and then starts its own, and that second fade legitimately begins at zero. So
 * the comparison is only made between frames showing the same words.
 */
internal fun TransitionTimeline.assertProgressNeverRestarts() {
    frames.zipWithNext().forEachIndexed { i, (previous, current) ->
        if (previous.phase != current.phase || previous.displayed != current.displayed) return@forEachIndexed
        assertTrue(
            current.progress >= previous.progress,
            "${current.phase} restarted at frame ${i + 1} without the words changing: " +
                "progress went ${previous.progress} -> ${current.progress}\n$this",
        )
    }
}

/** Every crossfade before the last ran to completion rather than being cut off by the next. */
internal fun TransitionTimeline.assertEachSwapCompletes() {
    val swaps = frames.filter { it.phase == BibleBandPhase.TEXT_SWAP }
    swaps.zipWithNext().forEach { (previous, current) ->
        if (current.progress >= previous.progress) return@forEach
        assertTrue(
            previous.progress >= COMPLETE_ENOUGH,
            "a crossfade was cut off at ${previous.progress} when the next one started\n$this",
        )
    }
}

/** How far through counts as finished, allowing for the clock's step landing short of exactly 1. */
private const val COMPLETE_ENOUGH = 0.95f

/**
 * The output never goes back to a text it has already left.
 *
 * This is the "flashes the previous verse, then the right one" fault: the text arrives, is replaced
 * by the old one for a frame, and arrives again.
 */
internal fun TransitionTimeline.assertTextNeverReverts() {
    val seen = mutableSetOf<String>()
    shownTexts.forEach { text ->
        assertTrue(seen.add(text), "the output went back to a text it had already left: \"$text\"\n$this")
    }
}

/**
 * No frame shows anything but [from] and [to] — a third text on screen is a flash of stale content.
 *
 * An empty text is allowed: a timeline starts before the content goes live and can end after it is
 * cleared, and neither of those is a flash.
 */
internal fun TransitionTimeline.assertOnlyTextsInFlight(from: String, to: String) {
    val allowed = setOf(from, to, "")
    frames.forEachIndexed { i, frame ->
        assertTrue(frame.displayed in allowed, "frame $i displayed an unexpected text: \"${frame.displayed}\"\n$this")
        assertTrue(
            frame.outgoing.isEmpty() || frame.outgoing in allowed,
            "frame $i played out an unexpected text: \"${frame.outgoing}\"\n$this",
        )
    }
}

/** Nothing is being played out except during a crossfade — a left-over outgoing layer is a ghost. */
internal fun TransitionTimeline.assertOutgoingOnlyWhileSwapping() {
    frames.forEachIndexed { i, frame ->
        if (frame.phase == BibleBandPhase.TEXT_SWAP) return@forEachIndexed
        assertTrue(
            frame.outgoing.isEmpty(),
            "frame $i is ${frame.phase} but still plays out \"${frame.outgoing}\"\n$this",
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Content and settings builders.
//
// Equivalent private builders are duplicated across the screenshot suites; consolidating those is
// a separate job, so this file builds only what the timeline suite needs.
// ---------------------------------------------------------------------------------------------

/** A band template with sub-second segments, so a whole transition costs a handful of frames. */
internal fun quickBandTemplate(dir: File): File = LottieBandTestSupport.writeTemplate(
    dir,
    cfg = BibleLottieGenConfig(
        canvasW = 960, canvasH = 180,
        bgInSeconds = 0.1f, textInSeconds = 0.1f, holdSeconds = 0.2f,
        textOutSeconds = 0.1f, bgOutSeconds = 0.1f,
    ),
)

internal fun lottieBackground(template: File) =
    BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = template.path)

internal fun section(vararg lines: String) = LyricSection(type = "verse", lines = lines.toList())
