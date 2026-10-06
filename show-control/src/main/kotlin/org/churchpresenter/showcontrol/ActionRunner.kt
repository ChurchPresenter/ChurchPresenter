package org.churchpresenter.showcontrol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Runs action lists against a [ShowHost], each in order on its own coroutine in [scope]: a `wait`
 * suspends its list, a macro runs its actions in place, and an action that fails is reported and
 * passed over. A run started under a key replaces the one still going under it -- a row firing
 * again, a macro pressed twice.
 */
class ActionRunner(private val host: ShowHost, private val scope: CoroutineScope) {

    private val runs = mutableMapOf<String, Job>()

    /**
     * Runs [actions] under [key], cancelling what was still running under it first. [chainDepth] is
     * how many row-action lists led to this one -- see [ChainDepth].
     */
    fun run(actions: List<Action>, key: String = ANONYMOUS, chainDepth: Int = 0): Job = synchronized(runs) {
        if (key != ANONYMOUS) runs.remove(key)?.cancel()
        val job = scope.launch(ChainDepth(chainDepth)) { perform(actions) }
        if (key != ANONYMOUS) {
            runs[key] = job
            job.invokeOnCompletion { synchronized(runs) { if (runs[key] === job) runs.remove(key) } }
        }
        job
    }

    /** Stops the run going under [key], if one is. */
    fun cancel(key: String) {
        synchronized(runs) { runs.remove(key) }?.cancel()
    }

    /** Stops every keyed run -- what Clear All does to the actions still waiting. */
    fun cancelAll() {
        synchronized(runs) { runs.values.toList().also { runs.clear() } }.forEach { it.cancel() }
    }

    /** Whether a run is going under [key]. */
    fun isRunning(key: String): Boolean = synchronized(runs) { runs[key]?.isActive == true }

    /** Runs [actions] here, in order; [depth] is how many macros deep this list is. */
    suspend fun perform(actions: List<Action>, depth: Int = 0) {
        for (action in actions) {
            when (action) {
                is Action.Wait -> delay((action.seconds.coerceAtLeast(0.0) * MILLIS_PER_SECOND).toLong())
                is Action.RunMacro -> runMacro(action, depth)
                is Action.Unknown -> Unit
                else -> attempt(action) { host.dispatch(action) }
            }
        }
    }

    private suspend fun runMacro(action: Action.RunMacro, depth: Int) {
        val steps = host.macro(action.name)
        when {
            steps == null -> host.reportError(action, IllegalArgumentException("No macro called ${action.name}"))
            depth >= MAX_MACRO_DEPTH ->
                host.reportError(action, IllegalStateException("Macros nested deeper than $MAX_MACRO_DEPTH"))
            else -> perform(steps, depth + 1)
        }
    }

    // Any failure of one action is reported and passed over, whatever its type: a host call reaches
    // media, devices and the network, and one that escaped would end the run and, with it, the
    // scope it runs in. Cancellation is not a failure and goes on up.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun attempt(action: Action, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            host.reportError(action, e)
        }
    }

    companion object {
        /** How many macros deep a run may go before the next one is refused. */
        const val MAX_MACRO_DEPTH = 8

        /** How many row-action lists deep a chain of next and previous may go before it stops. */
        const val MAX_CHAIN_DEPTH = 8
        private const val ANONYMOUS = ""
        private const val MILLIS_PER_SECOND = 1000
    }
}

/** Hands [action] -- one that is neither flow nor unknown -- to its call on [this]. */
@Suppress("CyclomaticComplexMethod") // One branch per action, by design.
internal suspend fun ShowHost.dispatch(action: Action) {
    when (action) {
        is Action.GoLive -> goLive(action)
        is Action.ToPreview -> toPreview(action)
        is Action.Take -> take(action.layer)
        is Action.Clear -> clear(action.layer)
        Action.ClearAll -> clearAll()
        is Action.ClearGroup -> clearGroup(action.group)
        is Action.Message -> message(action)
        is Action.Prop -> prop(action)
        is Action.LowerThird -> lowerThird(action.preset)
        is Action.Timer -> timer(action)
        is Action.Media -> media(action.command)
        is Action.ObsScene -> obsScene(action.scene)
        is Action.AtemKey -> atemKey(action)
        is Action.AtemMacro -> atemMacro(action.index)
        is Action.CompanionPress -> companion(action)
        Action.NextItem -> next()
        Action.PreviousItem -> previous()
        is Action.Wait, is Action.RunMacro, is Action.Unknown -> Unit
    }
}

/**
 * How many row-action lists led to the run in this coroutine. A row reached by `next` or `previous`
 * runs its own list one deeper, so two rows that step to each other stop at
 * [ActionRunner.MAX_CHAIN_DEPTH] instead of looping.
 */
class ChainDepth(val depth: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ChainDepth>
}

/** The [ChainDepth] of the run calling this, 0 outside one. */
suspend fun currentChainDepth(): Int = currentCoroutineContext()[ChainDepth]?.depth ?: 0
