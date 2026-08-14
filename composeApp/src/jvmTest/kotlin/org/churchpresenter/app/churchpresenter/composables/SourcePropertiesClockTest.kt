@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.utils.TimerStateManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Clock source, which is two controls in one: a wall clock, and a countdown with its own timer.
 *
 * Choosing Countdown is what makes the difference — it unfolds three duration fields, a running
 * read-out and a pair of transport buttons that none of the clock modes offer. That read-out is the
 * only thing on the whole panel that is not stored on the source at all: it comes from
 * `TimerStateManager`, a process-wide singleton keyed by source id, which is why every test here uses
 * an id of its own. Sharing one would leave a running timer behind for whichever test ran next.
 *
 * The two dropdowns both map between a stored constant and a shown label in both directions, so both
 * directions are walked; and because each maps "anything else" onto its own default, a stored value
 * this build does not know is pinned as well.
 */
class SourcePropertiesClockTest {

    /** Ordinals of the clock panel's fields — the header owns the first six. */
    private object Field {
        const val FONT_SIZE = 6
        const val TARGET_HOUR = 7
        const val TARGET_MINUTE = 8
        const val TARGET_SECOND = 9
    }

    /** Ordinals of the panel's checkboxes, in composition order. */
    private object Check {
        const val SHOW_HOURS = 0
        const val SHOW_SECONDS = 1
        const val BOLD = 2
        const val COUNT = 3
    }

    private fun countdown(id: String, minutes: Int = 5) =
        Fixture.clock(id).copy(mode = "countdown", targetMinute = minutes)

    // ── A wall clock ──────────────────────────────────────────────────────────

    @Test
    fun `the section is headed and every clock control captioned`() = sourcePanel(Fixture.clock("clk-caps")) { _ ->
        listOf(
            "MODE", "FORMAT", "Show Hours", "Show Seconds", "Bold", "FONT SIZE",
            "TEXT COLOR", "BACKGROUND COLOR",
        ).forEach { caption ->
            onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the clock panel")
        }
        // "Clock" is both the section heading and the mode dropdown's current choice.
        assertEquals(2, countOf(Label.CLOCK), "the section must be headed, above a mode reading Clock")
    }

    @Test
    fun `a wall clock offers one field, three checkboxes and no timer`() =
        sourcePanel(Fixture.clock("clk-shape")) { _ ->
            textFields().assertCountEquals(7)
            checkboxes().assertCountEquals(Check.COUNT)
            listOf("Duration Hours", "Duration Minutes", "Duration Seconds", "Start", "Reset")
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
        listOf("clock" to "Clock", "countdown" to "Countdown").forEach { (stored, shown) ->
            sourcePanel(Fixture.clock("clk-mode-$stored").copy(mode = stored)) { _ ->
                // "Clock" is also the section heading, so its own label is the second one.
                val expected = if (shown == "Clock") 2 else 1
                assertEquals(expected, countOf(shown), "$stored must read as \"$shown\"")
            }
        }
    }

    @Test
    fun `a mode this build does not know reads as Clock`() {
        sourcePanel(Fixture.clock("clk-mode-odd").copy(mode = "stopwatch")) { _ ->
            assertEquals(0, countOf("stopwatch"), "an unrecognised mode must not show itself")
            assertEquals(0, countOf("Countdown"), "and must not be treated as a countdown")
        }
    }

    @Test
    fun `choosing Countdown stores it and unfolds the countdown controls`() =
        sourcePanel(Fixture.clock("clk-to-countdown")) { get ->
            chooseFromDropdown(showing = "Clock", option = "Countdown")

            assertEquals("countdown", (get() as SceneSource.ClockSource).mode)
            onNodeWithText("DURATION HOURS").assertExists("the duration fields appear with it")
            onNodeWithText("DURATION MINUTES").assertExists()
            onNodeWithText("DURATION SECONDS").assertExists()
            textFields().assertCountEquals(10)
        }

    @Test
    fun `choosing Clock folds the countdown controls away again`() {
        sourcePanel(countdown("clk-to-clock")) { get ->
            chooseFromDropdown(showing = "Countdown", option = "Clock")

            assertEquals("clock", (get() as SceneSource.ClockSource).mode)
            assertEquals(0, countOf("DURATION HOURS"), "the duration fields go with it")
            textFields().assertCountEquals(7)
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
    fun `all three flags are on out of the box`() = sourcePanel(Fixture.clock("clk-flags")) { _ ->
        checkboxes()[Check.SHOW_HOURS].assertIsOn()
        checkboxes()[Check.SHOW_SECONDS].assertIsOn()
        checkboxes()[Check.BOLD].assertIsOn()
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
    fun `unticking Bold flips only that flag`() = sourcePanel(Fixture.clock("clk-bold")) { get ->
        toggleCheckbox(Check.BOLD)

        assertEquals(
            Fixture.clock("clk-bold").copy(bold = false), get(),
            "Bold may write only its own flag",
        )
        checkboxes()[Check.BOLD].assertIsOff()
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
            TimerStateManager.tick(id, 180)
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

            TimerStateManager.tick(id, 60)
            waitForIdle()

            onNodeWithText("00:00:59").assertExists("the panel observes the shared timer, not its own copy")
        }
    }
}
