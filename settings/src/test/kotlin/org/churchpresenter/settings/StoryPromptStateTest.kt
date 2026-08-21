package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StoryPromptStateTest {

    private val install = 1_700_000_000_000L
    private val week = STORY_PROMPT_MILLIS_PER_WEEK
    private val month = STORY_PROMPT_MILLIS_PER_MONTH
    private val day = 24 * 60 * 60 * 1000L

    private fun weeksEndingAt(now: Long, count: Int): Set<Long> {
        val current = storyPromptWeekOf(now)
        return (0 until count).map { current - it }.toSet()
    }

    private fun earned(now: Long, installedAt: Long = install) = StoryPromptState(
        installedAtMillis = installedAt,
        activeWeeks = weeksEndingAt(now, STORY_PROMPT_REQUIRED_ACTIVE_WEEKS),
    )

    @Test
    fun `a fresh install has no date until a launch stamps one`() {
        assertEquals(0L, StoryPromptState().installedAtMillis)
    }

    @Test
    fun `the first launch stamps the install date`() {
        assertEquals(install, StoryPromptState().stampingInstall(install).installedAtMillis)
    }

    @Test
    fun `a later launch leaves the original install date alone`() {
        val stamped = StoryPromptState().stampingInstall(install)

        assertEquals(install, stamped.stampingInstall(install + month).installedAtMillis)
    }

    @Test
    fun `a launch records the week it happened in`() {
        val state = StoryPromptState().recordingUse(install)

        assertEquals(setOf(storyPromptWeekOf(install)), state.activeWeeks)
    }

    @Test
    fun `two launches in the same week record one week`() {
        val weekStart = storyPromptWeekOf(install) * week
        val state = StoryPromptState()
            .recordingUse(weekStart + day)
            .recordingUse(weekStart + 5 * day)

        assertEquals(1, state.activeWeeks.size)
    }

    @Test
    fun `launches either side of a week boundary record two weeks`() {
        val weekStart = storyPromptWeekOf(install) * week
        val state = StoryPromptState()
            .recordingUse(weekStart + 6 * day)
            .recordingUse(weekStart + week + day)

        assertEquals(2, state.activeWeeks.size)
    }

    @Test
    fun `four weekly launches record four weeks`() {
        var state = StoryPromptState()
        repeat(4) { state = state.recordingUse(install + it * week) }

        assertEquals(4, state.activeWeeks.size)
    }

    @Test
    fun `weeks older than the kept window are pruned`() {
        var state = StoryPromptState()
        repeat(12) { state = state.recordingUse(install + it * week) }

        assertEquals(8, state.activeWeeks.size)
        assertTrue(state.activeWeeks.contains(storyPromptWeekOf(install + 11 * week)))
        assertFalse(state.activeWeeks.contains(storyPromptWeekOf(install)))
    }

    @Test
    fun `pruning never drops a week the prompt still needs`() {
        var state = StoryPromptState()
        repeat(20) { state = state.recordingUse(install + it * week) }
        val now = install + 19 * week

        assertTrue(state.usedEveryWeek(now))
    }

    @Test
    fun `four unbroken weeks of use count as weekly use`() {
        val now = install + 4 * week

        assertTrue(earned(now).usedEveryWeek(now))
    }

    @Test
    fun `a skipped week in the middle breaks weekly use`() {
        val now = install + 4 * week
        val current = storyPromptWeekOf(now)
        val state = StoryPromptState(
            installedAtMillis = install,
            activeWeeks = setOf(current, current - 1, current - 3),
        )

        assertFalse(state.usedEveryWeek(now))
    }

    @Test
    fun `three weeks of use is not enough`() {
        val now = install + 4 * week
        val state = StoryPromptState(installedAtMillis = install, activeWeeks = weeksEndingAt(now, 3))

        assertFalse(state.usedEveryWeek(now))
    }

    @Test
    fun `four weeks that ended a while ago do not count as weekly use now`() {
        val stale = install + 4 * week
        val state = StoryPromptState(installedAtMillis = install, activeWeeks = weeksEndingAt(stale, 4))

        assertFalse(state.usedEveryWeek(stale + 5 * week))
    }

    @Test
    fun `a brand new install is never due`() {
        assertFalse(StoryPromptState().isDue(install))
    }

    @Test
    fun `an install with no stamped date is never due however much it is used`() {
        val now = install + 6 * month
        val state = StoryPromptState(activeWeeks = weeksEndingAt(now, 4))

        assertFalse(state.isDue(now))
    }

    @Test
    fun `a month of weekly use makes the prompt due`() {
        val now = install + month

        assertTrue(earned(now).isDue(now))
    }

    @Test
    fun `a month of weekly use is not due a day early`() {
        val now = install + month - day

        assertFalse(earned(now).isDue(now))
    }

    @Test
    fun `a month of use with a week skipped is not due`() {
        val now = install + month
        val current = storyPromptWeekOf(now)
        val state = StoryPromptState(
            installedAtMillis = install,
            activeWeeks = setOf(current, current - 1, current - 2),
        )

        assertFalse(state.isDue(now))
    }

    @Test
    fun `a church that answered is never asked again`() {
        val now = install + month

        assertFalse(earned(now).answered().isDue(now))
    }

    @Test
    fun `answering marks the prompt finished`() {
        assertTrue(StoryPromptState().answered().finished)
    }

    @Test
    fun `showing the prompt counts against the allowance and stamps the time`() {
        val now = install + month
        val shown = earned(now).shown(now)

        assertEquals(1, shown.timesShown)
        assertEquals(now, shown.lastShownAtMillis)
    }

    @Test
    fun `the prompt is not due again the day after it was shown`() {
        val first = install + month
        val state = earned(first).shown(first)
        val now = first + day

        assertFalse(state.copy(activeWeeks = weeksEndingAt(now, 4)).isDue(now))
    }

    @Test
    fun `the prompt is due again a month after it was last shown`() {
        val first = install + month
        val second = first + month
        val state = earned(first).shown(first).copy(activeWeeks = weeksEndingAt(second, 4))

        assertTrue(state.isDue(second))
    }

    @Test
    fun `the second interval is measured from the showing not from the install`() {
        val first = install + 3 * month
        val state = earned(first).shown(first)
        val now = first + month / 2

        assertFalse(state.copy(activeWeeks = weeksEndingAt(now, 4)).isDue(now))
    }

    @Test
    fun `the prompt appears four times over four months and then stops`() {
        var state = earned(install)
        var now = install
        var shown = 0

        repeat(6) {
            now += month
            state = state.copy(activeWeeks = weeksEndingAt(now, 4))
            if (state.isDue(now)) {
                state = state.shown(now)
                shown++
            }
        }

        assertEquals(STORY_PROMPT_MAX_SHOWS, shown)
        assertEquals(STORY_PROMPT_MAX_SHOWS, state.timesShown)
    }

    @Test
    fun `a spent allowance is never due again`() {
        val now = install + 12 * month
        val state = earned(now).copy(timesShown = STORY_PROMPT_MAX_SHOWS)

        assertFalse(state.isDue(now))
    }

    @Test
    fun `a church that stops using the app is not asked when it comes back after one week`() {
        val gap = install + 6 * month
        val state = StoryPromptState(
            installedAtMillis = install,
            activeWeeks = setOf(storyPromptWeekOf(gap)),
        )

        assertFalse(state.isDue(gap))
    }

    @Test
    fun `a church that comes back and uses it weekly again becomes due`() {
        val gap = install + 6 * month
        var state = StoryPromptState(installedAtMillis = install)
        repeat(4) { state = state.recordingUse(gap + it * week) }
        val now = gap + 3 * week

        assertTrue(state.isDue(now))
    }

    @Test
    fun `recording a launch never changes the install date or the allowance`() {
        val state = earned(install + month).shown(install + month)
        val recorded = state.recordingUse(install + month + week)

        assertEquals(state.installedAtMillis, recorded.installedAtMillis)
        assertEquals(state.timesShown, recorded.timesShown)
        assertEquals(state.lastShownAtMillis, recorded.lastShownAtMillis)
        assertNotEquals(state.activeWeeks, recorded.activeWeeks)
    }
}
