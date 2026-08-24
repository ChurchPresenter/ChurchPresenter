@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.MainDesktop
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.settings.WindowLayoutSettings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.qa.QuestionDto
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.tabs.RecentMediaFiles
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.qa.QAManager
import org.churchpresenter.stt.STTManager
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import org.churchpresenter.ui.screenshot.THEMES
import org.churchpresenter.ui.screenshot.captureTo

private const val ROOT = "$SCREENSHOT_ROOT/previewApp"
/**
 * Where the fixture's media library lives — and, because the schedule row prints the path of every
 * file-backed item at detailed density, **a string that appears in the screenshots themselves**.
 *
 * So it has to be three things at once: real (the path shown is the path used), writable without a
 * permission prompt, and free of the developer's own username. `/Users/Shared` is the macOS
 * directory that satisfies all three — it exists on every install, is world-writable, and names
 * nobody. Elsewhere, `/tmp` is the equivalent and is what CI uses.
 *
 * The two roots produce different text on screen, which is deliberate and harmless: CI only ever
 * compares its own renders against its own, and images are not committed, so a local recording never
 * enters the comparison.
 *
 * **The output snapshots resolve their files through this same value**, so they present the file the
 * tab preview actually put live. A second root spelled out in that test picks the *other* candidate
 * on any machine where both exist, and then quietly renders whatever a previous fixture left behind
 * there.
 */
internal val LIBRARY = listOf(File("/Users/Shared/ChurchPresenter"), File("/tmp/ChurchPresenter"))
    .firstOrNull { it.parentFile?.isDirectory == true && (it.isDirectory || it.mkdirs()) }
    ?: File("build/app-preview-library").absoluteFile

// The real King James the app ships as a sample: 66 books with every chapter and verse, so the
// chapter grid and the verse pane are the real thing rather than a fixture's handful.
// A real, decodable clip, so the media preview shows video rather than an empty surface.
private val WELCOME_LOOP_SOURCE = File("src/jvmTest/resources/app-preview/welcome-loop.mp4")

// One of the ~290 stock backgrounds the app ships with, so the scene is built from what a user
// actually has on hand. Its lower half is near-black, which is why the text needs no scrim.
private val SUNRISE_SOURCE =
    File("../resources/src/main/composeResources/files/backgrounds/cross_10037772.jpg")

private val LOWER_THIRD_SOURCE = File("src/jvmTest/resources/app-preview/lower-thirds")

private val KJV_SOURCE = File("../resources/src/main/composeResources/files/bible_samples/kjv1769.spb")

/**
 * The pixel density every app-preview render is captured at. `1f` unless overridden.
 *
 * The preview shots are the ones used for the website, where they want to be crisp on a retina
 * display — but this same harness is the CI visual-regression baseline that runs on **every** pull
 * request, and doubling the density quadruples the pixel work. `the announcements tab` already costs
 * ~3.9s against AGENT.md's ~1s bar, so making 2x the default would tax every push for a benefit only
 * a marketing export ever collects.
 *
 * So it is a knob rather than a new default. Recording high-DPI captures:
 *
 * ```
 * ./gradlew :composeApp:recordRoborazziJvm --tests '*AppPreview*' -PpreviewDensity=2
 * ```
 *
 * **The canvas has to grow with it.** `runSkikoComposeUiTest`'s `size` is in *pixels*, not dp, so
 * raising density alone does not render the same layout more finely — it scales the UI up inside an
 * unchanged canvas and crops it. Done that way the fourth thumbnail simply falls off the window and
 * the test fails looking for it. Multiplying the size by the density keeps the layout identical in
 * dp and renders it at more pixels, which is what "retina" means here.
 *
 * At 2 the whole-app shots come out 3200x2000 and the output shots 3840x2160. **Do not commit a
 * baseline recorded this way** — every image would differ from CI's and the next comparison would
 * report the lot as changed.
 */
internal val PREVIEW_DENSITY: Float =
    System.getProperty("churchpresenter.previewDensity")?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f

internal val WINDOW = Size(1600f, 1000f)

