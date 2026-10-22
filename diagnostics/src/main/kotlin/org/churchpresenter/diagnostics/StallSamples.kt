package org.churchpresenter.diagnostics

// What one of a stall's samples says about the event thread: which part of it is the loop now
// running, and whether the thread was doing anything at all. StallGrouping reads these.

/**
 * [frames] above the innermost event loop on the stack: the frames the loop now running dispatched.
 * Under a modal dialog the thread runs a nested loop, and the code below it is only waiting for the
 * dialog to close. The whole stack when no loop is on it, as when it was cut short.
 */
internal fun aboveInnermostLoop(frames: List<StackTraceElement>): List<StackTraceElement> {
    val loop = frames.indexOfFirst(::isEventLoop)
    return if (loop < 0) frames else frames.subList(0, loop)
}

/** The first app frame below the innermost event loop: what opened the modal dialog [frames] is under. */
internal fun modalCaller(frames: List<StackTraceElement>): String? {
    val loop = frames.indexOfFirst(::isEventLoop)
    if (loop < 0) return null
    return frames.drop(loop + 1).firstOrNull { it.isAppFrame }?.groupName
}

/**
 * Whether [frames] is the event thread waiting for its next event, not inside one: the thread was
 * free when this was taken, so it is no evidence of where the stall was.
 */
internal fun isIdle(frames: List<StackTraceElement>): Boolean {
    val waiting = frames.indexOfFirst { it.className == EVENT_QUEUE && it.methodName == "getNextEvent" }
    if (waiting < 0) return false
    val dispatching = frames.indexOfFirst { it.methodName == "dispatchEvent" }
    return dispatching < 0 || waiting < dispatching
}

private fun isEventLoop(frame: StackTraceElement): Boolean =
    frame.className == EVENT_DISPATCH_THREAD && frame.methodName == "pumpOneEventForFilters"

private const val EVENT_QUEUE = "java.awt.EventQueue"
private const val EVENT_DISPATCH_THREAD = "java.awt.EventDispatchThread"
