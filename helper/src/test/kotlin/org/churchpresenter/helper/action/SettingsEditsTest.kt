package org.churchpresenter.helper.action

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSurface
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsEditsTest {

    private val plain = AppSettings()

    private fun withOverrides(vararg surfaces: BackgroundSurface): AppSettings {
        val projection = plain.projectionSettings
        val overridden = surfaces.map { it.name }.toSet()
        val profiles = projection.outputProfiles.map { it.copy(backgroundOverrides = overridden) }
        return plain.copy(projectionSettings = projection.copy(outputProfiles = profiles))
    }

    @Test
    fun `a song background colour lands on the Background tab`() {
        val edited = withBackgroundColor(plain, ContentScope.SONG, "#112233")
        assertEquals(Constants.BACKGROUND_COLOR, edited.backgroundSettings.songBackground.backgroundType)
        assertEquals("#112233", edited.backgroundSettings.songBackground.backgroundColor)
        assertEquals(plain.backgroundSettings.bibleBackground, edited.backgroundSettings.bibleBackground)
    }

    @Test
    fun `both backgrounds change for all`() {
        val edited = withBackgroundColor(plain, ContentScope.ALL, "#445566")
        assertEquals("#445566", edited.backgroundSettings.songBackground.backgroundColor)
        assertEquals("#445566", edited.backgroundSettings.bibleBackground.backgroundColor)
    }

    @Test
    fun `a profile keeping its own bible background gets the colour too`() {
        val edited = withBackgroundColor(withOverrides(BackgroundSurface.BIBLE), ContentScope.BIBLE, "#778899")
        val profile = edited.projectionSettings.outputProfiles.first()
        assertEquals("#778899", profile.backgroundSettings.bibleBackground.backgroundColor)
        assertEquals("#778899", edited.backgroundSettings.bibleBackground.backgroundColor)
    }

    @Test
    fun `a profile not overriding that surface is left alone`() {
        val start = withOverrides(BackgroundSurface.SONG)
        val edited = withBackgroundColor(start, ContentScope.BIBLE, "#778899")
        assertEquals(start.projectionSettings, edited.projectionSettings)
    }

    @Test
    fun `font steps move songs, bibles or both`() {
        val profile = { s: AppSettings -> s.projectionSettings.outputProfiles.first() }
        val start = profile(plain)
        val song = profile(withFontStep(plain, ContentScope.SONG, 1))
        assertEquals(start.bibleSettings, song.bibleSettings)
        val bible = profile(withFontStep(plain, ContentScope.BIBLE, -1))
        assertEquals(start.songSettings, bible.songSettings)
        assertEquals(step(start.bibleSettings.primaryBibleFontSize, -1), bible.bibleSettings.primaryBibleFontSize)
        val all = profile(withFontStep(plain, ContentScope.ALL, 1))
        assertEquals(step(start.bibleSettings.secondaryBibleFontSize, 1), all.bibleSettings.secondaryBibleFontSize)
    }

    @Test
    fun `fitted lyrics move their range, fixed lyrics their size`() {
        fun profiled(fit: Boolean): AppSettings {
            val projection = plain.projectionSettings
            val profiles = projection.outputProfiles.map {
                it.copy(songSettings = it.songSettings.copy(lyricsFontSizeAutoFit = fit, lyricsFontSize = 70))
            }
            return plain.copy(projectionSettings = projection.copy(outputProfiles = profiles))
        }
        val fitted = withFontStep(profiled(true), ContentScope.SONG, 1)
            .projectionSettings.outputProfiles.first().songSettings
        assertEquals(70, fitted.lyricsFontSize)
        assertEquals(step(60, 1), fitted.lyricsMaxFontSize)
        val fixed = withFontStep(profiled(false), ContentScope.SONG, 1)
            .projectionSettings.outputProfiles.first().songSettings
        assertEquals(step(70, 1), fixed.lyricsFontSize)
    }

    @Test
    fun `a step is a tenth, at least one point, kept readable`() {
        assertEquals(77, step(70, 1))
        assertEquals(13, step(12, 1))
        assertEquals(MIN_FONT_SIZE, step(MIN_FONT_SIZE, -1))
        assertEquals(MAX_FONT_SIZE, step(MAX_FONT_SIZE, 1))
    }

    @Test
    fun `only the sections a change touched are reverted, and not over a later edit`() {
        val after = plain.copy(hiddenTabs = setOf("WEB"))
        val sections = changedSections(plain, after)
        assertEquals(setOf(SettingsSection.HIDDEN_TABS), sections)
        assertEquals(plain.hiddenTabs, revertSections(after, plain, after, sections)?.hiddenTabs)
        val later = after.copy(hiddenTabs = setOf("WEB", "QA"))
        assertNull(revertSections(later, plain, after, sections))
        val all = SettingsSection.entries.toSet()
        val bg = withBackgroundColor(plain, ContentScope.SONG, "#123456")
        assertEquals(plain.backgroundSettings, revertSections(bg, plain, bg, all)?.backgroundSettings)
        assertEquals(setOf(SettingsSection.BACKGROUND), changedSections(plain, bg))
        val font = withFontStep(plain, ContentScope.SONG, 1)
        assertEquals(setOf(SettingsSection.PROJECTION), changedSections(plain, font))
    }
}