internal fun appPreview(
    name: String,
    tab: Tabs,
    seedSchedule: Boolean = true,
    settings: (AppSettings) -> AppSettings = { it },
    drive: ComposeUiTest.() -> Unit = {},
) {
    TestSingletons.latchSkikoHostOs()
    TestSingletons.latchToTestHome()
    val appSettings = settings(library())
    RecentPresentationFiles.files.clear()
    RecentPresentationFiles.pinned.clear()
    RecentPresentationFiles.files.addAll(
        listOf(File(LIBRARY, "Decks/Sermon.pdf").absolutePath, File(LIBRARY, "Decks/Advent Series.pptx").absolutePath),
    )
    RecentMediaFiles.paths.clear()
    RecentMediaFiles.pinned.clear()
    RecentMediaFiles.paths.addAll(
        listOf(
            File(LIBRARY, "Media/Welcome Loop.mp4").absolutePath,
            File(LIBRARY, "Media/Baptism Testimony.mp4").absolutePath,
        ),
    )
    writeScenes()
    writeQuestions()
    THEMES.forEach { (suffix, mode) ->
        runSkikoComposeUiTest(size = WINDOW * PREVIEW_DENSITY, density = Density(PREVIEW_DENSITY)) {
            var actions = ScheduleActions()
            val presenterManager = PresenterManager()
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    // main.kt provides this around the whole window; MediaTab returns early
                    // without it and the tab draws as an empty pane.
                    CompositionLocalProvider(LocalMediaViewModel provides MediaViewModel()) {
                    MainDesktop(
                        appSettings = appSettings,
                        presenterManager = presenterManager,
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        qaManager = QAManager(),
                        // MainDesktop renders the STT tab only when this is non-null — with the
                        // default null the tab is selectable and draws nothing at all. Constructing
                        // one is inert: STTManager opens no socket until it is told to connect.
                        sttManager = STTManager(),
                        // The app treats a non-empty server URL as "the companion server is up" —
                        // it is what clears the Q&A tab's "server not running" banner and gives the
                        // remote panels something to show.
                        serverUrl = "http://192.168.1.42:8080",
                        onScheduleActionsReady = { actions = it },
                        // What main.kt does: without it nothing ever goes live and the preview
                        // panel stays on the background.
                        presenting = { presenterManager.setPresentingMode(it) },
                        onVerseSelected = { presenterManager.setSelectedVerses(it) },
                        onSongItemSelected = {
                            presenterManager.setLyricSection(it)
                            presenterManager.setDisplayedLyricSection(it)
                        },
                        onAllSectionsChanged = { presenterManager.setAllLyricSections(it) },
                        onSectionIndexChanged = { presenterManager.setSongDisplaySectionIndex(it) },
                        onLineIndexChanged = { presenterManager.setSongDisplayLineIndex(it) },
                        theme = mode,
                    )
                    }
                }
            }
            waitForIdle()
            if (seedSchedule) {
                serviceSchedule(actions)
                waitForIdle()
            }
            selectTab(tab)
            drive()
            // main.kt owns the transition effects that move selected -> displayed and raise the
            // fade alphas; MainDesktop does not, so nothing would ever reach the live preview.
            presenterManager.setDisplayedVerses(presenterManager.selectedVerses.value)
            presenterManager.setDisplayedImagePath(presenterManager.selectedImagePath.value)
            presenterManager.setPictureTransitionAlpha(1f)
            presenterManager.setDisplayedSlide(presenterManager.selectedSlide.value)
            presenterManager.setSlideTransitionAlpha(1f)
            presenterManager.setDisplayedAnnouncementText(presenterManager.announcementText.value)
            presenterManager.setAnnouncementTransitionAlpha(1f)
            presenterManager.setBibleTransitionAlpha(1f)
            presenterManager.setSongTransitionAlpha(1f)
            pinLottieFrame(presenterManager)
            waitForIdle()
            captureTo(File("$ROOT/${name}_$suffix.png"))
        }
    }
}

/**
 * Pins the lower third to one frame, so a shot of a live lottie is the same picture twice.
 *
 * The presenter draws the pre-rendered frame stream when it is ready and composes the animation
 * itself until then, and the pre-render finishes off the UI thread whenever it finishes — so the
 * capture landed on a different frame every run: the band fully drawn in one recording, still
 * fading in the next. Measured at 580 pixels over the band itself, under the change threshold and
 * therefore never a failure, just a diff in every comparison for a picture nobody touched.
 *
 * Waiting for the pre-render puts every run on the same path, and the last frame is the settled one
 * — what stays on screen once the entrance has played, which is what a shot of a live lower third
 * should show. Both waits end on a positive signal, and a tab with no lower third live falls
 * straight through.
 */
private fun ComposeUiTest.pinLottieFrame(presenterManager: PresenterManager) {
    if (presenterManager.presentingMode.value != Presenting.LOWER_THIRD) return
    waitUntil("the lower-third pre-render is ready", 15_000L) {
        (presenterManager.lottieFrameCount.value ?: 0) > 0
    }
    val settled = (presenterManager.lottieFrameCount.value ?: return) - 1
    presenterManager.setLottieCurrentFrameIndex(settled)
    waitUntil("lower-third frame $settled is the one on screen", 5_000L) {
        presenterManager.lottieFrame.value?.index == settled
    }
}

