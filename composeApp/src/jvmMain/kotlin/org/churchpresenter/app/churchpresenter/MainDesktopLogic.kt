package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.Key
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.tabs.ScheduleToolbarButton
import org.churchpresenter.sharedui.models.Tabs

/**
 * The decisions the root composable makes, held apart from the composables that make them: here the
 * tabs, the hidden key sequences and the Schedule rows. The panel, Bible, slide, Instance Link and
 * off-screen decisions sit beside this file in `PanelLayoutLogic.kt`, `BibleWiringLogic.kt`,
 * `SlideAndPictureLogic.kt`, `InstanceLinkLogic.kt` and `MainDesktopOutputLogic.kt`.
 *
 * Everything in them is pure: no Compose, no view models, no I/O. Anything that needs those stays in
 * the composable and is reached through one of these.
 */

internal fun computeVisibleTabs(
    hiddenTabs: Set<String>,
    showCrosswordTab: Boolean,
    hasCompanionTabConnections: Boolean,
): List<Tabs> =
    (Tabs.entries.filter { tab ->
        tab != Tabs.CROSSWORD && tab.name !in hiddenTabs &&
            (tab != Tabs.COMPANION_SURFACE || hasCompanionTabConnections)
    } + if (showCrosswordTab) listOf(Tabs.CROSSWORD) else emptyList())
        .ifEmpty { listOf(Tabs.BIBLE) }

internal fun clampedTabIndex(selectedTabIndex: Int, visibleTabs: List<Tabs>): Int =
    selectedTabIndex.coerceIn(visibleTabs.indices)

internal fun resolveTabSelection(tab: Tabs, visibleTabs: List<Tabs>, currentIndex: Int): Int {
    val idx = visibleTabs.indexOf(tab)
    return if (idx >= 0) idx else currentIndex
}

internal data class SequenceStep(val progress: Int, val completed: Boolean)

internal fun advanceKeySequence(pressedKey: Key, sequence: List<Key>, currentProgress: Int): SequenceStep {
    val expected = sequence.getOrNull(currentProgress)
    if (pressedKey == expected) {
        val next = currentProgress + 1
        return if (next == sequence.size) SequenceStep(progress = 0, completed = true)
        else SequenceStep(progress = next, completed = false)
    }
    return SequenceStep(progress = if (pressedKey == sequence[0]) 1 else 0, completed = false)
}

internal fun visibleTabCount(hiddenTabs: Set<String>): Int =
    Tabs.entries.count { it != Tabs.CROSSWORD && it.name !in hiddenTabs }

internal fun isOnlyVisibleTab(tab: Tabs, hiddenTabs: Set<String>, visibleCount: Int): Boolean =
    tab.name !in hiddenTabs && visibleCount == 1

internal fun toggleHiddenTabs(hiddenTabs: Set<String>, tab: Tabs): Set<String> =
    if (tab.name !in hiddenTabs) hiddenTabs + tab.name else hiddenTabs - tab.name

/** The schedule toolbar's own version of [toggleHiddenTabs]; unlike tabs, hiding them all is allowed. */
internal fun toggleHiddenScheduleButton(hidden: Set<String>, button: ScheduleToolbarButton): Set<String> =
    if (button.name !in hidden) hidden + button.name else hidden - button.name

internal fun tabForScheduleItem(item: ScheduleItem): Tabs? = when (item) {
    is ScheduleItem.SongItem -> Tabs.SONGS
    is ScheduleItem.BibleVerseItem -> Tabs.BIBLE
    is ScheduleItem.LabelItem -> null
    is ScheduleItem.PictureItem -> Tabs.PICTURES
    is ScheduleItem.PresentationItem -> Tabs.PRESENTATION
    is ScheduleItem.MediaItem -> Tabs.MEDIA
    is ScheduleItem.LowerThirdItem -> Tabs.LOWER_THIRD
    is ScheduleItem.AnnouncementItem -> Tabs.ANNOUNCEMENTS
    is ScheduleItem.WebsiteItem -> Tabs.WEB
    is ScheduleItem.SceneItem -> Tabs.CANVAS
    is ScheduleItem.DictionaryItem -> Tabs.DICTIONARY
    // A cue fires from where it is; there is no tab to go to. An off-screen row never goes anywhere.
    is ScheduleItem.CueItem, is ScheduleItem.MinistryItem -> null
}

/**
 * Applies a scheduled announcement onto [settings], so the Announcements tab and the output show
 * exactly what was saved into the service order.
 *
 * Pulled out of the two composable lambdas that used to carry it — going live with a scheduled
 * announcement, and selecting its row — which held **byte-identical** 27-field copies. Every field
 * of `AnnouncementsSettings` comes from the item, so a field added to one side and forgotten on the
 * other silently drops it; and a mis-typed pairing (`targetMinute = item.targetSecond`) puts the
 * wrong countdown on screen with nothing to see until it is live.
 */
internal fun withAnnouncementFrom(settings: AppSettings, item: ScheduleItem.AnnouncementItem): AppSettings =
    settings.copy(
        announcementsSettings = settings.announcementsSettings.copy(
            text                = item.text,
            textColor           = item.textColor,
            backgroundColor     = item.backgroundColor,
            fontSize            = item.fontSize,
            fontType            = item.fontType,
            bold                = item.bold,
            italic              = item.italic,
            underline           = item.underline,
            shadow              = item.shadow,
            shadowColor         = item.shadowColor,
            shadowSize          = item.shadowSize,
            shadowOpacity       = item.shadowOpacity,
            horizontalAlignment = item.horizontalAlignment,
            position            = item.position,
            animationType       = item.animationType,
            animationDuration   = item.animationDuration,
            loopCount           = item.loopCount,
            timerHours          = item.timerHours,
            timerMinutes        = item.timerMinutes,
            timerSeconds        = item.timerSeconds,
            timerTextColor      = item.timerTextColor,
            timerExpiredText    = item.timerExpiredText,
            timerMode           = item.timerMode,
            targetHour          = item.targetHour,
            targetMinute        = item.targetMinute,
            targetSecond        = item.targetSecond,
            liveClockFormat     = item.liveClockFormat,
            backdrop            = item.backdrop,
            outline             = item.outline,
        )
    )
