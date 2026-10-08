package org.churchpresenter.liveshow

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What [LiveShow] keeps true after any run of operator moves, not only the ones [LiveShowTest]
 * scripts: every cue sits on its own layer, a cue never reaches the air on its own, going live
 * never disturbs what is cued, a take empties exactly what it took, a message clears everything
 * else, and clearing all leaves at most the background.
 *
 * Each step is checked against the state before it. The seed is pinned, so every run plays the
 * same sequences and a failure reproduces exactly.
 */
class LiveShowPropertyTest {

    private sealed interface Move {
        data class Set(val cue: Cue) : Move
        data class CueUp(val cue: Cue) : Move
        data class Take(val layer: Layer?) : Move
        data class Clear(val layer: Layer) : Move
        data class ClearAll(val keepBackground: Boolean) : Move
    }

    private val name = Arb.element("a", "b", "c")
    private val cue: Arb<Cue> = Arb.choice(
        name.map { Cue.Video("video-$it") },
        name.map { Cue.Picture("picture-$it") },
        name.map { Cue.Web("https://example.org/$it") },
        name.map { Cue.LowerThird(it) },
        name.map { Cue.Announcement(it) },
        name.map { Cue.Message("message $it") },
        name.map { Cue.Audio("audio-$it") },
        name.map { Cue.Captions },
        name.map { Cue.Background(BackgroundSource.entries.first()) },
    )
    private val layer = Arb.element(Layer.entries)
    private val move: Arb<Move> = Arb.choice(
        cue.map { Move.Set(it) },
        cue.map { Move.CueUp(it) },
        layer.orNull().map { Move.Take(it) },
        layer.map { Move.Clear(it) },
        Arb.boolean().map { Move.ClearAll(it) },
    )

    @Test
    fun `every move leaves the show as the operator asked`() = runBlocking<Unit> {
        checkAll(PropTestConfig(seed = 20_261_008L, iterations = 300), Arb.list(move, 1..40)) { moves ->
            val show = LiveShow()
            moves.forEach { m ->
                val program = show.program.value
                val preview = show.preview.value
                apply(show, m)
                check(m, program, preview, show.program.value, show.preview.value)
            }
        }
    }

    private fun apply(show: LiveShow, move: Move) = when (move) {
        is Move.Set -> show.set(move.cue)
        is Move.CueUp -> show.cue(move.cue)
        is Move.Take -> show.take(move.layer)
        is Move.Clear -> show.clear(move.layer)
        is Move.ClearAll -> show.clearAll(move.keepBackground)
    }

    private fun check(
        move: Move,
        program: Map<Layer, Cue>,
        preview: Map<Layer, Cue>,
        newProgram: Map<Layer, Cue>,
        newPreview: Map<Layer, Cue>,
    ) {
        assertTrue(newProgram.all { (l, c) -> c.layer == l }, "$move put a cue on another layer: $newProgram")
        assertTrue(newPreview.all { (l, c) -> c.layer == l }, "$move cued on another layer: $newPreview")
        when (move) {
            is Move.CueUp -> {
                assertEquals(program, newProgram, "$move reached the air")
                assertEquals(preview + (move.cue.layer to move.cue), newPreview, "$move")
            }
            is Move.Set -> {
                assertEquals(preview, newPreview, "$move disturbed what is cued")
                assertEquals(goingLive(program, mapOf(move.cue.layer to move.cue)), newProgram, "$move")
            }
            is Move.Take -> {
                val taken = if (move.layer == null) preview else preview.filterKeys { it == move.layer }
                assertEquals(preview - taken.keys, newPreview, "$move left something cued it took")
                assertEquals(if (taken.isEmpty()) program else goingLive(program, taken), newProgram, "$move")
            }
            is Move.Clear -> {
                assertEquals(preview, newPreview, "$move disturbed what is cued")
                assertEquals(program - move.layer, newProgram, "$move")
            }
            is Move.ClearAll -> {
                assertEquals(preview, newPreview, "$move disturbed what is cued")
                val kept = if (move.keepBackground) program.filterKeys { it == Layer.BACKGROUND } else emptyMap()
                assertEquals(kept, newProgram, "$move")
            }
        }
    }

    /** A message going up clears every other layer; anything else goes up beside what is there. */
    private fun goingLive(program: Map<Layer, Cue>, live: Map<Layer, Cue>): Map<Layer, Cue> =
        live[Layer.MESSAGES]?.let { mapOf(Layer.MESSAGES to it) } ?: (program + live)
}
