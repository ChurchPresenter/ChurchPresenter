package org.churchpresenter.showcontrol

/**
 * What runs an [Action]: the app's live output, schedule and integrations, one call per action.
 * Flow -- waits and macros -- is the [ActionRunner]'s, so a host only ever does one thing at a time.
 */
@Suppress("TooManyFunctions") // One call per action, by design.
interface ShowHost {
    suspend fun goLive(action: Action.GoLive)
    suspend fun toPreview(action: Action.ToPreview)
    suspend fun take(layer: String)
    suspend fun clear(layer: String)
    suspend fun clearAll()
    suspend fun clearGroup(group: String)
    suspend fun message(action: Action.Message)
    suspend fun prop(action: Action.Prop)
    suspend fun lowerThird(preset: String)
    suspend fun timer(action: Action.Timer)
    suspend fun media(command: MediaCommand)
    suspend fun obsScene(scene: String)
    suspend fun atemKey(action: Action.AtemKey)
    suspend fun atemMacro(index: Int)
    suspend fun companion(action: Action.CompanionPress)
    suspend fun next()
    suspend fun previous()

    /** The actions of the macro called [name], or null when there is none. */
    fun macro(name: String): List<Action>?

    /** [action] failed, or could not be run; the rest of its list runs on. */
    fun reportError(action: Action, error: Throwable)
}
