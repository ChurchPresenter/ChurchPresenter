package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.launch
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.message_title
import org.churchpresenter.strings.generated.resources.props_title
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_live_announcement
import org.churchpresenter.strings.generated.resources.helper_live_countdown
import org.churchpresenter.strings.generated.resources.helper_live_nothing
import org.churchpresenter.strings.generated.resources.helper_live_now
import org.churchpresenter.strings.generated.resources.helper_version
import org.churchpresenter.updater.UpdateChecker

// What the helper says when asked what is live or which version this is.

private const val SECONDS_PER_MINUTE = 60

/** What each live layer shows, in words: the verse, the song, the announcement, or the tab it is from. */
internal fun AppRootState.helperWhatsLive(): ActionOutcome {
    val live = presenterManager.liveContent.value
    if (live.isEmpty()) return ActionOutcome.Done(helperText(Res.string.helper_live_nothing))
    val parts = live.mapNotNull { mode -> describeLive(mode) }
    return ActionOutcome.Done(helperText(Res.string.helper_live_now, HelperText.Joined(parts)))
}

private fun AppRootState.describeLive(mode: Presenting): HelperText? = when (mode) {
    Presenting.NONE -> null
    Presenting.BIBLE -> presenterManager.displayedVerses.value.takeIf { it.isNotEmpty() }?.let { verses ->
        val first = verses.first()
        val last = verses.last()
        val span = if (verses.size > 1) "${first.verseNumber}-${last.verseNumber}" else "${first.verseNumber}"
        HelperText.Plain("${first.bookName} ${first.chapter}:$span")
    } ?: helperTabName(Tabs.BIBLE)
    Presenting.LYRICS -> presenterManager.displayedLyricSection.value.title.takeIf { it.isNotBlank() }
        ?.let { HelperText.Plain(it) } ?: helperTabName(Tabs.SONGS)
    Presenting.ANNOUNCEMENTS -> if (presenterManager.timerRunning.value) {
        val left = presenterManager.timerRemainingSeconds.value
        val clock = "%d:%02d".format(left / SECONDS_PER_MINUTE, left % SECONDS_PER_MINUTE)
        helperText(Res.string.helper_live_countdown, clock)
    } else {
        helperText(Res.string.helper_live_announcement, presenterManager.announcementText.value)
    }
    Presenting.PICTURES -> helperTabName(Tabs.PICTURES)
    Presenting.PRESENTATION -> helperTabName(Tabs.PRESENTATION)
    Presenting.MEDIA -> helperTabName(Tabs.MEDIA)
    Presenting.LOWER_THIRD -> helperTabName(Tabs.LOWER_THIRD)
    Presenting.WEBSITE -> helperTabName(Tabs.WEB)
    Presenting.CANVAS -> helperTabName(Tabs.CANVAS)
    Presenting.QA -> helperTabName(Tabs.QA)
    Presenting.STT -> helperTabName(Tabs.STT)
    Presenting.DICTIONARY -> helperTabName(Tabs.DICTIONARY)
    Presenting.MESSAGE -> helperText(Res.string.message_title)
    Presenting.PROPS -> helperText(Res.string.props_title)
}

/** Says the version, then checks the way Help → Check for Updates does, whose window gives the answer. */
internal fun AppRootState.helperCheckForUpdates(): ActionOutcome {
    coroutineScope.launch {
        pendingUpdateResult = UpdateChecker.checkForUpdate(includePrereleases = appSettings.participateInPrereleases)
        pendingUpdateCheckWasManual = true
        appSettings = appSettings.copy(lastUpdateCheckTimestamp = System.currentTimeMillis())
        settingsManager.saveSettings(appSettings)
    }
    return ActionOutcome.Done(helperText(Res.string.helper_version, BuildConfig.VERSION_DISPLAY))
}
