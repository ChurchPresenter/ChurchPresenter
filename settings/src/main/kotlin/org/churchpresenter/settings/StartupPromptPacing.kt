package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/** No prompt has opened unasked yet — the first launch that wants one may have it. */
internal const val NO_PROMPT_LAUNCH = -1

/** A launch that showed a prompt leaves the next one quiet, so prompts land every other start at most. */
internal const val LAUNCHES_BETWEEN_PROMPTS = 2

/**
 * How the dialogs that open on their own at startup — the setup wizard, the first update check, the
 * story prompt — are spread across launches, so a new install meets one a start rather than a stack.
 *
 * [launches] counts starts; [lastPromptLaunch] is the start that last showed one.
 */
@Serializable
data class StartupPromptPacing(
    val launches: Int = 0,
    val lastPromptLaunch: Int = NO_PROMPT_LAUNCH,
)

/** Counts this start. */
fun StartupPromptPacing.launched(): StartupPromptPacing = copy(launches = launches + 1)

/** Whether this start may open a prompt: none has opened yet, or none did on this start or the one before. */
fun StartupPromptPacing.promptAllowed(): Boolean =
    lastPromptLaunch == NO_PROMPT_LAUNCH || launches - lastPromptLaunch >= LAUNCHES_BETWEEN_PROMPTS

/** Records that this start showed a prompt. */
fun StartupPromptPacing.prompted(): StartupPromptPacing = copy(lastPromptLaunch = launches)
