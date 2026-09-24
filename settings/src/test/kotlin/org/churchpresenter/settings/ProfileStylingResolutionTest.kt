package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What an output renders with for captions, Q&A, the dictionary card, subtitles and scaling, once
 * those are styled per profile: the profile's look, with [STT_GLOBAL_KEYS] and [QA_GLOBAL_KEYS] --
 * the caption server, the Q&A rate limit, voting and the QR message -- still the document's.
 */
class ProfileStylingResolutionTest {

    private val global = AppSettings(
        sttSettings = STTSettings(
            serverUrl = "http://stt.local",
            lastConnectedUrl = "http://stt.local",
            textColor = "#FFFFFF",
        ),
        qaSettings = QASettings(rateLimitCooldownSeconds = 45, votingEnabled = true, qrCodeMessage = "Ask away"),
        dictionarySettings = DictionarySettings(wordColor = "#FFFFFF"),
        mediaSettings = MediaSettings(textColor = "#FFFFFF"),
        pictureSettings = PictureSettings(scaleMode = OutputScaleMode.FIT),
        mediaScaleMode = OutputScaleMode.FIT,
    )

    private val profile = OutputProfile(
        id = "foyer",
        sttSettings = STTSettings(serverUrl = "http://elsewhere", textColor = "#FFFF00", maxSegments = 2),
        qaSettings = QASettings(
            rateLimitCooldownSeconds = 1,
            votingEnabled = false,
            qrCodeMessage = "x",
            textColor = "#00FF00",
        ),
        dictionarySettings = DictionarySettings(wordColor = "#FF00FF"),
        mediaSettings = MediaSettings(textColor = "#00FFFF"),
        pictureScaleMode = OutputScaleMode.FILL,
        mediaScaleMode = OutputScaleMode.STRETCH,
    )

    private val rendered = global.resolvedFor(profile)

    @Test
    fun `captions look as the profile says, down to how many segments it keeps`() {
        assertEquals("#FFFF00", rendered.sttSettings.textColor)
        assertEquals(2, rendered.sttSettings.maxSegments)
    }

    @Test
    fun `the caption server stays the install's`() {
        assertEquals("http://stt.local", rendered.sttSettings.serverUrl)
        assertEquals("http://stt.local", rendered.sttSettings.lastConnectedUrl)
    }

    @Test
    fun `a question looks as the profile says`() {
        assertEquals("#00FF00", rendered.qaSettings.textColor)
    }

    @Test
    fun `how the audience submits questions stays the install's`() {
        assertEquals(45, rendered.qaSettings.rateLimitCooldownSeconds)
        assertEquals(true, rendered.qaSettings.votingEnabled)
        assertEquals("Ask away", rendered.qaSettings.qrCodeMessage)
    }

    @Test
    fun `the dictionary card and subtitles are the profile's whole`() {
        assertEquals("#FF00FF", rendered.dictionarySettings.wordColor)
        assertEquals("#00FFFF", rendered.mediaSettings.textColor)
    }

    @Test
    fun `pictures and video scale as the profile says`() {
        assertEquals(OutputScaleMode.FILL, rendered.pictureSettings.scaleMode)
        assertEquals(OutputScaleMode.STRETCH, rendered.mediaScaleMode)
    }

    @Test
    fun `the rest of the picture settings stay the document's`() {
        val withFolder = global.copy(pictureSettings = global.pictureSettings.copy(storageDirectory = "/pics"))
        assertEquals("/pics", withFolder.resolvedFor(profile).pictureSettings.storageDirectory)
    }

    @Test
    fun `two profiles resolve independently from one document`() {
        val other = OutputProfile(id = "stage")
        assertEquals("#FFFF00", global.resolvedFor(profile).sttSettings.textColor)
        assertEquals(STTSettings().textColor, global.resolvedFor(other).sttSettings.textColor)
        assertEquals(OutputScaleMode.FIT, global.resolvedFor(other).pictureSettings.scaleMode)
    }
}
