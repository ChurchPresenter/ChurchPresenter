@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.TimerStateManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Clock & Timer source, which is four controls in one: a wall clock, a countdown, a stopwatch and
 * a countdown to a time of day.
 *
 * The mode is what makes the difference — each unfolds its own fields, and two of them a transport.
 * That read-out is the only thing on the whole panel that is not stored on the source at all: it
 * comes from `TimerStateManager`, a process-wide singleton keyed by source id, which is why every
 * test here uses an id of its own. Sharing one would leave a running timer behind for whichever test
 * ran next.
 *
 * The two dropdowns both map between a stored constant and a shown label in both directions, so both
 * directions are walked; and because each maps "anything else" onto its own default, a stored value
 * this build does not know is pinned as well.
 */
class SourcePropertiesClockTest {

    /** Timers are process-wide and their tickers are real coroutines — see [TimerStateManager.clear]. */
    @AfterTest
    fun stopTimers() = TimerStateManager.clear()

    /** Ordinals of the clock panel's fields — the header owns the first six. */
    private object Field {
        const val LETTER_SPACING = 6
        const val CURVE = 7
        const val FONT_SIZE = 8
        const val TARGET_HOUR = 9
        const val TARGET_MINUTE = 10
        const val TARGET_SECOND = 11
        const val EXPIRED_TEXT = 12

        // The time of day a Specific Time clock counts to takes the same three slots.
        const val TIME_HOUR = 9
        const val TIME_MINUTE = 10
        const val TIME_SECOND = 11
    }

    /** Ordinals of the panel's checkboxes, in composition order. Bold is a button now, not a box. */
    private object Check {
        const val SHOW_HOURS = 0
        const val SHOW_SECONDS = 1
        const val COUNT = 2
    }

    private fun countdown(id: String, minutes: Int = 5) =
        Fixture.clock(id).copy(mode = "countdown", targetMinute = minutes)

    // ── A wall clock ──────────────────────────────────────────────────────────

    @Test
    fun `the section is headed and every clock control captioned`() = sourcePanel(Fixture.clock("clk-caps")) { _ ->
        listOf(
            "MODE", "FORMAT", "Show Hours", "Show Seconds", "Letter Spacing", "Curve", "FONT SIZE",
            "TEXT COLOR", "BACKGROUND COLOR",
        ).forEach { caption ->
            onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the clock panel")
        }
        assertEquals(1, countOf(Label.CLOCK), "the section is headed for all four of its modes")
        assertEquals(1, countOf("Clock"), "and the mode dropdown reads the one it is in")
    }

    @Test
    fun `a wall clock offers three fields, two checkboxes and no timer`() =
        sourcePanel(Fixture.clock("clk-shape")) { _ ->
            textFields().assertCountEquals(9)
            checkboxes().assertCountEquals(Check.COUNT)
            listOf("HR", "MIN", "SEC", "Start", "Reset")
                .forEach { assertEquals(0, countOf(it), "\"$it\" belongs to the countdown") }
            roleButtons().assertCountEquals(0)
        }

    @Test
    fun `every stored value is shown by the control that owns it`() {
        val styled = Fixture.clock("clk-shows").copy(fontSize = 120, fontColor = "#00FF00", backgroundColor = "#101010")
        sourcePanel(styled) { _ ->
            assertFieldShows("120", "the font size field")
            onNodeWithText("#00FF00").assertExists("the text colour field reads out its hex")
            onNodeWithText("#101010").assertExists("the background colour field reads out its hex")
        }
    }

    // ── Mode ──────────────────────────────────────────────────────────────────

    @Test
    fun `the mode dropdown names the stored mode`() {
        listOf(
            "clock" to "Clock",
            "countdown" to "Countdown",
            "count_up" to "Count Up",
            "target_time" to "Specific Time",
        ).forEach { (stored, shown) ->
            sourcePanel(Fixture.clock("clk-mode-$stored").copy(mode = stored)) { _ ->
                assertEquals(1, countOf(shown), "$stored must read as \"$shown\"")
            }
        }
    }

    @Test
    fun `a mode this build does not know reads as Clock`() {
        sourcePanel(Fixture.clock("clk-mode-odd").copy(mode = "stopwatch")) { _ ->
            assertEquals(0, countOf("stopwatch"), "an unrecognised mode must not show itself")
            assertEquals(0, countOf("Countdown"), "and must not be treated as a countdown")
            assertEquals(0, countOf("Count Up"))
            assertEquals(0, countOf("Specific Time"))
        }
    }

