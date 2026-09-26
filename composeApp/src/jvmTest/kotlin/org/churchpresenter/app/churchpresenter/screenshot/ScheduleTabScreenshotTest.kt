@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.calendar.ScheduleServiceLink
import org.churchpresenter.calendar.model.UpcomingLoad
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.tabs.ScheduleToolbarButton
import org.churchpresenter.app.churchpresenter.tabs.ScheduleToolbarTags
import org.churchpresenter.app.churchpresenter.tabs.scheduleTab
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.LocalTime
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test

class ScheduleTabScreenshotTest {

    private fun shoot(
        name: String,
        itemZoomPercent: Int = 100,
        width: Dp? = null,
        legacyRowActions: Boolean = false,
        hiddenToolbarButtons: Set<String> = emptySet(),
        rootIndex: Int = 0,
        seed: ScheduleViewModel.() -> Unit = { everyItemType() },
        clock: () -> LocalTime = { NOW },
        upcomingServiceLoad: UpcomingLoad? = null,
        scheduleService: ScheduleServiceLink? = null,
        offerAddToCalendar: Boolean = false,
        drive: ComposeUiTest.(ScheduleViewModel) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        // A feed left over from another shot would mark this one's cue rows.
        CueFeed.clear()
        scheduleTab(
            itemZoomPercent = itemZoomPercent,
            width = width,
            legacyRowActions = legacyRowActions,
            hiddenToolbarButtons = hiddenToolbarButtons,
            seed = seed,
            themeMode = mode,
            clock = clock,
            upcomingServiceLoad = upcomingServiceLoad,
            scheduleService = scheduleService,
            offerAddToCalendar = offerAddToCalendar,
        ) { vm, _ ->
            drive(vm)
            captureTo(file, rootIndex)
        }
    }

    private fun ScheduleViewModel.everyItemType() {
        addLabel("Welcome", "#FFFFFF", "#203040")
        addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
        addBibleVerse(
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = "For God so loved the world, that he gave his only begotten Son.",
        )
        // Paths and URLs here are drawn on screen — the schedule row prints the path of every
        // file-backed item at detailed density — and these shots are exported for the website. They
        // therefore match the app-preview fixture's library root and naming rather than standing in
        // as `/decks/…` placeholders, so two images of the same app do not disagree about where a
        // church keeps its files. See the LIBRARY note in AppPreviewSupport.kt.
        addPresentation(
            filePath = "/Users/Shared/ChurchPresenter/Decks/Sermon.pptx",
            fileName = "Sermon.pptx",
            slideCount = 24,
            fileType = "pptx",
        )
        addPicture(
            folderPath = "/Users/Shared/ChurchPresenter/Gallery",
            folderName = "Gallery",
            imageCount = 12,
        )
        addMedia(
            mediaUrl = "/Users/Shared/ChurchPresenter/Media/Welcome Loop.mp4",
            mediaTitle = "Welcome Loop",
            mediaType = "video",
        )
        addLowerThird(
            presetId = "lt-1",
            presetLabel = "Guest speaker",
            pauseAtFrame = true,
            pauseDurationMs = 4000,
        )
        addAnnouncement(text = "Fellowship lunch after the service")
        addWebsite(url = "https://churchpresenter.org/notices", title = "Notices")
        addScene(sceneId = "scene-1", sceneName = "Countdown scene")
        addDictionary(
            number = "H2617",
            word = "חֶסֶד",
            transliteration = "chesed",
            definition = "steadfast love",
        )
        // A ministry item only ever reaches the Schedule from a phone; the row shows what it
        // is and who, and cannot be presented.
        addRow(ScheduleItem.MinistryItem("ministry", "Violin", "Jake"), null)
    }

    @Test
    fun `every item type`() = shoot("every_item_type")

    // ── The calendar's notices under Add Files ──────────────────────────────────────────────────

