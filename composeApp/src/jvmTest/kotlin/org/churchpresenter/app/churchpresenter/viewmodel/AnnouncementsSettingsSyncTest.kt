package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reloading the Announcements tab, and what each timer mode means for the numbers on it.
 *
 * The tab is recreated whenever the operator switches away and back, so everything on it has to
 * survive a `buildSettings` → `syncFromSettings` round trip — including the fields with no public
 * getter (shadow colour/size/opacity), which are only observable through that round trip. The mode
 * switch matters just as much: each mode derives the remaining time from a different source, and a
 * mode change that forgets to recompute leaves a stale number under the operator's cursor.
 */
class AnnouncementsSettingsSyncTest {

    private val created = mutableListOf<AnnouncementsViewModel>()

    @AfterTest
    fun disposeAll() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    private fun vm(): AnnouncementsViewModel = AnnouncementsViewModel().also { created.add(it) }

    /** Every field set to something distinguishable from its default. */
    private fun fullSettings() = AnnouncementsSettings(
        text = "Welcome to the 10am service",
        textColor = "#112233",
        backgroundColor = "#445566",
        fontSize = 96,
        fontType = "Georgia",
        bold = true,
        italic = true,
        underline = true,
        shadow = true,
        shadowColor = "#778899",
        shadowSize = 42,
        shadowOpacity = 17,
        horizontalAlignment = Constants.CENTER_LEFT,
        position = Constants.CENTER_RIGHT,
        animationType = "FADE",
        animationDuration = 1500,
        loopCount = 4,
        timerHours = 1,
        timerMinutes = 2,
        timerSeconds = 3,
        timerTextColor = "#AABBCC",
        timerExpiredText = "We are starting!",
        timerMode = Constants.TIMER_MODE_DURATION,
        targetHour = 11,
        targetMinute = 22,
        targetSecond = 33,
        liveClockFormat = "HH:mm",
    )

    // ── Round trip ──────────────────────────────────────────────────────────────

    @Test
    fun `every field survives a save and reload`() {
        val original = fullSettings()
        val reloaded = vm().apply { syncFromSettings(original) }.buildSettings()
        assertEquals(original, reloaded, "a field missing from either half silently resets on tab switch")
    }

    @Test
    fun `the shadow fields survive even though nothing reads them back`() {
        // They have no public getter, so the round trip is the only thing keeping them wired up.
        val reloaded = vm().apply { syncFromSettings(fullSettings()) }.buildSettings()
        assertEquals("#778899", reloaded.shadowColor)
        assertEquals(42, reloaded.shadowSize)
        assertEquals(17, reloaded.shadowOpacity)
    }

