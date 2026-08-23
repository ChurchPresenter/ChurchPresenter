@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * The announcement half of the Announcements tab: the text, how it is styled and placed, and how it
 * reaches the screen.
 *
 * Everything here is asserted from outside the tab — what it draws, the settings it hands back to be
 * persisted, and what it puts on the `PresenterManager` — because the tab owns its view model
 * privately. See `AnnouncementsTabTestSupport.kt` for the harness.
 */
class AnnouncementsTabTest {

    // ── The text ────────────────────────────────────────────────────────────────

    @Test
    fun `an empty tab shows its hint and offers nothing to send`() = announcementsTab { _, _ ->
        assertTrue(showsExactly(AnnouncementLabel.TEXT_HINT), "the placeholder invites text")
        // Go Live exists but is disabled — there is nothing to show yet.
        annButton(AnnouncementLabel.GO_LIVE).assertIsNotEnabled()
        annButton(AnnouncementLabel.ADD_TO_SCHEDULE).assertIsNotEnabled()
    }

    @Test
    fun `typing an announcement stores it and enables the actions`() = announcementsTab { _, reports ->
        typeAnnouncement("Coffee is served in the hall")

        assertEquals("Coffee is served in the hall", reports.settings?.text)
        assertFalse(
            showsExactly(AnnouncementLabel.TEXT_HINT),
            "the hint gives way to the text",
        )
        annButton(AnnouncementLabel.GO_LIVE).assertIsEnabled()
        annButton(AnnouncementLabel.ADD_TO_SCHEDULE).assertIsEnabled()
    }

    @Test
    fun `an announcement of only spaces is not something to show`() = announcementsTab { _, _ ->
        typeAnnouncement("   ")

        // Blank text would put an empty box on the screen, so the actions stay shut.
        annButton(AnnouncementLabel.GO_LIVE).assertIsNotEnabled()
        annButton(AnnouncementLabel.ADD_TO_SCHEDULE).assertIsNotEnabled()
    }

    @Test
    fun `text saved earlier is shown when the tab opens`() =
        announcementsTab(initial = AnnouncementsSettings(text = "Welcome back")) { _, _ ->
            assertTrue(showsExactly("Welcome back"), "got ${renderedText().take(4)}")
            assertFalse(showsExactly(AnnouncementLabel.TEXT_HINT))
        }

    // ── Going live ──────────────────────────────────────────────────────────────

