@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PdfExportSettings
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.SavedTemplate
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.ui.CalendarApp
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import org.churchpresenter.theme.ChurchPresenterTheme
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import java.awt.Color as AwtColor

/**
 * The Calendar Manager window, state by state, in both themes.
 *
 * Shot through `CalendarApp` — the same entry point the Help menu opens — against a real temp
 * folder holding a real `calendar.json` and `presets.json`, and a real folder of `.song` files.
 * Every state is reached by clicking, exactly as a person would, so an image that looks wrong means
 * the window is wrong rather than the fixture.
 *
 * The day is pinned to [TODAY] and so is the service's own clock: a month grid drawn from the real
 * date, or a run of show judged against the real time, would be a different picture every day. The
 * run of show holds one row of **every** kind the planner can place, because the row icons, colors
 * and second lines are a `when` over the whole hierarchy and a screenshot is the only thing that
 * shows all of them at once.
 *
 * What is deliberately not here: the song editor a result row's pencil opens (it is the app's own
 * Edit Song dialog, covered by `EditSongDialogScreenshotTest`), the color picker a section swatch
 * opens (`SettingsFieldsScreenshotTest`), and the PDF export's file chooser, which is the platform's.
 */
class CalendarScreenshotTest {

    // ── The window ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the month, the day and the run of show`() = shoot("month")

    @Test
    fun `a day with nothing planned`() = shoot("empty_day", document = CalendarDocument())

    @Test
    fun `a day holding two services`() = shoot("two_services", document = documentWith(sunday(), evening()))

    /** A calendar opened from a backup says so until it is dismissed. */
    @Test
    fun `the banner shown when the file could not be read`() = shoot("recovered", corrupt = true)

