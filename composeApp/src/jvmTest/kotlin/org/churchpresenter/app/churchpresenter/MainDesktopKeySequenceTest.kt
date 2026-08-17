package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [advanceKeySequence] drives all three of MainDesktop's secret key sequences — Konami
 * (↑↑↓↓←→←→BA), the Crossword unlock (←→←→) and the Developer-menu unlock (D×7, modelled as a
 * sequence of seven D's) — extracted so the state machine behind them can be driven directly
 * instead of through a live `onPreviewKeyEvent` handler and a full composable mount.
 *
 * The behavior worth pinning down is what happens off the happy path: a wrong key mid-sequence
 * has to fully reset progress, EXCEPT when that wrong key happens to be the sequence's own first
 * key, in which case it must restart the count at 1 rather than 0 — otherwise typing the Konami
 * code twice in a row with no gap would silently fail to unlock the second time.
 */
class MainDesktopKeySequenceTest {

    private val konami = listOf(
        Key.DirectionUp, Key.DirectionUp,
        Key.DirectionDown, Key.DirectionDown,
        Key.DirectionLeft, Key.DirectionRight,
        Key.DirectionLeft, Key.DirectionRight,
        Key.B, Key.A,
    )

    private val crossword = listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionLeft, Key.DirectionRight)

    private val developerUnlock = List(7) { Key.D }

    @Test
    fun `the correct sequence, key by key, completes on the final key and nowhere earlier`() {
        var progress = 0
        konami.dropLast(1).forEach { key ->
            val step = advanceKeySequence(key, konami, progress)
            assertFalse(step.completed, "must not complete before the last key")
            progress = step.progress
        }

        val last = advanceKeySequence(konami.last(), konami, progress)
        assertTrue(last.completed)
        assertEquals(0, last.progress, "progress resets once the sequence completes")
    }

    @Test
    fun `an unrelated key at the very first step leaves progress at zero`() {
        val step = advanceKeySequence(Key.Spacebar, konami, currentProgress = 0)
        assertEquals(0, step.progress)
        assertFalse(step.completed)
    }

    @Test
    fun `a wrong key mid-sequence resets progress to zero`() {
        // Two correct Ups, then something that is neither the expected Down nor the sequence's
        // own first key (Up) — must fall all the way back to zero.
        val afterTwoUps = advanceKeySequence(Key.DirectionUp, konami, 1).progress
        assertEquals(2, afterTwoUps)

        val step = advanceKeySequence(Key.Spacebar, konami, afterTwoUps)
        assertEquals(0, step.progress)
        assertFalse(step.completed)
    }

    @Test
    fun `a wrong key that matches the sequence's own first key restarts at one, not zero`() {
        // Konami starts with Up. Failing on the third key (expects Down) by pressing Up again
        // must count as the start of a fresh attempt, not a dead reset — otherwise entering the
        // code twice back-to-back would never succeed on the second try.
        val afterTwoUps = advanceKeySequence(Key.DirectionUp, konami, 1).progress
        assertEquals(2, afterTwoUps)

        val step = advanceKeySequence(Key.DirectionUp, konami, afterTwoUps)
        assertEquals(
            1,
            step.progress,
            "Up is konami's own first key, so this must restart the count rather than zero it",
        )
        assertFalse(step.completed)
    }

    @Test
    fun `the crossword sequence completes on its fourth key`() {
        var progress = 0
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionLeft).forEach { key ->
            progress = advanceKeySequence(key, crossword, progress).progress
        }

        val step = advanceKeySequence(Key.DirectionRight, crossword, progress)
        assertTrue(step.completed)
        assertEquals(0, step.progress)
    }

    @Test
    fun `the crossword sequence's repeating pattern still resets correctly on a genuine mismatch`() {
        val afterLeftRight = advanceKeySequence(
            Key.DirectionRight,
            crossword,
            advanceKeySequence(Key.DirectionLeft, crossword, 0).progress,
        ).progress
        assertEquals(2, afterLeftRight)

        // Third key should be Left; pressing Up is not Left and not the sequence's first key.
        val step = advanceKeySequence(Key.DirectionUp, crossword, afterLeftRight)
        assertEquals(0, step.progress)
    }

    @Test
    fun `pressing D seven times in a row completes the developer unlock`() {
        var progress = 0
        repeat(6) { progress = advanceKeySequence(Key.D, developerUnlock, progress).progress }
        assertEquals(6, progress)

        val seventh = advanceKeySequence(Key.D, developerUnlock, progress)
        assertTrue(seventh.completed)
        assertEquals(0, seventh.progress)
    }

    @Test
    fun `any non-D key resets the developer unlock count to zero`() {
        var progress = 0
        repeat(4) { progress = advanceKeySequence(Key.D, developerUnlock, progress).progress }
        assertEquals(4, progress)

        val step = advanceKeySequence(Key.A, developerUnlock, progress)
        assertEquals(0, step.progress)
        assertFalse(step.completed)
    }

    @Test
    fun `a single-key sequence completes on the very first matching press`() {
        val single = listOf(Key.Enter)
        val step = advanceKeySequence(Key.Enter, single, currentProgress = 0)
        assertTrue(step.completed)
        assertEquals(0, step.progress)
    }

    @Test
    fun `a single-key sequence stays at zero for any other key`() {
        val single = listOf(Key.Enter)
        val step = advanceKeySequence(Key.Spacebar, single, currentProgress = 0)
        assertFalse(step.completed)
        assertEquals(0, step.progress)
    }
}
