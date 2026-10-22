package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.churchpresenter.settings.isDue
import org.churchpresenter.settings.launched
import org.churchpresenter.settings.promptAllowed
import org.churchpresenter.settings.prompted
import org.churchpresenter.settings.recordingUse
import org.churchpresenter.settings.shown
import org.churchpresenter.settings.stampingInstall
import org.churchpresenter.updater.UpdateCheckResult
import org.churchpresenter.updater.UpdateChecker

/*
 * The dialogs that open at startup without being asked for, spread across launches: one a start at
 * most, and a quiet start after it (StartupPromptPacing). The licence is not one of them — nothing
 * can be used before it is accepted.
 */

/**
 * Every dialog that opens at startup unasked. **A new one is added here and opened only through
 * [claimStartupPrompt]** — `StartupPromptsTest` fails on a start that leaves two dialogs up.
 *
 * [ignoresPacing] is for what cannot wait a start: it still takes the start, so the next one is quiet.
 */
internal enum class StartupPrompt(val ignoresPacing: Boolean = false) {
    SETUP_WIZARD,

    /** The first update check ever, shown whatever it found, to ask how often to check. */
    FIRST_UPDATE_CHECK,

    /** A newer version than the one running. */
    UPDATE_AVAILABLE(ignoresPacing = true),
    WICK_INTRO,
    STORY_PROMPT,
}

/** Takes this start's dialog for [prompt], or says no: one already opened on this start or the one before. */
internal fun AppRootState.claimStartupPrompt(prompt: StartupPrompt): Boolean {
    val pacing = appSettings.startupPrompts
    if (!prompt.ignoresPacing && !pacing.promptAllowed()) return false
    appSettings = appSettings.copy(startupPrompts = pacing.prompted())
    settingsManager.saveSettings(appSettings)
    return true
}

/** Counts this start and lets the setup wizard, when due, take its dialog — before the window first shows it. */
internal fun AppRootState.beginStartupPrompts() {
    appSettings = appSettings.copy(startupPrompts = appSettings.startupPrompts.launched())
    settingsManager.saveSettings(appSettings)
    if (showSetupWizard && !claimStartupPrompt(StartupPrompt.SETUP_WIZARD)) showSetupWizard = false
}

/**
 * The update check, Wick's intro and the story prompt, each opening only when this start's dialog is
 * still free. [checkForUpdate] asks GitHub, and [storyDelayMs] lets the window settle first; a test
 * answers the one and skips the other.
 */
internal suspend fun AppRootState.openStartupPrompts(
    checkForUpdate: suspend (includePrereleases: Boolean) -> UpdateCheckResult = UpdateChecker::checkForUpdate,
    storyDelayMs: Long = STORY_PROMPT_DELAY_MS,
) {
    val isFirstEverUpdateCheck = isFirstEverUpdateCheck(appSettings.lastUpdateCheckTimestamp)
    val updateCheckDue = appSettings.updateCheckInterval.isDueSince(appSettings.lastUpdateCheckTimestamp)
    // The first check ever always opens a dialog, so it runs only on a start that can show one.
    val promptAllowed = appSettings.startupPrompts.promptAllowed()
    if (shouldRunStartupUpdateCheck(updateCheckDue, isFirstEverUpdateCheck, promptAllowed)) {
        val result = checkForUpdate(appSettings.participateInPrereleases)
        appSettings = appSettings.copy(lastUpdateCheckTimestamp = System.currentTimeMillis())
        settingsManager.saveSettings(appSettings)
        val skipped = appSettings.skippedUpdateVersion
        pendingUpdateFor(isFirstEverUpdateCheck, result, skipped)?.let { (pending, manual) ->
            val prompt = if (manual) StartupPrompt.FIRST_UPDATE_CHECK else StartupPrompt.UPDATE_AVAILABLE
            if (claimStartupPrompt(prompt)) {
                pendingUpdateResult = pending
                pendingUpdateCheckWasManual = manual
            }
        }
    }
    startupChecksDone = true
    // Wick's first intro, if it would show now, has to win the dialog; otherwise it waits for another start.
    // Both are set before the next frame, so the intro is never drawn in between.
    wickIntroDeferred = !(wickIntroShowing && claimStartupPrompt(StartupPrompt.WICK_INTRO))

    val now = System.currentTimeMillis()
    val storyPrompt = appSettings.storyPrompt.stampingInstall(now).recordingUse(now)
    appSettings = appSettings.copy(storyPrompt = storyPrompt)
    settingsManager.saveSettings(appSettings)
    val storyDue = storyPrompt.isDue(now) && appSettings.startupPrompts.promptAllowed()
    if (shouldShowStoryPrompt(storyDue, updatePending = pendingUpdateResult != null)) {
        delay(storyDelayMs)
        // Never on top of Wick's intro: it waits until that is finished or skipped.
        snapshotFlow { wickIntroShowing }.first { !it }
        if (claimStartupPrompt(StartupPrompt.STORY_PROMPT)) {
            appSettings = appSettings.copy(storyPrompt = storyPrompt.shown(System.currentTimeMillis()))
            settingsManager.saveSettings(appSettings)
            showStoryPrompt = true
        }
    }
}
