package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the Songs and Bible tabs' went-live hooks report, read back from the real [UsageEvents]
 * store: each presentation is counted only when the live outputs are set up to show it, and an
 * output switched off (no display) is not one of them.
 */
class WentLiveUsageEventsTest {

    private var realHome: String? = null
    private lateinit var home: File

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-went-live-usage").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    /** Two live outputs, each following its own profile, plus one switched off. */
    private fun settings(
        first: OutputProfile,
        second: OutputProfile = first,
        bible: BibleSettings = BibleSettings(),
        background: BackgroundSettings = BackgroundSettings(),
    ) = AppSettings(
        bibleSettings = bible,
        backgroundSettings = background,
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(targetDisplay = 0, activeProfileId = "a"),
                ScreenAssignment(targetDisplay = 1, activeProfileId = "b"),
                ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE, activeProfileId = "off"),
            ),
            outputProfiles = listOf(
                first.copy(id = "a"),
                second.copy(id = "b"),
                OutputProfile(id = "off", songMode = Constants.SONG_LANG_SECONDARY),
            ),
        ),
    )

    private val lottieBand =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = "band.json")

    private fun recorded(): Set<UsageEvent> = UsageEvents.unreported().keys

    // ── Songs ────────────────────────────────────────────────────────────────────

    @Test
    fun `a plain song on matching outputs records no presentation events`() {
        val song = SongItem(number = "7", title = "Plain", lyrics = listOf("words"))

        recordSongWentLive(song, settings(OutputProfile(songMode = Constants.SONG_LANG_PRIMARY)), null)

        assertEquals(emptySet(), recorded())
    }

    @Test
    fun `a bilingual song on outputs set to different languages is dual language and split screen`() {
        val song = SongItem(
            number = "1", title = "Amazing Grace", lyrics = listOf("Amazing grace"),
            secondaryLyrics = listOf("Divnaya blagodat"),
        )
        val settings = settings(
            OutputProfile(songMode = Constants.SONG_LANG_BOTH),
            OutputProfile(songMode = Constants.SONG_LANG_PRIMARY),
        )

        recordSongWentLive(song, settings, null)

        assertEquals(setOf(UsageEvent.SONG_DUAL_LANGUAGE, UsageEvent.SONG_SPLIT_SCREEN), recorded())
    }

    @Test
    fun `a chorded song under a lottie band records the band and the chord chart`() {
        val song = SongItem(number = "2", title = "Chords", lyrics = listOf("[G]Amazing [C]grace"))
        val settings = settings(
            OutputProfile(songMode = Constants.SONG_LANG_PRIMARY, showChords = true),
            background = BackgroundSettings(songLowerThirdBackground = lottieBand),
        )

        recordSongWentLive(song, settings, null)

        assertEquals(setOf(UsageEvent.SONG_LOTTIE_BAND, UsageEvent.SONG_CHORD_CHART), recorded())
    }

    @Test
    fun `a song with no number is counted for the report as number zero`() {
        val statistics = StatisticsManager()
        val song = SongItem(number = "", title = "Unnumbered", songbook = "Loose", lyrics = listOf("words"))

        recordSongWentLive(song, settings(OutputProfile()), statistics)

        val logged = statistics.getAllSongsInRange(0L, Long.MAX_VALUE).single()
        assertEquals("Unnumbered", logged.title)
        assertEquals(0, logged.songNumber)
    }

    // ── Bible ────────────────────────────────────────────────────────────────────

    private val twoTranslations = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb"),
            BibleTranslationSettings(fileName = "rst.spb"),
        ),
    )

    @Test
    fun `one translation on every output records no bible presentation events`() {
        val settings = settings(
            OutputProfile(),
            bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
        )

        recordBibleWentLive(settings)

        assertEquals(emptySet(), recorded())
    }

    @Test
    fun `two translations shown together on one output and apart on another is multi and split`() {
        val settings = settings(
            OutputProfile(bibleTranslations = emptyList()),
            OutputProfile(bibleTranslations = listOf(1)),
            bible = twoTranslations,
        )

        recordBibleWentLive(settings)

        assertEquals(setOf(UsageEvent.BIBLE_MULTI_TRANSLATION, UsageEvent.BIBLE_SPLIT_SCREEN), recorded())
    }

    @Test
    fun `a verse under a lottie band records the band`() {
        val settings = settings(
            OutputProfile(),
            bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
            background = BackgroundSettings(bibleLowerThirdBackground = lottieBand),
        )

        recordBibleWentLive(settings)

        assertEquals(setOf(UsageEvent.BIBLE_LOTTIE_BAND), recorded())
    }
}
