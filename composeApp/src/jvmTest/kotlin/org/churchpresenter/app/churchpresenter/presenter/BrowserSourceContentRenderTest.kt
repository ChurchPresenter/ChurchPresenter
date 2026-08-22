package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test

/**
 * What a Browser Source output actually draws.
 *
 * This is the content of the `ImageComposeScene` inside [BrowserSourceVideoRenderer.start] — 209
 * lines that no test could reach while they were the lambda of a scene inside a coroutine, because
 * reaching them meant standing up the whole render loop. Extracting them into
 * [BrowserSourceContent] makes them ordinary Compose, and this renders each of its three top-level
 * branches.
 *
 * These matter because a Browser Source is what a stream sees. The branches are mutually exclusive
 * and picked from state that changes live mid-service: hitting "identify" must cover the output with
 * its number, a stage-monitor assignment must draw the monitor rather than the congregation view,
 * and an ordinary assignment must draw the presenter for whatever is live. Getting the wrong one on
 * air is invisible to the operator, whose own screen looks right.
 *
 * The frame pump around this — `scene.render`, the dirty-rect diff, the PNG encode — stays in
 * [BrowserSourceVideoRenderer.start] and is still uncovered; its decisions are already tested
 * separately in [BrowserSourceVideoRendererTest] (`decideTick`, `computeDirtyRect`, `cropPixels`,
 * `encodeFrame`).
 */
@OptIn(ExperimentalTestApi::class)
class BrowserSourceContentRenderTest {

    /**
     * Deliberately not 1920x1080. These assert that text reaches the semantics tree, not how it is
     * laid out, and a full-size Skia surface per test is real work: nine of them measurably loaded
     * the suite and tipped timing-sensitive tests elsewhere over. 960x540 is the same aspect ratio
     * and a quarter of the pixels.
     */
    private val screen = Modifier.size(960.dp, 540.dp)

