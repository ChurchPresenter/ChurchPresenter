package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a **follower** instance does with the live state its primary sends — the heart of multi-room
 * mirroring.
 *
 * An overflow room, a second campus or a foyer screen runs this on every push from the primary. If a
 * branch picks the wrong renderer or drops its payload, that room shows the wrong thing, or last
 * week's thing, while the main auditorium looks fine — the failure is invisible from where anyone is
 * watching. Nothing exercised any of it until this suite: it lived in `main.kt`, excluded from the
 * coverage gate as app-entry wiring and sitting at 0%.
 *
 * Two rules run through every branch, and both are asserted repeatedly:
 *
 *  * **The mode always switches, even when the payload is unusable.** A follower that cannot resolve
 *    the content still moves off whatever it was showing rather than leaving stale content up.
 *  * **An unusable payload must be a quiet no-op, not a crash.** These arrive over a network from a
 *    primary that may be a different version, so every field is nullable and every branch has to
 *    cope with the nulls.
 *
 * Not covered here: PICTURES and LOWER_THIRD fetch bytes over the link, and the MEDIA stream-url
 * path needs a reachable primary — those need a live socket, and are covered separately in
 * `ApplyRemoteLiveStateRemoteFetchTest` against a real [CompanionServer] (the same approach
 * `InstanceLinkClientTest` uses), so this class doesn't pay for starting one per test. The BIBLE
 * reference-only branch needs a real `Bible`, which `BibleViewModel`'s own suites already build; it
 * is left to them.
 */
class ApplyRemoteLiveStateTest {

    @BeforeTest
    fun setUp() {
        // InstanceLinkLogger resolves its path from user.home once per JVM and every branch below
        // logs, so it is pinned to the test home before anything touches it.
        TestSingletons.latchToTestHome()
    }

    private fun apply(
        state: LiveStateDto,
        presenter: PresenterManager = PresenterManager(),
        scenes: List<Scene> = emptyList(),
        onPlayRemoteMedia: ((String, String) -> Unit)? = null,
    ): PresenterManager {
        runBlocking {
            applyRemoteLiveState(
                state = state,
                presenterManager = presenter,
                instanceLinkViewModel = InstanceLinkViewModel(),
                localScenes = scenes,
                onPlayRemoteMedia = onPlayRemoteMedia,
            )
        }
        return presenter
    }

    // ── An unknown content type ─────────────────────────────────────────────────

    @Test
    fun `an unrecognised content type changes nothing at all`() {
        val presenter = PresenterManager()
        presenter.setPresentingMode(Presenting.LYRICS)

        apply(LiveStateDto(contentType = "SOMETHING_FROM_A_NEWER_VERSION"), presenter)

        // This is the one case that returns *before* switching mode: an unknown type cannot be
        // rendered, so a follower on an older build holds what it has rather than blanking.
        assertEquals(Presenting.LYRICS, presenter.presentingMode.value)
    }

    // ── Lyrics ──────────────────────────────────────────────────────────────────

    @Test
    fun `a song reaches the follower with its section and line`() {
        val presenter = apply(
            LiveStateDto(
                contentType = "LYRICS",
                songTitle = "Amazing Grace",
                songNumber = 42,
                sectionType = "verse",
                lines = listOf("Amazing grace how sweet the sound"),
                songSectionIndex = 2,
                songLineIndex = 1,
            )
        )

        val section = presenter.lyricSection.value
        assertEquals("Amazing Grace", section.title)
        assertEquals(42, section.songNumber)
        assertEquals(listOf("Amazing grace how sweet the sound"), section.lines)
        // The indices are what keep the overflow room on the same line as the auditorium.
        assertEquals(2, presenter.songDisplaySectionIndex.value)
        assertEquals(1, presenter.songDisplayLineIndex.value)
        assertEquals(Presenting.LYRICS, presenter.presentingMode.value)
    }

    @Test
    fun `a song with no title switches mode but shows nothing`() {
        val presenter = apply(LiveStateDto(contentType = "LYRICS"))

        assertEquals(Presenting.LYRICS, presenter.presentingMode.value, "the mode still switches")
        assertEquals("", presenter.lyricSection.value.title, "but there is nothing to put up")
    }

    @Test
    fun `a song with no section or line indices falls back rather than guessing`() {
        val presenter = apply(LiveStateDto(contentType = "LYRICS", songTitle = "Amazing Grace"))

        // -1 means "nothing highlighted", which is the honest reading of a missing index; 0 would
        // silently highlight the first line.
        assertEquals(-1, presenter.songDisplaySectionIndex.value)
        assertEquals(-1, presenter.songDisplayLineIndex.value)
    }

    // ── Announcements ───────────────────────────────────────────────────────────

    @Test
    fun `an announcement's text reaches the follower`() {
        val presenter = apply(LiveStateDto(contentType = "ANNOUNCEMENTS", announcementText = "Service starts in 5"))

        assertEquals("Service starts in 5", presenter.announcementText.value)
        assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
    }

