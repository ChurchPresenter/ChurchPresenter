package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Lottie band background: its type constant, its path on the config, and where its templates live. */
class LottieBandBackgroundTest {

    private fun roundTrip(settings: AppSettings): AppSettings =
        Json { ignoreUnknownKeys = true }.decodeFromString(
            AppSettings.serializer(),
            Json { encodeDefaults = true }.encodeToString(AppSettings.serializer(), settings),
        )

    @Test
    fun `the template path survives a settings file and defaults to none`() {
        assertEquals("", BackgroundConfig().backgroundLottie)
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_LOTTIE,
                    backgroundLottie = "/bands/verse.json",
                ),
                songLowerThirdBackground = BackgroundConfig(backgroundLottie = "/bands/lyric.json"),
            ),
        )
        val back = roundTrip(settings).backgroundSettings
        assertEquals(Constants.BACKGROUND_LOTTIE, back.bibleLowerThirdBackground.backgroundType)
        assertEquals("/bands/verse.json", back.bibleLowerThirdBackground.backgroundLottie)
        assertEquals("/bands/lyric.json", back.songLowerThirdBackground.backgroundLottie)
    }

    @Test
    fun `a file from before the field reads with no template`() {
        val json = """{"backgroundSettings":{"bibleLowerThirdBackground":{"backgroundType":"Color"}}}"""
        val settings = Json { ignoreUnknownKeys = true }.decodeFromString(AppSettings.serializer(), json)
        assertEquals("", settings.backgroundSettings.bibleLowerThirdBackground.backgroundLottie)
    }

    @Test
    fun `a profile carries its own template once it owns that surface`() {
        val ownTemplate = BackgroundSettings(
            songLowerThirdBackground = BackgroundConfig(
                Constants.BACKGROUND_LOTTIE,
                backgroundLottie = "/mine.json",
            ),
        )
        val owner = OutputProfile(
            backgroundSettings = ownTemplate,
            backgroundOverrides = setOf(BackgroundSurface.SONG_LOWER_THIRD.name),
        )
        val resolved = AppSettings().resolvedFor(owner)
        assertEquals("/mine.json", resolved.backgroundSettings.songLowerThirdBackground.backgroundLottie)

        // The same profile without the claim follows the Background tab instead -- which is the
        // default, so a profile nobody has pointed at a template shows the house background.
        val follower = owner.copy(backgroundOverrides = emptySet())
        assertEquals(
            "",
            AppSettings().resolvedFor(follower).backgroundSettings.songLowerThirdBackground.backgroundLottie,
            "a followed surface comes from the global document, whatever the profile happens to store",
        )

        val plain = AppSettings().resolvedFor(OutputProfile()).backgroundSettings
        assertEquals("", plain.songLowerThirdBackground.backgroundLottie)
    }

    @Test
    fun `the templates folder sits under the app data directory and is created by the manager`() {
        val home = Files.createTempDirectory("band-dir").toFile()
        val saved = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)
        try {
            val dir = SettingsManager.bibleLowerThirdsDir(File(home, "data"))
            assertEquals(File(home, "data/bible_lower_thirds"), dir)
            val manager = SettingsManager()
            assertEquals("bible_lower_thirds", manager.bibleLowerThirdsDir.name)
            assertTrue(manager.bibleLowerThirdsDir.isDirectory, "created on construction, like the presets folder")
            assertEquals("lottie_presets", manager.lottiePresetsDir.name)
            assertTrue(manager.lottiePresetsDir.isDirectory, "the presets folder the comment above appeals to")
        } finally {
            System.setProperty("user.home", saved)
            home.deleteRecursively()
        }
    }
}
