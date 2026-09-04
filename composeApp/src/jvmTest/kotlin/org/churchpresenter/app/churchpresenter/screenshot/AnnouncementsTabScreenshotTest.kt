@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementLabel
import org.churchpresenter.app.churchpresenter.tabs.annButton
import org.churchpresenter.app.churchpresenter.tabs.announcementsTab
import org.churchpresenter.app.churchpresenter.tabs.timerButton
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * Every state of the Announcements tab, in both themes.
 *
 * Not shot: anything whose content is the wall clock. Specific Time counts down to a time of day and
 * Clock Display *is* a clock, so both would draw different digits on every recording — an image that
 * changes for no reviewable reason, for ever, in a repo that keeps every version of it.
 *
 * Not shot either: the expired countdown. `announcementTimerExpired` is only ever set by the
 * countdown coroutine reaching zero, which costs a real second per theme with no seam to shorten it.
 */
class AnnouncementsTabScreenshotTest {

    private fun shoot(
        name: String,
        initial: AnnouncementsSettings = AnnouncementsSettings(),
        projectionSettings: ProjectionSettings = ProjectionSettings(),
        settings: (AppSettings) -> AppSettings = { it },
        presenter: Boolean = true,
        schedule: Boolean = true,
        width: Dp? = null,
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        announcementsTab(
            // Every state is shot with the entrance animation off. The shipped default slides the
            // preview text across the frame for twelve seconds, so a shot of it catches the text
            // wherever the frame clock happened to leave it — usually off-screen entirely, which
            // makes the preview pane useless for seeing the colour, position and styling being set.
            initial = initial.copy(animationType = Constants.ANIMATION_NONE),
            withPresenter = presenter,
            withOnAddToSchedule = schedule,
            projectionSettings = projectionSettings,
            settings = settings,
            width = width,
            themeMode = mode,
        ) { _, _ ->
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── The announcement text and its actions ───────────────────────────────────────────────────

    @Test
    fun `nothing typed yet, so every action is shut`() = shoot("empty")

    /**
     * Long enough to wrap the input box to its second line, which is as far as it grows.
     *
     * A one-line notice at the default centre position is what `position_center` already is, and a
     * second identical image tells a reviewer nothing.
     */
    @Test
    fun `text typed`() = shoot("text_typed", initial = notice().copy(text = LONG_NOTICE))

    @Test
    fun `the text on screen`() = shoot("text_live", initial = notice()) {
        annButton(AnnouncementLabel.SHOW).performClick()
        waitForIdle()
    }

    @Test
    fun `with no output to go live on`() = shoot("no_presenter", initial = notice(), presenter = false)

    @Test
    fun `with nowhere to schedule it`() = shoot("no_schedule", initial = notice(), schedule = false)

    // ── Formatting ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bold, italic and underlined, aligned left`() = shoot(
        "styled_text",
        initial = notice(
            textColor = "#FFD54F",
            fontSize = 72,
            bold = true,
            italic = true,
            underline = true,
            horizontalAlignment = Constants.LEFT,
        ),
    )

    /** Turning the shadow on opens a whole row of shadow controls under the formatting bar. */
    @Test
    fun `the shadow row open`() = shoot(
        "shadow_on",
        initial = notice(shadow = true),
    )

    /** The band and box the preview paints behind the text, as the presenter will draw them. */
    @Test
    fun `a backdrop behind the text`() = shoot(
        "backdrop_plate",
        initial = notice(
            backdrop = TextBackdrop(
                lineBackground = true,
                lineBackgroundColor = "#000000",
                lineBackgroundOpacity = 80,
                border = true,
                borderColor = "#FFD54F",
                borderWidth = 6,
                borderPadding = 18,
                borderRadius = 12,
            ),
        ),
    )

    /** A transparent background collapses the colour field to a single chip offering to pick one. */
    @Test
    fun `a transparent background`() = shoot(
        "background_transparent",
        initial = notice(backgroundColor = "transparent"),
    )

    // ── Position on screen ──────────────────────────────────────────────────────────────────────

    /**
     * All nine, one image each: the grid highlights the chosen cell and the preview moves the text
     * to the matching corner, and only shooting every one shows the two agreeing.
     */
    private fun position(name: String, position: String) =
        shoot("position_$name", initial = notice(position = position))

    @Test
    fun `positioned top left`() = position("top_left", Constants.TOP_LEFT)

    @Test
    fun `positioned top centre`() = position("top_center", Constants.TOP_CENTER)

    @Test
    fun `positioned top right`() = position("top_right", Constants.TOP_RIGHT)

    @Test
    fun `positioned centre left`() = position("center_left", Constants.CENTER_LEFT)

    @Test
    fun `positioned centre`() = position("center", Constants.CENTER)

    @Test
    fun `positioned centre right`() = position("center_right", Constants.CENTER_RIGHT)

    @Test
    fun `positioned bottom left`() = position("bottom_left", Constants.BOTTOM_LEFT)

    @Test
    fun `positioned bottom centre`() = position("bottom_center", Constants.BOTTOM_CENTER)

    @Test
    fun `positioned bottom right`() = position("bottom_right", Constants.BOTTOM_RIGHT)

    /** Under about 240dp of panel the grid drops to initials — TL, TC, TR — instead of wrapping. */
    @Test
    fun `a left panel too narrow for the position names`() = shoot(
        "position_abbreviated",
        initial = notice(),
        settings = { it.copy(maximizedLayout = it.maximizedLayout.copy(announcementsLeftPanelWidthDp = 200)) },
    )

    @Test
    fun `a left panel dragged wide`() = shoot(
        "wide_left_panel",
        initial = notice(),
        settings = { it.copy(maximizedLayout = it.maximizedLayout.copy(announcementsLeftPanelWidthDp = 520)) },
    )

    // ── The timer, in the two modes that do not track the wall clock ────────────────────────────

    @Test
    fun `a countdown set`() = shoot(
        "timer_countdown",
        initial = AnnouncementsSettings(timerMinutes = 5, timerSeconds = 30),
    )

    @Test
    fun `a countdown of over an hour`() = shoot(
        "timer_countdown_hours",
        initial = AnnouncementsSettings(timerHours = 1, timerMinutes = 15),
    )

    /**
     * Running is what puts the timer in the preview: stopped, the preview shows the announcement
     * text instead, whichever timer mode is selected.
     */
    @Test
    fun `the countdown running`() = shoot(
        "timer_running",
        initial = AnnouncementsSettings(timerMinutes = 5),
    ) { start() }

    @Test
    fun `an expiry message set`() = shoot(
        "timer_expiry_message",
        initial = AnnouncementsSettings(timerMinutes = 5, timerExpiredText = "The service is starting"),
    )

    @Test
    fun `count-up, which has no duration to configure`() = shoot(
        "timer_count_up",
        initial = AnnouncementsSettings(timerMode = Constants.TIMER_MODE_COUNT_UP),
    )

    @Test
    fun `the count-up running`() = shoot(
        "timer_count_up_running",
        initial = AnnouncementsSettings(timerMode = Constants.TIMER_MODE_COUNT_UP),
    ) { start() }

    /**
     * A countdown running with an announcement typed as well — the preview follows the timer, since
     * only one of the two can hold the live slot at a time.
     */
    @Test
    fun `the countdown running while text is also set`() = shoot(
        "timer_running_with_text",
        initial = notice().copy(timerMinutes = 5),
    ) { start() }

    // ── The timer's typography and placement ────────────────────────────────────────────────────
    // The countdown is drawn with the announcement's own font, size, colour and position — there is
    // no separate set for it — so each of those is shot with the timer running, where the preview
    // shows the countdown rather than the text.

    @Test
    fun `a large countdown`() = shoot(
        "timer_font_large",
        initial = runningTimer(fontSize = 140),
    ) { start() }

    @Test
    fun `a small countdown`() = shoot(
        "timer_font_small",
        initial = runningTimer(fontSize = 18),
    ) { start() }

    @Test
    fun `a countdown in a serif face`() = shoot(
        "timer_font_serif",
        initial = runningTimer(fontType = "Georgia"),
    ) { start() }

    @Test
    fun `a countdown in a monospaced face`() = shoot(
        "timer_font_mono",
        initial = runningTimer(fontType = "Courier New"),
    ) { start() }

    @Test
    fun `a coloured countdown`() = shoot(
        "timer_color",
        initial = runningTimer(textColor = "#FF5252"),
    ) { start() }

    @Test
    fun `a bold countdown on a solid background`() = shoot(
        "timer_bold_on_background",
        initial = runningTimer(bold = true, backgroundColor = "#1B2A5B", fontSize = 96),
    ) { start() }

    /** Centre is the default, so a ninth image here would be `timer_running` over again. */
    private fun timerPosition(name: String, position: String) =
        shoot("timer_position_$name", initial = runningTimer(position = position)) { start() }

    @Test
    fun `a countdown top left`() = timerPosition("top_left", Constants.TOP_LEFT)

    @Test
    fun `a countdown top centre`() = timerPosition("top_center", Constants.TOP_CENTER)

    @Test
    fun `a countdown top right`() = timerPosition("top_right", Constants.TOP_RIGHT)

    @Test
    fun `a countdown centre left`() = timerPosition("center_left", Constants.CENTER_LEFT)

    @Test
    fun `a countdown centre right`() = timerPosition("center_right", Constants.CENTER_RIGHT)

    @Test
    fun `a countdown bottom left`() = timerPosition("bottom_left", Constants.BOTTOM_LEFT)

    @Test
    fun `a countdown bottom centre`() = timerPosition("bottom_center", Constants.BOTTOM_CENTER)

    @Test
    fun `a countdown bottom right`() = timerPosition("bottom_right", Constants.BOTTOM_RIGHT)

    // ── Stage Monitor ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Stage Monitor screen configured adds a send button to each half`() = shoot(
        "stage_monitor_available",
        initial = notice(),
        projectionSettings = STAGE_MONITOR_SETUP,
    )

