@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.app.churchpresenter.StageMonitorScreen
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.settings.toZone
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The stage monitor — the screen the worship leader and the speaker read from.
 *
 * It is a grid of zones rather than one surface: every kind of content is assigned to a corner (or
 * to the whole screen), and each zone carries its own font, colours and alignment. So the states
 * worth reviewing are *which zone a thing lands in* and *what a zone looks like*, not just what is
 * live — which is why the layout shots below matter as much as the content ones.
 *
 * One image per state, not a light/dark pair: this screen paints from `StageMonitorSettings`, not
 * from the operator's theme.
 *
 * **The clock is switched off in every shot.** Its zone draws the wall clock, so leaving it on would
 * rewrite every one of these images the moment the minute turned — the same rule the Announcements
 * timer modes and the canvas clock source are held to.
 */
class StageMonitorScreenshotTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(
        name: String,
        settings: StageMonitorSettings = stageSettings(),
        showChords: Boolean = true,
        presenting: Presenting = Presenting.LYRICS,
        announcementActive: Boolean = false,
        section: LyricSection = songSection(),
        sections: List<LyricSection> = emptyList(),
        sectionIndex: Int = 0,
        verses: List<SelectedVerse> = emptyList(),
        nextVerses: List<SelectedVerse> = emptyList(),
        announcementText: String = "",
        imagePath: String? = null,
        slide: ImageBitmap? = null,
        notes: String = "",
        scene: Scene? = null,
        question: Question? = null,
        entry: StrongsEntry? = null,
        awaitPicture: Boolean = false,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    StageMonitorScreen(
                        sm = settings,
                        showChords = showChords,
                        presentingMode = presenting,
                        announcementActive = announcementActive,
                        currentLyricSection = section,
                        allLyricSections = sections,
                        songDisplaySectionIndex = sectionIndex,
                        displayedVerses = verses,
                        nextVerses = nextVerses,
                        announcementText = announcementText,
                        displayedImagePath = imagePath,
                        displayedSlide = slide,
                        presenterNotes = notes,
                        activeScene = scene,
                        displayedQuestion = question,
                        displayedDictionaryEntry = entry,
                        qaSettings = QASettings(),
                        dictionarySettings = DictionarySettings(),
                    )
                }
            }
        }
        // The picture zone loads its file on Dispatchers.IO, which the test clock does not wait for.
        // The slide carries a test tag for exactly this; without the wait the zone captures empty.
        if (awaitPicture) {
            waitUntil("the picture to be decoded", RENDER_TIMEOUT_MS) {
                onAllNodesWithTag("stage_slide").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    // ── Nothing live ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing live yet`() = shoot("idle", presenting = Presenting.NONE, section = LyricSection())

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    /** The section on screen, and the one after it in the corner the band watches. */
    @Test
    fun `a song, with the next section`() = shoot(
        "song",
        sections = SONG_SECTIONS,
        sectionIndex = 0,
    )

    @Test
    fun `the last section, with nothing after it`() = shoot(
        "song_last_section",
        section = SONG_SECTIONS.last(),
        sections = SONG_SECTIONS,
        sectionIndex = SONG_SECTIONS.lastIndex,
    )

    /**
     * A song whose chart the band reads, chords above the words.
     *
     * The chart is drawn from `LyricSection.chordLines` — the lines as written — while `lines` are
     * the same words with the markup taken off. The song parser is the one place that split happens,
     * so nothing downstream ever sees a `[G]`; a fixture that puts markup in `lines` gets it printed
     * inline, which is a broken fixture rather than a broken screen.
     */
    @Test
    fun `a song with its chords`() = shoot(
        "song_chords",
        showChords = true,
        section = chordSection(),
        sections = listOf(chordSection()),
    )

    /** The same song with chords off: the words alone, no chart. */
    @Test
    fun `the same song with chords turned off`() = shoot(
        "song_chords_hidden",
        showChords = false,
        section = chordSection(),
        sections = listOf(chordSection()),
    )

    /** A tempo set on the song lights the metronome dot in whichever corner it is anchored to. */
    @Test
    fun `a song with the metronome running`() = shoot(
        "song_metronome",
        settings = stageSettings(metronomePosition = MetronomePosition.TOP_RIGHT),
        section = songSection().copy(bpm = 72),
        sections = SONG_SECTIONS,
    )

    // ── Scripture ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a verse, with the next one`() = shoot(
        "bible",
        presenting = Presenting.BIBLE,
        section = LyricSection(),
        verses = listOf(verse()),
        nextVerses = listOf(verse(number = 17, text = "For God sent not his Son into the world to condemn the world.")),
    )

    @Test
    fun `a verse with nothing after it`() = shoot(
        "bible_no_next",
        presenting = Presenting.BIBLE,
        section = LyricSection(),
        verses = listOf(verse()),
    )

    // ── The other content types ─────────────────────────────────────────────────────────────────

    /**
     * Speaker notes from the deck — the reason a preacher looks at this screen at all.
     *
     * The notes need a zone of their own: the slide and the notes both default to the full screen,
     * so with the defaults one simply wins and the notes are never seen.
     */
    @Test
    fun `presenter notes`() = shoot(
        "presentation_notes",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.PRESENTATION to StageMonitorZone.A,
                StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.D,
            )
        ),
        presenting = Presenting.PRESENTATION,
        section = LyricSection(),
        slide = slideBitmap(),
        notes = NOTES,
    )

    @Test
    fun `a slide with no notes on it`() = shoot(
        "presentation_slide",
        presenting = Presenting.PRESENTATION,
        section = LyricSection(),
        slide = slideBitmap(),
    )

    @Test
    fun `an announcement routed here`() = shoot(
        "announcement",
        presenting = Presenting.LYRICS,
        announcementActive = true,
        announcementText = "Prayer meeting Wednesday at 7pm in the hall",
        sections = SONG_SECTIONS,
    )

    /** A countdown sent here reads as an announcement — they share one pre-formatted string. */
    @Test
    fun `a countdown routed here`() = shoot(
        "announcement_timer",
        presenting = Presenting.LYRICS,
        announcementActive = true,
        announcementText = "05:00",
        sections = SONG_SECTIONS,
    )

    @Test
    fun `a question`() = shoot(
        "qa",
        presenting = Presenting.QA,
        section = LyricSection(),
        question = Question(
            id = "q1",
            text = "How do I join a small group?",
            timestamp = 0L,
            status = QuestionStatus.APPROVED,
        ),
    )

    @Test
    fun `a dictionary entry`() = shoot(
        "dictionary",
        presenting = Presenting.DICTIONARY,
        section = LyricSection(),
        entry = strongs(),
    )

    @Test
    fun `a canvas scene`() = shoot(
        "canvas",
        presenting = Presenting.CANVAS,
        section = LyricSection(),
        scene = scene(),
    )

    // ── Layouts ─────────────────────────────────────────────────────────────────────────────────

    /** Everything on one full-screen zone — the simplest setup, and the default for most types. */
    @Test
    fun `one full-screen zone`() = shoot(
        "layout_full_screen",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.SONGS to StageMonitorZone.FULL_SCREEN,
                StageMonitorContentType.NEXT to StageMonitorZone.NONE,
            )
        ),
        sections = SONG_SECTIONS,
    )

    /** Four corners in use at once: words, what is next, the announcement and the notes. */
    @Test
    fun `four quadrants`() = shoot(
        "layout_four_quadrants",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.SONGS to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.C,
                StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.E,
            )
        ),
        announcementActive = true,
        announcementText = "Offering after the second song",
        notes = NOTES,
        sections = SONG_SECTIONS,
    )

    @Test
    fun `the words across the bottom instead`() = shoot(
        "layout_bottom_band",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.SONGS to StageMonitorZone.D,
                StageMonitorContentType.NEXT to StageMonitorZone.A,
            )
        ),
        sections = SONG_SECTIONS,
    )

    /** A picture, which fills whatever zone it is given rather than being typeset into it. */
    @Test
    fun `a picture`() = shoot(
        "pictures",
        presenting = Presenting.PICTURES,
        section = LyricSection(),
        imagePath = photo().absolutePath,
        awaitPicture = true,
    )

    /** The same picture in a corner, with the words still on the screen beside it. */
    @Test
    fun `a picture in a corner`() = shoot(
        "pictures_corner",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.PICTURES to StageMonitorZone.E,
                StageMonitorContentType.SONGS to StageMonitorZone.A,
            )
        ),
        presenting = Presenting.PICTURES,
        imagePath = photo().absolutePath,
        sections = SONG_SECTIONS,
        awaitPicture = true,
    )

    @Test
    fun `a question in a corner`() = shoot(
        "qa_corner",
        settings = stageSettings(zones = mapOf(StageMonitorContentType.QA to StageMonitorZone.C)),
        presenting = Presenting.QA,
        section = LyricSection(),
        question = Question(
            id = "q1",
            text = "How do I join a small group?",
            timestamp = 0L,
            status = QuestionStatus.APPROVED,
        ),
    )

    @Test
    fun `a dictionary entry in a corner`() = shoot(
        "dictionary_corner",
        settings = stageSettings(zones = mapOf(StageMonitorContentType.DICTIONARY to StageMonitorZone.B)),
        presenting = Presenting.DICTIONARY,
        section = LyricSection(),
        entry = strongs(),
    )

    @Test
    fun `a scene in a corner`() = shoot(
        "canvas_corner",
        settings = stageSettings(zones = mapOf(StageMonitorContentType.CANVAS to StageMonitorZone.D)),
        presenting = Presenting.CANVAS,
        section = LyricSection(),
        scene = scene(),
    )

    /**
     * A type sent nowhere: this leader wants only what is coming next, not the words on screen.
     *
     * Hiding *Next* instead would be the same picture as `song_chords_hidden`, which already shows a
     * lone top-left zone.
     */
    @Test
    fun `a content type switched off`() = shoot(
        "content_hidden",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.SONGS to StageMonitorZone.NONE,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
            )
        ),
        sections = SONG_SECTIONS,
    )

    // ── Metronome positions ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the metronome bottom left`() = shoot(
        "metronome_bottom_left",
        settings = stageSettings(metronomePosition = MetronomePosition.BOTTOM_LEFT),
        section = songSection().copy(bpm = 72),
        sections = SONG_SECTIONS,
    )

    @Test
    fun `the metronome dead centre`() = shoot(
        "metronome_centre",
        settings = stageSettings(metronomePosition = MetronomePosition.CENTER),
        section = songSection().copy(bpm = 72),
        sections = SONG_SECTIONS,
    )

    // ── More than a zone can hold ───────────────────────────────────────────────────────────────

    /** Notes longer than their zone: they scroll rather than being cut off. */
    @Test
    fun `notes longer than the zone`() = shoot(
        "long_notes",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.PRESENTATION to StageMonitorZone.A,
                StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.D,
            )
        ),
        presenting = Presenting.PRESENTATION,
        section = LyricSection(),
        slide = slideBitmap(),
        notes = LONG_NOTES,
    )

    /** A long verse in a corner zone — the words step down to whatever size fits. */
    @Test
    fun `a long verse in a corner`() = shoot(
        "bible_long",
        presenting = Presenting.BIBLE,
        section = LyricSection(),
        verses = listOf(verse(text = LONG_PASSAGE)),
        nextVerses = listOf(verse(number = 2, text = "He maketh me to lie down in green pastures.")),
    )

    // ── Zone styling ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `zones styled apart from each other`() = shoot(
        "styled_zones",
        settings = stageSettings(
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontSize = 64, color = "#FFD54F", bgColor = "#1B2A5B", bold = true, shadow = true,
                ),
                StageMonitorStyleZone.B to StageMonitorZoneStyle(
                    fontSize = 28, color = "#8FB3F5", bgColor = "#10131A", italic = true,
                    verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER,
                ),
            )
        ),
        sections = SONG_SECTIONS,
    )

    /** A different face per zone — the fonts are per-zone settings, not one screen-wide choice. */
    @Test
    fun `zones in different fonts`() = shoot(
        "zone_fonts",
        settings = stageSettings(
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontType = "Georgia", fontSize = 44, color = "#FFFFFF", bgColor = "#000000",
                ),
                StageMonitorStyleZone.B to StageMonitorZoneStyle(
                    fontType = "Courier New", fontSize = 32, color = "#8FB3F5", bgColor = "#000000",
                    verticalAlignment = Constants.MIDDLE, horizontalAlignment = Constants.CENTER,
                ),
            )
        ),
        sections = SONG_SECTIONS,
    )

    /** Bold, italic, underlined and shadowed — the four text switches a zone carries. */
    @Test
    fun `every text switch on`() = shoot(
        "zone_text_switches",
        settings = stageSettings(
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontSize = 44, color = "#FFFFFF", bgColor = "#1B2A5B",
                    bold = true, italic = true, underline = true, shadow = true,
                ),
            )
        ),
        sections = SONG_SECTIONS,
    )

    /** Where the words sit inside their zone: pinned to a corner, or centred in it. */
    @Test
    fun `zones aligned differently`() = shoot(
        "zone_alignment",
        settings = stageSettings(
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontSize = 40, color = "#FFFFFF", bgColor = "#000000",
                    verticalAlignment = Constants.BOTTOM, horizontalAlignment = Constants.RIGHT,
                ),
                StageMonitorStyleZone.B to StageMonitorZoneStyle(
                    fontSize = 40, color = "#FFFFFF", bgColor = "#000000",
                    verticalAlignment = Constants.TOP, horizontalAlignment = Constants.LEFT,
                ),
            )
        ),
        sections = SONG_SECTIONS,
    )

    /** Each zone on its own ground, so the leader can tell them apart at a glance. */
    @Test
    fun `zones on their own backgrounds`() = shoot(
        "zone_backgrounds",
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.SONGS to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.C,
            ),
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontSize = 40,
                    color = "#FFFFFF",
                    bgColor = "#10131A",
                ),
                StageMonitorStyleZone.B to StageMonitorZoneStyle(
                    fontSize = 36,
                    color = "#10131A",
                    bgColor = "#8FB3F5",
                ),
                StageMonitorStyleZone.C to StageMonitorZoneStyle(
                    fontSize = 32,
                    color = "#FFD54F",
                    bgColor = "#3B1F5B",
                ),
            ),
        ),
        announcementActive = true,
        announcementText = "Offering after the second song",
        sections = SONG_SECTIONS,
    )

    /** The chart's chords take the zone's own chord colour. */
    @Test
    fun `chords in the zone's colour`() = shoot(
        "chord_colour",
        showChords = true,
        settings = stageSettings(
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontSize = 40, color = "#FFFFFF", bgColor = "#000000", chordColor = "#FFD54F",
                ),
            ),
        ),
        section = chordSection(),
        sections = listOf(chordSection()),
    )

    @Test
    fun `large type for a leader who cannot see the screen`() = shoot(
        "large_type",
        settings = stageSettings(
            styles = mapOf(
                StageMonitorStyleZone.A to StageMonitorZoneStyle(
                    fontSize = 96,
                    color = "#FFFFFF",
                    bgColor = "#000000",
                ),
            )
        ),
        sections = SONG_SECTIONS,
    )

    // ── Every layout in the catalog ─────────────────────────────────────────────────────────────
    // One shot per arrangement, each routing a content type into every zone it draws, so the row and
    // cell weights are visible rather than inferred.
    //
    // The five-zone shots come out with one cell empty, and that is the truth rather than a gap:
    // only four content types can be live at once — what is being presented, its look-ahead, the
    // clock and an announcement — so a fifth zone has nothing to put in it until the mode changes.

    @Test
    fun `the two-zone layout, top over bottom`() = layoutShot("layout_top_bottom", StageMonitorLayout.TOP_BOTTOM)

    @Test
    fun `the two-zone layout, side by side`() = layoutShot("layout_left_right", StageMonitorLayout.LEFT_RIGHT)

    @Test
    fun `three zones, one over two`() = layoutShot("layout_top_two_below", StageMonitorLayout.TOP_TWO_BELOW)

    @Test
    fun `three zones stacked as rows`() = layoutShot("layout_three_rows", StageMonitorLayout.THREE_ROWS)

    @Test
    fun `four zones as a quad grid`() = layoutShot("layout_quad", StageMonitorLayout.QUAD)

    @Test
    fun `four zones, one over three`() = layoutShot("layout_top_three_below", StageMonitorLayout.TOP_THREE_BELOW)

    /** The arrangement the monitor has always drawn, with every one of its five zones carrying something. */
    @Test
    fun `five zones, the classic arrangement`() = layoutShot("layout_classic", StageMonitorLayout.CLASSIC)

    @Test
    fun `five zones, one over four`() = layoutShot("layout_top_four_below", StageMonitorLayout.TOP_FOUR_BELOW)

    // ── Scripture in each zone in turn ──────────────────────────────────────────────────────────
    // Which position a slot occupies depends on the layout, so what a verse looks like in Zone 4 of
    // the classic grid is not what it looks like in Zone 1 — each is its own piece of typography.

    @Test
    fun `scripture in zone one`() = verseInZone("bible_zone_1", StageMonitorZone.A)

    @Test
    fun `scripture in zone two`() = verseInZone("bible_zone_2", StageMonitorZone.B)

    @Test
    fun `scripture in zone three`() = verseInZone("bible_zone_3", StageMonitorZone.C)

    @Test
    fun `scripture in zone four`() = verseInZone("bible_zone_4", StageMonitorZone.D)

    @Test
    fun `scripture in zone five`() = verseInZone("bible_zone_5", StageMonitorZone.E)

    // ── A song in each zone in turn ─────────────────────────────────────────────────────────────

    @Test
    fun `a song in zone one`() = songInZone("song_zone_1", StageMonitorZone.A)

    @Test
    fun `a song in zone two`() = songInZone("song_zone_2", StageMonitorZone.B)

    @Test
    fun `a song in zone three`() = songInZone("song_zone_3", StageMonitorZone.C)

    @Test
    fun `a song in zone four`() = songInZone("song_zone_4", StageMonitorZone.D)

    @Test
    fun `a song in zone five`() = songInZone("song_zone_5", StageMonitorZone.E)

    // ── Drivers for the three sweeps above ──────────────────────────────────────────────────────

    private fun layoutShot(name: String, layout: StageMonitorLayout) = shoot(
        name,
        settings = stageSettings(zones = filling(layout), layout = layout),
        presenting = Presenting.BIBLE,
        verses = listOf(verse()),
        nextVerses = listOf(verse(number = 17, text = "For God sent not his Son to condemn the world.")),
        announcementActive = true,
        announcementText = "Offering after the second song",
        notes = NOTES,
    )

    /**
     * The verse alone in [zone], with the look-ahead out of the way so the zone is the subject.
     *
     * A passage rather than a single line, on purpose: the comparison worth having across the sweep
     * is how far each zone's auto-fit has to shrink the same words, and a short verse fits
     * everywhere and shows nothing.
     */
    private fun verseInZone(name: String, zone: StageMonitorZone) = shoot(
        name,
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.BIBLE to zone,
                StageMonitorContentType.NEXT to StageMonitorZone.NONE,
            ),
        ),
        presenting = Presenting.BIBLE,
        verses = listOf(verse(text = SWEEP_VERSE)),
    )

    /** The same idea for a song: a full section, so a narrow zone has something to shrink. */
    private fun songInZone(name: String, zone: StageMonitorZone) = shoot(
        name,
        settings = stageSettings(
            zones = mapOf(
                StageMonitorContentType.SONGS to zone,
                StageMonitorContentType.NEXT to StageMonitorZone.NONE,
            ),
        ),
        section = SWEEP_SECTION,
        sections = listOf(SWEEP_SECTION),
    )

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /**
     * Settings with the clock switched off.
     *
     * [zones] and [styles] are merged over the defaults rather than replacing them, so a shot only
     * has to name the zones it is actually about.
     */
    private fun stageSettings(
        zones: Map<StageMonitorContentType, StageMonitorZone> = emptyMap(),
        styles: Map<StageMonitorStyleZone, StageMonitorZoneStyle> = emptyMap(),
        metronomePosition: MetronomePosition = MetronomePosition.NONE,
        layout: StageMonitorLayout = StageMonitorLayout.CLASSIC,
    ) = layoutApplied(
        layout,
        StageMonitorSettings(
            contentZones = StageMonitorSettings.defaultContentZones() +
                mapOf(StageMonitorContentType.CLOCK to StageMonitorZone.NONE) + zones,
            zoneStyles = StageMonitorSettings.defaultZoneStyles() + styles,
            metronomePosition = metronomePosition,
        ),
    )

    /**
     * [settings] on [layout], with anything routed to a zone it does not draw sent to None — the
     * same normalising the settings tab does, so a shot never shows a routing the app would clear.
     */
    private fun layoutApplied(layout: StageMonitorLayout, settings: StageMonitorSettings) =
        settings.withLayout(layout)

    /**
     * One content type per zone the layout draws, in drawing order.
     *
     * Ordered by what is live while scripture is being presented: the verse, its look-ahead, an
     * announcement and the clock. A fifth slot gets presenter notes, which only draw while a
     * presentation is live — see the note above the layout shots.
     */
    private fun filling(layout: StageMonitorLayout): Map<StageMonitorContentType, StageMonitorZone> {
        val inOrder = listOf(
            StageMonitorContentType.BIBLE,
            StageMonitorContentType.NEXT,
            StageMonitorContentType.ANNOUNCEMENT_TEXT,
            StageMonitorContentType.CLOCK,
            StageMonitorContentType.PRESENTATION_NOTES,
        )
        return layout.slots.mapIndexed { index, slot -> inOrder[index] to slot.toZone() }.toMap()
    }

    private fun songSection() = SONG_SECTIONS.first()

    private fun chordSection() = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        // As the parser produces them: words in `lines`, the chart as written in `chordLines`.
        lines = listOf(
            "Amazing grace how sweet the sound",
            "That saved a wretch like me",
        ),
        chordLines = listOf(
            "A[G]mazing grace how [G7]sweet the [C]sound",
            "That [G]saved a wretch like [D]me",
        ),
    )

    private fun verse(
        number: Int = 16,
        text: String = "For God so loved the world, that he gave his only begotten Son.",
    ) = SelectedVerse(
        translationFileName = "kjv.spb",
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = number,
        verseText = text,
    )

    private fun strongs() = StrongsEntry(
        number = "G26",
        word = "ἀγάπη",
        transliteration = "agape",
        pronunciation = "ag-ah'-pay",
        definition = "brotherly love, affection, benevolence",
        kjvUsage = "love, charity",
    )

    /** A real, decodable image for the picture zone. */
    private fun photo(): java.io.File {
        FIXTURES.mkdirs()
        val file = java.io.File(FIXTURES, "backdrop.png")
        val image = java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = java.awt.GradientPaint(
            0f, 0f, java.awt.Color(0x2B3A67), 1280f, 720f, java.awt.Color(0x8FB3F5),
        )
        canvas.fillRect(0, 0, 1280, 720)
        canvas.dispose()
        javax.imageio.ImageIO.write(image, "png", file)
        return file
    }

    private fun scene() = Scene(
        name = "Welcome",
        sources = listOf(
            SceneSource.ColorSource(id = "c1", name = "Backdrop", color = "#1B2A5B"),
            SceneSource.TextSource(
                id = "t1",
                name = "Welcome",
                text = "Welcome",
                transform = SourceTransform(x = 0.2f, y = 0.4f, width = 0.6f, height = 0.2f),
                fontSize = 96,
            ),
        ),
    )

    private fun slideBitmap(): ImageBitmap {
        val bitmap = ImageBitmap(1920, 1080)
        val canvas = Canvas(bitmap)
        fun bar(left: Float, top: Float, width: Float, height: Float, colour: Color) {
            canvas.drawRect(left, top, left + width, top + height, Paint().apply { color = colour })
        }
        bar(0f, 0f, 1920f, 1080f, Color(0xFFFAFAFA))
        bar(0f, 0f, 1920f, 160f, Color(0xFF2B3A67))
        bar(120f, 320f, 1100f, 80f, Color(0xFF20242B))
        listOf(480f, 580f, 680f).forEach { y -> bar(120f, y, 1400f, 40f, Color(0xFFC9CDD4)) }
        return bitmap
    }

    private companion object {
        const val SECTION = "stageMonitor"

        val FIXTURES = java.io.File("build/screenshot-fixtures/stage-monitor")

        const val LONG_PASSAGE =
            "The LORD is my shepherd; I shall not want. He maketh me to lie down in green " +
                "pastures: he leadeth me beside the still waters. He restoreth my soul: he " +
                "leadeth me in the paths of righteousness for his name's sake."

        val LONG_NOTES = List(6) {
            "Point ${'$'}{it + 1}: read the passage slowly, pause before the last line, and let the " +
                "band come back in on the chorus rather than the verse."
        }.joinToString("\n\n")

        /** Long enough that every zone in the sweep has to fit it rather than just place it. */
        const val SWEEP_VERSE =
            "For God so loved the world, that he gave his only begotten Son, that whosoever " +
                "believeth in him should not perish, but have everlasting life."

        val SWEEP_SECTION = LyricSection(
            header = "[Verse 3]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf(
                "Through many dangers, toils and snares",
                "I have already come",
                "'Tis grace hath brought me safe thus far",
                "And grace will lead me home",
            ),
        )

        const val NOTES =
            "Read the passage slowly. Pause before the last line — the band comes back in on the " +
                "chorus, not the verse."

        val SONG_SECTIONS = listOf(
            LyricSection(
                header = "[Verse 1]",
                title = "Amazing Grace",
                songNumber = 42,
                type = Constants.SECTION_TYPE_VERSE,
                lines = listOf("Amazing grace how sweet the sound", "That saved a wretch like me"),
            ),
            LyricSection(
                header = "{Chorus}",
                title = "Amazing Grace",
                songNumber = 42,
                type = Constants.SECTION_TYPE_CHORUS,
                lines = listOf("Praise the Lord, praise the Lord", "Let the earth hear His voice"),
            ),
        )
    }
}
