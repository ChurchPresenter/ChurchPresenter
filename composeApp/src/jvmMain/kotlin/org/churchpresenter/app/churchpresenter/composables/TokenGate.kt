package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Consumer for an incrementing signal token, returned by [rememberTokenGate]. [consume] returns true
 * exactly once per new non-zero token value — skipping the initial value seen at first composition
 * and any repeat of an already-handled value. [lastHandled] exposes that same remembered value for
 * callers elsewhere in the same composable that need to check "was this the value auto-follow's own
 * effect just handled" without consuming it again.
 */
class TokenGate internal constructor(private val token: Int, private val lastHandledState: MutableState<Int>) {
    val lastHandled: Int get() = lastHandledState.value

    fun consume(): Boolean {
        if (token == 0 || token == lastHandledState.value) return false
        lastHandledState.value = token
        return true
    }
}

/**
 * Remembers the last-handled value of an incrementing signal [token], returning a [TokenGate] that
 * consumes it (typically from inside a `LaunchedEffect(token)` keyed on the same token) or reads the
 * last-handled value without consuming.
 *
 * Shared by Bible auto-follow's go-live consumers (BibleTab's own handler and MainDesktop's
 * tab-switch fallback, which both watch `BibleViewModel.autoFollowLiveToken`) so the "consume this
 * signal once" bookkeeping lives in one place instead of hand-rolled copies that could drift apart.
 */
@Composable
fun rememberTokenGate(token: Int): TokenGate {
    val lastHandled = remember { mutableStateOf(token) }
    return TokenGate(token, lastHandled)
}
