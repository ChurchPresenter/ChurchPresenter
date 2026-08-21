@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.songs.LyricSection
import org.churchpresenter.app.churchpresenter.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

/**
 * Pictures of what the congregation sees.
 *
 * The rest of the suite asserts invariants — that a string is on screen, that a colour is what was
 * configured — because font metrics differ across the three platforms this app ships on and pixel
 * assertions would flake (see `AGENT.md`). These tests assert nothing at all. They render a surface
 * and hand the image to Roborazzi; the comparison happens in CI, between the images this branch
 * produces and the ones the base branch produces, both rendered on the same Linux runner. That
 * sidesteps the platform problem entirely: no committed image is ever used as the baseline, so a
 * stale one cannot fail a build on the machine reading it.
 *
 * The PNGs are committed, under `composeApp/screenshots/`, so a reviewer can approve what is on
 * screen rather than take the diff on trust. See `SCREENSHOT_ROOT`.
 *
 * Consequences worth knowing:
 * - Recording locally writes PNGs to `composeApp/screenshots/` and asserts nothing. It is still a
 *   smoke test: a presenter that throws while composing fails here. Re-record before pushing when a
 *   surface here changed, and record on one platform per branch — Skia rasterises text per platform,
 *   so recording on a second OS rewrites nearly every file whether anything changed or not.
 * - `captureRoboImage` is inert unless a Roborazzi task turned recording on, so an ordinary
 *   `./gradlew :composeApp:jvmTest` pays only the composition, not the file write.
 * - What a diff means is a judgement call, not a failure: a deliberate design change shows up here
 *   exactly like a regression does. The PR comment is there to be looked at, not to gate the merge.
 *
 * Add a case here when a surface is worth watching, not for every state — each one is an image a
 * human has to look at when it changes.
 */
class PresenterScreenshotTest {

    /** A 1080p output, which is what the overwhelming majority of these surfaces are drawn onto. */
    private val screen = Modifier.size(1920.dp, 1080.dp)

    /**
     * Writes `<name>.png` at the root of [SCREENSHOT_ROOT].
     *
     * Through the shared constant rather than its own literal: the workflow compares images by their
     * path *relative to* that root, so a capture written somewhere else is not a differing image —
     * it is an image with no counterpart on the other side, and it silently stops being compared.
     */
    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$name.png")
    }

    private fun song(
        lines: List<String> = listOf(
            "Amazing grace how sweet the sound",
            "That saved a wretch like me",
        ),
        secondary: List<String> = emptyList(),
    ) = LyricSection(
        header = "[Verse 1]",
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
        secondaryLines = secondary,
    )

    private fun verse(
        text: String = "For God so loved the world, that he gave his only begotten Son.",
        fileName: String = "kjv.spb",
        abbreviation: String = "KJV",
    ) = SelectedVerse(
        translationFileName = fileName,
        bibleAbbreviation = abbreviation,
        bibleName = abbreviation,
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
    )

    // ── Songs ───────────────────────────────────────────────────────────────────

    @Test
    fun `song full screen`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) { SongPresenter(lyricSection = song(), appSettings = AppSettings()) }
            }
        }
        capture("song_fullscreen")
    }

    @Test
    fun `song lower third`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(
                        lyricSection = song(),
                        appSettings = AppSettings(),
                        isLowerThird = true,
                    )
                }
            }
        }
        capture("song_lower_third")
    }

    @Test
    fun `song in two languages`() = runComposeUiTest {
        val settings = AppSettings(
            songSettings = SongSettings(fullscreenLanguageDisplay = Constants.SONG_LANG_BOTH),
        )
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(
                        lyricSection = song(
                            secondary = listOf(
                                "О благодать, спасён тобой",
                                "Я из пучины бед",
                            ),
                        ),
                        appSettings = settings,
                    )
                }
            }
        }
        capture("song_two_languages")
    }

    // ── Bible ───────────────────────────────────────────────────────────────────

    @Test
    fun `bible verse full screen`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    BiblePresenter(selectedVerses = listOf(verse()), appSettings = AppSettings())
                }
            }
        }
        capture("bible_fullscreen")
    }

    @Test
    fun `bible verse lower third`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    BiblePresenter(
                        selectedVerses = listOf(verse()),
                        appSettings = AppSettings(),
                        isLowerThird = true,
                    )
                }
            }
        }
        capture("bible_lower_third")
    }

    @Test
    fun `bible in two translations`() = runComposeUiTest {
        val settings = AppSettings(
            bibleSettings = BibleSettings(
                translations = listOf(
                    BibleTranslationSettings(fileName = "kjv.spb"),
                    BibleTranslationSettings(fileName = "rst.spb"),
                ),
            ),
        )
        setContent {
            MaterialTheme {
                Box(screen) {
                    BiblePresenter(
                        selectedVerses = listOf(
                            verse(),
                            verse(
                                text = "Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного.",
                                fileName = "rst.spb",
                                abbreviation = "RST",
                            ),
                        ),
                        appSettings = settings,
                    )
                }
            }
        }
        capture("bible_two_translations")
    }
}
