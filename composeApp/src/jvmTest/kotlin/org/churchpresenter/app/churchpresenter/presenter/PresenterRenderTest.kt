package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants
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

    /** Three translations, for the cases where the middle one produces no verse. */
    private val threeTranslations = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            listOf("kjv.spb", "rst.spb", "lut.spb").map { BibleTranslationSettings(fileName = it) },
        ),
    )

    /** Four translations configured, for the per-output selection cases. */
    private val fourTranslations = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            listOf("kjv.spb", "rst.spb", "lut.spb", "afr.spb").map { BibleTranslationSettings(fileName = it) },
        ),
    )

    /** Settings with the title placed where the settings picker can actually put it. */
    private val titleAboveVerse = AppSettings(songSettings = SongSettings(titlePosition = Constants.ABOVE_VERSE))

    private fun verse(
        text: String = "For God so loved the world",
        book: String = "John",
        chapter: Int = 3,
        number: Int = 16,
        abbreviation: String = "KJV",
        translationFileName: String = "",
    ) = SelectedVerse(
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = book,
        chapter = chapter,
        verseNumber = number,
        verseText = text,
        translationFileName = translationFileName,
    )

    private fun lyric(
        header: String? = "[Verse 1]",
        title: String = "Amazing Grace",
        number: Int = 42,
        lines: List<String> = listOf("Amazing grace how sweet the sound"),
        translations: List<SectionTranslation> = emptyList(),
    ) = LyricSection(
        header = header,
        title = title,
        songNumber = number,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
        translations = translations,
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
    fun `a screen set to the first translation shows only that one`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, abbreviation = "RST")),
                    appSettings = bilingualBible,
                    bibleTranslations = listOf(0),
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onAllNodesWithText(russian, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a screen set to the second translation shows only that one`() = runComposeUiTest {
        // An overflow room running in another language. It is no longer promoted into the first
        // slot: styling is looked up per verse by its own translation, so it keeps its own.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, abbreviation = "RST")),
                    appSettings = bilingualBible,
                    bibleTranslations = listOf(1),
                )
            }
        }

        onNodeWithText(russian, substring = true).assertExists()
        onAllNodesWithText(english, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a screen set to a translation that is not there falls back to the first`() = runComposeUiTest {
        // A single-translation service must not black out the rooms configured for a later one.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse()),
                    appSettings = bilingualBible,
                    bibleTranslations = listOf(1),
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
    }

    @Test
    fun `a screen can show a non-adjacent pair out of a longer stack`() = runComposeUiTest {
        // The thing the old primary/secondary/both string could not express at all: first and third
        // of four, for a room that wants the original language and one of the translations.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse(),
                        verse(text = russian, abbreviation = "RST"),
                        verse(text = "Also sehr liebte Gott", abbreviation = "LUT"),
                        verse(text = "Want so lief het God", abbreviation = "AFR"),
                    ),
                    appSettings = fourTranslations,
                    bibleTranslations = listOf(0, 2),
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onNodeWithText("Also sehr liebte Gott", substring = true).assertExists()
        onAllNodesWithText(russian, substring = true).assertCountEquals(0)
        onAllNodesWithText("Want so lief het God", substring = true).assertCountEquals(0)
    }

    // ── Selections survive a gap in what actually rendered ──────────────────────────────────────
    //
    // `bibleTranslations` names positions in the *configured stack*, but the verse list only carries
    // translations that produced text. A module whose file has gone, or which simply has no verse at
    // this reference — a critical text stopping at Mark 16:8 — is absent, and the two stop lining up.
    // Matching each verse by its own translation is what keeps a screen on the language it was given.

    @Test
    fun `a screen keeps its translation when an earlier one produced nothing`() = runComposeUiTest {
        // Stack is [kjv, rst, lut] and this screen is set to lut, position 2 — but rst had no verse
        // here, so only two arrived. Position 2 no longer exists; lut is at index 1.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse(translationFileName = "kjv.spb"),
                        verse(text = "Also sehr liebte Gott", abbreviation = "LUT", translationFileName = "lut.spb"),
                    ),
                    appSettings = threeTranslations,
                    bibleTranslations = listOf(2),
                )
            }
        }

        onNodeWithText("Also sehr liebte Gott", substring = true)
            .assertExists("the screen was assigned lut and must still show lut")
        onAllNodesWithText(english, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a screen shows no other language when its own produced nothing`() = runComposeUiTest {
        // Same stack, this screen set to rst — the one that is missing. Falling back to the first is
        // the established behaviour; showing lut, which this screen was never given, would not be.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse(translationFileName = "kjv.spb"),
                        verse(text = "Also sehr liebte Gott", abbreviation = "LUT", translationFileName = "lut.spb"),
                    ),
                    appSettings = threeTranslations,
                    bibleTranslations = listOf(1),
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onAllNodesWithText("Also sehr liebte Gott", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun `a relayed verse with no translation name is still matched by position`() = runComposeUiTest {
        // Verses from a linked instance or the companion server carry no translation identity, so
        // position is all there is to go on and must keep working.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse(), verse(text = russian, abbreviation = "RST")),
                    appSettings = threeTranslations,
                    bibleTranslations = listOf(1),
                )
            }
        }

        onNodeWithText(russian, substring = true).assertExists()
        onAllNodesWithText(english, substring = true).assertCountEquals(0)
    }

    @Test
    fun `an empty selection shows every translation, including ones added later`() = runComposeUiTest {
        // Empty means all: an output left at the default picks up a bible added afterwards rather
        // than needing to be ticked again on every screen.
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse(),
                        verse(text = russian, abbreviation = "RST"),
                        verse(text = "Also sehr liebte Gott", abbreviation = "LUT"),
                    ),
                    appSettings = fourTranslations,
                    bibleTranslations = emptyList(),
                )
            }
        }

        onNodeWithText(english, substring = true).assertExists()
        onNodeWithText(russian, substring = true).assertExists()
        onNodeWithText("Also sehr liebte Gott", substring = true).assertExists()
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
    fun `on default settings the title is drawn`() = runComposeUiTest {
        setContent {
            Box(screen) { SongPresenter(lyricSection = lyric(), appSettings = AppSettings()) }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        onNodeWithText("42", substring = true).assertExists()
        // This was a documented gap: titlePosition defaulted to Middle, which the presenter draws
        // the title row at neither of -- so titleDisplay said "first page" and nothing appeared.
        // The default is AboveVerse now, so the setting does what it says out of the box.
        onAllNodesWithText("Amazing Grace", substring = true).onFirst().assertExists()
    }

    @Test
    fun `a bilingual song shows both languages`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = lyric(
                        lines = listOf("Amazing grace how sweet the sound"),
                        translations = listOf(SectionTranslation(lines = listOf("О благодать, спасён тобой"))),
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