    @Test
    fun `an announcement with no text switches mode without clearing what is there`() {
        val presenter = PresenterManager()
        presenter.setAnnouncementText("kept")

        apply(LiveStateDto(contentType = "ANNOUNCEMENTS"), presenter)

        assertEquals("kept", presenter.announcementText.value, "a null payload must not blank the text")
        assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
    }

    // ── Website ─────────────────────────────────────────────────────────────────

    @Test
    fun `a website's url and title both reach the follower`() {
        val presenter = apply(
            LiveStateDto(contentType = "WEBSITE", websiteUrl = "https://example.org/notices", websiteTitle = "Notices")
        )

        assertEquals("https://example.org/notices", presenter.websiteUrl.value)
        assertEquals("Notices", presenter.webPageTitle.value)
        assertEquals(Presenting.WEBSITE, presenter.presentingMode.value)
    }

    @Test
    fun `a website with no url leaves the previous one alone`() {
        val presenter = PresenterManager()
        presenter.setWebsiteUrl("https://example.org/previous")

        apply(LiveStateDto(contentType = "WEBSITE"), presenter)

        assertEquals("https://example.org/previous", presenter.websiteUrl.value)
    }

    // ── Canvas, which mirrors by id only ────────────────────────────────────────

    @Test
    fun `a scene the follower also has is activated`() {
        val scene = Scene(
            id = "scene-1", name = "Opening", canvasWidth = 1920, canvasHeight = 1080,
            sources = listOf(
                SceneSource.ShapeSource(
                    id = "s", name = "s", transform = SourceTransform(),
                    shapeType = "rectangle", strokeColor = "#FFF", fillColor = "#000", strokeWidth = 1f,
                )
            ),
        )

        val presenter = apply(
            LiveStateDto(contentType = "CANVAS", sceneId = "scene-1", sceneName = "Opening"),
            scenes = listOf(scene),
        )

        assertEquals("scene-1", presenter.activeScene.value?.id)
        assertEquals(Presenting.CANVAS, presenter.presentingMode.value)
    }

    @Test
    fun `a scene the follower does not have switches mode but activates nothing`() {
        val presenter = apply(
            LiveStateDto(contentType = "CANVAS", sceneId = "missing", sceneName = "Opening"),
            scenes = emptyList(),
        )

        // Canvas mirrors by id — scene content is not fetchable over the link — so a follower whose
        // scenes.json differs simply has nothing to show. It must not activate the wrong scene.
        assertNull(presenter.activeScene.value)
        assertEquals(Presenting.CANVAS, presenter.presentingMode.value)
    }

    @Test
    fun `a scene is matched by id, not by name`() {
        val other = Scene(id = "other", name = "Opening", canvasWidth = 1920, canvasHeight = 1080)

        val presenter = apply(
            LiveStateDto(contentType = "CANVAS", sceneId = "scene-1", sceneName = "Opening"),
            scenes = listOf(other),
        )

        // Two scenes can share a name; matching on it would put the wrong one on the wall.
        assertNull(presenter.activeScene.value)
    }

    // ── Q&A ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a displayed question reaches the follower`() {
        val presenter = apply(
            LiveStateDto(contentType = "QA", questionId = "q1", questionText = "How do I join a group?")
        )

