package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reading the band clock must not recompose anything.
 *
 * The clock moves on every animation frame. When [LocalLottieBandClock] carried the *value*, every
 * composable that read it — including `BiblePresenter`, 1,355 lines with no inner restart boundary —
 * re-ran for each tick, on every output at once. The band still animated, but it dropped frames, and
 * the crossfade looked steppy rather than smooth.
 *
 * The local carries the state holder instead, so a reader gets an object whose identity never
 * changes and takes the value at draw time. This pins that contract: the fix is invisible to every
 * behavioural test, and the obvious "simplification" of providing the value would undo it silently.
 */
@OptIn(ExperimentalTestApi::class)
class BandClockRecompositionTest {

    @Test
    fun `moving the band clock does not recompose its readers`() = runComposeUiTest {
        val manager = PresenterManager()
        var compositions = 0

        mainClock.autoAdvance = false
        try {
            setContent {
                CompositionLocalProvider(LocalLottieBandClock provides manager.lottieBandClock) {
                    ClockReader { compositions++ }
                }
            }
            mainClock.advanceTimeByFrame()
            val settled = compositions

            repeat(TICKS) { tick ->
                manager.setLottieBandClock(BibleBandClock(BibleBandPhase.TEXT_SWAP, tick / TICKS.toFloat()))
                mainClock.advanceTimeByFrame()
            }

            assertEquals(
                settled,
                compositions,
                "the band clock recomposed its reader $TICKS times instead of only redrawing it",
            )
        } finally {
            mainClock.autoAdvance = true
        }
    }

    /** Reads the local the way the band does — the holder, never the value during composition. */
    @Composable
    private fun ClockReader(onCompose: () -> Unit) {
        LocalLottieBandClock.current
        onCompose()
    }

    private companion object {
        /** Roughly the frames one 500 ms crossfade plays at 60 Hz. */
        const val TICKS = 30
    }
}
