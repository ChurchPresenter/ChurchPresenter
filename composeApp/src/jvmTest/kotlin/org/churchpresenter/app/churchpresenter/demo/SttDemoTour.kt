@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import org.churchpresenter.profiles.ProfilesSettingsTab
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.stt.transcribe
import org.churchpresenter.stt.transcribeInProgress
import org.churchpresenter.stt.translate
import org.churchpresenter.stt.translateInProgress
import kotlin.test.Test

/** The STT tab: live captions from a speech-to-text server, English with a Spanish translation. */
class SttDemoTour {
    @Test
    fun stt() = recordTour(
        "stt",
        tab = "STT",
        liveLabel = "Live captions",
        steps = 8,
        // Hidden at first, as on a fresh install, so the tour can show turning it on.
        settings = { bothLanguages(threeScreens(it)).copy(hiddenTabs = setOf("QA", "STT", "SIGNAGE")) },
        panel = { settings, change ->
            Box(Modifier.size(PROFILES_W.dp, PROFILES_H.dp)) { ProfilesSettingsTab(settings, change) }
        },
    ) { sttTour() }

    private fun DemoTour.sttTour() {
        say("intro")
        intro("Live captions and translation, on every screen.")
        say("what")
        caption("Speech to text", "Spoken words, written as they're said")
        say("server")
        caption("A Whisper-based server", "On its own computer, on your network")
        say("show-tab")
        val visibility = find(hasContentDescription("Tab Visibility", substring = true))
        step("Tab Visibility", "Tick STT to show it", visibility, numbered = true)
        menusInPlace = true
        runCatching { quietClick(find(hasText("STT")) { it.top > dp(MENU_TOP_DP) }) }
        quietClick(find(hasContentDescription("Tab Visibility", substring = true)))
        menusInPlace = false
        say("open-tab")
        openTab("STT", "Live captions")
        say("url")
        val url = find(hasSetTextAction()) { it.left > dp(TAB_AREA_LEFT_DP) }
        type("STT Server URL", "Your speech-to-text server", url, SERVER_URL)
        say("connect")
        // Connect and Go Live are icon buttons, named by their descriptions.
        val connect = find(hasContentDescription("Connect") or hasText("Connect")) { it.left > dp(TAB_AREA_LEFT_DP) }
        note("Connect", "Then watch the status line", connect)
        demoStt.applyConnected()
        ui.waitForIdle()
        pause(0.4)
        val preview = find(hasText("Live Preview", ignoreCase = true)) { it.left > dp(TAB_AREA_LEFT_DP) }
        say("preview")
        noteWhile("Live preview", "Words appear as they're spoken", preview) {
            feed(0, translated = false)
            feed(1, translated = false)
        }
        say("translation")
        noteWhile("Translation", "Spoken in English, shown in Spanish", preview) {
            demoStt.translate(*SPANISH.take(2).toTypedArray())
            pause(0.6)
            feed(2, translated = true)
        }
        say("go-live")
        val goLive = find(hasContentDescription("Go Live") or hasText("Go Live")) { it.left > dp(TAB_AREA_LEFT_DP) }
        step("Go Live", "Captions on every screen", goLive)
        say("watch")
        captionWhile("On the projector", "English with Spanish underneath", projector = true) {
            feed(3, translated = true)
            feed(4, translated = true)
        }
        say("detect")
        note("STT Display Settings", "Scripture detection", find(hasContentDescription("STT Display Settings")))
        captionLooks()
        say("tips")
        caption("Tips", "A close mic, and a test before the service")
        say("outro")
        outro()
    }

    /** Profiles → Live captions: how each screen draws them. */
    private fun DemoTour.captionLooks() {
        say("look")
        openPanel("Settings — Profiles", PROFILES_W, PROFILES_H)
        panelNote("Live captions", "Set for each screen", rail("Live captions"), click = true, numbered = true)
        say("presentation")
        point("Presentation", "Roll-up, pop-on, ticker, a few words at a time", hasText("Roll-up"))
        say("shape")
        point("Shape", "A card, or a full-width band", hasText("Full-width band"))
        say("reading")
        point("Easier to read", "Fade, speed limit, slide up, dim", hasText("Reading speed limit"))
        say("languages")
        point("Two languages", "Stacked, side by side or interleaved", hasText("Interleaved"))
        closePanel()
    }

    /**
     * Sentence [i] of the sermon, spoken a few words at a time the way the server sends it: the words
     * still being said, then the finished sentence; and, when [translated], its Spanish after it.
     */
    private fun DemoTour.feed(i: Int, translated: Boolean) {
        val words = ENGLISH[i].split(" ")
        for (n in WORDS_PER_STEP..words.size step WORDS_PER_STEP) {
            demoStt.transcribeInProgress(words.take(n).joinToString(" "))
            pause(WORD_STEP_S)
        }
        demoStt.transcribe(*ENGLISH.take(i + 1).toTypedArray())
        demoStt.transcribeInProgress("")
        pause(WORD_STEP_S)
        if (!translated) return
        val spanish = SPANISH[i].split(" ")
        for (n in WORDS_PER_STEP..spanish.size step WORDS_PER_STEP) {
            demoStt.translateInProgress(spanish.take(n).joinToString(" "))
            pause(WORD_STEP_S)
        }
        demoStt.translate(*SPANISH.take(i + 1).toTypedArray())
        demoStt.translateInProgress("")
        pause(WORD_STEP_S)
    }
}

/** Every screen shows the spoken words and their translation together. */
private fun bothLanguages(s: AppSettings): AppSettings = s.copy(
    sttSettings = s.sttSettings.copy(displayMode = BOTH),
    projectionSettings = s.projectionSettings.copy(
        outputProfiles = s.projectionSettings.outputProfiles.map { p ->
            p.copy(sttSettings = p.sttSettings.copy(displayMode = BOTH))
        },
    ),
)

private const val BOTH = "both"
private const val MENU_TOP_DP = 60f
private const val SERVER_URL = "http://192.168.1.20:8000"
private const val TAB_AREA_LEFT_DP = 380f
private const val WORDS_PER_STEP = 3
private const val WORD_STEP_S = 0.35
private const val PROFILES_W = 1400f
private const val PROFILES_H = 900f

private val ENGLISH = listOf(
    "Good morning, church. This morning we are in the Gospel of John, chapter fifteen.",
    "Jesus says, I am the vine, and you are the branches.",
    "Whoever remains in me will bear much fruit.",
    "Apart from him, we can do nothing.",
    "So this week, let us stay close to the vine.",
)

private val SPANISH = listOf(
    "Buenos días, iglesia. Esta mañana estamos en el Evangelio de Juan, capítulo quince.",
    "Jesús dice: yo soy la vid y ustedes son las ramas.",
    "El que permanece en mí dará mucho fruto.",
    "Separados de él, no podemos hacer nada.",
    "Así que esta semana, permanezcamos cerca de la vid.",
)
