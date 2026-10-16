package org.churchpresenter.outputguard

/** What the guard does with its windows on one look at the app. */
enum class GuardAction {
    /** Sit under the app's outputs, matching where they are and keeping their stills. */
    FOLLOW,

    /** The app's event thread is stuck while live: cover the outputs with their stills. */
    COVER_HANG,

    /** The app is gone: cover the outputs, start the app again, and wait for it. */
    COVER_DEATH,

    /** The app is quitting on purpose: close the windows and exit. */
    LEAVE,
}

/** How long the event thread may go without a tick, while live, before the guard covers a hang. */
const val HANG_MS = 3_000L

/** A heartbeat older than this means the app's own threads are stuck, not only its UI. */
const val HEARTBEAT_FRESH_MS = 1_500L

/**
 * The guard's choice, from what it sees: whether the app's process is alive, and what the app last
 * wrote. A quit always wins; a dead app is covered whatever it last said; a hang is covered only
 * where the operator asked for it, only while an output is live, and only while the app's other
 * threads still beat -- a stuck UI, not a frozen machine.
 */
fun decide(appAlive: Boolean, state: GuardState, now: Long): GuardAction = when {
    state.closing -> GuardAction.LEAVE
    !appAlive -> GuardAction.COVER_DEATH
    state.takeOverOnHang && state.live &&
        now - state.heartbeatAt < HEARTBEAT_FRESH_MS && now - state.uiTickAt > HANG_MS -> GuardAction.COVER_HANG
    else -> GuardAction.FOLLOW
}

/** How long a relaunched app may take to put its outputs up before the guard leaves anyway. */
const val SUCCESSOR_GRACE_MS = 30_000L

/**
 * Whether the app the guard relaunched -- any app but [deadPid] writing this build's state -- has
 * taken over: its outputs are up, or it has been beating for [SUCCESSOR_GRACE_MS] without any, as
 * an app with no output configured does. Until then the guard keeps the stills up.
 */
fun successorTookOver(state: GuardState, deadPid: Long, firstSeenAt: Long?, now: Long): Boolean {
    if (!state.isCurrent || state.appPid == deadPid || state.appPid == 0L) return false
    return state.outputsUp || (firstSeenAt != null && now - firstSeenAt >= SUCCESSOR_GRACE_MS)
}
