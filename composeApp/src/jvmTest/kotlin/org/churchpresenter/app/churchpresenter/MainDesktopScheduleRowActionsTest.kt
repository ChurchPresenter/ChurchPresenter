package org.churchpresenter.app.churchpresenter

import io.mockk.spyk
import io.mockk.verify
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A Schedule announcement or lower-third row put on screen, over a real [PresenterManager] -- what the
 * main window does with the row, without the main window.
 */
class MainDesktopScheduleRowActionsTest {

    private lateinit var dir: File
    private val managers = mutableListOf<PresenterManager>()
    private var settings = AppSettings()
    private val presented = mutableListOf<Presenting>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-schedule-row-actions").toFile()
    }

    @AfterTest
    fun tearDown() {
        managers.forEach { runCatching { it.pauseAnnouncementTimer(null) } }
        dir.deleteRecursively()
    }

    private fun manager(): PresenterManager = PresenterManager().also { managers += it }

    private fun present(item: ScheduleItem.AnnouncementItem, presenter: PresenterManager) =
        presentAnnouncementItem(
            item,
            timerExpiredDefaultLabel = "Time's up",
            presenterManager = presenter,
            onSettingsChange = { transform -> settings = transform(settings) },
            presenting = { presented += it },
        )

    // ── Announcements ───────────────────────────────────────────────────────────

    @Test
    fun `a text announcement puts its text up, keeps its look in the settings and goes live`() {
        val presenter = manager()
        val item = ScheduleItem.AnnouncementItem(id = "a", text = "Welcome", fontSize = 72)

        present(item, presenter)

        assertEquals("Welcome", presenter.announcementText.value)
        assertFalse(presenter.announcementTickerLive.value)
        assertEquals("Welcome", settings.announcementsSettings.text)
        assertEquals(72, settings.announcementsSettings.fontSize)
        assertEquals(listOf(Presenting.ANNOUNCEMENTS), presented)
    }

    @Test
    fun `a text announcement in preview mode is cued on preview, not put on air`() {
        val presenter = manager()
        presenter.previewBus.setEnabled(true)

        present(ScheduleItem.AnnouncementItem(id = "a", text = "Coffee after"), presenter)

        assertEquals("Coffee after", presenter.previewBus.manager.announcementText.value)
        assertTrue(presenter.previewBus.manager.isLive(Presenting.ANNOUNCEMENTS))
        assertEquals("", presenter.announcementText.value)
    }

    /**
     * A spy, as a last resort: the expiry text is only ever seen when the countdown reaches zero, a
     * whole second away, so the text handed to the timer is read off the call. The ticker going live
     * with the time on it is the real outcome asserted alongside.
     */
    @Test
    fun `a timer with no expiry text of its own starts with the default label`() {
        val presenter = spyk(manager())
        val item = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5)

        present(item, presenter)

        verify { presenter.goLiveAnnouncementTimer(item, "Time's up") }
        assertTrue(presenter.announcementTickerLive.value)
        assertEquals("05:00", presenter.announcementText.value)
        assertEquals(5, settings.announcementsSettings.timerMinutes)
        assertEquals(listOf(Presenting.ANNOUNCEMENTS), presented)
    }

    @Test
    fun `a timer with expiry text of its own keeps it`() {
        val presenter = spyk(manager())
        val item = ScheduleItem.AnnouncementItem(
            id = "t", text = "", isTimer = true, timerSeconds = 30, timerExpiredText = "Doors open",
        )

        present(item, presenter)

        verify { presenter.goLiveAnnouncementTimer(item, "Doors open") }
        assertTrue(presenter.announcementTickerLive.value)
        assertEquals("00:30", presenter.announcementText.value)
    }

    // ── Lower thirds ────────────────────────────────────────────────────────────

    private fun preset(name: String): File = File(dir, "$name.json").apply { writeText("{}") }

    private fun row(label: String, id: String = "id-$label") = ScheduleItem.LowerThirdItem(
        id = "row", presetId = id, presetLabel = label, pauseAtFrame = true, pauseDurationMs = 2_500,
    )

    @Test
    fun `a lower third found by its label is cued on preview with the row's pause`() {
        val presenter = manager()
        presenter.previewBus.setEnabled(true)
        preset("Pastor")

        presentLowerThirdItem(row("Pastor"), dir.absolutePath, presenter)

        val cued = presenter.previewBus.manager
        assertEquals("{}", cued.lottieJsonContent.value)
        assertEquals("Pastor", cued.currentLowerThirdName.value)
        assertTrue(cued.lottiePauseAtFrame.value)
        assertEquals(-1f, cued.lottiePauseFrame.value)
        assertEquals(2_500, cued.lottiePauseDurationMs.value)
        assertTrue(cued.isLive(Presenting.LOWER_THIRD))
        assertFalse(presenter.isLive(Presenting.LOWER_THIRD))
        assertEquals("", presenter.lottieJsonContent.value, "nothing reaches the air until it is taken")
        assertTrue(presenter.showPresenterWindow.value)
    }

    @Test
    fun `a lower third found by its id goes straight to air outside preview mode`() {
        val presenter = manager()
        preset("preset-7")

        presentLowerThirdItem(row("Renamed since", id = "preset-7"), dir.absolutePath, presenter)

        assertEquals("preset-7", presenter.currentLowerThirdName.value)
        assertTrue(presenter.isLive(Presenting.LOWER_THIRD))
    }

    @Test
    fun `a lower third whose preset is not in the folder shows nothing`() {
        val presenter = manager()
        preset("Someone else")

        presentLowerThirdItem(row("Pastor"), dir.absolutePath, presenter)

        assertEquals("", presenter.lottieJsonContent.value)
        assertFalse(presenter.anythingLive)
    }

    @Test
    fun `a lower third whose folder is gone shows nothing`() {
        val presenter = manager()

        presentLowerThirdItem(row("Pastor"), File(dir, "never-made").absolutePath, presenter)

        assertEquals("", presenter.lottieJsonContent.value)
        assertFalse(presenter.anythingLive)
    }
}