    /**
     * The next planned service, due in an hour and twenty minutes. The notice draws the time *left*,
     * never a date, so the picture is the same on any day; its moment is set from the wall clock
     * the shot starts at, with half a minute's slack, so the text holds however long the render
     * takes. Epoch milliseconds rather than a `java.time` now(), which is the date-drawing family
     * `ScreenshotInvariantsTest` keeps out of screenshots.
     */
    private fun sundayLoad() = UpcomingLoad(
        serviceId = "sunday",
        serviceName = "Sunday Morning",
        loadAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(System.currentTimeMillis() + SUNDAY_LOAD_IN_MILLIS),
            ZoneId.systemDefault(),
        ),
    )

    @Test
    fun `the next service announced, with Load now`() =
        shoot("autoload_notice", seed = {}, upcomingServiceLoad = sundayLoad())

    @Test
    fun `load now asking what to do with the rows already there`() =
        shoot("load_now_confirm", upcomingServiceLoad = sundayLoad(), rootIndex = 1) {
            onNodeWithText("Load now").performClick()
            waitForIdle()
        }

    @Test
    fun `changes offered to be saved back to the calendar`() = shoot(
        "save_to_calendar",
        scheduleService = ScheduleServiceLink("sunday", "Sunday Morning", hasChanges = true),
    )

    @Test
    fun `a hand-built schedule offered to the calendar`() = shoot("add_to_calendar", offerAddToCalendar = true)

    /**
     * A service loaded from the calendar, part-way through: every row carries the time the plan
     * works out for it, and the live row says how far the service is from that plan.
     */
    @Test
    fun `a loaded service running behind its plan`() = shoot("behind_plan", seed = { loadedService() }) { vm ->
        // The second song was planned for 10:05 and went live at 10:07: two minutes behind, and
        // at 10:14 -- past the five it was planned to take -- the overrun has grown it to four.
        vm.markLive(vm.scheduleItems[2].id, LocalTime.of(10, 7))
        waitForIdle()
    }

    /** A cue that was due while the operator was live with something else: marked, not fired. */
    @Test
    fun `a cue the engine skipped`() = shoot("cue_skipped", seed = { loadedService() }) { vm ->
        val cue = vm.scheduleItems.first { it is ScheduleItem.CueItem }
        CueFeed.post(FiredCue(cue, LocalTime.of(10, 10), skipped = true))
        waitForIdle()
    }

    /** What the calendar puts in the Schedule: pinned starts, planned lengths, a cue, a service start. */
    private fun ScheduleViewModel.loadedService() {
        setServiceStart("10:00")
        addLabel("Worship", "#FFFFFF", "#5B9DF5")
        addRow(
            ScheduleItem.SongItem("s1", 1, "Amazing Grace", "Hymnal", songId = "Hymnal::1"),
            RowTiming(startAt = "10:00", runSeconds = 300, atEnd = RowEnd.NEXT),
        )
        addRow(
            ScheduleItem.SongItem("s2", 42, "Here I Am to Worship", "Hymnal", songId = "Hymnal::42"),
            RowTiming(runSeconds = 300),
        )
        addRow(
            ScheduleItem.CueItem("cue", CueAction.BLANK, label = "Blank before the sermon", absoluteTime = "10:10"),
            null,
        )
        addRow(
            ScheduleItem.PresentationItem(
                "deck", "/Users/Shared/ChurchPresenter/Decks/Sermon.pptx", "Sermon.pptx", 24, "pptx",
            ),
            RowTiming(runSeconds = 1920),
        )
    }

    @Test
    fun `every timer mode`() = shoot(
        "timers",
        seed = {
            addAnnouncement(text = "", isTimer = true, timerMinutes = 5)
            addAnnouncement(text = "", isTimer = true, timerHours = 1, timerMinutes = 30, timerSeconds = 15)
            addAnnouncement(
                text = "",
                isTimer = true,
                timerMode = Constants.TIMER_MODE_CLOCK,
                targetHour = 10,
                targetMinute = 30,
            )
            addAnnouncement(text = "", isTimer = true, timerMode = Constants.TIMER_MODE_COUNT_UP)
            addAnnouncement(text = "", isTimer = true, timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY)
        },
    )

    @Test
    fun `labels in their own colours`() = shoot(
        "labels_coloured",
        seed = {
            addLabel("Welcome", "#FFFFFF", "#203040")
            addLabel("Worship", "#1B5E20", "#C8E6C9")
            addLabel("Sermon", "#FFFFFF", "#B71C1C")
            addLabel("Communion", "#4A148C", "#E1BEE7")
            addLabel("Sending", "#000000", "#FFD54F")
        },
    )

    @Test
    fun `a long announcement is truncated`() = shoot(
        "announcement_truncated",
        seed = {
            addAnnouncement(
                text = "The fellowship lunch will be held in the hall directly after the service, " +
                    "and everyone is very welcome to stay",
            )
        },
    )

    @Test
    fun `a plan imported from Planning Center`() = shoot(
        "planning_center_import",
        seed = {
            addLabel("Pre-Service", "#FFFFFF", "#6750A4")
            addSong(songNumber = 0, title = "Build My Life", songbook = "Planning Center")
            addLabel("Worship", "#FFFFFF", "#6750A4")
            addSong(songNumber = 0, title = "Goodness Of God", songbook = "Planning Center")
            addLabel("Message", "#FFFFFF", "#6750A4")
            addBibleVerse(
                bookName = "Romans",
                chapter = 8,
                verseNumber = 28,
                verseText = "And we know that all things work together for good.",
            )
            addPresentation(
                filePath = "/planning-center/sermon-slides.pptx",
                fileName = "sermon-slides.pptx",
                slideCount = 18,
                fileType = "pptx",
            )
        },
    )

    @Test
    fun `scene and dictionary rows`() = shoot(
        "scene_and_dictionary",
        seed = {
            addScene(sceneId = "scene-1", sceneName = "Countdown scene")
            addDictionary(
                number = "H2617",
                word = "חֶסֶד",
                transliteration = "chesed",
                definition = "steadfast love",
            )
        },
    )

    @Test
    fun `an empty schedule`() = shoot("empty", seed = {})

    @Test
    fun `an item selected`() = shoot("item_selected") { vm ->
        vm.scheduleItems.getOrNull(1)?.let { vm.selectItem(it.id) }
        waitForIdle()
    }

    @Test
    fun `redo available after an undo`() = shoot("toolbar_redo_available") { vm ->
        vm.undo()
        waitForIdle()
    }

    @Test
    fun `a narrow panel wraps the toolbar`() = shoot("narrow_panel", width = 240.dp)

    /**
     * The legacy card layout: every row's buttons on their own line, none of them over the title.
     *
     * Shot at 320dp rather than the harness's full window, because this layout spreads its buttons
     * across the row — remove at one end, the rest at the other — and at 1024dp that reads as a
     * mistake rather than as the layout an operator sees in a real panel.
     */
    @Test
    fun `legacy row actions`() = shoot("legacy_row_actions", legacyRowActions = true, width = 320.dp)

    /** The header with toolbar buttons and the title-row readouts turned off from the options menu. */
    @Test
    fun `toolbar buttons hidden`() = shoot(
        "toolbar_buttons_hidden",
        hiddenToolbarButtons = setOf(
            ScheduleToolbarButton.PLANNING_CENTER.name,
            ScheduleToolbarButton.UNDO.name,
            ScheduleToolbarButton.REDO.name,
            ScheduleToolbarButton.ITEM_COUNT.name,
        ),
    )

    /** The options menu itself — an open menu is its own compose root, hence [rootIndex] 1. */
    @Test
    fun `the options menu`() = shoot("options_menu", rootIndex = 1) {
        onNodeWithTag(ScheduleToolbarTags.OPTIONS).performClick()
        waitForIdle()
    }

    /**
     * The schedule image the website's homepage uses, written straight into `previewApp/` — beside
     * the app-preview captures it sits next to on the page.
     *
     * Every other shot in this class renders into the harness's full 1024dp window, because a
     * layout test wants the panel given room and then observed. That is nearly three times the
     * width an operator's panel actually has: `AppPreviewSupport.library()` settles on
     * `schedulePanelWidthDp = 360`. So the rows come out with their text against the left edge and
     * most of each row empty — fine for a regression diff, but on a marketing page it reads as a
     * stretched, broken screenshot, which is what shipped. 400dp is a panel someone would really
     * use, so the rows fill their width.
     *
     * Written unstacked, one file per theme, unlike everything else here. The website needs the two
     * themes as separate images and was cutting them out of the stacked pair by hand — which is how
     * it came to ship a crop of a *stale* render for a release. [stackedThemes] is still right for
     * the regression shots; it is the wrong shape for an export.
     */
    @Test
    fun `the schedule panel at the width an operator uses`() {
        val dir = File("$SCREENSHOT_ROOT/previewApp").apply { mkdirs() }
        THEMES.forEach { (suffix, mode) ->
            scheduleTab(
                width = 400.dp,
                seed = { everyItemType() },
                themeMode = mode,
                density = PREVIEW_DENSITY,
            ) { _, _ ->
                captureTo(File(dir, "schedule_$suffix.png"))
            }
        }
    }

    @Test
    fun `density extra compact`() = shoot("density_extra_compact", itemZoomPercent = 55)

    @Test
    fun `density compact`() = shoot("density_compact", itemZoomPercent = 70)

    @Test
    fun `density detailed`() = shoot("density_detailed", itemZoomPercent = 150)

    @Test
    fun `density extra detailed`() = shoot("density_extra_detailed", itemZoomPercent = 200)

    private companion object {
        /** The wall clock every shot is judged against -- the live row's badge would otherwise tick. */
        val NOW: LocalTime = LocalTime.of(10, 14)

        const val SECTION = "scheduleTab"

        /** An hour and twenty minutes, less the half minute of slack -- "in 1 h 20 min". */
        const val SUNDAY_LOAD_IN_MILLIS = (80 * 60 - 30) * 1_000L
    }
}
