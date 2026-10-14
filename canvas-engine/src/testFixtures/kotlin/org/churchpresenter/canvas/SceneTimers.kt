package org.churchpresenter.canvas

/**
 * Stops every countdown the scene timers hold. They live in a process-wide object keyed by source
 * id, so a suite that starts one clears it after, or the next test meets a timer already running.
 */
fun clearSceneTimers() = TimerStateManager.clear()