    @Test
    fun `choosing Countdown stores it and unfolds the countdown controls`() =
        sourcePanel(Fixture.clock("clk-to-countdown")) { get ->
            chooseFromDropdown(showing = "Clock", option = "Countdown")

            assertEquals("countdown", (get() as SceneSource.ClockSource).mode)
            onNodeWithText("HR").assertExists("the duration fields appear with it, as one hr:min:sec row")
            onNodeWithText("MIN").assertExists()
            onNodeWithText("SEC").assertExists()
            assertEquals(2, countOf(":"), "with a colon between each pair")
            onNodeWithText("TEXT WHEN TIMER EXPIRES").assertExists("and the message shown at zero")
            textFields().assertCountEquals(13)
        }

    @Test
    fun `choosing Clock folds the countdown controls away again`() {
        sourcePanel(countdown("clk-to-clock")) { get ->
            chooseFromDropdown(showing = "Countdown", option = "Clock")

            assertEquals("clock", (get() as SceneSource.ClockSource).mode)
            assertEquals(0, countOf("HR"), "the duration fields go with it")
            textFields().assertCountEquals(9)
        }
    }

    // ── Time format ───────────────────────────────────────────────────────────

    @Test
    fun `the format dropdown names the stored format`() {
        listOf("24h" to "24h", "12h" to "12h").forEach { (stored, shown) ->
            sourcePanel(Fixture.clock("clk-fmt-$stored").copy(timeFormat = stored)) { _ ->
                assertEquals(1, countOf(shown), "$stored must read as \"$shown\"")
            }
        }
    }

    @Test
    fun `a format this build does not know reads as 24h`() {
        sourcePanel(Fixture.clock("clk-fmt-odd").copy(timeFormat = "swatch-beats")) { _ ->
            onNodeWithText("24h").assertExists("an unrecognised format must name a real option")
            assertEquals(0, countOf("swatch-beats"))
        }
    }

    @Test
    fun `choosing 12h stores it`() = sourcePanel(Fixture.clock("clk-12h")) { get ->
        chooseFromDropdown(showing = "24h", option = "12h")

        assertEquals(
            Fixture.clock("clk-12h").copy(timeFormat = "12h"), get(),
            "the format dropdown may write only the format",
        )
    }

    @Test
    fun `choosing 24h stores it`() {
        sourcePanel(Fixture.clock("clk-24h").copy(timeFormat = "12h")) { get ->
            chooseFromDropdown(showing = "12h", option = "24h")

            assertEquals("24h", (get() as SceneSource.ClockSource).timeFormat)
        }
    }

    // ── The three flags ───────────────────────────────────────────────────────

    @Test
    fun `both display flags are on out of the box, and the clock starts bold`() =
        sourcePanel(Fixture.clock("clk-flags")) { get ->
            checkboxes()[Check.SHOW_HOURS].assertIsOn()
            checkboxes()[Check.SHOW_SECONDS].assertIsOn()
            assertEquals(true, (get() as SceneSource.ClockSource).bold, "a read-out is bold by default")
        }

    @Test
    fun `unticking Show Hours flips only that flag`() = sourcePanel(Fixture.clock("clk-hours")) { get ->
        toggleCheckbox(Check.SHOW_HOURS)

        assertEquals(
            Fixture.clock("clk-hours").copy(showHours = false), get(),
            "Show Hours may write only its own flag",
        )
        checkboxes()[Check.SHOW_HOURS].assertIsOff()
    }

    @Test
    fun `unticking Show Seconds flips only that flag`() = sourcePanel(Fixture.clock("clk-seconds")) { get ->
        toggleCheckbox(Check.SHOW_SECONDS)

        assertEquals(
            Fixture.clock("clk-seconds").copy(showSeconds = false), get(),
            "Show Seconds may write only its own flag",
        )
        checkboxes()[Check.SHOW_SECONDS].assertIsOff()
    }

    @Test
    fun `the Bold button turns the clock's own bold off again`() = sourcePanel(Fixture.clock("clk-bold")) { get ->
        clickStyleButton("B")

        assertEquals(
            Fixture.clock("clk-bold").copy(bold = false), get(),
            "B may write only the bold flag",
        )
    }