    // ── The run of show ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a row opened for editing, with its timing panel`() = shoot("row_editor", rootIndex = 1, trim = true) {
        clickText("Amazing Grace")
    }

    @Test
    fun `a row's length typed in place`() = shoot("duration_typed") {
        clickText("5:00")
        onAllNodes(hasSetTextAction())[0].performTextInput("5:30")
        waitForIdle()
    }

    /** The clock stepped on, which is how a planner sees which cues would have fired. */
    @Test
    fun `the run clock stepped forward`() = shoot("run_clock_stepped") {
        repeat(2) { clickIcon("Run clock") }
    }

    /** What each song has actually taken, offered beside the plan where it differs. */
    @Test
    fun `measured lengths offered beside the plan`() = shoot("measured_lengths", measured = true)

    @Test
    fun `a row opened for editing, with its measured length among the choices`() =
        shoot("row_editor_measured", rootIndex = 1, trim = true, measured = true) {
            clickText("Amazing Grace")
        }

    /** Every file moved and a verse the Bible does not have: the marks, and the header's count. */
    @Test
    fun `the pre-flight marks`() = shoot("preflight_marks", document = documentWith(withEverythingMissing()))

    /** A cue that was due while the operator was live: the toast, and the mark on its row. */
    @Test
    fun `a cue the engine skipped`() = shoot("cue_skipped") {
        val cue = CalendarScreenshotTest.sunday().items.first { it is ScheduleItem.CueItem }
        CueFeed.post(FiredCue(cue, LocalTime.of(9, 58), skipped = true))
        waitForIdle()
    }

    @Test
    fun `loading a service that would replace a schedule already in use`() =
        shoot("load_confirm", rootIndex = 1, trim = true, scheduleInUse = true) {
            clickText("Load into Schedule")
        }

    // ── The picker ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the picker, on songs`() = sheet("picker_songs") { openPicker() }

    @Test
    fun `the picker's song results, filtered`() = sheet("picker_songs_filtered") {
        openPicker()
        onAllNodes(hasSetTextAction())[0].performTextInput("grace")
        waitForIdle()
    }

    @Test
    fun `the picker's bible tab, on its books`() = sheet("picker_bible") {
        openPicker()
        clickText("Bible")
    }

    @Test
    fun `the picker's bible tab, on a chapter's verses`() =
        sheet("picker_bible_verses") {
            openPicker()
            clickText("Bible")
            clickInSheet("Genesis")
            clickInSheet("1")
        }

    @Test
    fun `the picker's section tab`() = sheet("picker_sections") {
        openPicker()
        // Exactly "Section", and inside the sheet: the row that opened it reads "Add song, verse
        // or section", which a substring match would find first and click shut again.
        clickInSheet("Section", anchor = "Songs")
    }

    /** The ministry tab: what happens up front and never on screen, as three fields on one row. */
    @Test
    fun `the picker's ministry tab, filled in`() = sheet("picker_ministry") {
        openPicker()
        clickInSheet("Ministry", anchor = "Songs")
        onAllNodes(hasSetTextAction())[0].performTextInput("Violin")
        onAllNodes(hasSetTextAction())[1].performTextInput("Jake")
        onAllNodes(hasSetTextAction())[2].performTextInput("3:30")
        waitForIdle()
    }

    @Test
    fun `the picker's presets tab, with its kind chips`() = sheet("picker_presets") {
        openPicker()
        clickText("Presets")
    }

    @Test
    fun `a preset opened to show what it puts on screen`() =
        sheet("picker_preset_preview") {
            openPicker()
            clickText("Presets")
            clickIcon("Preview")
        }

    // ── The sheets ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `adding a service, with somewhere to start from`() = shoot("service_new", rootIndex = 1, trim = true) {
        clickText("Add service")
    }

    @Test
    fun `editing a service that belongs to a series`() =
        shoot("service_series", seriesDocument(), rootIndex = 1, trim = true) {
            clickIcon("Edit service")
        }

    @Test
    fun `copying a service to another date`() = shoot("copy_sheet", rootIndex = 1, trim = true) {
        clickIcon("Copy this service")
    }

    /** The same sheet with a repeat chosen, which brings up the count and the date preview. */
    @Test
    fun `copying a service as a weekly series`() = shoot("copy_sheet_repeating", rootIndex = 1, trim = true) {
        clickIcon("Copy this service")
        clickLast("Weekly")
    }

    @Test
    fun `saving a run of show as a template`() = shoot("template_sheet", rootIndex = 1, trim = true) {
        clickIcon("Save this run of show as a template")
    }

    // ── Settings ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `settings, on its sections`() = shoot("settings_sections", rootIndex = 1, trim = true) { openSettings() }

    @Test
    fun `settings, on the templates it has saved`() = shoot("settings_templates", rootIndex = 1, trim = true) {
        openSettings()
        clickText("Templates")
    }

    @Test
    fun `settings, on the presets the tabs have saved`() = shoot("settings_presets", rootIndex = 1, trim = true) {
        openSettings()
        clickLast("Presets")
    }

    @Test
    fun `settings, on the pdf export`() = shoot("settings_export", rootIndex = 1, trim = true) {
        openSettings()
        clickLast("Export")
    }

    /** The header's Export split open, on the choice between the two copies. */
    @Test
    fun `the export menu`() = shoot("export_menu", rootIndex = 1, trim = true) {
        clickIcon("Choose which copy to export")
    }

    // ── What the window says about the app around it ──────────────────────────────────────────────

    /** Auto-load off -- the default -- so the note that says so, and offers to turn it on, shows. */
    @Test
    fun `the note shown while services do not load by themselves`() = shoot(
        "auto_load_off",
        document = documentWith(sunday()).let { it.copy(preferences = it.preferences.copy(autoLoadService = false)) },
    )

    /**
     * Sync on, so the header offers Invite a phone and counts down to the next pull. The countdown is
     * always 4:12 ahead of whenever it is read, so the picture cannot catch it mid-tick.
     */
    @Test
    fun `the header counting down to the next sync`() = shoot(
        "sync_countdown",
        cloudSync = CalendarCloudSync(
            enabled = { true },
            setEnabled = {},
            nextSyncAt = { System.currentTimeMillis() + SYNC_COUNTDOWN_MILLIS },
        ),
    )

    @Test
    fun `a service's date field, with its month open`() = shoot("service_date_picker", rootIndex = 2, trim = true) {
        clickIcon("Edit service")
        // The day heading reads the same date, so the field is the last node that does.
        val field = onAllNodesWithText(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).format(TODAY),
        )
        field[field.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    @Test
    fun `a new service starting from the Schedule tab`() =
        shoot("service_new_from_schedule", rootIndex = 1, trim = true, scheduleInUse = true) {
            clickText("Add service")
            onAllNodesWithText("The Schedule tab")[0].performClick()
            waitForIdle()
        }

    /** Auto-load on, and scrolled to it, so the lead-time row it reveals is in the shot. */
    @Test
    fun `settings, on the defaults`() = shoot("settings_defaults", rootIndex = 1, trim = true) {
        openSettings()
        clickText("Defaults")
        onAllNodesWithText("How far ahead")[0].performScrollTo()
        waitForIdle()
    }

    // ── Getting there ───────────────────────────────────────────────────────────────────────────

    /**
     * A picker shot, over a plan still being built.
     *
     * The picker opens from the **last row** of the run of show, and the full one-of-every-kind
     * plan puts that row below the fold — so these are shot over a shorter plan rather than over a
     * scrolled one. The sheet fills the window either way; what is behind it is not the picture.
     */
    private fun sheet(
        name: String,
        rootIndex: Int = 1,
        trim: Boolean = true,
        drive: ComposeUiTest.() -> Unit,
    ) = shoot(name, documentWith(planInProgress()), rootIndex, trim, drive = drive)

    private fun ComposeUiTest.openPicker() = clickFirst("Add song, verse or section")

    private fun ComposeUiTest.openSettings() = clickFirst("Calendar settings")

    private fun ComposeUiTest.clickText(text: String) {
        onAllNodesWithText(text, substring = true, ignoreCase = true)[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.clickFirst(text: String) = clickText(text)

    private fun ComposeUiTest.clickLast(text: String) {
        val nodes = onAllNodesWithText(text, substring = true, ignoreCase = true)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    /** An icon button carries its label as a description rather than as text. */
    private fun ComposeUiTest.clickIcon(description: String) {
        onAllNodesWithContentDescription(description, substring = true)[0].performClick()
        waitForIdle()
    }

    /**
     * Clicks [text] inside the open sheet.
     *
     * A dialog is its own compose root over a window whose month grid is a wall of day numbers, so
     * "1" matches a chapter tile and a Sunday alike. The node sharing a root with [anchor] — a
     * label only the sheet draws — is the one meant.
     */
    private fun ComposeUiTest.clickInSheet(text: String, anchor: String = "All books") {
        val sheet = onAllNodesWithText(anchor, substring = true).fetchSemanticsNodes().first().root
        val nodes = onAllNodesWithText(text, substring = false)
        val index = nodes.fetchSemanticsNodes().indexOfFirst { it.root === sheet }
        nodes[index].performClick()
        waitForIdle()
    }

    private fun shoot(
        name: String = "",
        document: CalendarDocument = documentWith(sunday()),
        rootIndex: Int = 0,
        trim: Boolean = false,
        corrupt: Boolean = false,
        scheduleInUse: Boolean = false,
        /** Whether the host answers how long each row usually runs -- the `usually` chips. */
        measured: Boolean = false,
        /** The app's calendar sync, for the header's invite and next-sync countdown. */
        cloudSync: CalendarCloudSync? = null,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name, trim) { mode, file ->
        val folder = Files.createTempDirectory("calendar-shot").toFile()
        val songs = Files.createTempDirectory("calendar-shot-songs").toFile()
        try {
            // A feed left over from another shot would mark this one's cues.
            CueFeed.clear()
            seed(folder, document, corrupt)
            SongLibrary(songs).let { library -> STOCK_SONGS.forEach { library.writeNew(it) } }
            runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.fillMaxSize()) {
                            CalendarApp(
                                storeFolder = folder,
                                songFolder = songs,
                                host = host(scheduleInUse, measured, cloudSync),
                                onClose = {},
                                today = TODAY,
                                // Pinned: the run of show draws the clock and marks which cues
                                // have fired against it, so the real one would make every one of
                                // these images differ from the last recording.
                                now = { NOW },
                                // Unconfined so the store and the song folder are read inline: the
                                // window is what is being photographed, not the disk it loads from.
                                // A watch that never finishes is a test that never settles.
                                watchStoreFolder = false,
                                io = Dispatchers.Unconfined,
                            )
                        }
                    }
                }
                waitForIdle()
                drive()
                waitForIdle()
                captureTo(file, rootIndex)
            }
        } finally {
            folder.deleteRecursively()
            songs.deleteRecursively()
        }
    }

    /**
     * Writes the calendar the window opens on.
     *
     * With [corrupt] the file itself is nonsense and the newest backup holds the document, which is
     * the only way to reach the recovery banner: the window decides it has recovered by failing to
     * read, not by being told.
     */
    private fun seed(folder: File, document: CalendarDocument, corrupt: Boolean) {
        val json = Json { encodeDefaults = true }.encodeToString(CalendarDocument.serializer(), document)
        File(folder, "calendar.json").writeText(if (corrupt) "{ not json" else json)
        if (corrupt) File(folder, "calendar.json.bak1").writeText(json)
        PresetStore(folder).save(PresetDocument(presets = STOCK_PRESETS))
    }

    private fun host(scheduleInUse: Boolean, measured: Boolean, cloudSync: CalendarCloudSync?) = CalendarHost(
        cloudSync = cloudSync,
        bibleBooks = { BIBLE_BOOKS },
        currentSchedule = {
            if (scheduleInUse) List(4) { index -> song("s$index", 100 + index, "In the schedule") } else emptyList()
        },
        // What a song has usually taken here: twelve seconds over what is planned, so every song
        // row -- and the row editor -- offers the measured length beside the plan.
        measuredSeconds = { row -> if (measured && row is ScheduleItem.SongItem) 312 else null },
    )

    private companion object {
        const val SECTION = "calendarManager"

        /** How far ahead the sync countdown reads: 4:12. */
        const val SYNC_COUNTDOWN_MILLIS = 252_000L

        /**
         * The size the app opens this window at (`CalendarWindow`), so the shots are the layout a
         * planner actually sees.
         *
         * The default test surface is 1024x768, and at that width the run of show's header drops
         * its clock chip and its add row falls below the fold — states that exist only because the
         * window was too small, photographed as if they were the design.
         */
        const val WINDOW_WIDTH = 1280
        const val WINDOW_HEIGHT = 860

        /** A Sunday, so the month grid and the service's own week read as a real plan. */
        val TODAY: LocalDate = LocalDate.of(2026, 9, 20)

        /** Ten past ten: the service is under way, so fired cues and pending ones are both shown. */
        val NOW: LocalTime = LocalTime.of(10, 10)

        val BIBLE_BOOKS = listOf(
            CalendarBibleBook(bookId = 1, name = "Genesis", verseCounts = listOf(31, 25, 24)),
            // A hundred chapters, so the fixture's Psalm 100 passes the pre-flight check: the
            // check reads this list, and a verse the stub cannot find would mark every shot.
            CalendarBibleBook(bookId = 19, name = "Psalms", verseCounts = List(99) { 8 } + 5),
            CalendarBibleBook(bookId = 43, name = "John", verseCounts = listOf(51, 25, 36)),
            CalendarBibleBook(bookId = 45, name = "Romans", verseCounts = listOf(32, 29, 31)),
        )

        fun song(id: String, number: Int, title: String) = ScheduleItem.SongItem(
            id = id, songNumber = number, title = title, songbook = "Hymnal", songId = "Hymnal::$number",
        )

        fun heading(id: String, text: String, color: String) =
            ScheduleItem.LabelItem(id = id, text = text, textColor = "#FFFFFF", backgroundColor = color)

        /**
         * A service with one row of every kind, which is what makes this suite worth having: the
         * icons, the colors and the second lines are each a `when` over the whole row hierarchy.
         */
        /**
         * The files the fixture's rows point at, on disk, so the pre-flight check finds them and
         * the default shots carry no warning marks -- those are a state of their own, shot over
         * [withEverythingMissing]. Made once per JVM and removed with it.
         */
        val ASSETS: File by lazy {
            Files.createTempDirectory("calendar-shot-assets").toFile().also { root ->
                root.deleteOnExit()
                File(root, "photos/welcome").mkdirs()
                File(root, "photos/welcome/one.png").writeText("png")
                File(root, "decks").mkdirs()
                File(root, "decks/sermon.pptx").writeText("pptx")
                File(root, "clips").mkdirs()
                File(root, "clips/testimony.mp4").writeText("mp4")
                ImageIO.write(logoImage(), "png", File(root, "logo.png"))
            }
        }

        /** A plain drawn mark, so the Export tab's logo preview has a real picture to show. */
        private fun logoImage(): BufferedImage = BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            graphics.color = AwtColor(0x5B, 0x9D, 0xF5)
            graphics.fillOval(8, 8, 80, 80)
            graphics.color = AwtColor.WHITE
            graphics.fillRect(42, 20, 12, 56)
            graphics.fillRect(26, 36, 44, 12)
            graphics.dispose()
        }

        /** [sunday] with every file gone and a verse the Bible does not have: the pre-flight marks. */
        fun withEverythingMissing(): PlannedService = sunday().let { service ->
            service.copy(
                items = service.items.map { row ->
                    when (row) {
                        is ScheduleItem.PictureItem -> row.copy(folderPath = "/gone/photos")
                        is ScheduleItem.PresentationItem -> row.copy(filePath = "/gone/sermon.pptx")
                        is ScheduleItem.MediaItem -> row.copy(mediaUrl = "/gone/testimony.mp4")
                        is ScheduleItem.BibleVerseItem -> row.copy(bookName = "Nowhere", bookId = 0)
                        else -> row
                    }
                },
            )
        }

        fun sunday() = PlannedService(
            id = "sunday",
            date = TODAY.toString(),
            name = "Sunday Morning",
            startTime = "10:00",
            kind = ServiceKind.SUNDAY.id,
            items = listOf(
                heading("h1", "Pre-Service", "#4FD3E8"),
                ScheduleItem.PictureItem("pics", File(ASSETS, "photos/welcome").path, "Welcome loop", 24),
                ScheduleItem.CueItem(
                    id = "cue", action = CueAction.GO_LIVE, label = "Go live", absoluteTime = "09:58",
                ),
                heading("h2", "Worship", "#5B9DF5"),
                song("a", 1, "Amazing Grace"),
                song("b", 42, "Here I Am to Worship"),
                ScheduleItem.BibleVerseItem(
                    id = "v", bookName = "Psalms", chapter = 100, verseNumber = 1,
                    verseText = "Make a joyful noise unto the LORD, all ye lands.", verseRange = "1-5",
                ),
                heading("h3", "Word", "#E8A33D"),
                ScheduleItem.PresentationItem("deck", File(ASSETS, "decks/sermon.pptx").path, "Sermon", 18, "pptx"),
                ScheduleItem.MediaItem("clip", File(ASSETS, "clips/testimony.mp4").path, "Testimony", "local"),
                ScheduleItem.MinistryItem("solo", "Violin", "Jake"),
                heading("h4", "Response", "#6FD8A8"),
                ScheduleItem.SceneItem("scene", "scene-1", "Bible with Background"),
                ScheduleItem.AnnouncementItem("ann", "Fellowship lunch after the service"),
                ScheduleItem.WebsiteItem("web", "https://example.org/give", "Giving page"),
                ScheduleItem.LowerThirdItem("third", "preset-1", "Pastor Ruth", false, 0),
                ScheduleItem.DictionaryItem("dict", "G26", "agapē", "agape", "love, goodwill"),
            ),
            plannedSeconds = mapOf(
                "pics" to 600, "a" to 300, "b" to 270, "v" to 120, "deck" to 1920, "clip" to 240,
                "solo" to 210, "scene" to 180, "ann" to 90,
            ),
            timing = mapOf(
                "pics" to RowTiming(startAt = "09:45", repeats = 0, atEnd = RowEnd.NEXT),
                "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
                "b" to RowTiming(followsPrevious = true, atEnd = RowEnd.NEXT),
                "clip" to RowTiming(runSeconds = null, repeats = 2, atEnd = RowEnd.BLANK),
            ),
        )

        /**
         * A service part-way through being planned, which is when the sheets are actually opened.
         *
         * The picker opens from the last row of the run of show, and a plan with one row of every
         * kind puts that row below the fold — so the sheets are shot over a shorter plan rather
         * than over a scrolled one.
         */
        fun planInProgress() = PlannedService(
            id = "sunday",
            date = TODAY.toString(),
            name = "Sunday Morning",
            startTime = "10:00",
            kind = ServiceKind.SUNDAY.id,
            items = listOf(
                heading("h1", "Pre-Service", "#4FD3E8"),
                ScheduleItem.PictureItem("pics", "/photos/welcome", "Welcome loop", 24),
                heading("h2", "Worship", "#5B9DF5"),
                song("a", 1, "Amazing Grace"),
            ),
            plannedSeconds = mapOf("pics" to 600, "a" to 300),
            timing = mapOf("pics" to RowTiming(startAt = "09:45", repeats = 0, atEnd = RowEnd.NEXT)),
        )

        fun evening() = PlannedService(
            id = "evening",
            date = TODAY.toString(),
            name = "Evening Prayer",
            startTime = "18:30",
            kind = ServiceKind.MIDWEEK.id,
            items = listOf(heading("e1", "Prayer", "#C9A2F0"), song("e2", 7, "Be Thou My Vision")),
            plannedSeconds = mapOf("e2" to 240),
        )

        /** The same Sunday as part of a weekly series, for the sheet's "Applies to" section. */
        fun seriesDocument(): CalendarDocument {
            val first = sunday().copy(seriesId = "weekly", repeat = ServiceRepeat.WEEKLY.id)
            val next = first.copy(
                id = "sunday-2", date = TODAY.plusWeeks(1).toString(), items = emptyList(),
                plannedSeconds = emptyMap(), timing = emptyMap(),
            )
            return documentWith(first, next)
        }

        fun documentWith(vararg services: PlannedService) = CalendarDocument(
            services = services.toList(),
            templates = listOf(
                SavedTemplate(
                    id = "t1", name = "Sunday Morning", startTime = "10:00",
                    kind = ServiceKind.SUNDAY.id, items = listOf(heading("t-h", "Worship", "#5B9DF5")),
                ),
                SavedTemplate(
                    id = "t2", name = "Carol Service", startTime = "18:00",
                    kind = ServiceKind.SPECIAL.id, items = listOf(song("t-s", 3, "Silent Night")),
                ),
            ),
            // On, so the Defaults tab is shot with the lead-time row the switch reveals; and a
            // letterhead filled in, so the Export tab is shot the way a church leaves it.
            preferences = CalendarPreferences(
                autoLoadService = true,
                autoLoadLeadMinutes = 10,
                pdfExport = PdfExportSettings(
                    logoPath = File(ASSETS, "logo.png").absolutePath,
                    churchName = "Grace Community Church",
                    churchAddress = "120 Main Street, Springfield",
                ),
            ),
        )

        val STOCK_PRESETS = listOf(
            ItemPreset(
                id = "p1", name = "Countdown to start", savedAt = "2026-09-01T09:00",
                item = ScheduleItem.AnnouncementItem(
                    id = "p1i", text = "", isTimer = true, timerMinutes = 5,
                    timerExpiredText = "We begin shortly",
                ),
            ),
            ItemPreset(
                id = "p2", name = "Welcome slideshow", savedAt = "2026-08-30T09:00",
                item = ScheduleItem.PictureItem("p2i", "/photos/welcome", "Welcome loop", 24),
            ),
            ItemPreset(
                id = "p3", name = "Giving scene", savedAt = "2026-08-20T09:00",
                item = ScheduleItem.SceneItem("p3i", "scene-2", "Giving"),
            ),
            ItemPreset(
                id = "p4", name = "Announcement loop", savedAt = "2026-08-10T09:00",
                item = ScheduleItem.AnnouncementItem(id = "p4i", text = "Fellowship lunch after the service"),
            ),
        )

        val STOCK_SONGS = listOf(
            librarySong("1", "Amazing Grace", "John Newton"),
            librarySong("42", "Here I Am to Worship", "Tim Hughes"),
            librarySong("7", "Be Thou My Vision", "Dallán Forgaill"),
            librarySong("108", "Grace Unmeasured", "Bob Kauflin"),
            librarySong("15", "Holy, Holy, Holy", "Reginald Heber"),
        )

        fun librarySong(number: String, title: String, author: String) = SongItem(
            number = number,
            title = title,
            songbook = "Hymnal",
            author = author,
            lyrics = listOf("[Verse 1]", "A line of the song", "", "{Chorus}", "Sing it again"),
        )
    }
}
