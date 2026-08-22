@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the platform sees on the stage monitor.
 *
 * The screen is a router: each content type is assigned a zone in settings, and what a zone draws
 * depends on which types are *active* for the current presenting mode. The rules worth pinning are
 * the routing ones — a zone shows its assigned live content, falls back to the clock when it has one
 * assigned and nothing live, and shows nothing otherwise; a type routed to FULL_SCREEN takes the
 * whole monitor and the quadrants are not drawn at all; and a type routed to NONE disappears.
 *
 * Everything is driven through the composable's own parameters, so no view model or output window is
 * involved. Announcements are deliberately additive rather than exclusive — an announcement on the
 * stage monitor must not blank out the verse the reader is following — which is asserted directly.
 *
 * Not covered here: actual video playback for MEDIA (needs a loaded VLC player — the routing and the
 * view-model presence/loaded/audio guards ahead of it are covered instead), LOWER_THIRD/WEB/STT
 * (the screen has no live data plumbed through for yet and draws them as empty — that emptiness is
 * covered), and the picture-decode failure path (loadImageBitmapFromPath's catch block): there is no
 * observable state change between "still loading" and "failed and stayed null", so a test can't wait
 * on a positive signal for it without racing the background decode.
 */
class StageMonitorScreenTest {

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun verse(
        book: String = "John",
        chapter: Int = 3,
        number: Int = 16,
        range: String = "",
        text: String = "For God so loved the world",
    ) = SelectedVerse(
        bookName = book,
        chapter = chapter,
        verseNumber = number,
        verseRange = range,
        verseText = text,
    )

    private fun section(vararg lines: String, bpm: Int = 0) =
        LyricSection(title = "Verse 1", lines = lines.toList(), bpm = bpm)

    /** Settings routing exactly [routes], with every other content type sent to NONE. */
    private fun routing(vararg routes: Pair<StageMonitorContentType, StageMonitorZone>) =
        StageMonitorSettings(
            contentZones = StageMonitorContentType.entries.associateWith { StageMonitorZone.NONE } +
                routes.toMap(),
        )

    private fun screen(
        sm: StageMonitorSettings,
        presentingMode: Presenting = Presenting.NONE,
        announcementActive: Boolean = presentingMode == Presenting.ANNOUNCEMENTS,
        currentLyricSection: LyricSection = section(),
        allLyricSections: List<LyricSection> = emptyList(),
        songDisplaySectionIndex: Int = 0,
        displayedVerses: List<SelectedVerse> = emptyList(),
        nextVerses: List<SelectedVerse> = emptyList(),
        announcementText: String = "",
        displayedImagePath: String? = null,
        displayedSlide: ImageBitmap? = null,
        presenterNotes: String = "",
        activeScene: Scene? = null,
        displayedQuestion: Question? = null,
        qaSettings: QASettings = QASettings(),
        displayedDictionaryEntry: StrongsEntry? = null,
        dictionarySettings: DictionarySettings = DictionarySettings(),
        mediaViewModel: MediaViewModel? = null,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                    Box(modifier = Modifier.size(800.dp, 600.dp)) {
                        StageMonitorScreen(
                            sm = sm,
                            presentingMode = presentingMode,
                            announcementActive = announcementActive,
                            currentLyricSection = currentLyricSection,
                            allLyricSections = allLyricSections,
                            songDisplaySectionIndex = songDisplaySectionIndex,
                            displayedVerses = displayedVerses,
                            nextVerses = nextVerses,
                            announcementText = announcementText,
                            displayedImagePath = displayedImagePath,
                            displayedSlide = displayedSlide,
                            presenterNotes = presenterNotes,
                            activeScene = activeScene,
                            displayedQuestion = displayedQuestion,
                            qaSettings = qaSettings,
                            displayedDictionaryEntry = displayedDictionaryEntry,
                            dictionarySettings = dictionarySettings,
                        )
                    }
                }
            }
        }
        block()
    }

    // ── Bible ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a live verse is shown with its reference above the text`() {
        screen(
            sm = routing(StageMonitorContentType.BIBLE to StageMonitorZone.A),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse()),
        ) {
            assertTrue(rendersText("John 3:16\nFor God so loved the world"), renderedText().toString())
        }
    }

    @Test
    fun `a verse range is shown as the range, not as the first verse number`() {
        screen(
            sm = routing(StageMonitorContentType.BIBLE to StageMonitorZone.A),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse(number = 16, range = "16-18")),
        ) {
            assertTrue(rendersContaining("John 3:16-18"), renderedText().toString())
        }
    }

    @Test
    fun `the next verse is a lookahead, not the current one repeated`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
            ),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse(number = 16, text = "current verse")),
            nextVerses = listOf(verse(number = 17, text = "the verse after")),
        ) {
            assertTrue(rendersContaining("current verse"), renderedText().toString())
            assertTrue(rendersContaining("the verse after"))
        }
    }

    @Test
    fun `with no verse selected the bible zone draws nothing`() {
        screen(
            sm = routing(StageMonitorContentType.BIBLE to StageMonitorZone.A),
            presentingMode = Presenting.BIBLE,
            displayedVerses = emptyList(),
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    @Test
    fun `with no next verse the lookahead zone stays empty`() {
        screen(
            sm = routing(StageMonitorContentType.NEXT to StageMonitorZone.B),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse()),
            nextVerses = emptyList(),
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Songs ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the live section's lines are shown together`() {
        screen(
            sm = routing(StageMonitorContentType.SONGS to StageMonitorZone.A),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("first line", "second line"),
        ) {
            assertTrue(rendersText("first line\nsecond line"), renderedText().toString())
        }
    }

    @Test
    fun `the next zone shows the section after the one being sung`() {
        screen(
            sm = routing(
                StageMonitorContentType.SONGS to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
            ),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("now singing"),
            allLyricSections = listOf(section("now singing"), section("up next")),
            songDisplaySectionIndex = 0,
        ) {
            assertTrue(rendersText("now singing"), renderedText().toString())
            assertTrue(rendersText("up next"))
        }
    }

    @Test
    fun `on the last section the next zone is empty rather than wrapping around`() {
        screen(
            sm = routing(StageMonitorContentType.NEXT to StageMonitorZone.B),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("the last one"),
            allLyricSections = listOf(section("the first one"), section("the last one")),
            songDisplaySectionIndex = 1,
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Routing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a zone shows nothing when the type routed to it is not live`() {
        screen(
            sm = routing(StageMonitorContentType.BIBLE to StageMonitorZone.A),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("a lyric nobody routed"),
            displayedVerses = listOf(verse()),
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    @Test
    fun `a type routed to NONE is not drawn anywhere`() {
        screen(
            sm = routing(StageMonitorContentType.BIBLE to StageMonitorZone.NONE),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse(text = "unrouted verse")),
        ) {
            assertFalse(rendersContaining("unrouted verse"), renderedText().toString())
        }
    }

    @Test
    fun `a full-screen type takes over and the quadrants are not drawn`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.FULL_SCREEN,
                // Assigned a quadrant, and live — but full screen wins outright.
                StageMonitorContentType.NEXT to StageMonitorZone.B,
            ),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse(text = "the whole screen")),
            nextVerses = listOf(verse(text = "a quadrant that loses")),
        ) {
            assertTrue(rendersContaining("the whole screen"), renderedText().toString())
            assertFalse(rendersContaining("a quadrant that loses"))
        }
    }

    @Test
    fun `two types in one zone resolve to the live one`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.A,
                StageMonitorContentType.SONGS to StageMonitorZone.A,
            ),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("the live one"),
            displayedVerses = listOf(verse(text = "the idle one")),
        ) {
            assertTrue(rendersText("the live one"), renderedText().toString())
            assertFalse(rendersContaining("the idle one"))
        }
    }

    // ── Clock ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a clock zone shows a clock`() {
        screen(sm = routing(StageMonitorContentType.CLOCK to StageMonitorZone.C)) {
            assertTrue(
                renderedText().any { CLOCK_SHAPE.containsMatchIn(it) },
                "expected a clock, got ${renderedText()}",
            )
        }
    }

    @Test
    fun `a zone sharing a live type with the clock falls back to the clock when idle`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.C,
                StageMonitorContentType.CLOCK to StageMonitorZone.C,
            ),
            presentingMode = Presenting.NONE,
        ) {
            assertTrue(renderedText().any { CLOCK_SHAPE.containsMatchIn(it) }, renderedText().toString())
        }
    }

    @Test
    fun `the live type wins over the clock in a shared zone`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.C,
                StageMonitorContentType.CLOCK to StageMonitorZone.C,
            ),
            presentingMode = Presenting.BIBLE,
            displayedVerses = listOf(verse(text = "scripture beats the clock")),
        ) {
            assertTrue(rendersContaining("scripture beats the clock"), renderedText().toString())
            assertFalse(renderedText().any { CLOCK_SHAPE.containsMatchIn(it) })
        }
    }

    // ── Announcements and timers ────────────────────────────────────────────────────────────────

    @Test
    fun `an announcement is shown alongside the verse, not instead of it`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.A,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.D,
            ),
            presentingMode = Presenting.BIBLE,
            announcementActive = true,
            displayedVerses = listOf(verse(text = "still following along")),
            announcementText = "05:00",
        ) {
            // The whole point of announcementActive being independent of presentingMode.
            assertTrue(rendersContaining("still following along"), renderedText().toString())
            assertTrue(rendersText("05:00"))
        }
    }

    @Test
    fun `a timer zone with nothing to show holds a placeholder`() {
        screen(
            sm = routing(StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.D),
            presentingMode = Presenting.ANNOUNCEMENTS,
            announcementText = "",
        ) {
            assertTrue(rendersText("--:--"), renderedText().toString())
        }
    }

    @Test
    fun `announcements mode alone does not light up the bible zone`() {
        screen(
            sm = routing(StageMonitorContentType.BIBLE to StageMonitorZone.A),
            presentingMode = Presenting.ANNOUNCEMENTS,
            displayedVerses = listOf(verse(text = "not live any more")),
            announcementText = "10:00",
        ) {
            assertFalse(rendersContaining("not live any more"), renderedText().toString())
        }
    }

    // ── Presenter notes ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `presenter notes are shown while a deck is live`() {
        screen(
            sm = routing(StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.E),
            presentingMode = Presenting.PRESENTATION,
            presenterNotes = "remember to mention the offering",
        ) {
            assertTrue(rendersText("remember to mention the offering"), renderedText().toString())
        }
    }

    @Test
    fun `presenter notes are not shown when a deck is not live`() {
        screen(
            sm = routing(StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.E),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("a lyric"),
            presenterNotes = "notes for a deck nobody is showing",
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Slides ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a live slide is drawn`() {
        screen(
            sm = routing(StageMonitorContentType.PRESENTATION to StageMonitorZone.A),
            presentingMode = Presenting.PRESENTATION,
            displayedSlide = ImageBitmap(16, 16),
        ) {
            assertEquals(1, imageCount(), "the slide bitmap should be drawn")
        }
    }

    @Test
    fun `a presentation zone with no slide yet draws no image`() {
        screen(
            sm = routing(StageMonitorContentType.PRESENTATION to StageMonitorZone.A),
            presentingMode = Presenting.PRESENTATION,
            displayedSlide = null,
        ) {
            assertEquals(0, imageCount())
        }
    }

    // ── Pictures ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a picture is decoded from disk and drawn`() {
        val file = File.createTempFile("stage-monitor-picture", ".png")
        try {
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", file)
            screen(
                sm = routing(StageMonitorContentType.PICTURES to StageMonitorZone.A),
                presentingMode = Presenting.PICTURES,
                displayedImagePath = file.absolutePath,
            ) {
                waitUntil("the picture is decoded and drawn") { imageCount() == 1 }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a pictures zone with nothing loaded yet draws no image`() {
        screen(
            sm = routing(StageMonitorContentType.PICTURES to StageMonitorZone.A),
            presentingMode = Presenting.PICTURES,
            displayedImagePath = null,
        ) {
            assertEquals(0, imageCount())
        }
    }

    // ── Canvas ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a live scene is drawn`() {
        val scene = Scene(
            sources = listOf(
                SceneSource.TextSource(id = "t1", name = "Text 1", text = "Welcome Home"),
            ),
        )
        screen(
            sm = routing(StageMonitorContentType.CANVAS to StageMonitorZone.A),
            presentingMode = Presenting.CANVAS,
            activeScene = scene,
        ) {
            assertTrue(rendersContaining("Welcome Home"), renderedText().toString())
        }
    }

    @Test
    fun `a canvas zone with no active scene draws nothing`() {
        screen(
            sm = routing(StageMonitorContentType.CANVAS to StageMonitorZone.A),
            presentingMode = Presenting.CANVAS,
            activeScene = null,
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Q&A ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a live question is shown`() {
        screen(
            sm = routing(StageMonitorContentType.QA to StageMonitorZone.A),
            presentingMode = Presenting.QA,
            displayedQuestion = Question(id = "q1", text = "What time is the potluck?", timestamp = 0L),
            qaSettings = QASettings(),
        ) {
            assertTrue(rendersContaining("What time is the potluck?"), renderedText().toString())
        }
    }

    @Test
    fun `a qa zone with no live question draws nothing`() {
        screen(
            sm = routing(StageMonitorContentType.QA to StageMonitorZone.A),
            presentingMode = Presenting.QA,
            displayedQuestion = null,
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Dictionary ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a live dictionary entry is shown`() {
        screen(
            sm = routing(StageMonitorContentType.DICTIONARY to StageMonitorZone.A),
            presentingMode = Presenting.DICTIONARY,
            displayedDictionaryEntry = StrongsEntry(
                number = "H430",
                word = "אֱלֹהִים",
                transliteration = "elohim",
                pronunciation = "el-o-heem'",
                definition = "gods in the ordinary sense",
            ),
            dictionarySettings = DictionarySettings(),
        ) {
            assertTrue(rendersContaining("elohim"), renderedText().toString())
        }
    }

    @Test
    fun `a dictionary zone with no live entry draws nothing`() {
        screen(
            sm = routing(StageMonitorContentType.DICTIONARY to StageMonitorZone.A),
            presentingMode = Presenting.DICTIONARY,
            displayedDictionaryEntry = null,
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Not-yet-plumbed content types ───────────────────────────────────────────────────────────

    @Test
    fun `lower third, website and STT zones exist but draw nothing yet`() {
        for ((presentingMode, contentType) in listOf(
            Presenting.LOWER_THIRD to StageMonitorContentType.LOWER_THIRD,
            Presenting.WEBSITE to StageMonitorContentType.WEB,
            Presenting.STT to StageMonitorContentType.STT,
        )) {
            screen(
                sm = routing(contentType to StageMonitorZone.A),
                presentingMode = presentingMode,
            ) {
                assertEquals(emptySet(), renderedText(), "presentingMode=$presentingMode")
            }
        }
    }

    // ── Media ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a media zone with no view model draws nothing`() {
        screen(
            sm = routing(StageMonitorContentType.MEDIA to StageMonitorZone.A),
            presentingMode = Presenting.MEDIA,
            mediaViewModel = null,
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    @Test
    fun `a media zone with an audio file selected draws nothing`() {
        val viewModel = MediaViewModel().apply { loadMedia("file:///tmp/song.mp3", Constants.MEDIA_TYPE_LOCAL) }
        screen(
            sm = routing(StageMonitorContentType.MEDIA to StageMonitorZone.A),
            presentingMode = Presenting.MEDIA,
            mediaViewModel = viewModel,
        ) {
            assertEquals(emptySet(), renderedText())
        }
    }

    // ── Styling and layout ──────────────────────────────────────────────────────────────────────

    @Test
    fun `every zone can draw at once`() {
        screen(
            sm = routing(
                StageMonitorContentType.BIBLE to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
                StageMonitorContentType.CLOCK to StageMonitorZone.C,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.D,
                StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.E,
            ),
            presentingMode = Presenting.BIBLE,
            announcementActive = true,
            displayedVerses = listOf(verse(text = "top left")),
            nextVerses = listOf(verse(text = "top right")),
            announcementText = "bottom middle",
        ) {
            assertTrue(rendersContaining("top left"), renderedText().toString())
            assertTrue(rendersContaining("top right"))
            assertTrue(rendersText("bottom middle"))
            assertTrue(renderedText().any { CLOCK_SHAPE.containsMatchIn(it) })
        }
    }

    @Test
    fun `an unusual alignment and colour still render the text`() {
        val style = StageMonitorZoneStyle(
            horizontalAlignment = "right",
            verticalAlignment = "bottom",
            bgColor = "#102030",
            color = "#FFEEDD",
            bold = true,
            italic = true,
            underline = true,
        )
        screen(
            sm = routing(StageMonitorContentType.SONGS to StageMonitorZone.A).copy(
                zoneStyles = mapOf(StageMonitorStyleZone.A to style),
            ),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("styled lyric"),
        ) {
            assertTrue(rendersText("styled lyric"), renderedText().toString())
        }
    }

    @Test
    fun `a full-screen zone with its own style still renders`() {
        screen(
            sm = routing(StageMonitorContentType.CLOCK to StageMonitorZone.FULL_SCREEN).copy(
                zoneStyles = mapOf(
                    StageMonitorStyleZone.FULL_SCREEN to StageMonitorZoneStyle(
                        horizontalAlignment = "center",
                        verticalAlignment = "middle",
                    ),
                ),
            ),
        ) {
            assertTrue(renderedText().any { CLOCK_SHAPE.containsMatchIn(it) }, renderedText().toString())
        }
    }

    // ── Metronome ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the metronome only appears for a song with a tempo`() {
        val sm = routing(StageMonitorContentType.SONGS to StageMonitorZone.A)
            .copy(metronomePosition = MetronomePosition.TOP_CENTER)

        screen(sm = sm, presentingMode = Presenting.LYRICS, currentLyricSection = section("a", bpm = 90)) {
            assertEquals(1, metronomeCount(), "a song with a tempo gets a dot")
        }
        screen(sm = sm, presentingMode = Presenting.LYRICS, currentLyricSection = section("a", bpm = 0)) {
            assertEquals(0, metronomeCount(), "no tempo, no dot")
        }
        screen(
            sm = sm,
            presentingMode = Presenting.BIBLE,
            currentLyricSection = section("a", bpm = 90),
            displayedVerses = listOf(verse()),
        ) {
            assertEquals(0, metronomeCount(), "the dot is for songs, not scripture")
        }
    }

    @Test
    fun `no metronome position means no dot at all`() {
        screen(
            sm = routing(StageMonitorContentType.SONGS to StageMonitorZone.A)
                .copy(metronomePosition = MetronomePosition.NONE),
            presentingMode = Presenting.LYRICS,
            currentLyricSection = section("a", bpm = 120),
        ) {
            assertEquals(0, metronomeCount())
        }
    }

    private companion object {
        /**
         * A wall clock, however it is formatted: `HH:mm:ss` on a 24-hour system, `hh:mm:ss a`
         * otherwise. The seconds are what make this safe to search for — a bare `h:mm` would also
         * match a verse reference like "John 3:16".
         */
        val CLOCK_SHAPE = Regex("""\d{1,2}:\d{2}:\d{2}""")
    }
}

// ── Reading what was drawn ──────────────────────────────────────────────────────────────────────

private fun ComposeUiTest.renderedText(): Set<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("\n") { it.text }
        }
        .filter { it.isNotBlank() }
        .toSet()

private fun ComposeUiTest.rendersText(text: String): Boolean = renderedText().contains(text)

private fun ComposeUiTest.rendersContaining(fragment: String): Boolean =
    renderedText().any { it.contains(fragment) }

/**
 * How many nodes carry [tag].
 *
 * The slide image and the metronome dot are the two things here with no text and no content
 * description — the slide is decorative to a screen reader and the dot is a bare `Box` — so both
 * carry a test tag, which is the only handle a test has on "it is on the monitor".
 */
private fun ComposeUiTest.taggedCount(tag: String): Int =
    onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).size

private fun ComposeUiTest.imageCount(): Int = taggedCount("stage_slide")

private fun ComposeUiTest.metronomeCount(): Int = taggedCount("stage_metronome")
