@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.media.tabs.RecentMediaFiles
import java.io.File
import kotlin.test.Test
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.app.churchpresenter.screenshot.library

/** The Media tour: a video from the recents, played on the big screen, with its sound and subtitles. */
class MediaDemoTour {

    @Test
    fun media() {
        seedRecentMedia()
        recordTour("media", tab = "Media", liveLabel = "Play a video", steps = 10) { mediaTour() }
    }

    private fun DemoTour.mediaTour() {
        say("intro")
        intro("Play videos and music on the big screen.")
        say("open-tab")
        openTab("Media", "Videos, music and live streams")
        say("vlc")
        note("VLC is required", "Free from videolan.org — install it once", text("No media loaded"))
        say("select-file")
        note("Select a file", "Video, audio — and subtitle files", find(hasText("Select File")) { it.left > dp(380f) })
        sources()
        say("recents")
        note("Recent files", "One click brings one back", find(hasText("Welcome Loop.mp4")) { it.left > dp(380f) })
        say("favorite")
        val testimony = find(hasText("Baptism Testimony.mp4")) { it.left > dp(380f) }.bounds
        val star = find(hasContentDescription("Pin")) {
            it.left > testimony.left && it.left < testimony.right + dp(40f)
        }
        step("Pin a favorite", "Pinned files stay at the front", star)
        say("open-recent")
        val loop = find(hasText("Welcome Loop.mp4")) { it.left > dp(380f) }
        step("Open a file", "It loads, ready to play", loop) {
            loop.node.performClickSafely()
            waitFor("the video is loaded", 30_000L) { !hasNode(hasText("No media loaded")) }
        }
        say("go-live")
        goLive(sub = "Plays it on the projector")
        say("live")
        showLive(sub = "The welcome loop is playing")
        controls()
        say("preset")
        note(
            "Save a preset",
            "A named shortcut to this video for any future service",
            find(hasContentDescription("Save preset")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule")
        step(
            "Add it to the schedule",
            "Play it at the right moment in the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule-row")
        val row = reveal { findAll(hasText("Welcome Loop")) { it.right < dp(380f) }.last() }
        note("Added to your schedule", "Run the whole service from this list", row)
        say("outro")
        outro()
    }

    /** Local file or network URL: the two places a video can come from. */
    private fun DemoTour.sources() {
        say("network")
        step("Network URL", "Play a stream or a video on the web", find(hasText("Network URL")))
        say("url")
        type(
            "Paste the address",
            "http, https, RTSP — anything VLC can open",
            searchField(),
            "https://stream.example.church/live.m3u8",
        )
        say("local")
        step("Back to Local File", "Files on this computer", find(hasText("Local File")))
    }

    /** Playback, loop, subtitles and sound. */
    private fun DemoTour.controls() {
        say("transport")
        note("Play, pause and seek", "Skip back or forward, or stop", find(hasContentDescription("Stop")))
        say("loop")
        val loop = find(hasContentDescription("Loop", substring = true))
        note("Loop", "Repeat it — a set number of times, or forever", loop)
        say("subtitles")
        val subtitles = find(hasContentDescription("Subtitles", substring = true))
        step("Subtitles", "Built into the video, or from a file", subtitles)
        say("subtitle-menu")
        step("Choose a track", "Or load an .srt or .vtt file", menuItem("Off"))
        say("volume")
        note("Volume", "Drag to set how loud it plays", text("100%"))
        say("mute")
        val mute = find(hasContentDescription("Mute"))
        step("Mute", "Silence it in one click", mute)
        say("unmute")
        val unmute = find(hasContentDescription("mute", substring = true, ignoreCase = true))
        step("And back on", "The volume is just as you left it", unmute)
    }
}

/** Clicks, tolerating a control that moved while the file loaded. */
private fun SemanticsNodeInteraction.performClickSafely() {
    runCatching { performClick() }
}

/** The two files the recent chips show, as the app preview's library has them. */
private fun seedRecentMedia() {
    library()
    RecentMediaFiles.paths.clear()
    RecentMediaFiles.pinned.clear()
    RecentMediaFiles.paths.addAll(
        listOf(
            File(LIBRARY, "Media/Welcome Loop.mp4").absolutePath,
            File(LIBRARY, "Media/Baptism Testimony.mp4").absolutePath,
        ),
    )
}
