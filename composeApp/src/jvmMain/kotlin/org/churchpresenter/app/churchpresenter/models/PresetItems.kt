package org.churchpresenter.app.churchpresenter.models

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import java.util.UUID

/**
 * The item a tab's **Save preset** writes — built the same way its Add to Schedule builds the row,
 * so a preset and a schedule row made from the same tab state are the same thing.
 */
fun announcementPresetItem(settings: AnnouncementsSettings): ScheduleItem.AnnouncementItem {
    val isTimer = settings.timerMode != Constants.TIMER_MODE_DURATION ||
        settings.timerHours > 0 || settings.timerMinutes > 0 || settings.timerSeconds > 0
    return ScheduleItem.AnnouncementItem(
        id = UUID.randomUUID().toString(),
        text = settings.text,
        textColor = settings.textColor,
        backgroundColor = settings.backgroundColor,
        fontSize = settings.fontSize,
        fontType = settings.fontType,
        bold = settings.bold,
        italic = settings.italic,
        underline = settings.underline,
        shadow = settings.shadow,
        shadowColor = settings.shadowColor,
        shadowSize = settings.shadowSize,
        shadowOpacity = settings.shadowOpacity,
        horizontalAlignment = settings.horizontalAlignment,
        position = settings.position,
        animationType = settings.animationType,
        animationDuration = settings.animationDuration,
        loopCount = settings.loopCount,
        isTimer = isTimer,
        timerHours = settings.timerHours,
        timerMinutes = settings.timerMinutes,
        timerSeconds = settings.timerSeconds,
        timerTextColor = settings.timerTextColor,
        timerExpiredText = settings.timerExpiredText,
        timerMode = settings.timerMode,
        targetHour = settings.targetHour,
        targetMinute = settings.targetMinute,
        targetSecond = settings.targetSecond,
        liveClockFormat = settings.liveClockFormat,
        backdrop = settings.backdrop,
        outline = settings.outline,
    )
}