/**
 * The tab's own Go Live button, not the schedule row's — both carry the same description, and the
 * schedule's sits far to the left of the tab area.
 */
internal fun ComposeUiTest.goLive() {
    // The tab's own button, not the schedule's: a click on a disabled control is silently swallowed,
    // and the tab's is disabled until whatever it would put live has finished loading — the Lottie
    // parse behind the lower-third preview, for one, which finishes off the composition thread and
    // so outlives `waitForIdle`. Without this the shot came out with the preview still spinning in
    // one theme and loaded in the other, from the same test.
    waitUntil("the tab's Go Live button is enabled", 5_000L) {
        onAllNodes(hasContentDescription("Go Live") and isEnabled())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .any { it.boundsInRoot.left > PANEL_LEFT }
    }
    val nodes = onAllNodesWithContentDescription("Go Live")
    val rightmost = nodes.fetchSemanticsNodes(atLeastOneRootRequired = false)
        .withIndex().maxByOrNull { it.value.boundsInRoot.left } ?: error("no Go Live button on screen")
    nodes[rightmost.index].performClick()
    waitForIdle()
}

/** Left of this is the schedule panel, which carries a Go Live on every row. */
private const val PANEL_LEFT = 380f

/**
 * A button by description inside the tab's own left panel — the timer panel carries its own Play
 * and Go Live, and the whole-screen selectors would find the announcement's instead.
 */
internal fun ComposeUiTest.clickInPanel(description: String) {
    val nodes = onAllNodesWithContentDescription(description)
    val inPanel = nodes.fetchSemanticsNodes(atLeastOneRootRequired = false)
        .withIndex()
        .filter { it.value.boundsInRoot.left in PANEL_LEFT..820f }
    require(inPanel.isNotEmpty()) { "no \"$description\" button in the tab panel" }
    nodes[inPanel.first().index].performClick()
    waitForIdle()
}

/**
 * The Go Live button belonging to the row that shows [rowText] — a list where every row carries one
 * cannot be driven by position alone.
 */