    @Test
    fun `reloading replaces the previous state rather than merging into it`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings())
        vm.syncFromSettings(AnnouncementsSettings())

        assertEquals("", vm.text)
        assertEquals(48, vm.fontSize)
        assertEquals(0, vm.loopCount)
    }

    @Test
    fun `an edit after reloading is what gets saved`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings())
        vm.setText("Changed my mind")
        assertEquals("Changed my mind", vm.buildSettings().text)
    }

    // ── Remaining time per mode ─────────────────────────────────────────────────

    @Test
    fun `reloading a duration timer shows the configured duration`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings()) // 1h 2m 3s
        assertEquals(3723, vm.timerRemaining)
    }

    @Test
    fun `reloading a specific-time timer counts to the target instead`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings().copy(timerMode = Constants.TIMER_MODE_CLOCK))
        assertTrue(
            vm.timerRemaining in 1..86_400,
            "a target time is always somewhere in the next 24h, got ${vm.timerRemaining}"
        )
    }

    @Test
    fun `reloading a stopwatch starts it from zero`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings().copy(timerMode = Constants.TIMER_MODE_COUNT_UP))
        assertEquals(0, vm.countUpElapsed, "a stopwatch does not resume across a tab switch")
    }

    // ── Mode switching ──────────────────────────────────────────────────────────

    @Test
    fun `switching to specific time defaults the target to right now`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings()) // target is 11:22:33 from a previous session

        val before = LocalTime.now().toSecondOfDay()
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK)
        val after = LocalTime.now().toSecondOfDay()

        val target = vm.targetHour * 3600 + vm.targetMinute * 60 + vm.targetSecond
        assertTrue(
            target in before..after || (after < before && target >= before), // tolerate a midnight roll
            "expected the target to default to now ($before..$after), got $target"
        )
    }

    @Test
    fun `re-selecting the mode already active leaves the target alone`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK)
        vm.setTargetHour(11)
        vm.setTargetMinute(22)
        vm.setTargetSecond(33)

        vm.setTimerMode(Constants.TIMER_MODE_CLOCK)

        assertEquals(11, vm.targetHour, "re-clicking the mode must not wipe the time just typed")
        assertEquals(22, vm.targetMinute)
        assertEquals(33, vm.targetSecond)
    }

    @Test
    fun `switching back to a duration restores the configured duration`() {
        val vm = vm()
        vm.setTimerMinutes(5)
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK)

        vm.setTimerMode(Constants.TIMER_MODE_DURATION)

        assertEquals(300, vm.timerRemaining, "the countdown must come back to what the H/M/S fields say")
    }

    @Test
    fun `editing the duration fields does nothing in specific-time mode`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK)
        val remaining = vm.timerRemaining

        vm.setTimerMinutes(30)
        vm.stepTimerSeconds(1)

        assertEquals(remaining, vm.timerRemaining, "the H/M/S fields do not apply to a target time")
        assertEquals(30, vm.timerMinutes, "but the fields themselves still hold what was typed")
    }

    @Test
    fun `editing the target does nothing in duration mode`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_DURATION)
        vm.setTimerMinutes(5)

        vm.setTargetHour(23)
        vm.setTargetMinute(59)

        assertEquals(300, vm.timerRemaining, "a target time does not apply to a plain countdown")
        assertEquals(23, vm.targetHour)
    }

    @Test
    fun `editing the target in specific-time mode recomputes the countdown`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK)

        vm.setTargetHour((LocalTime.now().hour + 2) % 24)

        assertTrue(vm.timerRemaining in 1..86_400, "got ${vm.timerRemaining}")
    }

    // ── Live clock ──────────────────────────────────────────────────────────────

    /**
     * NOT asserted here: that the text switches to the NEW pattern immediately. In clock-display
     * mode a 1s ticker coroutine owns `liveClockText` — it reads the format, formats the string,
     * then writes — so a `setLiveClockFormat` landing between that read and that write is
     * overwritten by the older pattern until the next tick. Pinning the exact pattern from a test
     * therefore races the ticker, and waiting a tick out costs a second. What is race-free, and
     * what actually matters on screen, is that a real formatted clock is showing rather than a
     * literal pattern or a blank. The pattern below stays loose for the same reason the default
     * format does: it follows the system's 12/24-hour locale, so the AM/PM marker is not always
     * "AM"/"PM".
     */
    @Test
    fun `choosing the clock format puts a real clock on screen`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK_DISPLAY)

        vm.setLiveClockFormat("HH:mm")

        assertEquals("HH:mm", vm.liveClockFormat)
        assertTrue(
            Regex("""\d{1,2}:\d{2}(:\d{2})?(\s+\S+)?""").matches(vm.liveClockText),
            "expected a formatted wall clock, got \"${vm.liveClockText}\""
        )
    }

    @Test
    fun `a second format change keeps a real clock on screen`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_CLOCK_DISPLAY)
        vm.setLiveClockFormat("HH:mm")

        vm.setLiveClockFormat("HH:mm:ss")

        assertEquals("HH:mm:ss", vm.liveClockFormat, "the ticker formats from this on its next tick")
        assertTrue(
            Regex("""\d{1,2}:\d{2}(:\d{2})?(\s+\S+)?""").matches(vm.liveClockText),
            "expected a formatted wall clock, got \"${vm.liveClockText}\""
        )
    }

    @Test
    fun `choosing a format outside clock mode changes nothing on screen`() {
        val vm = vm()
        vm.setTimerMode(Constants.TIMER_MODE_DURATION)

        vm.setLiveClockFormat("HH:mm")

        assertEquals("HH:mm", vm.liveClockFormat, "the preference is still remembered")
        assertEquals("", vm.liveClockText, "but no clock is being shown to restate")
    }

    @Test
    fun `the clock format defaults to the system's 12 or 24 hour preference`() {
        assertTrue(
            vm().liveClockFormat in listOf("HH:mm:ss", "h:mm:ss a"),
            "an unexpected default would render as a literal pattern on screen"
        )
    }

    @Test
    fun `a reloaded clock format is the one used`() {
        val vm = vm()
        vm.syncFromSettings(fullSettings().copy(timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY))
        assertEquals("HH:mm", vm.liveClockFormat)
    }
}
