package org.churchpresenter.app.churchpresenter

import org.churchpresenter.bibletab.ContinuationSpeed
import org.churchpresenter.bibletab.EngineStart
import org.churchpresenter.bibletab.LiveReference
import org.churchpresenter.bibletab.TextMatchLevel
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainDesktopBibleEngineGateTest {

    private fun settingsFor(vararg fileNames: String) = BibleSettings(
        translations = fileNames.map { BibleTranslationSettings(fileName = it) },
    )

    @Test
    fun `the engine runs only when connected, enabled and given something to index`() {
        assertTrue(shouldRunBibleEngine(sttConnected = true, engineEnabled = true, engineBibles = listOf("kjv.spb")))
    }

    @Test
    fun `no speech feed means no engine, however it is configured`() {
        assertFalse(shouldRunBibleEngine(sttConnected = false, engineEnabled = true, engineBibles = listOf("kjv.spb")))
    }

    @Test
    fun `the engine stays off while switched off`() {
        assertFalse(shouldRunBibleEngine(sttConnected = true, engineEnabled = false, engineBibles = listOf("kjv.spb")))
    }

    @Test
    fun `with no bibles installed there is nothing to match against`() {
        assertFalse(shouldRunBibleEngine(sttConnected = true, engineEnabled = true, engineBibles = emptyList()))
    }

    @Test
    fun `the index key is the translation file names`() {
        assertEquals(listOf("kjv.spb", "niv.spb"), engineBibleFiles(settingsFor("kjv.spb", "niv.spb")))
    }

    @Test
    fun `swapping primary and secondary does not change the key, so the engine keeps running`() {
        assertEquals(
            engineBibleFiles(settingsFor("kjv.spb", "niv.spb")),
            engineBibleFiles(settingsFor("niv.spb", "kjv.spb")),
        )
    }

    @Test
    fun `switching to a different translation does change the key`() {
        assertTrue(
            engineBibleFiles(settingsFor("kjv.spb")) != engineBibleFiles(settingsFor("niv.spb")),
            "a different bible has to re-index, or the engine matches against the old text",
        )
    }

    @Test
    fun `adding a translation changes the key`() {
        assertTrue(
            engineBibleFiles(settingsFor("kjv.spb")) != engineBibleFiles(settingsFor("kjv.spb", "niv.spb")),
        )
    }

    @Test
    fun `no translations yields an empty key, which is what keeps the engine off`() {
        val key = engineBibleFiles(BibleSettings())
        assertTrue(key.isEmpty())
        assertFalse(shouldRunBibleEngine(sttConnected = true, engineEnabled = true, engineBibles = key))
    }

    @Test
    fun `the key ignores styling, so recolouring a translation does not re-index`() {
        val plain = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")))
        val restyled = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb", textColor = "#123456", textFontSize = 12),
            ),
        )
        assertEquals(engineBibleFiles(plain), engineBibleFiles(restyled))
    }

    @Test
    fun `the engine starts on the speech feed, the bible folder and its own settings`() {
        val settings = AppSettings(
            sttSettings = STTSettings(serverUrl = "http://stt:9000"),
            bibleSettings = BibleSettings(storageDirectory = "/bibles"),
            bibleEngineSettings = BibleEngineSettings(runLocal = false, host = "engine", port = 9100),
        )
        val start = bibleEngineStart(settings, listOf("kjv.spb"), TextMatchLevel.AGGRESSIVE, ContinuationSpeed.FAST)
        assertEquals(
            EngineStart(
                sttUrl = "http://stt:9000",
                bibleRoot = "/bibles",
                bibleFiles = listOf("kjv.spb"),
                runLocal = false,
                host = "engine",
                port = 9100,
                level = "aggressive",
                continuationSpeed = "fast",
            ),
            start,
        )
    }

    @Test
    fun `an auto-follow detection is logged from its first verse, and nothing selected logs nothing`() {
        val verses = listOf(
            SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16),
            SelectedVerse(bookName = "John", chapter = 3, verseNumber = 17),
        )
        assertEquals(
            LiveReference(
                displayBookIndex = 42,
                chapter = 3,
                verseStart = 16,
                verseEnd = null,
                source = "auto",
                autoFollow = true,
                matchType = "exact",
            ),
            autoFollowLiveReference(verses, 42, "exact"),
        )
        assertNull(autoFollowLiveReference(emptyList(), 42, null))
    }
}