internal fun ComposeUiTest.goLiveOnRow(rowText: String) {
    val row = onAllNodes(androidx.compose.ui.test.hasText(rowText, substring = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .first().boundsInRoot
    val nodes = onAllNodesWithContentDescription("Go Live")
    val onRow = nodes.fetchSemanticsNodes(atLeastOneRootRequired = false)
        .withIndex()
        // x guard as well as y: a schedule row at the same height carries its own Go Live, and
        // without this the click lands on the schedule instead of the question.
        .first { it.value.boundsInRoot.center.y in row.top..row.bottom && it.value.boundsInRoot.left > PANEL_LEFT }
    nodes[onRow.index].performClick()
    waitForIdle()
}

private fun ComposeUiTest.selectTab(tab: Tabs) {
    // The tab strip scrolls: a tab past the right edge is composed but not clickable until it is
    // brought into view, and the click silently lands on nothing.
    onNodeWithText(tabLabel(tab)).performScrollTo().performClick()
    waitForIdle()
}

private fun tabLabel(tab: Tabs) = when (tab) {
    Tabs.BIBLE -> "Bible"
    Tabs.SONGS -> "Songs"
    Tabs.PICTURES -> "Pictures"
    Tabs.PRESENTATION -> "Presentation"
    Tabs.MEDIA -> "Media"
    Tabs.LOWER_THIRD -> "Lower Third"
    Tabs.ANNOUNCEMENTS -> "Announcements"
    Tabs.WEB -> "Web"
    Tabs.CANVAS -> "Canvas"
    Tabs.QA -> "Q&A"
    // "STT", not "Captions": `tab_stt` in strings.xml is the three letters. The map said Captions
    // and nothing noticed, because no preview used this tab until one was added for the website.
    Tabs.STT -> "STT"
    Tabs.DICTIONARY -> "Dictionary"
    Tabs.CROSSWORD -> "Crossword"
    Tabs.COMPANION_SURFACE -> "Companion"
}

private fun serviceSchedule(actions: ScheduleActions) {
    actions.addMedia(File(LIBRARY, "Media/Welcome Loop.mp4").absolutePath, "Welcome Loop", "video")
    actions.addSong(320, "Come Thou Fount Of Every Blessing", "Hymnal", "song-320")
    actions.addSong(12, "Amazing Grace", "Hymnal", "song-12")
    actions.addBibleVerse("Psalms", 23, 1, "The LORD is my shepherd; I shall not want.", "1-3", 19)
    actions.addPresentation(File(LIBRARY, "Decks/Sermon.pdf").absolutePath, "Sermon", SERMON_SLIDES.size, "pdf")
    actions.addSong(455, "It Is Well With My Soul", "Hymnal", "song-455")
    actions.addPicture(File(LIBRARY, "Gallery").absolutePath, "Gallery", PHOTOS.size)
    actions.addAnnouncement(
        ScheduleItem.AnnouncementItem(
            id = "ann-1",
            text = "Fellowship lunch in the hall after the service",
        )
    )
    actions.addAnnouncement(
        ScheduleItem.AnnouncementItem(
            id = "ann-countdown",
            text = "",
            isTimer = true,
            timerMinutes = 5,
            timerExpiredText = "The service is beginning",
        )
    )
    actions.addAnnouncement(
        ScheduleItem.AnnouncementItem(
            id = "ann-until",
            text = "",
            isTimer = true,
            timerMode = Constants.TIMER_MODE_CLOCK,
            targetHour = 10,
            targetMinute = 30,
        )
    )
    actions.addAnnouncement(
        ScheduleItem.AnnouncementItem(
            id = "ann-countup",
            text = "",
            isTimer = true,
            timerMode = Constants.TIMER_MODE_COUNT_UP,
        )
    )
    actions.addAnnouncement(
        ScheduleItem.AnnouncementItem(
            id = "ann-clock",
            text = "",
            isTimer = true,
            timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY,
        )
    )
    actions.addWebsite("https://churchpresenter.org/give", "Giving page")
}

/**
 * Writes the Q&A questions into `~/.churchpresenter/qa_state.json`, the same file `QAManager`
 * reads at construction and writes back on every change — so they survive a settings dump rather
 * than living only in the preview run.
 *
 * The wrapper object is private to `QAManager`, so the two DTO lists are serialised and the
 * envelope written around them.
 */
private fun writeQuestions() {
    val asked = listOf(
        QuestionDto(
            "q1",
            "How do we know the resurrection actually happened?",
            "Sarah",
            "",
            1_770_000_000_000,
            "APPROVED",
            12,
            12,
            0,
        ),
        QuestionDto(
            "q2",
            "What does it mean to be baptised in the Spirit?",
            "Michael",
            "",
            1_770_000_060_000,
            "APPROVED",
            9,
            9,
            0,
        ),
        QuestionDto(
            "q3",
            "How can I share my faith with family who aren't interested?",
            "Grace",
            "",
            1_770_000_120_000,
            "PENDING",
            7,
            7,
            0,
        ),
        QuestionDto("q4", "Why does God allow suffering?", "Daniel", "", 1_770_000_180_000, "PENDING", 6, 6, 0),
        QuestionDto("q5", "Is it wrong to doubt sometimes?", "Ruth", "", 1_770_000_240_000, "PENDING", 4, 4, 0),
        QuestionDto(
            "q6",
            "How should we pray when we don't know what to ask for?",
            "Peter",
            "",
            1_770_000_300_000,
            "PENDING",
            3,
            3,
            0,
        ),
        QuestionDto(
            "q7",
            "What's the difference between grace and mercy?",
            "Hannah",
            "",
            1_770_000_360_000,
            "DENIED",
            0,
            0,
            0,
        ),
    )
    val answered = listOf(
        QuestionDto(
            "q0",
            "Where in the Bible does it talk about the good shepherd?",
            "Anna",
            "",
            1_769_999_400_000,
            "DONE",
            15,
            15,
            0,
        ),
    )
    val list = ListSerializer(QuestionDto.serializer())
    val json = Json { encodeDefaults = true }
    val file = File(System.getProperty("user.home"), ".churchpresenter/qa_state.json")
    file.parentFile?.mkdirs()
    file.writeText(
        """{"questions":${json.encodeToString(list, asked)},""" +
            """"history":${json.encodeToString(list, answered)},"votedIps":{}}"""
    )
}

/**
 * Writes the Canvas scenes where `SceneViewModel` reads them — it owns its own file under
 * `user.home` and MainDesktop builds it internally, so there is no instance to seed directly.
 */
private fun writeScenes() {
    val file = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")
    file.parentFile?.mkdirs()
    file.writeText(Json { prettyPrint = false }.encodeToString(previewScenes()))
}

/** The Canvas scenes, shared by the tab preview and the output snapshot of the same scene. */
internal fun previewScenes(): List<Scene> = listOf(
    Scene(
        id = "scene-welcome",
        name = "Welcome",
        sources = listOf(
            SceneSource.ImageSource(
                id = "bg", name = "Cross At Sunset",
                transform = SourceTransform(x = 0f, y = 0f, width = 1f, height = 1f),
                filePath = File(LIBRARY, "Backgrounds/Cross At Sunset.jpg").absolutePath,
                contentScale = "CROP",
            ),
            SceneSource.TextSource(
                id = "title", name = "Title",
                transform = SourceTransform(x = 0.06f, y = 0.66f, width = 0.6f, height = 0.14f),
                text = "He Is Risen", fontSize = 104, bold = true, horizontalAlignment = "left",
            ),
            SceneSource.TextSource(
                id = "verse", name = "Verse",
                transform = SourceTransform(x = 0.06f, y = 0.81f, width = 0.62f, height = 0.09f),
                text = "\"He is not here: for he is risen\"  ·  Matthew 28:6",
                fontSize = 38, fontColor = "#F3DCA8", horizontalAlignment = "left",
            ),
            SceneSource.QRCodeSource(
                id = "qr", name = "Giving QR",
                transform = SourceTransform(x = 0.04f, y = 0.05f, width = 0.1f, height = 0.18f),
                content = "https://churchpresenter.org/give",
            ),
        ),
    ),
    Scene(id = "scene-countdown", name = "Countdown"),
    Scene(id = "scene-sermon", name = "Sermon Title"),
    Scene(id = "scene-notices", name = "Notices"),
)

// ── The library on disk ─────────────────────────────────────────────────────

internal fun library(): AppSettings {
    val songs = File(LIBRARY, "Songs")
    val bibles = File(LIBRARY, "Bibles")
    val pictures = File(LIBRARY, "Gallery")
    val decks = File(LIBRARY, "Decks")
    listOf(songs, bibles, pictures, decks).forEach { it.mkdirs() }
    writeSongs(songs)
    KJV_SOURCE.copyTo(File(bibles, "kjv1769.spb"), overwrite = true)
    writePictures(pictures)
    writeDeck(File(decks, "Sermon.pdf"))
    File(LIBRARY, "Media").mkdirs()
    WELCOME_LOOP_SOURCE.copyTo(File(LIBRARY, "Media/Welcome Loop.mp4"), overwrite = true)
    val lowerThirds = File(LIBRARY, "Lower Thirds").apply { mkdirs() }
    LOWER_THIRD_SOURCE.listFiles()?.forEach { it.copyTo(File(lowerThirds, it.name), overwrite = true) }
    File(LIBRARY, "Backgrounds").mkdirs()
    SUNRISE_SOURCE.copyTo(File(LIBRARY, "Backgrounds/Cross At Sunset.jpg"), overwrite = true)
    // Panel sizes an operator would settle on: a schedule panel wide enough to keep its header on
    // one row, and a lyric pane that leaves the song list room to breathe.
    val layout = WindowLayoutSettings(
        schedulePanelWidthDp = 360,
        lyricsPanelWidthDp = 380,
        // The announcements position grid is capped at 400dp and splits that three ways, so the
        // panel has to clear 400 before "Bottom Center" stops wrapping.
        announcementsLeftPanelWidthDp = 440,
    )
    return AppSettings(
        schedulePanelWidthDp = 360,
        maximizedLayout = layout,
        windowedLayout = layout,
        songSettings = SongSettings(storageDirectory = songs.absolutePath),
        bibleSettings = BibleSettings(
            storageDirectory = bibles.absolutePath,
            primaryBible = "kjv1769.spb",
            verticalAlignment = Constants.CENTER,
            primaryBibleHorizontalAlignment = Constants.CENTER,
            primaryReferenceHorizontalAlignment = Constants.CENTER,
        ),
        pictureSettings = PictureSettings(storageDirectory = pictures.absolutePath),
        presentationStorageDirectory = decks.absolutePath,
        streamingSettings = StreamingSettings(lowerThirdFolder = File(LIBRARY, "Lower Thirds").absolutePath),
        // No slide-in, so the announcement is settled in the middle of the output at capture time
        // rather than still travelling up from the bottom.
        announcementsSettings = AnnouncementsSettings(animationType = Constants.ANIMATION_NONE),
        webBookmarks = listOf(
            WebBookmark(url = "https://churchpresenter.org/give", title = "Giving"),
            WebBookmark(url = "https://churchpresenter.org/notices", title = "Weekly Notices"),
            WebBookmark(url = "https://churchpresenter.org/prayer", title = "Prayer Requests"),
            WebBookmark(url = "https://www.biblegateway.com", title = "Bible Gateway"),
        ),
        hiddenTabs = emptySet(),
        // Pinned: both default to `UUID.randomUUID()`, and the device id is drawn in a text field on
        // the Companion Satellite settings tab — so its screenshot carried a fresh id every run.
        companionSatelliteConnections = listOf(
            CompanionSatelliteSettings(id = "preview-connection", deviceId = "churchpresenter-preview"),
        ),
    )
}

private fun writeSongs(dir: File) {
    val parser = SongFileParser()
    HYMNS.forEach { hymn ->
        val book = File(dir, hymn.songbook).apply { mkdirs() }
        parser.writeSongFile(
            SongItem(
                number = hymn.number,
                title = hymn.title,
                songbook = hymn.songbook,
                author = hymn.author,
                lyrics = hymn.lyrics,
            ),
            File(book, "${hymn.number} - ${hymn.title}.song").absolutePath,
        )
    }
}

private val BACKGROUNDS_SOURCE = File("../resources/src/main/composeResources/files/backgrounds")

/**
 * The picture gallery, as display name to background category.
 *
 * These were twelve generated colour gradients, which made the Pictures tab the one panel that
 * obviously came from a fixture. They are now real photographs — the app already ships ~290 of them,
 * so this costs the repository nothing and exercises the real JPEG decode path rather than a
 * synthetic PNG.
 *
 * **The names say only what the category guarantees.** The specific file behind "Worship" is
 * whichever sorts first in that category, and nobody here has looked at it, so a caption like
 * "Hands Raised" would be a guess that the image is free to contradict. A gallery that names what it
 * actually knows is both honest and stable when the shipped set changes.
 */
private val PHOTOS = listOf(
    "01 Worship" to "worship",
    "02 Worship" to "worship",
    "03 Church" to "church",
    "04 Church" to "church",
    "05 Cross" to "cross",
    "06 Cross" to "cross",
    "07 Candles" to "candles",
    "08 Candles" to "candles",
    "09 Mountains" to "mountains",
    "10 Sky" to "sky_clouds",
    "11 Ocean" to "ocean_waves",
    "12 Landscape" to "nature_landscape",
)

/**
 * Copies the gallery out of the shipped backgrounds, **downscaled**.
 *
 * The sources are ~1880x1253. Twelve of those decoded at full size is real work on a two-core
 * runner, and the Pictures tab waits for every thumbnail before it can be photographed — that wait
 * has already timed out once when the record job grew. 960px keeps the photographs unmistakably
 * photographic while costing a quarter of the pixels.
 */
private fun writePictures(dir: File) {
    val taken = mutableMapOf<String, Int>()
    PHOTOS.forEach { (name, category) ->
        val nth = taken.merge(category, 1, Int::plus)!! - 1
        val source = BACKGROUNDS_SOURCE
            .listFiles { file -> file.name.startsWith("${category}_") }
            ?.sortedBy { it.name }
            ?.getOrNull(nth)
            ?: error("no shipped background left in '$category' for '$name'")
        ImageIO.write(scaledToWidth(ImageIO.read(source), 960), "png", File(dir, "$name.png"))
    }
}

private fun scaledToWidth(source: BufferedImage, width: Int): BufferedImage {
    if (source.width <= width) return source
    val height = (source.height.toLong() * width / source.width).toInt().coerceAtLeast(1)
    val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val canvas = scaled.createGraphics()
    canvas.drawImage(source.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
    canvas.dispose()
    return scaled
}

private val SERMON_SLIDES = listOf(
    "The God Who Keeps",
    "Psalm 23:1-6",
    "1. He Provides",
    "2. He Restores",
    "3. He Walks With Us",
    "Response",
)

private fun writeDeck(file: File) {
    PDDocument().use { doc ->
        SERMON_SLIDES.forEach { title ->
            val page = PDPage(PDRectangle(720f, 405f))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 40f)
                stream.newLineAtOffset(56f, 220f)
                stream.showText(title)
                stream.endText()
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 20f)
                stream.newLineAtOffset(56f, 170f)
                stream.showText("Sunday morning service")
                stream.endText()
            }
        }
        doc.save(file)
    }
}

