package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two verse layouts the full-screen single-verse tests in [PresenterRenderTest] don't reach:
 * lower-third mode and a multi-verse selection.
 *
 * Lower-third mode is an entirely separate styling path (every font/colour/size picker switches to
 * its `*LowerThird*` field); if it stopped composing the verse the band would go blank on air while
 * the operator's full-screen preview looked fine. Lower-third mode WITH a second Bible exercises the
 * secondary-translation branch of that same path — neither the full-screen bilingual test nor the
 * mono lower-third test reaches it — and both translations must appear in the band.
 *
 * Both assert on the verse text and reference that land on screen, so the layout path runs and
 * nothing races a crossfade.
 */
@OptIn(ExperimentalTestApi::class)
class BiblePresenterLayoutRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun verse(
        text: String,
        number: Int,
        book: String = "John",
        chapter: Int = 3,
        abbreviation: String = "KJV",
        fileName: String = "",
    ) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = book,
        chapter = chapter,
        verseNumber = number,
        verseText = text,
    )

    @Test
    fun `lower-third mode still composes the verse and its reference`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse("For God so loved the world", 16)),
                    appSettings = AppSettings(),
                    isLowerThird = true,
                )
            }
        }
        onNodeWithText(
            "For God so loved the world",
            substring = true,
        ).assertExists("the band must show the verse on air")
        onNodeWithText("John 3:16", substring = true).assertExists("the lower third still needs its reference")
    }

    @Test
    fun `lower-third mode carries both translations when a second bible is configured`() = runComposeUiTest {
        val bilingual = AppSettings(bibleSettings = BibleSettings(secondaryBible = "RST"))
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16),
                        verse("Ибо так возлюбил Бог мир", 16, book = "Иоанна", abbreviation = "RST"),
                    ),
                    appSettings = bilingual,
                    isLowerThird = true,
                )
            }
        }
        onNodeWithText(
            "For God so loved the world",
            substring = true,
        ).assertExists("the band must carry the primary translation")
        onNodeWithText("Ибо так возлюбил Бог мир", substring = true)
            .assertExists("the lower-third secondary-translation style path must render the second language too")
    }

    // ── The band reads the per-translation style, not the retired globals ────────────────────────
    //
    // The lower third used to take its sizes, alignments and abbreviation flag from the legacy
    // primary/secondary fields on BibleSettings, which no UI writes any more — so every one of those
    // controls in Bible settings did nothing at all to the band. Only the abbreviation flag is
    // observable from a test (a size is not in the semantics tree), and it stands in for the rest.

    @Test
    fun `the band shows an abbreviation when the translation asks for one`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(BibleTranslationSettings(fileName = "kjv.spb", showAbbreviation = true)),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse("For God so loved the world", 16, fileName = "kjv.spb")),
                    appSettings = settings,
                    isLowerThird = true,
                )
            }
        }

        onNodeWithText("KJV", substring = true)
            .assertExists("the band must honour the translation's own abbreviation setting")
    }

    @Test
    fun `the band omits the abbreviation when the translation does not ask for one`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(BibleTranslationSettings(fileName = "kjv.spb", showAbbreviation = false)),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(verse("For God so loved the world", 16, fileName = "kjv.spb")),
                    appSettings = settings,
                    isLowerThird = true,
                )
            }
        }

        onNodeWithText("John 3:16", substring = true).assertExists("the reference itself still shows")
        onAllNodesWithText("KJV", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a translation asking for an abbreviation it does not have shows the reference alone`() = runComposeUiTest {
        // The abbreviation is derived from the module's title, and a module with a blank title has
        // none. The flag is still on in settings, so both halves of the condition matter: shown
        // unguarded this draws "John 3:16 " with a trailing separator and nothing after it.
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(BibleTranslationSettings(fileName = "untitled.spb", showAbbreviation = true)),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, abbreviation = "", fileName = "untitled.spb"),
                    ),
                    appSettings = settings,
                    isLowerThird = true,
                )
            }
        }

        onNodeWithText("John 3:16", substring = true).assertExists("the reference still has to show")
    }

    @Test
    fun `a full-screen translation asking for an abbreviation it does not have shows the reference alone`() =
        runComposeUiTest {
        // Same rule on the full-screen path, which builds its reference in a different place.
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(BibleTranslationSettings(fileName = "untitled.spb", showAbbreviation = true)),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, abbreviation = "", fileName = "untitled.spb"),
                    ),
                    appSettings = settings,
                )
            }
        }

        onNodeWithText("John 3:16", substring = true).assertExists()
    }

    @Test
    fun `a bilingual band drops the abbreviation of whichever translation lacks one`() = runComposeUiTest {
        // The band builds its two references independently, so the guard has to hold on each
        // side. Here the primary has an abbreviation and the secondary does not, and both are
        // asking for one — the side that has none must fall back to the plain reference rather
        // than draw a leading separator with nothing before it.
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb", showAbbreviation = true),
                    BibleTranslationSettings(fileName = "untitled.spb", showAbbreviation = true),
                ),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "",
                            fileName = "untitled.spb",
                        ),
                    ),
                    appSettings = settings,
                    isLowerThird = true,
                )
            }
        }

        onNodeWithText("KJV John 3:16", substring = true).assertExists("the side that has one still shows it")
        onNodeWithText(
            "Иоанна 3:16",
            substring = true,
        ).assertExists("and the side that does not still shows its reference")
    }

    @Test
    fun `a parallel pair selected as a range shows the range on both sides`() = runComposeUiTest {
        // A multi-verse selection carries its span in verseRange, and the secondary reference is
        // built from that rather than from the single verse number — otherwise the two columns
        // disagree about what is on screen.
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb"),
                ),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb").copy(verseRange = "16-17"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "RST",
                            fileName = "rst.spb",
                        )
                            .copy(verseRange = "16-17"),
                    ),
                    appSettings = settings,
                )
            }
        }

        onNodeWithText("John 3:16-17", substring = true).assertExists("the primary side spans the selection")
        onNodeWithText("Иоанна 3:16-17", substring = true).assertExists("and so does the secondary")
    }

    @Test
    fun `the band styles the translation it is actually showing`() = runComposeUiTest {
        // Assigned the third of three. The band used to hand slot 0 the first translation's style
        // whatever was in it, so this verse would have been drawn with kjv's abbreviation setting.
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb", showAbbreviation = false),
                    BibleTranslationSettings(fileName = "rst.spb", showAbbreviation = false),
                    BibleTranslationSettings(fileName = "lut.spb", showAbbreviation = true),
                ),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "RST",
                            fileName = "rst.spb",
                        ),
                        verse(
                            "Also sehr liebte Gott",
                            16,
                            book = "Johannes",
                            abbreviation = "LUT",
                            fileName = "lut.spb",
                        ),
                    ),
                    appSettings = settings,
                    isLowerThird = true,
                    bibleTranslations = listOf(2),
                )
            }
        }

        onNodeWithText("LUT", substring = true)
            .assertExists("the band must use lut's style, since lut is what it is showing")
    }

    @Test
    fun `full screen composes every translation in a parallel stack`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf("kjv.spb", "rst.spb", "lut.spb").map {
                    BibleTranslationSettings(fileName = it)
                },
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse("Ибо так возлюбил Бог мир", 16, abbreviation = "RST", fileName = "rst.spb"),
                        verse("Denn also hat Gott die Welt geliebt", 16, abbreviation = "LUT", fileName = "lut.spb"),
                    ),
                    appSettings = settings,
                )
            }
        }

        onNodeWithText("For God so loved the world", substring = true).assertExists()
        onNodeWithText("Ибо так возлюбил Бог мир", substring = true).assertExists()
        onNodeWithText("Denn also hat Gott die Welt geliebt", substring = true).assertExists()
    }

    @Test
    fun `missing middle verse still renders the later translation`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf("kjv.spb", "missing.spb", "lut.spb").map {
                    BibleTranslationSettings(fileName = it)
                },
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse("Denn also hat Gott die Welt geliebt", 16, fileName = "lut.spb"),
                    ),
                    appSettings = settings,
                )
            }
        }

        onNodeWithText("For God so loved the world", substring = true).assertExists()
        onNodeWithText("Denn also hat Gott die Welt geliebt", substring = true).assertExists()
    }

    // ── The full-screen stack divides the output, it does not hug one edge of it ─────────────────
    //
    // Each translation gets an equal band of the height and is aligned inside it by the global
    // verticalAlignment — what the 50/50 split this replaced did for two, generalised to any number.
    // The stack that landed with #91 wrapped its content instead, so with the default bottom
    // alignment two short verses bunched against the bottom edge and left the whole top half of the
    // output empty: a blank band an operator reads as a section of its own.

    /** A settings stack of [count] translations, matching the file names [stackVerses] hands out. */
    private fun stackOf(count: Int): AppSettings = AppSettings(
        bibleSettings = BibleSettings().withTranslations(
            (0 until count).map { BibleTranslationSettings(fileName = "bible$it.spb") },
        ),
    )

    private fun stackVerses(vararg markers: String) = markers.mapIndexed { index, marker ->
        verse(marker, 16, fileName = "bible$index.spb")
    }

    /**
     * The height the presenter actually drew into.
     *
     * Not [screen]'s 1080: the test harness roots these at its own window size and clips to it, so a
     * band worked out from the requested size lands in the wrong place. Read it back instead.
     */
    private fun ComposeUiTest.outputHeight(): Float =
        onRoot().fetchSemanticsNode().size.height.toFloat()

    @Test
    fun `four long translations stay inside the output bounds`() = runComposeUiTest {
        val files = listOf("one.spb", "two.spb", "three.spb", "four.spb")
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                files.map { BibleTranslationSettings(fileName = it, textFontSize = 100, referenceFontSize = 70) },
            ),
        )
        val markers = listOf("FIRST", "SECOND", "THIRD", "FOURTH")
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = markers.mapIndexed { index, marker ->
                        verse(
                            text = "$marker ${"long verse text ".repeat(45)}",
                            number = 16,
                            fileName = files[index],
                        )
                    },
                    appSettings = settings,
                )
            }
        }

        markers.forEach { marker ->
            val bounds = onNodeWithText(marker, substring = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top >= 0f, "$marker starts above the output: $bounds")
            assertTrue(bounds.bottom <= 1080f, "$marker is clipped below the output: $bounds")
        }
    }

    @Test
    fun `two short translations leave no empty band above the stack`() = runComposeUiTest {
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = stackVerses("FIRST TEXT", "SECOND TEXT"),
                    appSettings = stackOf(2),
                )
            }
        }

        // The regression: wrapped and bottom-aligned, both blocks sat below the halfway line and left
        // the whole top half of the output blank.
        val half = outputHeight() / 2f
        val firstTop = onNodeWithText("FIRST TEXT").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            firstTop < half,
            "the first translation must use the top half of the output, not bunch at the bottom: $firstTop",
        )
    }

    @Test
    fun `each translation is drawn inside its own band`() = runComposeUiTest {
        val markers = listOf("FIRST TEXT", "SECOND TEXT", "THIRD TEXT")
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = stackVerses(*markers.toTypedArray()),
                    appSettings = stackOf(markers.size),
                )
            }
        }

        // Bands, not pixel positions: font metrics differ across the three target platforms, but the
        // verse belonging to band i must have its centre inside band i whatever they measure.
        val bandHeight = outputHeight() / markers.size
        markers.forEachIndexed { index, marker ->
            val bounds = onNodeWithText(marker).fetchSemanticsNode().boundsInRoot
            val centre = (bounds.top + bounds.bottom) / 2f
            assertTrue(
                centre > index * bandHeight && centre < (index + 1) * bandHeight,
                "$marker must sit in band $index (${index * bandHeight}..${(index + 1) * bandHeight}), was at $centre",
            )
        }
    }

    @Test
    fun `a long verse beside a short one still fits its own band`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf("bible0.spb", "bible1.spb").map {
                    BibleTranslationSettings(fileName = it, textFontSize = 90)
                },
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("LONG ${"and it came to pass ".repeat(60)}", 16, fileName = "bible0.spb"),
                        verse("SHORT", 16, fileName = "bible1.spb"),
                    ),
                    appSettings = settings,
                )
            }
        }

        // The long one can no longer borrow the short one's slack, so the shared scale shrinks until
        // it fits its own band — and neither translation may leave the output.
        val height = outputHeight()
        listOf("LONG", "SHORT").forEach { marker ->
            val bounds = onNodeWithText(marker, substring = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top >= 0f, "$marker starts above the output: $bounds")
            assertTrue(bounds.bottom <= height, "$marker is clipped below the output: $bounds")
        }
        val shortTop = onNodeWithText("SHORT").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            shortTop >= height / 2f,
            "the second translation belongs in the lower band, was at $shortTop",
        )
    }

    @Test
    fun `multi translation spacing separates adjacent blocks`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings(
                multiTranslationSpacing = 80,
                multiTranslationDivider = true,
            ).withTranslations(
                listOf("one.spb", "two.spb").map {
                    BibleTranslationSettings(
                        fileName = it,
                        textFontSize = 40,
                        referenceFontSize = 24,
                        showAbbreviation = true,
                    )
                },
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("FIRST TEXT", 16, abbreviation = "ONE", fileName = "one.spb"),
                        verse("SECOND TEXT", 16, abbreviation = "TWO", fileName = "two.spb"),
                    ),
                    appSettings = settings,
                )
            }
        }

        val firstReferenceBottom = onNodeWithText("ONE John 3:16").fetchSemanticsNode().boundsInRoot.bottom
        val secondTextTop = onNodeWithText("SECOND TEXT").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            secondTextTop - firstReferenceBottom >= 75f,
            "configured spacing must remain visible between translation blocks",
        )
    }

    @Test
    fun `a band whose second translation is switched off shows only the first`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb", lowerThirdEnabled = false),
                ),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "RST",
                            fileName = "rst.spb",
                        ),
                    ),
                    appSettings = settings,
                    isLowerThird = true,
                )
            }
        }

        onNodeWithText("For God so loved the world", substring = true).assertExists()
        onAllNodesWithText("Ибо так возлюбил Бог мир", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a full-screen output still shows both when the band's second translation is off`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb", lowerThirdEnabled = false),
                ),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "RST",
                            fileName = "rst.spb",
                        ),
                    ),
                    appSettings = settings,
                )
            }
        }

        onNodeWithText("For God so loved the world", substring = true).assertExists()
        onNodeWithText("Ибо так возлюбил Бог мир", substring = true).assertExists()
    }

    @Test
    fun `a vertical band stacks a parallel pair`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb"),
                ),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "RST",
                            fileName = "rst.spb",
                        ),
                    ),
                    appSettings = settings,
                    isLowerThird = true,
                    isLowerThirdVertical = true,
                )
            }
        }

        onNodeWithText("For God so loved the world", substring = true).assertExists()
        onNodeWithText("Ибо так возлюбил Бог мир", substring = true).assertExists()
    }

    @Test
    fun `a verse arriving with a second translation this output never asked for shows only the first`() =
        runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings().withTranslations(
                listOf(BibleTranslationSettings(fileName = "kjv.spb")),
            ),
        )
        setContent {
            Box(screen) {
                BiblePresenter(
                    selectedVerses = listOf(
                        verse("For God so loved the world", 16, fileName = "kjv.spb"),
                        verse(
                            "Ибо так возлюбил Бог мир",
                            16,
                            book = "Иоанна",
                            abbreviation = "RST",
                            fileName = "rst.spb",
                        ),
                    ),
                    appSettings = settings,
                )
            }
        }

        onNodeWithText("For God so loved the world", substring = true).assertExists()
        onAllNodesWithText("Ибо так возлюбил Бог мир", substring = true).assertCountEquals(0)
    }
}
