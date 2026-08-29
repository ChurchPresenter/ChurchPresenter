package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.dialogs.tabs.BackgroundScope
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [BackgroundSettingsViewModel] is a pure settings transform: it hands the caller a function that
 * returns an updated [AppSettings]. The property that matters is that an update touches exactly
 * the surface it names and leaves the other five alone — the tab writes all six through this one
 * method, so a wrong scope silently overwrites the wrong surface.
 */
class BackgroundSettingsViewModelTest {

    private val vm = BackgroundSettingsViewModel()

    /** Applies one update to [start] and returns the result. */
    private fun apply(
        start: AppSettings = AppSettings(),
        update: (((AppSettings) -> AppSettings) -> Unit) -> Unit,
    ): AppSettings {
        var current = start
        update { transform -> current = transform(current) }
        return current
    }

    private fun config(color: String) = BackgroundConfig(backgroundColor = color)

    @Test
    fun `the default color is updated`() {
        val result = apply { onChange ->
            vm.updateBackground(BackgroundScope.DEFAULT, config("#123456"), onChange)
        }
        assertEquals("#123456", result.backgroundSettings.defaultBackgroundColor)
    }

    @Test
    fun `each background slot is updated independently`() {
        val bible = config("#111111")
        val bibleLower = config("#222222")
        val song = config("#333333")
        val songLower = config("#444444")

        var settings = AppSettings()
        settings = apply(settings) { onChange ->
            vm.updateBackground(BackgroundScope.BIBLE, bible, onChange)
        }
        settings = apply(settings) { onChange ->
            vm.updateBackground(BackgroundScope.BIBLE_LOWER_THIRD, bibleLower, onChange)
        }
        settings = apply(settings) { onChange ->
            vm.updateBackground(BackgroundScope.SONG, song, onChange)
        }
        settings = apply(settings) { onChange ->
            vm.updateBackground(BackgroundScope.SONG_LOWER_THIRD, songLower, onChange)
        }

        with(settings.backgroundSettings) {
            assertEquals(bible, bibleBackground)
            assertEquals(bibleLower, bibleLowerThirdBackground)
            assertEquals(song, songBackground)
            assertEquals(songLower, songLowerThirdBackground)
        }
    }

    @Test
    fun `updating the bible background leaves the song backgrounds untouched`() {
        val start = AppSettings().let { s ->
            s.copy(
                backgroundSettings = s.backgroundSettings.copy(
                    songBackground = config("#SONG"),
                    songLowerThirdBackground = config("#SONGLT"),
                ),
            )
        }
        val result = apply(start) { onChange ->
            vm.updateBackground(BackgroundScope.BIBLE, config("#BIBLE"), onChange)
        }

        assertEquals("#BIBLE", result.backgroundSettings.bibleBackground.backgroundColor)
        assertEquals("#SONG", result.backgroundSettings.songBackground.backgroundColor)
        assertEquals("#SONGLT", result.backgroundSettings.songLowerThirdBackground.backgroundColor)
    }

    @Test
    fun `full-screen and lower-third slots are distinct for the same content type`() {
        // These two are the easiest pair to confuse — same content, different presentation mode.
        var settings = apply { onChange ->
            vm.updateBackground(BackgroundScope.SONG, config("#FULL"), onChange)
        }
        settings = apply(settings) { onChange ->
            vm.updateBackground(BackgroundScope.SONG_LOWER_THIRD, config("#LOWER"), onChange)
        }

        assertEquals("#FULL", settings.backgroundSettings.songBackground.backgroundColor)
        assertEquals("#LOWER", settings.backgroundSettings.songLowerThirdBackground.backgroundColor)
    }

    @Test
    fun `updates leave the rest of the settings object alone`() {
        val start = AppSettings(theme = "dark", language = "ru", windowWidth = 1600)
        val result = apply(start) { onChange ->
            vm.updateBackground(BackgroundScope.DEFAULT, config("#000000"), onChange)
        }

        assertEquals("dark", result.theme)
        assertEquals("ru", result.language)
        assertEquals(1600, result.windowWidth)
    }

    @Test
    fun `updates compose so a later one does not undo an earlier one`() {
        var settings = apply { onChange ->
            vm.updateBackground(BackgroundScope.DEFAULT, config("#AAAAAA"), onChange)
        }
        settings = apply(settings) { onChange ->
            vm.updateBackground(BackgroundScope.BIBLE, config("#BBBBBB"), onChange)
        }

        assertEquals("#AAAAAA", settings.backgroundSettings.defaultBackgroundColor)
        assertEquals("#BBBBBB", settings.backgroundSettings.bibleBackground.backgroundColor)
    }
}