    @Test
    fun `Italic, Underline and Strikethrough each store their own face`() {
        sourcePanel(Fixture.clock("clk-italic")) { get ->
            clickStyleButton("I")
            assertEquals(Fixture.clock("clk-italic").copy(italic = true), get())
        }
        sourcePanel(Fixture.clock("clk-underline")) { get ->
            clickStyleButton("U")
            assertEquals(Fixture.clock("clk-underline").copy(underline = true), get())
        }
        sourcePanel(Fixture.clock("clk-strike")) { get ->
            clickStyleButton("S")
            assertEquals(Fixture.clock("clk-strike").copy(strikethrough = true), get())
        }
    }

    // ── Letter spacing and curve ──────────────────────────────────────────────

    @Test
    fun `typing a letter spacing stores it`() = sourcePanel(Fixture.clock("clk-tracking")) { get ->
        commitField(Field.LETTER_SPACING, "30")

        assertEquals(30f, (get() as SceneSource.ClockSource).letterSpacing)
    }

    @Test
    fun `dragging the curve arches the read-out`() = sourcePanel(Fixture.clock("clk-curve")) { get ->
        tapSliderUnder("Curve", fraction = 1f, gapDp = Gap.INPUT)

        assertEquals(200f, (get() as SceneSource.ClockSource).curve)
    }

    @Test
    fun `the read-out is straight and evenly spaced out of the box`() =
        sourcePanel(Fixture.clock("clk-plain")) { get ->
            val source = get() as SceneSource.ClockSource
            assertEquals(0f, source.curve)
            assertEquals(0f, source.letterSpacing)
        }

    @Test
    fun `a flag that was stored off can be turned back on`() {
        sourcePanel(Fixture.clock("clk-reon").copy(showHours = false)) { get ->
            checkboxes()[Check.SHOW_HOURS].assertIsOff()
            toggleCheckbox(Check.SHOW_HOURS)

            assertEquals(true, (get() as SceneSource.ClockSource).showHours)
            checkboxes()[Check.SHOW_HOURS].assertIsOn()
        }
    }

    // ── Font size and colours ─────────────────────────────────────────────────

    @Test
    fun `typing a font size stores it`() = sourcePanel(Fixture.clock("clk-size")) { get ->
        typeField(Field.FONT_SIZE, "150")

        assertEquals(150, (get() as SceneSource.ClockSource).fontSize)
        assertFieldShows("150", "the font size field")
    }

    @Test
    fun `a font size below the minimum is raised to it`() = sourcePanel(Fixture.clock("clk-small")) { get ->
        typeField(Field.FONT_SIZE, "2")

        assertEquals(8, (get() as SceneSource.ClockSource).fontSize, "the smallest clock is 8pt")
    }

    @Test
    fun `a font size above the maximum is lowered to it`() = sourcePanel(Fixture.clock("clk-big")) { get ->
        typeField(Field.FONT_SIZE, "9000")

        assertEquals(500, (get() as SceneSource.ClockSource).fontSize, "the largest clock is 500pt")
    }

    @Test
    fun `text that is not a number leaves the font size alone`() = sourcePanel(Fixture.clock("clk-nan")) { get ->
        typeField(Field.FONT_SIZE, "large")

        assertEquals(64, (get() as SceneSource.ClockSource).fontSize)
    }

    @Test
    fun `recolouring the text stores the new hex`() = sourcePanel(Fixture.clock("clk-fg")) { get ->
        recolor(fromHex = "#FFFFFF", toHex = "#FFAA00")

        val source = get() as SceneSource.ClockSource
        assertEquals("#FFAA00", source.fontColor)
        assertEquals("#00000000", source.backgroundColor, "and the background is untouched")
    }

    @Test
    fun `recolouring the background stores the new hex`() = sourcePanel(Fixture.clock("clk-bg")) { get ->
        recolor(fromHex = "#00000000", toHex = "#222222")

        val source = get() as SceneSource.ClockSource
        assertEquals("#222222", source.backgroundColor)
        assertEquals("#FFFFFF", source.fontColor, "and the text colour is untouched")
    }

    // ── Countdown durations ───────────────────────────────────────────────────