        assertEquals("q1", presenter.displayedQuestion.value?.id)
        assertEquals("How do I join a group?", presenter.displayedQuestion.value?.text)
        assertEquals(Presenting.QA, presenter.presentingMode.value)
    }

    @Test
    fun `a question missing either half is not shown`() {
        // Both id and text are required — half a question on an overflow screen is worse than none.
        assertNull(apply(LiveStateDto(contentType = "QA", questionId = "q1")).displayedQuestion.value)
        assertNull(apply(LiveStateDto(contentType = "QA", questionText = "text")).displayedQuestion.value)
    }

    // ── Dictionary ──────────────────────────────────────────────────────────────

    @Test
    fun `a full dictionary entry is shown as sent`() {
        val entry = org.churchpresenter.app.churchpresenter.data.StrongsEntry(
            number = "G5485", word = "χάρις", transliteration = "charis",
            pronunciation = "khar'-ece", definition = "grace",
        )

        val presenter = apply(LiveStateDto(contentType = "DICTIONARY", dictionaryEntry = entry))

        assertEquals("G5485", presenter.displayedDictionaryEntry.value?.number)
        assertEquals("χάρις", presenter.displayedDictionaryEntry.value?.word)
        assertEquals(Presenting.DICTIONARY, presenter.presentingMode.value)
    }

    @Test
    fun `a primary that sends only the word still shows the word`() {
        val presenter = apply(LiveStateDto(contentType = "DICTIONARY", dictionaryWord = "χάρις"))

        // An older primary carries no full entry; showing the bare word beats showing nothing.
        assertEquals("χάρις", presenter.displayedDictionaryEntry.value?.word)
        assertEquals("", presenter.displayedDictionaryEntry.value?.definition)
    }

    @Test
    fun `a dictionary push with neither entry nor word shows nothing`() {
        val presenter = apply(LiveStateDto(contentType = "DICTIONARY"))

        assertNull(presenter.displayedDictionaryEntry.value)
        assertEquals(Presenting.DICTIONARY, presenter.presentingMode.value)
    }

    // ── Modes with no feed to mirror ────────────────────────────────────────────

    @Test
    fun `a mode with no feed still switches the follower to it`() {
        // Presentation mirrors through its own broadcast and STT has no caption feed to mirror, so
        // this branch only switches mode — but it must still do that, or the follower would keep
        // showing the previous content while the primary has moved on.
        listOf("PRESENTATION", "STT").forEach { type ->
            val presenter = apply(LiveStateDto(contentType = type))
            assertEquals(
                Presenting.valueOf(type),
                presenter.presentingMode.value,
                "$type must at least switch the mode",
            )
        }
    }

    @Test
    fun `media with a url is handed to the local player`() {
        val played = mutableListOf<Pair<String, String>>()

        val presenter = apply(
            LiveStateDto(
                contentType = "MEDIA",
                mediaType = org.churchpresenter.app.churchpresenter.utils.Constants.MEDIA_TYPE_URL,
                mediaUrl = "https://example.org/clip.mp4",
            ),
            onPlayRemoteMedia = { url, type -> played += url to type },
        )

        // The MediaViewModel stays owned by its composable, so playback is handed back through a
        // callback rather than driven from here.
        assertTrue(played.isNotEmpty(), "a url must reach the local player")
        assertEquals("https://example.org/clip.mp4", played.first().first)
        assertEquals(Presenting.MEDIA, presenter.presentingMode.value)
    }

    @Test
    fun `every branch leaves the follower in the mode it was sent`() {
        // The single invariant that matters most across the whole function: whatever happens to the
        // payload, the follower ends up on the renderer the primary asked for.
        val types = listOf("LYRICS", "ANNOUNCEMENTS", "WEBSITE", "CANVAS", "QA", "DICTIONARY", "MEDIA")

        types.forEach { type ->
            val presenter = apply(LiveStateDto(contentType = type))
            assertEquals(
                Presenting.valueOf(type),
                presenter.presentingMode.value,
                "$type must switch the mode even with an empty payload",
            )
        }
    }

    @Test
    fun `a bible verse sent as wording is shown as sent`() {
        val presenter = apply(
            LiveStateDto(
                contentType = "BIBLE",
                bookName = "John",
                chapter = 3,
                verseNumber = 16,
                verseText = "For God so loved the world.",
                verseRange = "16-17",
            )
        )

        val verse = presenter.selectedVerses.value.single()
        assertEquals("John", verse.bookName)
        assertEquals(3, verse.chapter)
        assertEquals(16, verse.verseNumber)
        assertEquals("For God so loved the world.", verse.verseText)
        assertEquals("16-17", verse.verseRange)
        assertEquals(Presenting.BIBLE, presenter.presentingMode.value)
    }

    @Test
    fun `a bible verse with only a book name is shown with zeroed numbers rather than dropped`() {
        val presenter = apply(LiveStateDto(contentType = "BIBLE", bookName = "Psalms"))

        val verse = presenter.selectedVerses.value.single()
        assertEquals("Psalms", verse.bookName)
        assertEquals(0, verse.chapter)
        assertEquals(0, verse.verseNumber)
        assertEquals("", verse.verseText)
        assertEquals("", verse.verseRange)
    }

    @Test
    fun `a bible push with no book name shows nothing`() {
        val presenter = apply(LiveStateDto(contentType = "BIBLE"))

        assertTrue(presenter.selectedVerses.value.isEmpty())
        assertEquals(Presenting.BIBLE, presenter.presentingMode.value)
    }

    @Test
    fun `media with a url but no local player is not played`() {
        val presenter = apply(
            LiveStateDto(
                contentType = "MEDIA",
                mediaType = org.churchpresenter.app.churchpresenter.utils.Constants.MEDIA_TYPE_URL,
                mediaUrl = "https://example.org/clip.mp4",
            ),
            onPlayRemoteMedia = null,
        )

        assertEquals(Presenting.MEDIA, presenter.presentingMode.value)
        assertEquals("", presenter.currentMediaUrl.value)
    }

    @Test
    fun `media typed as a url but carrying none is not played`() {
        val played = mutableListOf<Pair<String, String>>()

        apply(
            LiveStateDto(
                contentType = "MEDIA",
                mediaType = org.churchpresenter.app.churchpresenter.utils.Constants.MEDIA_TYPE_URL,
            ),
            onPlayRemoteMedia = { url, type -> played += url to type },
        )

        assertTrue(played.isEmpty())
    }

    @Test
    fun `media with neither a url nor a stream id is not played`() {
        val played = mutableListOf<Pair<String, String>>()

        val presenter = apply(
            LiveStateDto(contentType = "MEDIA"),
            onPlayRemoteMedia = { url, type -> played += url to type },
        )

        assertTrue(played.isEmpty())
        assertEquals(Presenting.MEDIA, presenter.presentingMode.value)
    }
}