    @Test
    fun `going live puts the announcement on screen`() = announcementsTab { presenter, _ ->
        typeAnnouncement("Service starts in ten minutes")
        annButton(AnnouncementLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
        assertEquals("Service starts in ten minutes", presenter.announcementText.value)
    }

    @Test
    fun `the show button becomes a hide button once it is live`() = announcementsTab { presenter, _ ->
        typeAnnouncement("Notices")
        assertTrue(hasAnnButton(AnnouncementLabel.SHOW), "offers to show, to begin with")

        annButton(AnnouncementLabel.SHOW).performClick()
        waitForIdle()

        assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
        assertTrue(hasAnnButton(AnnouncementLabel.HIDE), "the same button now offers to hide")
        assertFalse(hasAnnButton(AnnouncementLabel.SHOW))
    }

    @Test
    fun `hiding asks for the display to be cleared`() = announcementsTab { presenter, _ ->
        typeAnnouncement("Notices")
        annButton(AnnouncementLabel.SHOW).performClick()
        waitForIdle()
        assertFalse(presenter.clearDisplayRequested.value, "nothing asked for yet")

        annButton(AnnouncementLabel.HIDE).performClick()
        waitForIdle()

        // The tab raises the request; MainDesktop is what watches it and takes the content down,
        // so the mode is still ANNOUNCEMENTS here — the request is the whole of the tab's part.
        assertTrue(presenter.clearDisplayRequested.value, "the display was asked to clear")
    }

    // ── Adding to the schedule ──────────────────────────────────────────────────

    @Test
    fun `adding to the schedule hands over the announcement, not the timer`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerHours = 1, timerMinutes = 30, timerSeconds = 15),
        ) { _, reports ->
            typeAnnouncement("Offering today")
            annButton(AnnouncementLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            val item = reports.scheduled.single()
            assertEquals("Offering today", item.text)
            // A text announcement scheduled from here must not carry the timer that happens to be
            // set up beside it, or it would show up in the service order as a countdown.
            assertEquals(0, item.timerHours)
            assertEquals(0, item.timerMinutes)
            assertEquals(0, item.timerSeconds)
        }

    @Test
    fun `adding to the schedule keeps the styling the announcement was given`() =
        announcementsTab { _, reports ->
            typeAnnouncement("Styled")
            clickLabel(AnnouncementLabel.BOLD)
            annButton(AnnouncementLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            assertTrue(reports.scheduled.single().bold, "the styling travels with the text")
        }

    // ── Styling ─────────────────────────────────────────────────────────────────

    @Test
    fun `each style toggle turns on and off again`() = announcementsTab { _, reports ->
        typeAnnouncement("Styled")

        clickLabel(AnnouncementLabel.BOLD)
        assertTrue(reports.settings?.bold == true, "bold on")
        clickLabel(AnnouncementLabel.BOLD)
        assertFalse(reports.settings?.bold == true, "and off again")

        clickLabel(AnnouncementLabel.ITALIC)
        assertTrue(reports.settings?.italic == true)
        clickLabel(AnnouncementLabel.UNDERLINE)
        assertTrue(reports.settings?.underline == true)
        clickLabel(AnnouncementLabel.SHADOW)
        assertTrue(reports.settings?.shadow == true)
    }

    @Test
    fun `the styles are independent of each other`() = announcementsTab { _, reports ->
        typeAnnouncement("Styled")

        clickLabel(AnnouncementLabel.BOLD)
        clickLabel(AnnouncementLabel.ITALIC)
        clickLabel(AnnouncementLabel.BOLD)

        val settings = reports.settings
        assertFalse(settings?.bold == true, "bold was turned back off")
        assertTrue(settings?.italic == true, "without disturbing italic")
    }

    @Test
    fun `styles saved earlier come back with the announcement`() =
        announcementsTab(
            initial = AnnouncementsSettings(text = "Saved", bold = true, shadow = true),
        ) { _, reports ->
            // Toggling something unrelated makes the tab report the whole settings object back,
            // which is where the previously-saved flags have to have survived.
            clickLabel(AnnouncementLabel.ITALIC)

            val settings = reports.settings
            assertTrue(settings?.bold == true, "bold survived the round trip")
            assertTrue(settings?.shadow == true, "and so did the shadow")
            assertTrue(settings?.italic == true, "alongside the new one")
        }

    // ── Position ────────────────────────────────────────────────────────────────

    @Test
    fun `every screen position can be chosen`() = announcementsTab { _, reports ->
        clickLabel(AnnouncementLabel.TOP_LEFT)
        assertEquals("Top Left", reports.settings?.position)

        clickLabel(AnnouncementLabel.CENTER)
        assertEquals("Center", reports.settings?.position, "and changed again")
    }

    @Test
    fun `the tab renders without a presenter, offering only what it can do`() =
        announcementsTab(withPresenter = false) { _, _ ->
            // The Announcements tab is also shown in a follower window with no output of its own.
            assertTrue(showsExactly(AnnouncementLabel.TEXT_HINT), "the editor is still usable")
            assertFalse(hasAnnButton(AnnouncementLabel.GO_LIVE), "but nothing offers to go live")
            assertFalse(hasAnnButton(AnnouncementLabel.SHOW))
        }

    @Test
    fun `with no schedule to add to, neither half offers to add to it`() =
        announcementsTab(withOnAddToSchedule = false, initial = AnnouncementsSettings(timerMinutes = 5)) { _, _ ->
            typeAnnouncement("Notices")

            assertFalse(hasAnnButton(AnnouncementLabel.ADD_TO_SCHEDULE))
        }
}