    @Test
    fun `a countdown shows its stored duration in three fields`() {
        val configured = Fixture.clock("clk-dur-shown")
            .copy(mode = "countdown", targetHour = 1, targetMinute = 30, targetSecond = 15)
        sourcePanel(configured) { _ ->
            assertFieldShows("1", "the duration hours field")
            assertFieldShows("30", "the duration minutes field")
            assertFieldShows("15", "the duration seconds field")
        }
    }

    @Test
    fun `typing duration hours stores them`() = sourcePanel(countdown("clk-dur-h")) { get ->
        typeField(Field.TARGET_HOUR, "2")

        assertEquals(2, (get() as SceneSource.ClockSource).targetHour)
    }

    @Test
    fun `duration hours above the maximum are lowered to it`() = sourcePanel(countdown("clk-dur-h-max")) { get ->
        typeField(Field.TARGET_HOUR, "500")

        assertEquals(99, (get() as SceneSource.ClockSource).targetHour, "the longest countdown is 99 hours")
    }

    @Test
    fun `typing duration minutes stores them`() = sourcePanel(countdown("clk-dur-m")) { get ->
        typeField(Field.TARGET_MINUTE, "45")

        assertEquals(45, (get() as SceneSource.ClockSource).targetMinute)
    }

    @Test
    fun `duration minutes past the end of an hour are lowered to it`() =
        sourcePanel(countdown("clk-dur-m-max")) { get ->
            typeField(Field.TARGET_MINUTE, "90")

            assertEquals(59, (get() as SceneSource.ClockSource).targetMinute, "minutes stop at 59")
        }

    @Test
    fun `typing duration seconds stores them`() = sourcePanel(countdown("clk-dur-s")) { get ->
        typeField(Field.TARGET_SECOND, "20")

        assertEquals(20, (get() as SceneSource.ClockSource).targetSecond)
    }

    @Test
    fun `duration seconds past the end of a minute are lowered to it`() =
        sourcePanel(countdown("clk-dur-s-max")) { get ->
            typeField(Field.TARGET_SECOND, "75")

            assertEquals(59, (get() as SceneSource.ClockSource).targetSecond, "seconds stop at 59")
        }

    @Test
    fun `a negative duration is raised to zero`() = sourcePanel(countdown("clk-dur-neg")) { get ->
        typeField(Field.TARGET_MINUTE, "-5")

        assertEquals(0, (get() as SceneSource.ClockSource).targetMinute)
    }

    @Test
    fun `text that is not a number leaves a duration alone`() = sourcePanel(countdown("clk-dur-nan")) { get ->
        typeField(Field.TARGET_MINUTE, "five")

        assertEquals(5, (get() as SceneSource.ClockSource).targetMinute, "the stored duration is untouched")
    }

    // ── The timer ─────────────────────────────────────────────────────────────

    @Test
    fun `the timer reads out the whole stored duration as hours, minutes and seconds`() {
        val configured = Fixture.clock("clk-timer-readout")
            .copy(mode = "countdown", targetHour = 1, targetMinute = 2, targetSecond = 3)
        sourcePanel(configured) { _ ->
            onNodeWithText("01:02:03").assertExists("the timer must show the duration it will count from")
        }
    }

    @Test
    fun `the countdown offers a start and a reset button`() = sourcePanel(countdown("clk-timer-buttons")) { _ ->
        roleButtons().assertCountEquals(2)
        onNodeWithText("Start").assertExists()
        onNodeWithText("Reset").assertExists()
    }

    @Test
    fun `Start cannot be pressed for a countdown of no length`() {
        // Zero duration is the default; there is nothing to count down, so the button is dead.
        sourcePanel(Fixture.clock("clk-timer-zero").copy(mode = "countdown")) { _ ->
            onNodeWithText("Start").assertIsNotEnabled()
            // Reset stays live so a finished timer can be re-armed.
            onNodeWithText("Reset").assertIsEnabled()
        }
    }

    @Test
    fun `pressing Start runs the timer and the button becomes Pause`() {
        val id = "clk-timer-start"
        sourcePanel(countdown(id)) { _ ->
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()

            assertEquals(
                true, TimerStateManager.getState(id, 0).isRunning,
                "pressing Start must set the shared timer running",
            )
            onNodeWithText("Pause").assertExists("and the button must now offer the opposite action")
            assertEquals(0, countOf("Start"))
        }
    }

