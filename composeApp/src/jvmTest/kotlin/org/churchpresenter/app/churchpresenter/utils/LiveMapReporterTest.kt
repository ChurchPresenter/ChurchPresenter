package org.churchpresenter.app.churchpresenter.utils

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.UpdateCheckInterval
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * [LiveMapReporter] fires an anonymous city-level ping on launch. The launch/retry is network and
 * timing bound, but the decisions that shape the request are pure: which os tag the platform maps
 * to, which setup facts are derived from the settings, and how the ping url is assembled —
 * including the dev-build split that keeps IDE/`run` launches out of the real-user stats, and the
 * opt-in updateCheck parameter.
 */
class LiveMapReporterTest {

    @Test
    fun `os names map to the website's platform tags`() {
        assertEquals("windows", LiveMapReporter.osTag("Windows 11"))
        assertEquals("macos", LiveMapReporter.osTag("Mac OS X"))
        assertEquals("linux", LiveMapReporter.osTag("Linux"))
    }

    @Test
    fun `an unrecognised os is reported as unknown rather than guessed`() {
        assertEquals("unknown", LiveMapReporter.osTag("SunOS"))
        assertEquals("unknown", LiveMapReporter.osTag(""))
    }

    @Test
    fun `the ping url carries platform, os and version`() {
        val url = LiveMapReporter.buildPingUrl(
            os = "macos", version = "26.1.0", updateCheckInterval = null, isDevBuild = false,
        )
        assertTrue(url.startsWith("https://www.churchpresenter.org/api/ping?"), url)
        assertTrue("platform=desktop" in url)
        assertTrue("os=macos" in url)
        assertTrue("version=26.1.0" in url)
    }

    @Test
    fun `a dev build is tagged with the dev source, a release build is not`() {
        val dev = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = true)
        assertTrue("src=dev" in dev, dev)

