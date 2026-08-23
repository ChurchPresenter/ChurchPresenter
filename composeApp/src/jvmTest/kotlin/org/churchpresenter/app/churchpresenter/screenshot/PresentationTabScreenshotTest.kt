@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.tabs.presentationTab
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.Slide
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.model.pdfDeck
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.RENDER_TIMEOUT_MS
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

class PresentationTabScreenshotTest {

    private val temps = mutableListOf<File>()

    @AfterTest
    fun cleanUpFiles() {
        temps.forEach { it.deleteRecursively() }
        temps.clear()
    }

    private fun deckFile(name: String, titles: List<String>): File {
        val dir = Files.createTempDirectory("cp-presentation-shot").toFile().also { temps += it }
        val file = File(dir, name)
        PDDocument().use { doc ->
            titles.forEach { title ->
                val page = PDPage(PDRectangle(720f, 405f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 44f)
                    stream.newLineAtOffset(56f, 230f)
                    stream.showText(title)
                    stream.endText()
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 22f)
                    stream.newLineAtOffset(56f, 180f)
                    stream.showText("Sunday morning service")
                    stream.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun sermonDeck() = deckFile("Sermon.pdf", SERMON_SLIDES)

    private fun syntheticDeck(file: File, slides: Int, video: Boolean = false, builds: Int = 0) = pdfDeck(
        sourceFile = file,
        slideWidthPt = 720.0,
        slideHeightPt = 405.0,
        slides = (0 until slides).map { index ->
            Slide(
                index = index,
                notes = "",
                transition = null,
                layers = if (video && index == 0) listOf(
                    LayerSpec.Media(
                        id = "media-0",
                        zIndex = 0,
                        boundsPt = RectPt(0.0, 0.0, 100.0, 100.0),
                        shapeIndex = 0,
                        contentRectPt = RectPt(0.0, 0.0, 100.0, 100.0),
                        mediaFile = null,
                    )
                ) else emptyList(),
                timeline = if (builds > 0) Timeline(List(builds + index) { Step(emptyList()) }) else null,
                fidelity = Fidelity.NATIVE,
            )
        },
    )

    private fun shoot(
        name: String,
        settings: (AppSettings) -> AppSettings = { it },
        presenter: Boolean = true,
        frozen: Boolean = false,
        vlcAvailable: Boolean = true,
        width: Dp? = null,
        rootIndex: Int = 0,
        drive: ComposeUiTest.(PresentationViewModel) -> Unit = { load(it, sermonDeck()) },
    ) = stackedThemes(SECTION, name) { mode, file ->
        presentationTab(
            settings = settings,
            presenterManager = if (presenter) PresenterManager() else null,
            presentationFrozen = frozen,
            vlcAvailable = vlcAvailable,
            width = width,
            themeMode = mode,
        ) { vm, _ ->
            drive(vm)
            captureTo(file, rootIndex)
        }
    }

    private fun ComposeUiTest.load(vm: PresentationViewModel, file: File, deck: Deck? = null) {
        val before = vm.loadGeneration
        deck?.let { d -> vm.loadDeck = { LoadResult.Success(d) } }
        vm.addPresentation(file)
        waitUntil("deck ${file.name} rasterised", RENDER_TIMEOUT_MS) {
            vm.loadGeneration != before && !vm.isLoading
        }
        // Thumbnails decode on Dispatchers.IO inside produceState. Until each one lands its tile
        // draws as plain black, so wait for every composed slide to have its image, flushing the
        // off-thread snapshot writes that carry them.
        waitUntil("every composed slide drawn", RENDER_TIMEOUT_MS) {
            Snapshot.sendApplyNotifications()
            val labelled = semantics(SemanticsProperties.Text) { it.joinToString("") { t -> t.text } }
                .filter { SLIDE_LABEL.matches(it) }.toSet()
            val drawn = semantics(SemanticsProperties.ContentDescription) { it.first() }
                .filter { SLIDE_LABEL.matches(it) }.toSet()
            drawn.containsAll(labelled)
        }
        waitForIdle()
    }

    private fun <T> ComposeUiTest.semantics(key: SemanticsPropertyKey<T>, read: (T) -> String): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(key), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { node -> node.config.getOrNull(key)?.let(read) }

    private fun ComposeUiTest.loadFailing(vm: PresentationViewModel, error: DeckLoadError) {
        val dir = Files.createTempDirectory("cp-presentation-shot-error").toFile().also { temps += it }
        vm.loadDeck = { LoadResult.Failure(error) }
        vm.addPresentation(File(dir, "Sermon.pdf").apply { writeText("") })
        waitUntil("the failed load reported") { !vm.isLoading && vm.loadError != null }
        waitForIdle()
    }

    private lateinit var savedFiles: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        savedFiles = RecentPresentationFiles.files.toList()
        savedPinned = RecentPresentationFiles.pinned.toList()
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.files.addAll(savedFiles)
        RecentPresentationFiles.pinned.clear()
        RecentPresentationFiles.pinned.addAll(savedPinned)
    }

    @Test
    fun `no file opened yet`() = shoot("no_file", drive = {})

    @Test
    fun `a deck open`() = shoot("deck_open")

    @Test
    fun `a deck long enough to fill the grid`() = shoot("many_slides") { vm ->
        load(vm, deckFile("Advent Series.pdf", (1..14).map { "Slide $it" }))
    }

    @Test
    fun `a one-slide deck`() = shoot("single_slide") { vm ->
        load(vm, deckFile("Notice.pdf", listOf("One Slide")))
    }

    @Test
    fun `a later slide selected`() = shoot("slide_selected") { vm ->
        load(vm, sermonDeck())
        vm.selectSlide(3)
        waitForIdle()
    }

    @Test
    fun `the deck auto-advancing`() = shoot("playing") { vm ->
        load(vm, sermonDeck())
        onNodeWithContentDescription(PLAY).performClick()
        waitForIdle()
    }

    @Test
    fun `looping turned off`() = shoot(
        "loop_off",
        settings = { it.copy(presentationSettings = it.presentationSettings.copy(isLooping = false)) },
    )

    @Test
    fun `with no output to go live on the buttons are gone`() = shoot("no_presenter", presenter = false)

    @Test
    fun `the output frozen`() = shoot("output_frozen", frozen = true)

    @Test
    fun `slides that animate carry a build count`() = shoot("build_badges") { vm ->
        val file = deckFile("Easter.pdf", (1..3).map { "Slide $it" })
        load(vm, file, deck = syntheticDeck(file, slides = 3, builds = 2))
    }

    @Test
    fun `an embedded video with no VLC to play it`() = shoot("vlc_missing", vlcAvailable = false) { vm ->
        val file = deckFile("Testimony.pdf", listOf("Testimony", "Video"))
        load(vm, file, deck = syntheticDeck(file, slides = 2, video = true))
    }

    @Test
    fun `two decks open at once`() = shoot("multi_file") { vm ->
        load(vm, sermonDeck())
        load(vm, deckFile("Announcements.pdf", listOf("Welcome", "Notices")))
    }

    @Test
    fun `the recent files bar, with the open deck marked`() = shoot("recent_files") { vm ->
        val open = sermonDeck()
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
        RecentPresentationFiles.files.addAll(
            listOf(open.absolutePath, "/Decks/Advent Series.pptx", "/Decks/Baptism.key"),
        )
        RecentPresentationFiles.pinned.add("/Decks/Liturgy.pdf")
        load(vm, open)
    }

    @Test
    fun `a password-protected deck`() = shoot("error_password_protected") { vm ->
        loadFailing(vm, DeckLoadError.PASSWORD_PROTECTED)
    }

    @Test
    fun `a deck with no pages`() = shoot("error_empty_document") { vm ->
        loadFailing(vm, DeckLoadError.EMPTY_DOCUMENT)
    }

    @Test
    fun `a file that will not parse`() = shoot("error_render_failed") { vm ->
        loadFailing(vm, DeckLoadError.PARSE_FAILED)
    }

    @Test
    fun `settings tiles carrying non-default values`() = shoot(
        "settings_customised",
        settings = {
            it.copy(
                presentationSettings = it.presentationSettings.copy(
                    autoScrollInterval = 15f,
                    transitionDuration = 900f,
                    animationType = Constants.ANIMATION_SLIDE_RIGHT,
                )
            )
        },
    )

    @Test
    fun `the animation picker`() = shoot("animation_picker", rootIndex = 1) { vm ->
        load(vm, sermonDeck())
        onNodeWithText(ANIMATION_TYPE, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun `the auto-scroll interval editor`() = shoot("interval_editor", rootIndex = 1) { vm ->
        load(vm, sermonDeck())
        onNodeWithText(AUTO_SCROLL, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun `the transition duration editor`() = shoot("transition_editor", rootIndex = 1) { vm ->
        load(vm, sermonDeck())
        onNodeWithText(TRANSITION, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun `a narrow panel drops the hint onto its own row`() = shoot("narrow_panel", width = 420.dp)

    @Test
    fun `a half-width panel`() = shoot("medium_panel", width = 760.dp)

    private companion object {
        const val SECTION = "presentationTab"

        const val PLAY = "Play"
        // "Slide 3", the tile label — not "Slide 3 of 6", which is the counter in the controls bar.
        val SLIDE_LABEL = Regex("""Slide \d+""")
        const val ANIMATION_TYPE = "ANIMATION TYPE"
        const val AUTO_SCROLL = "AUTO-SCROLL INTERVAL"
        const val TRANSITION = "TRANSITION DURATION"

        val SERMON_SLIDES = listOf(
            "Title",
            "The Text",
            "Point One",
            "Point Two",
            "Point Three",
            "Response",
        )
    }
}