    @Test
    fun `pressing Pause stops the timer and the button becomes Start again`() {
        val id = "clk-timer-pause"
        sourcePanel(countdown(id)) { _ ->
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Pause").performScrollTo().performClick()
            waitForIdle()

            assertEquals(false, TimerStateManager.getState(id, 0).isRunning, "pressing Pause must stop it")
            onNodeWithText("Start").assertExists()
        }
    }

    @Test
    fun `Reset puts the timer back to the full duration and stops it`() {
        val id = "clk-timer-reset"
        sourcePanel(countdown(id, minutes = 3)) { _ ->
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()
            TimerStateManager.tick(id)
            waitForIdle()

            onNodeWithText("Reset").performScrollTo().performClick()
            waitForIdle()

            val state = TimerStateManager.getState(id, 180)
            assertEquals(180, state.remainingSeconds, "Reset must restore the whole duration")
            assertEquals(false, state.isRunning, "and leave it stopped")
            onNodeWithText("00:03:00").assertExists("the read-out follows the reset")
        }
    }

    @Test
    fun `the timer read-out follows a tick of the shared state`() {
        val id = "clk-timer-tick"
        sourcePanel(countdown(id, minutes = 1)) { _ ->
            onNodeWithText("00:01:00").assertExists("the read-out starts at the full duration")
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()

            TimerStateManager.tick(id)
            waitForIdle()

            onNodeWithText("00:00:59").assertExists("the panel observes the shared timer, not its own copy")
        }
    }

    // ── Text when the countdown expires ───────────────────────────────────────

    @Test
    fun `typing an expiry message stores it`() = sourcePanel(countdown("clk-expired")) { get ->
        typeField(Field.EXPIRED_TEXT, "Time's up!")

        assertEquals("Time's up!", (get() as SceneSource.ClockSource).expiredText)
    }

    @Test
    fun `a stored expiry message is shown by the field that owns it`() {
        sourcePanel(countdown("clk-expired-shown").copy(expiredText = "We begin shortly")) { _ ->
            assertFieldShows("We begin shortly", "the expiry message field")
        }
    }

    @Test
    fun `the expiry message belongs to the countdown alone`() = sourcePanel(Fixture.clock("clk-no-expiry")) { _ ->
        assertEquals(0, countOf("TEXT WHEN TIMER EXPIRES"))
    }

    // ── Count Up ──────────────────────────────────────────────────────────────

    @Test
    fun `choosing Count Up stores it and leaves nothing to configure but the transport`() =
        sourcePanel(Fixture.clock("clk-to-countup")) { get ->
            chooseFromDropdown(showing = "Clock", option = "Count Up")

            assertEquals("count_up", (get() as SceneSource.ClockSource).mode)
            textFields().assertCountEquals(9)
            assertEquals(0, countOf("HR"), "a stopwatch counts from zero, not from a duration")
            roleButtons().assertCountEquals(2)
            onNodeWithText("00:00:00").assertExists("and it reads out at zero")
        }

    @Test
    fun `a stopwatch can be started from zero, unlike a countdown of no length`() {
        val id = "clk-countup-start"
        sourcePanel(Fixture.clock(id).copy(mode = "count_up")) { _ ->
            onNodeWithText("Start").assertIsEnabled()
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()

            assertEquals(true, TimerStateManager.getState(id, 0).isRunning)
            onNodeWithText("Pause").assertExists()
        }
    }

    @Test
    fun `the stopwatch read-out follows a tick up of the shared state`() {
        val id = "clk-countup-tick"
        sourcePanel(Fixture.clock(id).copy(mode = "count_up")) { _ ->
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()

            TimerStateManager.tickUp(id)
            waitForIdle()

            onNodeWithText("00:00:01").assertExists()
        }
    }

    @Test
    fun `Reset puts the stopwatch back to zero and stops it`() {
        val id = "clk-countup-reset"
        sourcePanel(Fixture.clock(id).copy(mode = "count_up")) { _ ->
            onNodeWithText("Start").performScrollTo().performClick()
            waitForIdle()
            TimerStateManager.tickUp(id)
            waitForIdle()

            onNodeWithText("Reset").performScrollTo().performClick()
            waitForIdle()

            assertEquals(TimerStateManager.TimerState(0, false), TimerStateManager.getState(id, 0))
            onNodeWithText("00:00:00").assertExists()
        }
    }