        val release = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = false)
        assertFalse("src=dev" in release, release)
    }

    @Test
    fun `build provenance is included when known and omitted when not`() {
        val known = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            repoSlug = "churchpresenter/churchpresenter", commit = "a1b2c3d", buildType = "release",
        )
        assertTrue("repo=churchpresenter/churchpresenter" in known, known)
        assertTrue("commit=a1b2c3d" in known, known)
        assertTrue("build=release" in known, known)

        // A source-tarball build has no git at all — the server treats the
        // missing params the same as unrecognised ones and still counts it.
        val unknown = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            repoSlug = "unknown", commit = "unknown", buildType = "nogit",
        )
        assertFalse("repo=" in unknown, unknown)
        assertFalse("commit=" in unknown, unknown)
        assertTrue("build=nogit" in unknown, unknown)
    }

    @Test
    fun `provenance fields that are blank are omitted, like the unknown ones`() {
        // A build made outside git leaves these empty rather than "unknown", and an empty
        // repo=/commit= on the wire would be counted as a value by the server.
        val blank = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            repoSlug = "", commit = "", buildType = "",
        )
        assertFalse("repo=" in blank, blank)
        assertFalse("commit=" in blank, blank)
        assertFalse("build=" in blank, blank)
    }

    @Test
    fun `a fork build reports its own repo slug`() {
        val fork = LiveMapReporter.buildPingUrl(
            "windows", "26.1.0", null, isDevBuild = false,
            repoSlug = "someone/churchpresenter-fork", commit = "deadbeef", buildType = "release",
        )
        assertTrue("repo=someone/churchpresenter-fork" in fork, fork)
    }

    @Test
    fun `the configured update-check interval is included when set and omitted when null`() {
        val withInterval = LiveMapReporter.buildPingUrl(
            "windows", "26.1.0", UpdateCheckInterval.WEEKLY, isDevBuild = false,
        )
        assertTrue("updateCheck=weekly" in withInterval, withInterval)

        val without = LiveMapReporter.buildPingUrl("windows", "26.1.0", null, isDevBuild = false)
        assertFalse("updateCheck" in without, without)
    }

    private fun settings(
        bibles: List<String> = emptyList(),
        outputs: List<ScreenAssignment> = emptyList(),
    ) = AppSettings(
        bibleSettings = BibleSettings(translations = bibles.map { BibleTranslationSettings(fileName = it) }),
        projectionSettings = ProjectionSettings(screenAssignments = outputs),
    )

    private fun output(displayMode: String, targetDisplay: Int = 0) =
        ScreenAssignment(targetDisplay = targetDisplay, displayMode = displayMode)

    @Test
    fun `setup facts count the attached screens and the configured translation stack`() {
        val facts = LiveMapReporter.setupFacts(
            settings(bibles = listOf("kjv1769.spb", "rst.spb", "ukr.spb")),
            screenCount = 3,
        )
        assertEquals(3, facts.screens)
        assertEquals(3, facts.bibles)
    }

    @Test
    fun `an install with no translations configured reports none rather than failing`() {
        assertEquals(0, LiveMapReporter.setupFacts(settings(), screenCount = 1).bibles)
    }

    @Test
    fun `the legacy primary-secondary pair still counts as two translations`() {
        // A settings file written before the translation list existed — translationList() falls
        // back to the pair, so the count must too.
        val legacy = AppSettings(
            bibleSettings = BibleSettings(primaryBible = "kjv1769.spb", secondaryBible = "rst.spb")
        )
        assertEquals(2, LiveMapReporter.setupFacts(legacy, screenCount = 1).bibles)
    }

    @Test
    fun `stage monitor and lower third are reported from the output display modes`() {
        val facts = LiveMapReporter.setupFacts(
            settings(
                outputs = listOf(
                    output(Constants.DISPLAY_MODE_FULLSCREEN),
                    output(Constants.DISPLAY_MODE_STAGE_MONITOR),
                    output(Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL),
                )
            ),
            screenCount = 3,
        )
        assertTrue(facts.stageMonitor)
        assertTrue(facts.lowerThird)
    }

    @Test
    fun `a fullscreen-only setup reports neither`() {
        val facts = LiveMapReporter.setupFacts(
            settings(outputs = listOf(output(Constants.DISPLAY_MODE_FULLSCREEN))),
            screenCount = 1,
        )
        assertFalse(facts.stageMonitor)
        assertFalse(facts.lowerThird)
    }

    @Test
    fun `an output with no display chosen is not counted as in use`() {
        val facts = LiveMapReporter.setupFacts(
            settings(
                outputs = listOf(
                    output(Constants.DISPLAY_MODE_STAGE_MONITOR, targetDisplay = Constants.KEY_TARGET_NONE),
                    output(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, targetDisplay = Constants.KEY_TARGET_NONE),
                )
            ),
            screenCount = 1,
        )
        assertFalse(facts.stageMonitor, "a stage monitor switched off is not a stage monitor in use")
        assertFalse(facts.lowerThird, "a lower third switched off is not a lower third in use")
    }

    @Test
    fun `the ping url carries the setup facts that are set`() {
        val url = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            setup = LiveMapReporter.SetupFacts(
                screens = 2, bibles = 3, stageMonitor = true, lowerThird = true,
            ),
        )
        assertTrue("screens=2" in url, url)
        assertTrue("bibles=3" in url, url)
        assertTrue("stageMonitor=true" in url, url)
        assertTrue("lowerThird=true" in url, url)
    }

    @Test
    fun `unknown or unused setup facts are omitted rather than sent as zero`() {
        val url = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = false)
        assertFalse("screens" in url, url)
        assertFalse("bibles" in url, url)
        assertFalse("stageMonitor" in url, url)
        assertFalse("lowerThird" in url, url)
    }

    @Test
    fun `usage events are appended under their own wire names`() {
        val url = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            events = mapOf(UsageEvent.SONG_DUAL_LANGUAGE to 7),
        )
        assertTrue("${UsageEvent.SONG_DUAL_LANGUAGE.param}=7" in url, url)
    }

    @Test
    fun `every output kind reaches the ping under its own name`() {
        // NDI and Browser Source are counted alongside DeckLink, so the three audience output
        // kinds can be compared against each other rather than only SDI being visible.
        val url = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            events = mapOf(
                UsageEvent.DECKLINK_OUTPUT to 1,
                UsageEvent.NDI_OUTPUT to 1,
                UsageEvent.BROWSER_SOURCE_OUTPUT to 2,
            ),
        )
        assertTrue("decklinkOutput=1" in url, url)
        assertTrue("ndiOutput=1" in url, url)
        assertTrue("browserSourceOutput=2" in url, url)
    }

    @Test
    fun `an event with nothing new is not sent at all`() {
        val none = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = false)
        assertFalse(UsageEvent.SONG_DUAL_LANGUAGE.param in none, none)

        val zero = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            events = mapOf(UsageEvent.SONG_DUAL_LANGUAGE to 0),
        )
        assertFalse(UsageEvent.SONG_DUAL_LANGUAGE.param in zero, zero)
    }

    @Test
    fun `the interface language is reported, and omitted when unset`() {
        val ru = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            setup = LiveMapReporter.SetupFacts(language = "ru"),
        )
        assertTrue("lang=ru" in ru, ru)
        assertFalse("lang=" in LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = false))
    }

    @Test
    fun `setup facts carry the configured interface language`() {
        val facts = LiveMapReporter.setupFacts(AppSettings(language = "uk"), screenCount = 1)
        assertEquals("uk", facts.language)
    }

    @Test
    fun `a standalone instance reports its events`() {
        val pending = mapOf(UsageEvent.SONG_DUAL_LANGUAGE to 3)
        assertEquals(pending, LiveMapReporter.eventsToReport(AppSettings(), pending))
    }

    @Test
    fun `a linked instance reports none of them, so one service is counted once`() {
        val pending = mapOf(UsageEvent.SONG_DUAL_LANGUAGE to 3)
        val linked = AppSettings(instanceLink = InstanceLinkSettings(enabled = true))
        assertTrue(LiveMapReporter.eventsToReport(linked, pending).isEmpty())
    }

    @Test
    fun `library size and the previous session length are reported when known`() {
        val url = LiveMapReporter.buildPingUrl(
            "linux", "26.1.0", null, isDevBuild = false,
            setup = LiveMapReporter.SetupFacts(songbooks = 3, songs = 812, sessionMinutes = 95),
        )
        assertTrue("songbooks=3" in url, url)
        assertTrue("songs=812" in url, url)
        assertTrue("sessionMinutes=95" in url, url)

        val empty = LiveMapReporter.buildPingUrl("linux", "26.1.0", null, isDevBuild = false)
        assertFalse("songs" in empty, empty)
        assertFalse("sessionMinutes" in empty, empty)
    }

    @Test
    fun `setup facts carry the song counts and session length they are given`() {
        val facts = LiveMapReporter.setupFacts(
            AppSettings(), screenCount = 2, songCounts = 4 to 900, sessionMinutes = 12,
        )
        assertEquals(4, facts.songbooks)
        assertEquals(900, facts.songs)
        assertEquals(12, facts.sessionMinutes)
    }

    @Test
    fun `song counts from an unset song folder are zero rather than an error`() {
        assertEquals(0 to 0, LiveMapReporter.gatherSongCounts(AppSettings()))
    }

    @Test
    fun `a graphics stack that will not come up counts no screens rather than ending the app`() {
        // The usage ping asks how many displays are attached, and bringing AWT's graphics
        // environment up loads the platform's native font manager. When that cannot load the JVM
        // raises ExceptionInInitializerError — an Error, which `catch (Exception)` does not see —
        // so a line that exists to put a number in a ping killed the app before its window opened.
        assertEquals(0, LiveMapReporter.detectScreenCount { throw ExceptionInInitializerError("no GE") })
        assertEquals(0, LiveMapReporter.detectScreenCount { throw NoClassDefFoundError("LocalGE") })
    }

    @Test
    fun `a machine that answers is reported as it answered`() {
        assertEquals(3, LiveMapReporter.detectScreenCount { 3 })
    }

    private fun pingThatFails(times: Int): Pair<suspend () -> Boolean, () -> Int> {
        var calls = 0
        val ping: suspend () -> Boolean = {
            calls++
            calls > times
        }
        return ping to { calls }
    }

    private fun retry(ping: suspend () -> Boolean, quick: Int = 3, slow: Int = 15) = runBlocking {
        LiveMapReporter.pingWithRetry(
            ping,
            quickAttempts = quick, quickDelay = Duration.ZERO,
            slowAttempts = slow, slowDelay = Duration.ZERO,
        )
    }

    @Test
    fun `a first-try success pings exactly once`() {
        val (ping, calls) = pingThatFails(0)
        assertTrue(retry(ping), "a delivered ping reports success, which is what releases the events")
        assertEquals(1, calls(), "a success on the first attempt must not retry")
    }

    @Test
    fun `it stops as soon as a quick retry succeeds`() {
        val (ping, calls) = pingThatFails(2) // 1st and 2nd fail, 3rd succeeds
        retry(ping)
        assertEquals(3, calls(), "stops on the third (successful) quick attempt")
    }

    @Test
    fun `it falls through to the slow retries when every quick attempt fails`() {
        val (ping, calls) = pingThatFails(3) // 3 quick fail, then succeeds on the 1st slow
        retry(ping)
        assertEquals(4, calls(), "3 quick + 1 slow attempt before success")
    }

    @Test
    fun `it gives up after quick plus slow attempts are exhausted`() {
        val (ping, calls) = pingThatFails(Int.MAX_VALUE) // never succeeds
        assertFalse(retry(ping, quick = 3, slow = 15), "an undelivered ping must not release the events")
        assertEquals(18, calls(), "every attempt tried, then it stops rather than looping forever")
    }

    private class Captured(var hits: Int = 0, var installId: String? = null)

    private fun withServer(block: (url: String, captured: Captured) -> Unit) {
        val captured = Captured()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/ping") { ex ->
            captured.hits++
            captured.installId = ex.requestHeaders.getFirst("X-Install-Id")
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/api/ping?platform=desktop", captured)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `ping sends the request and forwards the install id header`() {
        withServer { url, captured ->
            assertTrue(runBlocking { LiveMapReporter.ping(url, installId = "install-abc") })
            assertEquals(1, captured.hits, "a reachable server is pinged exactly once, with no retry")
            assertEquals("install-abc", captured.installId)
        }
    }

    @Test
    fun `ping omits the install id header when none is supplied`() {
        withServer { url, captured ->
            runBlocking { LiveMapReporter.ping(url, installId = null) }
            assertEquals(1, captured.hits)
            assertNull(captured.installId, "a null id must not send the X-Install-Id header")
        }
    }

    @Test
    fun `ping omits the install id header when the id is blank`() {
        withServer { url, captured ->
            runBlocking { LiveMapReporter.ping(url, installId = "   ") }
            assertEquals(1, captured.hits)
            assertNull(captured.installId, "a blank id is treated as no id")
        }
    }
}