private class Hymn(
    val number: String,
    val title: String,
    val author: String,
    val songbook: String = "Hymnal",
    val lyrics: List<String>,
)

private val HYMNS = listOf(
    Hymn("12", "Amazing Grace", "John Newton", lyrics = listOf(
        "[Verse 1]",
        "Amazing grace! how sweet the sound",
        "That saved a wretch like me!",
        "I once was lost, but now am found,",
        "Was blind, but now I see.",
        "[Verse 2]",
        "'Twas grace that taught my heart to fear,",
        "And grace my fears relieved;",
        "How precious did that grace appear",
        "The hour I first believed!",
        "[Verse 3]",
        "Through many dangers, toils and snares,",
        "I have already come;",
        "'Tis grace hath brought me safe thus far,",
        "And grace will lead me home.",
        "[Verse 4]",
        "The Lord has promised good to me,",
        "His word my hope secures;",
        "He will my shield and portion be,",
        "As long as life endures.",
        "[Verse 5]",
        "Yea, when this flesh and heart shall fail,",
        "And mortal life shall cease,",
        "I shall possess, within the veil,",
        "A life of joy and peace.",
        "[Verse 6]",
        "When we've been there ten thousand years,",
        "Bright shining as the sun,",
        "We've no less days to sing God's praise",
        "Than when we'd first begun.",
    )),
    Hymn("45", "Be Thou My Vision", "Dallan Forgaill", lyrics = listOf(
        "[Verse 1]", "Be Thou my vision, O Lord of my heart;", "Naught be all else to me, save that Thou art.",
        "Thou my best thought, by day or by night,", "Waking or sleeping, Thy presence my light.",
        "[Verse 2]", "Be Thou my wisdom, and Thou my true word;", "I ever with Thee and Thou with me, Lord.",
    )),
    Hymn("78", "Holy, Holy, Holy", "Reginald Heber", lyrics = listOf(
        "[Verse 1]", "Holy, holy, holy! Lord God Almighty!", "Early in the morning our song shall rise to Thee.",
        "[Verse 2]",
        "Holy, holy, holy! all the saints adore Thee,",
        "Casting down their golden crowns around the glassy sea.",
    )),
    Hymn("103", "How Great Thou Art", "Carl Boberg", lyrics = listOf(
        "[Verse 1]", "O Lord my God, when I in awesome wonder", "Consider all the worlds Thy hands have made.",
        "[Chorus]", "Then sings my soul, my Saviour God, to Thee,", "How great Thou art, how great Thou art!",
    )),
    Hymn("115", "A Mighty Fortress Is Our God", "Martin Luther", lyrics = listOf(
        "[Verse 1]", "A mighty fortress is our God,", "A bulwark never failing.",
    )),
    Hymn("131", "All Hail The Power Of Jesus' Name", "Edward Perronet", lyrics = listOf(
        "[Verse 1]", "All hail the power of Jesus' name!", "Let angels prostrate fall.",
    )),
    Hymn("157", "Crown Him With Many Crowns", "Matthew Bridges", lyrics = listOf(
        "[Verse 1]", "Crown Him with many crowns,", "The Lamb upon His throne.",
    )),
    Hymn("178", "O Worship The King", "Robert Grant", lyrics = listOf(
        "[Verse 1]", "O worship the King, all glorious above,", "And gratefully sing His power and His love.",
    )),
    Hymn("204", "O For A Thousand Tongues To Sing", "Charles Wesley", lyrics = listOf(
        "[Verse 1]", "O for a thousand tongues to sing", "My great Redeemer's praise.",
    )),
    Hymn("221", "Praise To The Lord, The Almighty", "Joachim Neander", lyrics = listOf(
        "[Verse 1]",
        "Praise to the Lord, the Almighty, the King of creation!",
        "O my soul, praise Him, for He is thy health and salvation!",
    )),
    Hymn("244", "Rock Of Ages", "Augustus Toplady", lyrics = listOf(
        "[Verse 1]", "Rock of Ages, cleft for me,", "Let me hide myself in Thee.",
    )),
    Hymn("267", "Just As I Am", "Charlotte Elliott", lyrics = listOf(
        "[Verse 1]", "Just as I am, without one plea,", "But that Thy blood was shed for me.",
    )),
    Hymn("289", "Nearer, My God, To Thee", "Sarah F. Adams", lyrics = listOf(
        "[Verse 1]", "Nearer, my God, to Thee, nearer to Thee!", "E'en though it be a cross that raiseth me.",
    )),
    Hymn("320", "Come Thou Fount Of Every Blessing", "Robert Robinson", lyrics = listOf(
        "[Verse 1]", "Come, Thou Fount of every blessing,", "Tune my heart to sing Thy grace.",
        "[Verse 2]", "Here I raise mine Ebenezer;", "Hither by Thy help I'm come.",
    )),
    Hymn("341", "Abide With Me", "Henry F. Lyte", lyrics = listOf(
        "[Verse 1]", "Abide with me; fast falls the eventide;", "The darkness deepens; Lord, with me abide.",
    )),
    Hymn("367", "Great Is Thy Faithfulness", "Thomas Chisholm", lyrics = listOf(
        "[Verse 1]", "Great is Thy faithfulness, O God my Father,", "There is no shadow of turning with Thee.",
    )),
    Hymn("388", "What A Friend We Have In Jesus", "Joseph Scriven", lyrics = listOf(
        "[Verse 1]", "What a friend we have in Jesus,", "All our sins and griefs to bear!",
    )),
    Hymn("402", "Blessed Assurance", "Fanny Crosby", lyrics = listOf(
        "[Verse 1]", "Blessed assurance, Jesus is mine!", "O what a foretaste of glory divine!",
    )),
    Hymn("419", "To God Be The Glory", "Fanny Crosby", lyrics = listOf(
        "[Verse 1]",
        "To God be the glory, great things He hath done,",
        "So loved He the world that He gave us His Son.",
    )),
    Hymn("437", "Come, Thou Long-Expected Jesus", "Charles Wesley", lyrics = listOf(
        "[Verse 1]", "Come, Thou long-expected Jesus,", "Born to set Thy people free.",
    )),
    Hymn("455", "It Is Well With My Soul", "Horatio Spafford", lyrics = listOf(
        "[Verse 1]", "When peace like a river attendeth my way,", "When sorrows like sea billows roll.",
        "[Chorus]", "It is well with my soul,", "It is well, it is well with my soul.",
    )),
    Hymn("472", "When I Survey The Wondrous Cross", "Isaac Watts", lyrics = listOf(
        "[Verse 1]", "When I survey the wondrous cross", "On which the Prince of glory died.",
    )),
    Hymn("488", "Joyful, Joyful, We Adore Thee", "Henry van Dyke", lyrics = listOf(
        "[Verse 1]", "Joyful, joyful, we adore Thee,", "God of glory, Lord of love.",
    )),
    Hymn("501", "The Old Rugged Cross", "George Bennard", lyrics = listOf(
        "[Verse 1]", "On a hill far away stood an old rugged cross,", "The emblem of suffering and shame.",
    )),
    Hymn("519", "Love Divine, All Loves Excelling", "Charles Wesley", lyrics = listOf(
        "[Verse 1]", "Love divine, all loves excelling,", "Joy of heaven, to earth come down.",
    )),
    Hymn("534", "Guide Me, O Thou Great Jehovah", "William Williams", lyrics = listOf(
        "[Verse 1]", "Guide me, O Thou great Jehovah,", "Pilgrim through this barren land.",
    )),
    Hymn("7", "Blessed Be The Name", "Ralph E. Hudson", songbook = "Gospel Songs", lyrics = listOf(
        "[Verse 1]", "O for a thousand tongues to sing", "The praises of my God and King!",
        "[Chorus]", "Blessed be the name, blessed be the name,", "Blessed be the name of the Lord.",
    )),
    Hymn("14", "I Need Thee Every Hour", "Annie S. Hawks", songbook = "Gospel Songs", lyrics = listOf(
        "[Verse 1]", "I need Thee every hour, most gracious Lord;", "No tender voice like Thine can peace afford.",
    )),
    Hymn("22", "Sweet Hour Of Prayer", "William W. Walford", songbook = "Gospel Songs", lyrics = listOf(
        "[Verse 1]", "Sweet hour of prayer! sweet hour of prayer!", "That calls me from a world of care.",
    )),
    Hymn("31", "Standing On The Promises", "R. Kelso Carter", songbook = "Gospel Songs", lyrics = listOf(
        "[Verse 1]", "Standing on the promises of Christ my King,", "Through eternal ages let His praises ring.",
    )),
    Hymn("44", "Leaning On The Everlasting Arms", "Elisha A. Hoffman", songbook = "Gospel Songs", lyrics = listOf(
        "[Verse 1]", "What a fellowship, what a joy divine,", "Leaning on the everlasting arms.",
    )),
    Hymn("58", "Softly And Tenderly", "Will L. Thompson", songbook = "Gospel Songs", lyrics = listOf(
        "[Verse 1]", "Softly and tenderly Jesus is calling,", "Calling for you and for me.",
    )),
)
