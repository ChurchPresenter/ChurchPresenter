package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.slides.presenter.PicturePresenter
import org.churchpresenter.stt.STTSegment
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** A named piece of content, told which frame it is drawing. */
typealias Scenario = Pair<String, @Composable (frame: Int) -> Unit>

/**
 * The content the render benchmark and the soak test put on an off-screen output: one scenario per
 * content type, with sample songs, verses, questions, captions, a canvas scene and a Lottie lower
 * third.
 */
object BenchmarkScenarios {

    /**
     * Every content type, each changing what it shows from frame to frame where it can.
     *
     * The lower third's Lottie file is parsed here, once, as the app parses one before it goes live
     * rather than inside the output.
     */
    fun all(photo: File): List<Scenario> {
        val lowerThird = LottieComposition.parse(lowerThirdJson())
        return listOf(
            "song verse" to { frame ->
                Fill { SongPresenter(lyricSection = verse(frame), appSettings = AppSettings()) }
            },
            "song bilingual" to { frame ->
                Fill { SongPresenter(lyricSection = verse(frame, bilingual = true), appSettings = AppSettings()) }
            },
            "song chord chart" to { frame ->
                Fill {
                    SongPresenter(
                        lyricSection = verse(frame, chords = true),
                        appSettings = AppSettings(),
                        showChords = true,
                    )
                }
            },
            "bible verse" to { frame ->
                Fill { BiblePresenter(selectedVerses = listOf(bibleVerse(frame)), appSettings = AppSettings()) }
            },
            "bible two translations" to { frame ->
                Fill {
                    BiblePresenter(
                        selectedVerses = listOf(bibleVerse(frame), bibleVerse(frame, "rst.spb", RUSSIAN)),
                        appSettings = TWO_TRANSLATIONS,
                    )
                }
            },
            "announcement scrolling" to { _ ->
                Fill { AnnouncementsPresenter(text = NOTICE, appSettings = SCROLLING_NOTICE) }
            },
            "question" to { frame -> Fill { QAPresenter(question = question(frame)) } },
            "dictionary entry" to { _ ->
                Fill { DictionaryPresenter(entry = STRONGS, dictionarySettings = AppSettings().dictionarySettings) }
            },
            "captions" to { frame ->
                Fill {
                    STTPresenter(
                        segments = captions(frame),
                        inProgressText = "",
                        translationSegments = emptyList(),
                        inProgressTranslation = "",
                        highlightedWords = emptyList(),
                        sttSettings = STTSettings(dripFeedEnabled = false),
                    )
                }
            },
            "picture" to { _ -> Fill { PicturePresenter(imagePath = photo.absolutePath) } },
            "canvas scene" to { frame -> Fill { ScenePresenter(scene = scene(frame)) } },
            "lottie lower third" to { frame ->
                Fill { LowerThirdPresenter(composition = lowerThird, progress = { (frame % LOOP) / LOOP.toFloat() }) }
            },
        )
    }

    @Composable
    private fun Fill(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }

    private fun verse(frame: Int, bilingual: Boolean = false, chords: Boolean = false) = LyricSection(
        header = "[Verse ${frame % 2 + 1}]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = VERSES[frame % 2],
        translations = if (bilingual) listOf(SectionTranslation(lines = SECONDARY)) else emptyList(),
        chordLines = if (chords) CHORDS else emptyList(),
    )

    private fun bibleVerse(frame: Int, file: String = "kjv.spb", text: String? = null) = SelectedVerse(
        translationFileName = file,
        bibleAbbreviation = file.substringBefore('.').uppercase(),
        bibleName = file,
        bookName = "John",
        chapter = 3,
        verseNumber = 16 + frame % 2,
        verseText = text ?: BIBLE[frame % 2],
    )

    private fun question(frame: Int) = Question(
        id = "q${frame % 2}",
        text = QUESTIONS[frame % 2],
        timestamp = 0L,
        status = QuestionStatus.APPROVED,
    )

    /** A rolling transcript that gains a segment every few frames, as a live caption feed does. */
    private fun captions(frame: Int) = CAPTION_LINES.take(1 + frame / CAPTION_EVERY % CAPTION_LINES.size)
        .mapIndexed { i, text -> STTSegment(i, "", text, 0.0, 0.0, true) }

    private fun scene(frame: Int) = Scene(
        name = "Welcome",
        sources = listOf(
            SceneSource.ColorSource(id = "c1", name = "Backdrop", color = "#1B2A5B"),
            SceneSource.TextSource(
                id = "t1",
                name = "Welcome",
                text = "Welcome to the 10:30 service",
                transform = SourceTransform(
                    x = 0.1f,
                    y = 0.3f + (frame % LOOP) / LOOP.toFloat() * 0.2f,
                    width = 0.8f,
                    height = 0.2f,
                ),
                fontSize = 96,
            ),
        ),
    )

    private fun lowerThirdJson(): String =
        checkNotNull(javaClass.getResource("/app-preview/lower-thirds/Guest Speaker.json")).readText()

    /** A 4K photograph in a temp directory of its own, for the picture scenario; delete its parent when done. */
    fun photo(): File {
        val dir = Files.createTempDirectory("render-benchmark").toFile()
        val file = File(dir, "backdrop.png")
        val image = BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = GradientPaint(0f, 0f, Color(0x2B3A67), 3840f, 2160f, Color(0x8FB3F5))
        canvas.fillRect(0, 0, 3840, 2160)
        canvas.dispose()
        ImageIO.write(image, "png", file)
        return file
    }
}

/** Frames in one cycle of the animated scenarios. */
private const val LOOP = 120

/** Frames between caption segments arriving. */
private const val CAPTION_EVERY = 10

private val VERSES = listOf(
    listOf("Amazing grace how sweet the sound", "That saved a wretch like me"),
    listOf("I once was lost but now am found", "Was blind but now I see"),
)
private val SECONDARY = listOf("О благодать, спасён тобой", "Я из пучины бед")
private val CHORDS = listOf("[G]Amazing [C]grace how [G]sweet the sound", "That [G]saved a [D]wretch like [G]me")
private val BIBLE = listOf(
    "For God so loved the world, that he gave his only begotten Son.",
    "For God sent not his Son into the world to condemn the world.",
)
private const val RUSSIAN = "Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного."
private val QUESTIONS = listOf(
    "How do I join a small group?",
    "How should a small group decide what to study together, and how often should it change?",
)
private const val NOTICE = "Prayer meeting Wednesday at 7pm in the hall"
private val CAPTION_LINES = listOf(
    "In the beginning was the Word, and the Word was with God, and the Word was God.",
    "The same was in the beginning with God.",
    "All things were made by him; and without him was not any thing made that was made.",
    "In him was life; and the life was the light of men.",
)
private val STRONGS = StrongsEntry(
    number = "G26",
    word = "ἀγάπη",
    transliteration = "agape",
    pronunciation = "ag-ah'-pay",
    definition = "brotherly love, affection, benevolence",
    kjvUsage = "love, charity",
)
private val TWO_TRANSLATIONS = AppSettings(
    bibleSettings = BibleSettings(
        translations = listOf("kjv.spb", "rst.spb").map { BibleTranslationSettings(fileName = it) },
    ),
)
private val SCROLLING_NOTICE = AppSettings(
    // Two seconds across the screen, so the text is on screen for most measured frames rather than
    // still sliding in from off it.
    announcementsSettings = AnnouncementsSettings(
        animationType = Constants.ANIMATION_SLIDE_FROM_RIGHT,
        animationDuration = 2_000,
    ),
)
