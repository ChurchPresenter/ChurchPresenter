package org.churchpresenter.liveoutput

import androidx.compose.runtime.State

/** How many presses of Clear, in quick succession, release the screen holds. */
private const val CLEAR_PRESSES_TO_RELEASE = 3

/** How quick: the third press must come this soon after the first. */
private const val CLEAR_PRESS_WINDOW_MS = 1_500L

/**
 * How a screen is held back from what goes live (see [ScreenHolds.setScreenHold]). Not the tab lock:
 * a held screen ignores every change of content, of any type, until it is released.
 */
enum class ScreenHold {
    /** The screen keeps the last frame it drew. */
    HOLD,

    /** The screen shows black. */
    BLANK,
}

/**
 * Each screen's hold, apart from the live mode and the tab locks ([OutputLocks]). Runtime only: it is
 * never saved, so a screen cannot stay held unseen across a restart. Part of [PresenterManager].
 */
interface ScreenHolds {
    /** Screen slot index -> how it is held. A missing entry is not held. */
    val screenHolds: State<Map<Int, ScreenHold>>

    /** How many screens the output windows opened, which [holdAllScreens] holds. */
    val screenCount: Int

    /** Holds [screenIndex] as [hold], or releases it when [hold] is null. */
    fun setScreenHold(screenIndex: Int, hold: ScreenHold?)

    /** Holds every screen as [hold], or releases them all when [hold] is null. */
    fun holdAllScreens(hold: ScreenHold?)

    /** Tells the holds how many screens there are; the output windows say so as they open. */
    fun setScreenCount(count: Int)
}

internal class ScreenHoldsState(
    private val context: PresenterContext,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScreenHolds {

    override val screenHolds: State<Map<Int, ScreenHold>> = context.screenHolds

    override val screenCount: Int get() = context.screenCount.intValue

    /** When the last [CLEAR_PRESSES_TO_RELEASE] presses of Clear came, oldest first. */
    private val clearPresses = ArrayDeque<Long>()

    override fun setScreenHold(screenIndex: Int, hold: ScreenHold?) {
        context.screenHolds.value = context.screenHolds.value.holding(screenIndex, hold)
    }

    override fun holdAllScreens(hold: ScreenHold?) {
        context.screenHolds.value = (0 until screenCount).fold(context.screenHolds.value) { held, index ->
            held.holding(index, hold)
        }
    }

    override fun setScreenCount(count: Int) {
        context.screenCount.intValue = count
    }

    /**
     * Counts a press of Clear, and releases every hold when the third one lands within
     * [CLEAR_PRESS_WINDOW_MS] of the first. One press leaves a hold as it is. Nothing is counted while
     * no screen is held, so a hold put up after a few ordinary clears starts from none.
     */
    fun noteClearPress() {
        if (context.screenHolds.value.isEmpty()) {
            clearPresses.clear()
            return
        }
        val now = clock()
        clearPresses.addLast(now)
        while (clearPresses.size > CLEAR_PRESSES_TO_RELEASE) clearPresses.removeFirst()
        if (clearPresses.size == CLEAR_PRESSES_TO_RELEASE && now - clearPresses.first() <= CLEAR_PRESS_WINDOW_MS) {
            clearPresses.clear()
            context.screenHolds.value = emptyMap()
        }
    }
}

/** [this] with [index] held as [hold], or released when [hold] is null. */
private fun Map<Int, ScreenHold>.holding(index: Int, hold: ScreenHold?): Map<Int, ScreenHold> =
    if (hold == null) this - index else this + (index to hold)
