package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L

private const val MILLIS_PER_DAY =
    MILLIS_PER_SECOND * SECONDS_PER_MINUTE * MINUTES_PER_HOUR * HOURS_PER_DAY
internal const val STORY_PROMPT_MILLIS_PER_WEEK = MILLIS_PER_DAY * DAYS_PER_WEEK
internal const val STORY_PROMPT_MILLIS_PER_MONTH = MILLIS_PER_DAY * DAYS_PER_MONTH

internal const val STORY_PROMPT_MAX_SHOWS = 4
internal const val STORY_PROMPT_REQUIRED_ACTIVE_WEEKS = 4
private const val STORY_PROMPT_WEEKS_KEPT = 8

internal fun storyPromptWeekOf(millis: Long): Long = millis / STORY_PROMPT_MILLIS_PER_WEEK

@Serializable
data class StoryPromptState(
    val installedAtMillis: Long = 0L,
    val activeWeeks: Set<Long> = emptySet(),
    val timesShown: Int = 0,
    val lastShownAtMillis: Long = 0L,
    val finished: Boolean = false,
)

fun StoryPromptState.stampingInstall(nowMillis: Long): StoryPromptState =
    if (installedAtMillis != 0L) this else copy(installedAtMillis = nowMillis)

fun StoryPromptState.recordingUse(nowMillis: Long): StoryPromptState {
    val thisWeek = storyPromptWeekOf(nowMillis)
    val kept = (activeWeeks + thisWeek).filter { it > thisWeek - STORY_PROMPT_WEEKS_KEPT }.toSet()
    return copy(activeWeeks = kept)
}

internal fun StoryPromptState.usedEveryWeek(nowMillis: Long): Boolean {
    val thisWeek = storyPromptWeekOf(nowMillis)
    return (0 until STORY_PROMPT_REQUIRED_ACTIVE_WEEKS).all { thisWeek - it in activeWeeks }
}

fun StoryPromptState.isDue(nowMillis: Long): Boolean {
    if (finished) return false
    if (installedAtMillis == 0L) return false
    if (timesShown >= STORY_PROMPT_MAX_SHOWS) return false
    val since = if (lastShownAtMillis == 0L) installedAtMillis else lastShownAtMillis
    if (nowMillis - since < STORY_PROMPT_MILLIS_PER_MONTH) return false
    return usedEveryWeek(nowMillis)
}

fun StoryPromptState.shown(nowMillis: Long): StoryPromptState =
    copy(timesShown = timesShown + 1, lastShownAtMillis = nowMillis)

fun StoryPromptState.answered(): StoryPromptState = copy(finished = true)
