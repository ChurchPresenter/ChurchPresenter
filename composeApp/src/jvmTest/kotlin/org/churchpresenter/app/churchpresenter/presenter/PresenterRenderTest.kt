package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

/**
 * What the congregation actually reads off the screen.
 *
 * These render the two presenters that carry almost every service — a lyric slide and a verse
 * slide — in a real composition, and assert on the text that lands on it. Everything between the
 * setting and the pixel runs on the way: language selection, the title rule, the reference line.
 *
 * The failures this catches are the ones nothing reports. A song shown in the wrong language for
 * the room, a secondary translation that silently stops appearing, a verse rendered without its
 * reference so nobody can find it in their own Bible — each of those looks like a working app from
 * the operator's desk, because the operator is looking at a different screen.
 *
 * Both presenters lay out against the space they are handed, so each is given a screen-sized box.
 *
 * One thing is deliberately NOT asserted here: whether a title or number that IS composed is
 * actually visible. Both presenters keep a fully transparent copy of that row on every slide to
 * reserve its height, so the lyric does not jump as it comes and goes — and an alpha of zero is not
 * part of the semantics tree, so a test cannot tell the reserved copy from a shown one. Which
 * slides they appear on is covered directly instead, in [SongPresenterTitleRuleTest].
 */
@OptIn(ExperimentalTestApi::class)
class PresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private val english = "For God so loved the world"
    private val russian = "Ибо так возлюбил Бог мир"

    /** Settings with a second translation configured, which is what turns the parallel layout on. */
    private val bilingualBible = AppSettings(bibleSettings = BibleSettings(secondaryBible = "RST"))

    /** Settings with the title placed where the settings picker can actually put it. */
    private val titleAboveVerse = AppSettings(songSettings = SongSettings(titlePosition = Constants.ABOVE_VERSE))

    private fun verse(
        text: String = "For God so loved the world",
        book: String = "John",
        chapter: Int = 3,
        number: Int = 16,
        abbreviation: String = "KJV",
    ) = SelectedVerse(
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = book,
        chapter = chapter,
        verseNumber = number,
        verseText = text,
    )

    private fun lyric(
        header: String? = "[Verse 1]",
        title: String = "Amazing Grace",
        number: Int = 42,
        lines: List<String> = listOf("Amazing grace how sweet the sound"),
        secondaryLines: List<String> = emptyList(),
    ) = LyricSection(
        header = header,
        title = title,
        songNumber = number,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
        secondaryLines = secondaryLines,
    )

    // ── Scripture on screen ─────────────────────────────────────────────────────

    @Test
    fun `a verse is put on screen with its reference`() = runComposeUiTest {
        setContent {
            Box(screen) { BiblePresenter(selectedVerses = listOf(verse()), appSettings = AppSettings()) }
        }

        onNodeWithText(english, substring = true).assertExists()
        onNodeWithText("John 3:16", substring = true).assertExists("without the reference nobody can follow along")
    }

    @Test
    fun `the reference is the book, chapter and verse of the passage being read`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse(book = "Psalms", chapter = 23, number = 1, text = "The LORD is my shepherd"),
                    ),
                    appSettings = AppSettings(),
                )
            }
        }

        onNodeWithText("Psalms 23:1", substring = true).assertExists()
    }

    @Test
    fun `a range of verses names the range on screen`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse().copy(verseRange = "16-18")),
                    appSettings = AppSettings(),
                )
            }
        }

        onNodeWithText("John 3:16-18", substring = true).assertExists("the room has to know how much is being read")
    }

    @Test
    fun `both translations are shown when a second bible is configured`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, book = "Иоанна", abbreviation = "RST")),
                    appSettings = bilingualBible,
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onNodeWithText(russian, substring = true).assertExists()
        onNodeWithText("Иоанна 3:16", substring = true).assertExists("each translation names the book its own way")
    }

    @Test
    fun `no second bible configured shows only the primary`() = runComposeUiTest {
        // A verse can arrive carrying a secondary — from a linked instance, say — while this
        // machine has no second translation set up. It must not appear unasked.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, abbreviation = "RST")),
                    appSettings = AppSettings(),
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onAllNodesWithText(russian, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a screen set to the primary translation shows only that one`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, abbreviation = "RST")),
                    appSettings = bilingualBible,
                    languageMode = Constants.SONG_LANG_PRIMARY,
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onAllNodesWithText(russian, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a screen set to the secondary translation shows it in the primary's place`() = runComposeUiTest {
        // An overflow room running in another language: the secondary is promoted, not added.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, abbreviation = "RST")),
                    appSettings = bilingualBible,
                    languageMode = Constants.SONG_LANG_SECONDARY,
                )
            }
        }

        onNodeWithText(russian, substring = true).assertExists()
        onAllNodesWithText(english, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a screen set to the secondary falls back to the primary when there is only one`() = runComposeUiTest {
        // A single-translation service must not black out the rooms configured for the secondary.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse()),
                    appSettings = bilingualBible,
                    languageMode = Constants.SONG_LANG_SECONDARY,
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
    }

    @Test
    fun `nothing selected leaves the screen blank rather than failing`() = runComposeUiTest {
        // Between two passages the selection is briefly empty; the output simply goes blank.
        setContent {
            Box(screen) { BiblePresenter(selectedVerses = emptyList(), appSettings = AppSettings()) }
        }

        onAllNodesWithText(english, substring = true).assertCountEquals(0)
    }

    @Test
    fun `the key output carries the same verse as the fill`() = runComposeUiTest {
        // Fill and key are two renders of one slide; a difference between them shows on air as a
        // graphic keyed against the wrong matte.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse()),
                    appSettings = AppSettings(),
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onNodeWithText("John 3:16", substring = true).assertExists()
    }

    // ── Lyrics on screen ────────────────────────────────────────────────────────

    @Test
    fun `a lyric line is put on screen`() = runComposeUiTest {
        setContent {
            Box(screen) { SongPresenter(lyricSection = lyric(), appSettings = AppSettings()) }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
    }

    @Test
    fun `every line of the section is on screen at once`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = lyric(lines = listOf("Amazing grace", "how sweet the sound", "that saved a wretch")),
                    appSettings = AppSettings(),
                )
            }
        }

        listOf("Amazing grace", "how sweet the sound", "that saved a wretch").forEach {
            onNodeWithText(it, substring = true).assertExists()
        }
    }

    @Test
    fun `the song number is on the opening slide`() = runComposeUiTest {
        setContent {
            Box(screen) { SongPresenter(lyricSection = lyric(number = 42), appSettings = AppSettings()) }
        }

        onNodeWithText("42", substring = true).assertExists("the number is how a congregation finds it in a hymnal")
    }

    @Test
    fun `an unnumbered song shows no number at all`() = runComposeUiTest {
        setContent {
            Box(screen) { SongPresenter(lyricSection = lyric(number = 0), appSettings = AppSettings()) }
        }

        // A bare "0" on the wall means nothing to anyone.
        onAllNodesWithText("0", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the title appears on the opening slide once it has been given a position`() = runComposeUiTest {
        setContent {
            Box(screen) { SongPresenter(lyricSection = lyric(), appSettings = titleAboveVerse) }
        }

        onNodeWithText("Amazing Grace", substring = true).assertExists()
    }

    @Test
    fun `on default settings the title is never drawn -- known gap`() = runComposeUiTest {
        setContent {
            Box(screen) { SongPresenter(lyricSection = lyric(), appSettings = AppSettings()) }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        onNodeWithText("42", substring = true).assertExists("the number does show, which is what hides this")
        // Current behaviour: titlePosition defaults to a value nothing renders.
        onAllNodesWithText("Amazing Grace", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a bilingual song shows both languages`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = lyric(
                        lines = listOf("Amazing grace how sweet the sound"),
                        secondaryLines = listOf("О благодать, спасён тобой"),
                    ),
                    appSettings = AppSettings(),
                )
            }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        onNodeWithText("О благодать, спасён тобой", substring = true).assertExists()
    }

    @Test
    fun `a section with no lines renders an empty slide rather than failing`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = lyric(lines = emptyList(), title = "", number = 0),
                    appSettings = AppSettings(),
                )
            }
        }

        onAllNodesWithText("Amazing grace", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the key output carries the same lyric as the fill`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = lyric(),
                    appSettings = AppSettings(),
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                )
            }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
    }
}