    private fun verse(text: String) = SelectedVerse(
        translationFileName = "",
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    /**
     * Renders [BrowserSourceContent] the way the renderer does, with the optional collaborators the
     * scene passes as null — a Browser Source with no STT, Q&A url or server url configured is the
     * ordinary case, and the ones that need them have their own presenters tested elsewhere.
     */
    private fun render(
        mode: Presenting,
        assignment: ScreenAssignment = ScreenAssignment(),
        settings: AppSettings = AppSettings(),
        outputIndex: Int = 0,
        seed: PresenterManager.() -> Unit = {},
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val manager = PresenterManager().apply(seed)
        setContent {
            Box(screen) {
                BrowserSourceContent(
                    appSettingsState = mutableStateOf(settings),
                    screenAssignmentState = mutableStateOf(assignment),
                    effectiveModeState = mutableStateOf(mode),
                    presenterManager = manager,
                    mediaViewModel = null,
                    outputIndex = outputIndex,
                    sttManager = null,
                    qaDisplayUrlState = null,
                    serverUrlState = null,
                )
            }
        }
        body()
    }

    @Test
    fun `identify covers the output with its own number`() = render(
        mode = Presenting.NONE,
        outputIndex = 2,
        seed = { identifyBrowserSourceOutput(2) },
    ) {
        // One-based on screen: the operator is matching this against a numbered list, not an index.
        onNodeWithText("Browser Source 3").assertExists()
    }

    @Test
    fun `identify names only the output that was asked for`() = render(
        mode = Presenting.NONE,
        outputIndex = 1,
        seed = { identifyBrowserSourceOutput(0) },
    ) {
        // Identifying output 0 must not light up output 1 — otherwise every Browser Source flashes
        // the same number and the button tells the operator nothing.
        onNodeWithText("Browser Source 2").assertDoesNotExist()
        onNodeWithText("Browser Source 1").assertDoesNotExist()
    }

    @Test
    fun `identify calls a renamed output what the operator calls it`() = render(
        mode = Presenting.NONE,
        assignment = ScreenAssignment(browserSourceName = "Chords"),
        outputIndex = 2,
        seed = { identifyBrowserSourceOutput(2) },
    ) {
        // The operator is matching this against their own list of outputs, which reads "Chords"
        // once they have named it — the number they never see again would identify nothing.
        onNodeWithText("Chords").assertExists()
        onNodeWithText("Browser Source 3").assertDoesNotExist()
    }

    @Test
    fun `a live verse reaches the screen`() = render(
        mode = Presenting.BIBLE,
        seed = { setDisplayedVerses(listOf(verse("For God so loved the world"))) },
    ) {
        onNodeWithText("For God so loved the world", substring = true)
            .assertExists("the verse is the whole point of the output")
    }

    @Test
    fun `a live lyric section reaches the screen`() = render(
        mode = Presenting.LYRICS,
        seed = { setDisplayedLyricSection(LyricSection(type = "verse", lines = listOf("Amazing grace how sweet"))) },
    ) {
        onNodeWithText("Amazing grace how sweet", substring = true).assertExists()
    }

    /**
     * Told apart by the *next* verse, not by the current one.
     *
     * Both branches show what is live — the platform needs the same words the room is reading — so
     * asserting on the current verse cannot distinguish them (my first attempt did, and it failed
     * for that reason). Only the stage monitor looks ahead.
     */
    @Test
    fun `a stage-monitor assignment looks ahead, the congregation view does not`() {
        val seed: PresenterManager.() -> Unit = {
            setDisplayedVerses(listOf(verse("For God so loved the world")))
            setNextVerses(listOf(verse("That whosoever believeth in him")))
        }

        render(mode = Presenting.BIBLE, seed = seed) {
            onNodeWithText("For God so loved the world", substring = true).assertExists()
            // The congregation must never be shown the verse that is coming.
            onNodeWithText("That whosoever believeth in him", substring = true).assertDoesNotExist()
        }

        render(
            mode = Presenting.BIBLE,
            assignment = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
            seed = seed,
        ) {
            onNodeWithText("That whosoever believeth in him", substring = true)
                .assertExists("looking ahead is what the platform needs the monitor for")
        }
    }

    @Test
    fun `nothing live leaves the output blank rather than showing stale content`() = render(
        mode = Presenting.NONE,
        seed = { setDisplayedVerses(listOf(verse("For God so loved the world"))) },
    ) {
        // A Browser Source sits in a live scene; a verse left up after it was cleared is on air.
        onNodeWithText("For God so loved the world", substring = true).assertDoesNotExist()
    }

    // ── The rest of the mode dispatch ───────────────────────────────────────────
    //
    // One test per branch of the `when (mode)` inside [BrowserSourceContent]. These were
    // unreachable before the extraction for the same reason as everything above, and each is a
    // thing that can end up on a live stream. The device-backed modes (MEDIA, PICTURES,
    // PRESENTATION, CANVAS, WEBSITE) are deliberately left out: they need VLC, a decoded image, a
    // rasterised deck or a browser, which is the unreachable part this split was never going to fix.

    @Test
    fun `an announcement reaches the screen`() = render(
        mode = Presenting.ANNOUNCEMENTS,
        seed = { setDisplayedAnnouncementText("Fellowship lunch after the service") },
    ) {
        onNodeWithText("Fellowship lunch after the service", substring = true).assertExists()
    }

    @Test
    fun `a question reaches the screen`() = render(
        mode = Presenting.QA,
        seed = {
            setDisplayedQuestion(
                Question(id = "q1", text = "How do we know the canon is settled?", timestamp = 0L)
            )
        },
    ) {
        onNodeWithText("How do we know the canon is settled?", substring = true).assertExists()
    }

    @Test
    fun `a dictionary entry reaches the screen`() = render(
        mode = Presenting.DICTIONARY,
        seed = {
            setDisplayedDictionaryEntry(
                StrongsEntry(
                    number = "H430",
                    word = "\u02BC\u0115l\u00F4h\u00EEym",
                    transliteration = "elohim",
                    pronunciation = "el-o-heem'",
                    definition = "gods in the ordinary sense; the supreme God",
                    kjvUsage = "God (2346x)",
                )
            )
        },
    ) {
        onNodeWithText("elohim", substring = true).assertExists()
    }
}