    @Test
    fun `switching mode re-seeds the shared timer`() {
        val id = "clk-mode-reseed"
        sourcePanel(countdown(id, minutes = 5)) { _ ->
            onNodeWithText("Start").performScrollTo().performClick()
            TimerStateManager.tick(id)
            waitForIdle()

            chooseFromDropdown(showing = "Countdown", option = "Count Up")

            assertEquals(
                TimerStateManager.TimerState(0, false), TimerStateManager.getState(id, 0),
                "a stopwatch must not open holding what the countdown left behind",
            )
        }
    }

    // ── Specific Time ─────────────────────────────────────────────────────────

    @Test
    fun `choosing Specific Time stores it and unfolds the three time fields`() =
        sourcePanel(Fixture.clock("clk-to-target")) { get ->
            chooseFromDropdown(showing = "Clock", option = "Specific Time")

            assertEquals("target_time", (get() as SceneSource.ClockSource).mode)
            onNodeWithText("HR").assertExists()
            onNodeWithText("MIN").assertExists()
            onNodeWithText("SEC").assertExists()
            assertEquals(2, countOf(":"), "the time of day reads as one hr:min:sec row too")
            textFields().assertCountEquals(12)
        }

    @Test
    fun `a specific-time clock has no transport of its own`() {
        sourcePanel(Fixture.clock("clk-target-notransport").copy(mode = "target_time")) { _ ->
            roleButtons().assertCountEquals(0)
            listOf("Start", "Pause", "Reset").forEach {
                assertEquals(0, countOf(it), "\"$it\" belongs to the modes that are driven by hand")
            }
        }
    }

    @Test
    fun `the time being counted to is read back under the fields`() {
        val configured = Fixture.clock("clk-target-readout")
            .copy(mode = "target_time", targetTimeHour = 10, targetTimeMinute = 30, targetTimeSecond = 5)
        sourcePanel(configured) { _ ->
            onNodeWithText("Count down to 10:30:05").assertExists()
        }
    }

    @Test
    fun `typing a target hour stores it`() {
        sourcePanel(Fixture.clock("clk-target-h").copy(mode = "target_time")) { get ->
            typeField(Field.TIME_HOUR, "10")

            assertEquals(10, (get() as SceneSource.ClockSource).targetTimeHour)
        }
    }

    @Test
    fun `a target hour past the end of the day is lowered to it`() {
        sourcePanel(Fixture.clock("clk-target-h-max").copy(mode = "target_time")) { get ->
            typeField(Field.TIME_HOUR, "30")

            assertEquals(23, (get() as SceneSource.ClockSource).targetTimeHour, "a time of day stops at 23")
        }
    }

    @Test
    fun `typing target minutes and seconds stores them`() {
        sourcePanel(Fixture.clock("clk-target-ms").copy(mode = "target_time")) { get ->
            typeField(Field.TIME_MINUTE, "45")
            typeField(Field.TIME_SECOND, "20")

            val source = get() as SceneSource.ClockSource
            assertEquals(45, source.targetTimeMinute)
            assertEquals(20, source.targetTimeSecond)
        }
    }

    @Test
    fun `target minutes and seconds past the end of an hour and a minute are lowered to them`() {
        sourcePanel(Fixture.clock("clk-target-ms-max").copy(mode = "target_time")) { get ->
            typeField(Field.TIME_MINUTE, "90")
            typeField(Field.TIME_SECOND, "75")

            val source = get() as SceneSource.ClockSource
            assertEquals(59, source.targetTimeMinute)
            assertEquals(59, source.targetTimeSecond)
        }
    }

    @Test
    fun `the expiry message belongs to the countdown, not to a specific time`() {
        sourcePanel(Fixture.clock("clk-target-noduration").copy(mode = "target_time")) { _ ->
            assertEquals(0, countOf("TEXT WHEN TIMER EXPIRES"))
        }
    }

    @Test
    fun `the style row carries no text-backing control`() = sourcePanel(Fixture.clock()) { _ ->
        onAllNodesWithText("B").assertCountEquals(1)
        onAllNodesWithText("A").assertCountEquals(0)
    }
}
