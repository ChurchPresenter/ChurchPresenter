package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.isWickAvailable

// Wick in production: nothing of it appears until the operator starts it from Help → Show Helper.

/**
 * Whether "Meet Wick" is on screen: asked for again, or due the first time — after the licence, the
 * setup wizard and the startup update check, and never beside the update window, the story prompt
 * or a live service, so only one of them is ever up at once.
 */
internal val AppRootState.wickIntroShowing: Boolean
    get() {
        if (helperState.replayIntro) return true
        val helper = appSettings.helper
        return wickAvailable && !helper.introSeen && helper.enabled && appReady && eulaAccepted &&
            !showSetupWizard && startupChecksDone && pendingUpdateResult == null && !showStoryPrompt &&
            presenterManager.liveContent.value.isEmpty()
    }

/**
 * Whether Wick is here at all: always in dev mode, otherwise only once the operator has started it
 * from Help → Show Helper. Whether the lamp shows is then [HelperSettings.enabled].
 */
internal val AppRootState.wickAvailable: Boolean
    get() = isWickAvailable(devMode, appSettings.helper)

/**
 * Help → Show Helper: starts Wick for good and puts the lamp back if it was hidden. The first time it
 * plays the intro; after that it opens the bubble.
 */
internal fun AppRootState.showHelper() {
    val helper = appSettings.helper
    if (!helper.enabled || !helper.startedByUser) {
        saveHelperSettings(helper.copy(enabled = true, startedByUser = true))
    }
    if (helper.introSeen) helperState.isOpen = true else helperState.replayIntro = true
}
