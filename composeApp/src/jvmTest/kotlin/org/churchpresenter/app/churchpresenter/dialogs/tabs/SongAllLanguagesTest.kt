package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.SongStyleTarget
import org.churchpresenter.presenter.defaultSongElementStyle
import org.churchpresenter.presenter.withElementStyle
import org.churchpresenter.presenter.elementStyle
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTextStyle
import org.churchpresenter.settings.SongTranslationSettings
import org.churchpresenter.settings.translationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A song's All layer: All is the first language's look except where the first language has values of
 * its own, which are kept apart so All can still be read, edited and put back there.
 */
class SongAllLanguagesTest {

    private val lyrics = SongStyleElement.LYRICS
    private val full = SongStyleTarget.FULL_SCREEN
    private val second = SongStyleLanguage.SECONDARY

    private fun size(song: SongSettings, language: SongStyleLanguage) =
        song.elementStyle(lyrics, full, language).fontSize

    private fun SongSettings.allSize() = allLanguagesStyle(lyrics, full).fontSize

    private fun SongSettings.sized(language: SongStyleLanguage?, value: Int): SongSettings {
        val style = if (language == null) allLanguagesStyle(lyrics, full) else elementStyle(lyrics, full, language)
        val edited = style.copy(fontSize = value)
        return when {
            language == null -> withAllLanguagesStyle(lyrics, full, edited)
            !language.isTranslation -> withFirstLanguageStyle(lyrics, full, edited)
            else -> withElementStyle(lyrics, full, language, edited)
        }
    }

    private val song = SongSettings(lyricsFontSize = 70)

    @Test
    fun `with nothing of its own, All is the first language's look`() {
        assertEquals(70, song.allSize())
        assertEquals(emptySet(), song.languageOwnFields(lyrics, full, SongStyleLanguage.PRIMARY))
    }

    @Test
    fun `All reaches every language`() {
        val all = song.sized(null, 60)
        assertEquals(60, size(all, SongStyleLanguage.PRIMARY))
        assertEquals(60, size(all, second))
        assertEquals(60, all.allSize())
    }

    @Test
    fun `the first language's own size leaves All and the others where they were`() {
        val own = song.sized(SongStyleLanguage.PRIMARY, 50)
        assertEquals(50, size(own, SongStyleLanguage.PRIMARY))
        assertEquals(70, own.allSize())
        assertEquals(70, size(own, second), "the second no longer follows the first")
        assertTrue(own.translationSettings(0).overrideStyle, "given All's look of its own first")
        assertEquals(setOf("fontSize"), own.languageOwnFields(lyrics, full, SongStyleLanguage.PRIMARY))
    }

    @Test
    fun `All after that skips the first language's own value but reaches the rest`() {
        val all = song.sized(SongStyleLanguage.PRIMARY, 50).sized(null, 80)
        assertEquals(50, size(all, SongStyleLanguage.PRIMARY))
        assertEquals(80, size(all, second))
        assertEquals(80, all.allSize())
    }

    @Test
    fun `the first language's chip hands its value back to All`() {
        val own = song.sized(SongStyleLanguage.PRIMARY, 50).sized(null, 80)
        val back = own.clearLanguageOwn(lyrics, full, SongStyleLanguage.PRIMARY, setOf("fontSize"))
        assertEquals(80, size(back, SongStyleLanguage.PRIMARY))
        assertEquals(emptySet(), back.languageOwnFields(lyrics, full, SongStyleLanguage.PRIMARY))
    }

    @Test
    fun `another language's own value survives All, and its chip gives All's back`() {
        val own = song.sized(second, 40).sized(null, 90)
        assertEquals(40, size(own, second))
        assertEquals(setOf("fontSize"), own.languageOwnFields(lyrics, full, second))
        val back = own.clearLanguageOwn(lyrics, full, second, setOf("fontSize"))
        assertEquals(90, size(back, second))
    }

    @Test
    fun `an element with no look per language is written to its only look`() {
        val number = SongStyleElement.NUMBER
        val edited = song.withAllLanguagesStyle(number, full, song.elementStyle(number, full).copy(fontSize = 33))
        assertEquals(33, edited.elementStyle(number, full).fontSize)
        val firstOnly = song.withFirstLanguageStyle(number, full, song.elementStyle(number, full).copy(fontSize = 33))
        assertEquals(edited, firstOnly)
        assertEquals(emptySet(), edited.languageOwnFields(number, full, second))
    }

    @Test
    fun `writing the first language's own look unchanged changes nothing`() {
        assertEquals(song, song.withFirstLanguageStyle(lyrics, full, song.elementStyle(lyrics, full)))
    }

    @Test
    fun `a language still following the first owns nothing`() {
        val follower = SongSettings(translations = listOf(SongTranslationSettings(overrideStyle = false)))
        assertEquals(emptySet(), follower.languageOwnFields(lyrics, full, second))
    }

    @Test
    fun `Reset on a picked language gives back only its own values, and under All restores the default`() {
        var current = song.sized(second, 40)
        val picked = SongEdit(current, lyrics, full, second, perLanguage = true) { t -> current = t(current) }
        assertTrue(picked.resettable)
        picked.reset()
        assertEquals(70, size(current, second))
        assertFalse(SongEdit(current, lyrics, full, second, perLanguage = true) {}.resettable)

        current = song.sized(null, 55)
        val all = SongEdit(current, lyrics, full, null, perLanguage = true) { t -> current = t(current) }
        assertTrue(all.resettable)
        all.reset()
        assertEquals(defaultSongElementStyle(lyrics, full).fontSize, current.allSize())
    }

    @Test
    fun `an edit under All or on one language goes to the right place`() {
        var current = song
        SongEdit(current, lyrics, full, second, perLanguage = true) { t -> current = t(current) }
            .write(SongTextStyle(fontSize = 44))
        assertEquals(44, size(current, second))
        assertEquals(70, size(current, SongStyleLanguage.PRIMARY))
        SongEdit(current, lyrics, full, SongStyleLanguage.PRIMARY, perLanguage = true) { t -> current = t(current) }
            .write(current.elementStyle(lyrics, full).copy(fontSize = 66))
        assertEquals(66, size(current, SongStyleLanguage.PRIMARY))
        assertEquals(70, current.allSize())
    }
}