    @Test
    fun `sent to the Stage Monitor`() = shoot(
        "stage_monitor_sent",
        initial = notice(),
        projectionSettings = STAGE_MONITOR_SETUP,
    ) {
        annButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
        waitForIdle()
    }

    @Test
    fun `the timer sent to the Stage Monitor`() = shoot(
        "stage_monitor_timer_sent",
        initial = AnnouncementsSettings(timerMinutes = 5),
        projectionSettings = STAGE_MONITOR_SETUP,
    ) {
        timerButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
        waitForIdle()
    }

    // ── Panel widths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a narrow panel`() = shoot(
        "narrow_panel",
        initial = notice(),
        width = 520.dp,
    )

    @Test
    fun `a half-width panel`() = shoot(
        "medium_panel",
        initial = notice(),
        width = 820.dp,
    )

    /** A five-minute countdown carrying the typography a church would have set for it. */
    private fun runningTimer(
        textColor: String = "#FFFFFF",
        backgroundColor: String = AnnouncementsSettings().backgroundColor,
        fontSize: Int = 48,
        fontType: String = "Arial",
        bold: Boolean = false,
        position: String = Constants.CENTER,
    ) = AnnouncementsSettings(
        timerMinutes = 5,
        textColor = textColor,
        backgroundColor = backgroundColor,
        fontSize = fontSize,
        fontType = fontType,
        bold = bold,
        position = position,
    )

    /** The tab carrying a typed announcement, which most states need before they mean anything. */
    private fun notice(
        textColor: String = "#FFFFFF",
        backgroundColor: String = AnnouncementsSettings().backgroundColor,
        fontSize: Int = 48,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        shadow: Boolean = false,
        horizontalAlignment: String = Constants.CENTER,
        position: String = Constants.CENTER,
        backdrop: TextBackdrop = TextBackdrop(),
    ) = AnnouncementsSettings(
        text = NOTICE,
        textColor = textColor,
        backgroundColor = backgroundColor,
        fontSize = fontSize,
        bold = bold,
        italic = italic,
        underline = underline,
        shadow = shadow,
        horizontalAlignment = horizontalAlignment,
        position = position,
        backdrop = backdrop,
    )

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** Sets the selected timer/clock mode running, which is what puts its value in the preview. */
    private fun ComposeUiTest.start() {
        annButton(AnnouncementLabel.START).performClick()
        waitForIdle()
    }

    private companion object {
        const val SECTION = "announcementsTab"

        const val NOTICE = "Prayer meeting Wednesday at 7pm in the hall"

        const val LONG_NOTICE =
            "Prayer meeting Wednesday at 7pm in the hall — everyone is welcome, and supper is " +
                "served in the lounge afterwards"

        val STAGE_MONITOR_SETUP = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
                ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
            ),
        )
    }
}
